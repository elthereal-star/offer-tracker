# 投递与执行链路

链路分三段：**入库（事务内）→ Outbox 投递 → Worker 消费**。中间用 `dispatchStatus` 串起来，而不是直接"入库就发消息"，目的是让投递失败也能被重试。

> 三段中后两段都标记了 `@Profile("production")`。默认 profile 下只有第一段存在。

## 第一段：入库

`AiTaskService.submit` 在同一个事务里插入 `status=PENDING` / `dispatchStatus=NEW` 的记录。**提交时不发消息**。

## 第二段：Outbox 投递器 `AiTaskOutboxDispatcher`

用 `@Scheduled(fixedDelayString = "${offer-tracker.ai.task-dispatch-delay:1000}")` 每秒扫一次。

### 候选条件

```java
.eq(status, "PENDING")
.le(availableAt, now)
.and(q -> q.eq(dispatchStatus, "NEW")
        .or().in(dispatchStatus, "PUBLISHED", "DISPATCHING").lt(updatedAt, retryBefore))
.orderByAsc(createdAt).last("LIMIT 100")
```

读作：**要么是全新的（`NEW`），要么是投过但卡住了（`PUBLISHED` / `DISPATCHING` 且 `updatedAt` 早于 `retryBefore`）。** `retryBefore = now - retryDelay`，`retryDelay` 来自 `offer-tracker.ai.task-dispatch-retry-delay`（默认 `1m`）。这就是"投递丢失"的自愈机制。

### 抢占是 CAS

```java
int claimed = tasks.update(claiming /* dispatchStatus=DISPATCHING */, wrapperWithSameConditions);
if (claimed == 0) return;   // 别人抢到了
```

**读取用的条件被原样复制到 update 的 where 里**，靠受影响行数判定抢占成功。多实例同时扫到同一条只会有一个成功。

### 投递结果

- 成功：`queue.publish(task)` → 把 `dispatchStatus` 置为 `PUBLISHED`（条件里再带上 `dispatchStatus = 'DISPATCHING'`）。
- 抛异常：把 `dispatchStatus` 退回 `NEW` 并**重新抛出**，让调度框架记录错误，下一轮重试。

## 队列实现：`AiTaskQueue`

| 实现 | 生效 profile | 行为 |
|---|---|---|
| `LocalAiTaskQueue` | `!production`（默认） | **空方法**，注释写明"本地与测试环境只持久化任务状态，不依赖 Redis" |
| `RedisAiTaskQueue` | `production` | 往 Redis Stream `offer-tracker:ai-tasks` 写一条 `{taskId, taskType}` |

注意发到 Stream 里的**只有 id 和类型**，payload 留在库里由 Worker 回查。这样消息体很小，也避免 payload 改动导致队列里堆积旧格式。

## 第三段：Worker `RedisAiTaskWorker`

同样是 `@Scheduled` 驱动，`offer-tracker.ai.task-poll-delay`（默认 1000ms）。

### 消费者组初始化

`@PostConstruct` 调 `ensureGroup()`，用 `XGROUP CREATE ... MKSTREAM` 创建组 `offer-tracker-ai-workers`，起止偏移 `ReadOffset.latest()`。捕获 `BUSYGROUP` 认为组已存在（`isExistingGroup` 会遍历 cause 链找这个关键字），其他异常只记 debug 日志、**不抛**，下一轮轮询再试。`groupReady` 是 `volatile`，避免多线程重复建组。

消费者名 `consumer = "worker-" + UUID.randomUUID()` —— 每个实例一个独立消费者名，才能被 `XPENDING` 正确区分。

### 处理一条消息 `process(record)`

