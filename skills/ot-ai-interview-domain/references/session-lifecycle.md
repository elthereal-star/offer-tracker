# 会话生命周期

全部入口在 `AiInterviewService`。除 `finish` 外都带 `@Transactional`；`finish` 的**事务边界由方法内部的 `TransactionTemplate` 分两段控制**，原因见下方「收尾为什么被拆成三段」。

## 五个阶段

| 阶段 | 入口 | 调 AI | 行锁 | 业务会话锁 | 主要副作用 |
|---|---|---|---|---|---|
| 出题 | `create` | 是（生成第一题） | 无（此时 sessionId 还不存在） | 无 | 插入会话 + 第一题 + 快照 |
| 作答 | `answer` | **否** | 是 | 无 | 写入 answer、更新会话、快照 |
| 评分 | `evaluate` | 是 | 是 | 无 | 写入 score / feedback、快照 |
| 追问 | `followUp` | 是 | 是 | 无 | 插入下一题、更新会话、快照 |
| 收尾 | `finish` | 是（生成报告） | **无**（改用只读校验） | **是**（但只覆盖毫秒级临界区） | 写入平均分 + 报告 + 终态、快照 |

「行锁」指 `AiInterviewSessionMapper.selectIdForUpdate`（`SELECT ... FOR UPDATE`），它同时承担**存在性校验**与**归属校验**：

```sql
SELECT id FROM ai_interview_sessions
 WHERE id = #{id} AND (#{ownerId} IS NULL OR owner_id = #{ownerId}) FOR UPDATE
```

`ownerId` 为空表示不校验归属（未登录场景），这是刻意的：默认 profile 下 `offer-tracker.auth.required=false`。

## 各阶段的守卫顺序

顺序是契约的一部分，改动时必须保持一致：

- **`answer`**：行锁 → 会话必须 `ACTIVE` → 题目必须属于该会话（否则 404）
- **`evaluate`**：行锁 → 必须 `ACTIVE` → 题目存在（404）→ 必须先有作答（409「请先提交回答」）
- **`followUp`**：行锁 → 交给 LiteFlow 链 `aiInterviewFollowUpChain` 裁决（顺序见下）
- **`finish`**：业务会话锁 → 行锁 → 已 `COMPLETED` 则幂等返回 → 至少一道题有评分（409「请至少完成一道题目的 AI 评分」）

## 追问的守卫顺序（已抽成 LiteFlow 链）

`followUp` 现在只做三件事：加行锁 → 执行链 → 把链的裁决翻译成 HTTP 语义。
判定逻辑拆成了 `src/main/resources/liteflow/ai-interview-followup.xml` 里的 5 个节点，顺序即守卫顺序：

```
THEN(requireActiveGuard, loadFollowUpContext, requireLatestQuestionGuard, requireEvaluationGuard, followUpDecisionFinalize)
```

| 节点 | 职责 | 对应裁决 |
|---|---|---|
| `requireActiveGuard` | 会话必须 `ACTIVE`，否则 409「AI 面试会话已结束」 | `REJECTED(409)` |
| `loadFollowUpContext` | 载入上一题实体 + 会话最新题号 | 题目不存在 → `REJECTED(404)` |
| `requireLatestQuestionGuard` | 只允许对最新一道题追问 | 旧题重复请求 → `SKIPPED` |
| `requireEvaluationGuard` | 必须有 score 与 feedback | 缺评分 → `REJECTED(409)` |
| `followUpDecisionFinalize` | 算下一题号 + 拼提示词 | `READY` |

裁决结果写入 `AiFollowUpContext`，由 Service 翻译：`REJECTED` → `BusinessException(code, message)`；`SKIPPED` → `return get(sessionId)`；`READY` → 继续调 AI 出题。

**改这条链时注意两点**：一是节点顺序不能动，它就是原来的 if 顺序；二是节点的 `process()` 必须先判 `context.decided()`，否则前一个节点已作出的结论会被覆盖。

## 追问的短路语义

`requireLatestQuestionGuard` 判定的这一条是**幂等**而非报错，容易在重构时被误改成异常：

```java
if (previous.getQuestionNo() < latestQuestionNo) context.skip();
```