1. 解析 `taskId`；取不到就 `acknowledge` 丢弃。
2. `tasks.selectById(id)`；查不到也 ack 丢弃。
3. `claim(task)` —— 见下。
4. `CurrentUserContext.set(new CurrentUser(task.getOwnerId(), "USER"))` —— **Worker 线程手动把任务所有者放进上下文**，因为这里没有 HTTP 请求，`AuthenticationContextFilter` 不参与。这是异步执行复用同步业务方法（`interviews.create/evaluate/followUp/finish`）的关键。
5. 按 `taskType` 分派到 `AiInterviewService` 或 `AiInterviewRepairService`。
6. 成功：`status=SUCCEEDED`，`result` 存序列化结果，清空 `leaseUntil`。
7. 失败：见下。
8. `finally` 里 `CurrentUserContext.clear()` 并 `acknowledge(record)`。

### `claim(task)` 的 CAS

```java
.update(/* status=PROCESSING, attempts=attempts+1, leaseUntil=now+LEASE */,
        .eq(id).eq(status, "PENDING"))
```

只有从 `PENDING` 抢到 `PROCESSING` 才算认领成功。`availableAt` 未到的任务直接返回 false。租约时长 `LEASE = 5 分钟`。

### 失败与重试

`attempts` 在 `claim` 时就已经 +1。失败后：

```java
task.setStatus(attempts >= MAX_ATTEMPTS ? "FAILED" : "PENDING");
task.setDispatchStatus("PENDING".equals(status) ? "NEW" : "PUBLISHED");
```

- 还有机会：退回 `PENDING` + `NEW`，等 Outbox 重新投递（顺手把 `availableAt` 的延迟重试也走了一遍）。
- 用尽 `MAX_ATTEMPTS = 3`：置 `FAILED` 并**写入死信流** `offer-tracker:ai-tasks:dead-letter`。

错误信息经过 `safeErrorMessage` 处理：**只有 `BusinessException` 的 message 会外泄**，其他异常统一写成 `"AI 任务执行失败，请稍后重试"`，且最长截到 **1024 个码点**（用 `offsetByCodePoints` 而不是 `substring`，避免把代理对切一半）。这样既不会把堆栈或 SQL 细节写进库，也不会出现超长字段。

### 租约回收 `recoverExpiredLeases`

`offer-tracker.ai.task-lease-scan-delay`（默认 30000ms）扫一次：找出 `status=PROCESSING` 且 `leaseUntil < now` 的任务，最多 `LEASE_RECOVERY_BATCH_SIZE = 100` 条。

对每条做条件更新：

```java
.update(/* status = exhausted ? FAILED : PENDING, leaseUntil = null */,
        .eq(id).eq(status, "PROCESSING").lt(leaseUntil, now))
```

`updated == 0` 就 `continue` —— 说明别的实例已经抢先回收了。

**这段解决的是"Worker 进程被杀"**：任务被认领后如果进程崩溃，`status` 会永远停在 `PROCESSING`。租约到期后被这里捞回来重跑。同一思路在 `RedisVersionedState` 里也有（版本化 CAS），两者都是"用过期时间 + 条件更新做自愈"。

## 指标

| 指标 | 含义 |
|---|---|
| `offer_tracker_ai_tasks_published_total` | 投递到队列的次数 |
| `offer_tracker_ai_tasks_succeeded_total` | 执行成功次数 |
| `offer_tracker_ai_tasks_retried_total` | 失败但会重试 |
| `offer_tracker_ai_tasks_dead_letter_total` | 进入死信 |
| `offer_tracker_ai_tasks_lease_recovered_total` | 租约过期被回收 |

## 相关测试

- `AiTaskServiceTest` —— 提交校验与幂等。
- `AiTaskOutboxDispatcherTest` —— 投递抢占与重试。
- `AiTaskOutboxRecoveryTest` / `AiTaskLeaseRecoveryTest` —— 租约回收。
- `RedisAiTaskWorkerStateTest` —— `safeErrorMessage` 等纯函数。
- `AiTaskOutboxRedisIntegrationTest` —— 全链路，默认跳过（标了 skip），需要真实 Redis 才跑。