含义是：**给一道已经被追问过的旧题再次请求追问时，不报错、不重复出题，直接返回当前会话。** 这是为前端重试准备的幂等行为。

## 运行态快照

每个成功变更的阶段末尾都会调用 `AiInterviewRuntimeSnapshotService.checkpoint(session)`，它负责：

- 把会话字段与全部题目序列化成 JSON，写入 `ai_interview_runtime_snapshots`（`version` 每写一次 +1）
- 追加到 `AiRuntimeArchive`（默认 profile 下是 `Noop`，激活 `mongo-archive` 才真正写 Mongo）
- 向 `AiInterviewEventHub` 推送 `snapshot` 事件（仅 `realtime` profile）

读取侧是 `rehydrate(session)`：**MySQL 快照是权威源，Mongo 只在前者缺失时兜底**，且只补齐缺失字段，不覆盖权威行数据。

## 收尾的 fencing 写入

`finish` 走的是带校验的终态写入，而不是普通 `updateById`：

```java
int fenced = sessions.completeWithFence(sessionId, COMPLETED, average, report, finishedAt, fenceToken);
if (fenced == 0) return resolveFenceRejection(sessionId, fenceToken);
```

令牌由 `AiSessionLock.executeFenced` 在**拿到锁之后**发号。目的是覆盖"锁租约已过期、原持有者仍在跑"的窗口 —— 详情见 `ot-ai-runtime`。

## 收尾为什么被拆成三段

`finish` 是唯一要「读状态 → 判断 → 调 AI → 写终态」的入口，而那次 AI 调用最坏 90 秒、锁租约只有 30 秒。把 AI 调用留在锁与事务里，会同时踩三个坑：租约过期导致旧持有者回写、HikariCP 连接被占满、行锁持有 90 秒（超过 MySQL 默认的 50 秒锁等待）把同会话的 `answer`/`evaluate` 顶到超时。

所以它被拆成三段，**只有第一、三段在事务里**：

| 段 | 做什么 | 是否持锁 | 是否在事务里 | 耗时 |
|---|---|---|---|---|
| ① `planFinish` | 校验存在性/归属、必须 `ACTIVE`、至少一道已评分、算平均分、拼提示词 | 否 | 是（短） | 毫秒 |
| ② | `checkAiRequestAllowed()` + 调 AI 生成报告 | 否 | **否** | 最长 90 秒 |
| ③ `commitFinish` | 取会话锁 → 发 fencing 令牌 → 条件写入 + 快照 | 是（短） | 是（短） | 毫秒 |

```java
public AiInterviewSessionResponse finish(Long sessionId) {
    FinishPlan plan = transactions.execute(status -> planFinish(sessionId));
    if (plan.alreadyCompleted() != null) return plan.alreadyCompleted();
    checkAiRequestAllowed();
    String report = singleFlight.execute("finish:" + ownerKey() + ":" + sessionId, () -> ai.chat(...)).trim();
    return operationLock.executeFenced(sessionId,
            fenceToken -> transactions.execute(status -> commitFinish(sessionId, plan.average(), report, fenceToken)));
}
```

几个关键点：

- **`finish` 上没有 `@Transactional`。** `AiInterviewFinalizeTest.finishDoesNotRunInsideASingleTransaction` 用反射断言这一点，防止后来人顺手加回去。
- **第一段用只读的 `loadSessionOrThrow`，不再用 `lockSessionOrThrow`。** 校验阶段本来就不该持行锁。
- **第二段不持锁也能保证只调一次 AI。** 因为 single-flight 的 key 是 `finish:{ownerKey}:{sessionId}`，两个并发收尾会被折叠成同一次调用、拿到同一份报告。所以"必须持锁跨过 AI 调用"这个前提本来就不成立。
- **第三段重新读一次会话状态**（`commitFinish` 里的第一个判断），因为第一段到第三段之间状态可能已经变了。已 `COMPLETED` 就直接幂等返回。
- ②③ 之间隔了几十秒，但**第 ① 段算出的 prompt 会被一直带着**，所以重算不会发生。

被 fencing 拒绝时的收口语义（幂等返回还是 409）见 `ot-ai-runtime` 的 `locking-and-fencing.md`。
