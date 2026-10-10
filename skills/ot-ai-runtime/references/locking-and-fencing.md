# 会话锁与 Fencing 令牌

这两件事经常被混为一谈，但它们解决的问题不同：

- **会话锁**回答「同一时刻谁可以改这个会话」；
- **Fencing 令牌**回答「锁已经不属于它了，它还拿着旧结果回来写怎么办」。

设计上有一条硬约束贯穿始终：**临界区里不能有耗时操作**。收尾流程为此被拆成三段，见下文「为什么不能只靠锁」。

## 会话锁：`AiSessionLock`

```java
public interface AiSessionLock {
    <T> T execute(Long sessionId, Supplier<T> operation);           // 不带令牌
    <T> T executeFenced(Long sessionId, LongFunction<T> operation);  // 把令牌交给调用方
}
```

| 实现 | 生效 profile | 机制 | 抢不到锁 |
|---|---|---|---|
| `AiInterviewOperationLock` | `!redis`（默认） | `ConcurrentHashMap<Long, ReentrantLock>` | 阻塞等待 |
| `RedisAiSessionLock` | `redis` | `SET key value NX EX 30` + Lua 释放 | 轮询 25ms，累计 15 秒后抛「AI 面试会话操作繁忙，请稍后重试」 |

两点实现细节：

- 本地实现里 `locks` 是**按会话 id 惰性创建**的，`release` 时如果 `!lock.hasQueuedThreads()` 会把 entry 删掉，避免 map 无限增长。
- Redis 实现释放锁时用 Lua 比对 value：`if get(KEYS[1]) == ARGV[1] then del`。value 是每次调用新生成的 UUID，这样**不会误删别人的租约**。

目前**只有 `finish()` 走 `executeFenced`**；`create` / `answer` / `evaluate` / `followUp` 走的是 `execute`。注意 `evaluate` / `followUp` 也会调 AI，但它们**不拿会话锁**（只有 `selectIdForUpdate` 的行锁），所以会话锁的持有时长完全由 `finish` 决定 —— 这正是下面那套设计能成立的前提。

## Fencing 令牌：`AiSessionFence`

```java
public interface AiSessionFence {
    long issue(Long sessionId);   // 发下一个令牌，必大于库中已记录值
}
```

接口只有一个方法。曾经还有一个 `isCurrent(sessionId, token)`，后来删掉了：生产代码里没有任何调用点，而且它能表达的判断（「你是不是最新的持有者」）已经被存储层的条件写入完全覆盖，留着一个并发原语上的无用方法只会误导后来人。

| 实现 | 生效 profile | 载体 |
|---|---|---|
| `LocalAiSessionFence` | `!redis`（默认） | `ConcurrentHashMap<Long, AtomicLong>` |
| `RedisAiSessionFence` | `redis` | `offer:ai:fence:{sessionId}` + Lua |

`issue(null)` 返回 0，0 约定为「未参与防护」。

### 发号器必须与库中对齐（易失性是个陷阱）

发号器的存储是**易失的**：进程内计数器重启即归零；Redis 键在未开持久化时重启、主从切换、键被逐出、人工 FLUSH 之后同样归零。而库里的 `fence_token` 是**持久化**的。

两者不对齐的后果很严重：库里已经是 5，重启后 `issue` 返回 1，条件写入 `fence_token < 1` 恒不成立 → **这个会话的收尾被永久拒绝**（而锁还在正常发放，所以现象是"锁没问题但永远写不进去"，很难查）。

所以两个实现都在发号前把库中值当作下限：

```java
// LocalAiSessionFence：每个会话的首次发号读一次库
AtomicLong counter = counters.computeIfAbsent(sessionId, ignored -> new AtomicLong());
if (counter.get() == 0L) counter.compareAndSet(0L, storedFenceToken(sessionId));
return counter.incrementAndGet();
```

```lua
-- RedisAiSessionFence：把库中值一起传进 Lua，取 max 后再 +1
local current = tonumber(redis.call('GET', KEYS[1]) or '0')
local stored  = tonumber(ARGV[1])
if stored > current then current = stored end
current = current + 1
redis.call('SET', KEYS[1], current)
return current
```

这样「发出的令牌 &gt; 库中值」这个不变量就**不依赖发号器的持久性**。代价是 Redis 实现每次发号多一次主键查询 —— 发号只发生在收尾这条低频路径上，可以忽略。

对应测试：`AiSessionFenceTest.issuesTokenAboveTheValueAlreadyStoredInDatabase`。

## 发号时机是这套机制的命门

令牌在**拿到锁之后**才发：

```java
// AiInterviewOperationLock
public <T> T executeFenced(Long sessionId, LongFunction<T> operation) {
    ReentrantLock lock = acquire(sessionId);
    try { return operation.apply(fence.issue(sessionId)); } finally { release(sessionId, lock); }
}
```

这样「令牌更大」等价于「持锁更晚」。如果反过来先发号再抢锁，两个线程可能拿到 A&lt;B 的令牌但 B 先拿到锁，条件写入就会用错误的顺序判定先后。

代价是 `AiSessionLock` 依赖了 `AiSessionFence`（而不是反过来），这是一个刻意的取舍：**令牌的语义依赖于锁，耦合方向就应该是锁指向令牌。**

## 存储层的条件写入

```java
@Update("UPDATE ai_interview_sessions SET status=..., average_score=..., report=..., fence_token=#{token}, updated_at=... "
      + "WHERE id=#{id} AND fence_token < #{token}")
int completeWithFence(Long id, String status, Integer averageScore, String report,
                      LocalDateTime updatedAt, long token);

@Select("SELECT fence_token FROM ai_interview_sessions WHERE id=#{id}")
Long selectFenceToken(Long id);
```

- 条件是 `fence_token < #{token}`，**严格小于**：同一个令牌重复写也会被拒绝。
- 被拒绝的写入**不会推进 `fence_token`**，所以真正的赢家仍然可以后续写入。
- 迁移：`V19__add_ai_interview_session_fence_token.sql`，列定义 `BIGINT NOT NULL DEFAULT 0`。

### 被拒绝之后怎么收口

`fence_token` 只可能由收尾写入推进，而每次推进都伴随 `status=COMPLETED`，所以「写入被拒」在实际语义上等价于「已经有人把这次收尾做完了」。于是收口逻辑是：

```java
// AiInterviewService.resolveFenceRejection
Long stored = sessions.selectFenceToken(sessionId);
log.warn("AI 面试收尾写入被 fencing 令牌拒绝: sessionId={} token={} storedToken={}", sessionId, fenceToken, stored);
fenceRejections.increment();
AiInterviewSession current = sessions.selectById(sessionId);
if (current != null && "COMPLETED".equals(current.getStatus())) return get(sessionId);  // 幂等
throw new BusinessException(409, "会话已被更晚的操作接管，本次收尾写入已丢弃");
```

三个要点：

1. **幂等优先于报错。** 调用方要的结果已经落地了，返回 409 是把它当失败处理，语义不对。
2. **必须有 WARN 日志。** 这条分支此前是完全静默的 —— 生产上如果它开始频繁触发，没人会发现。
3. **必须有 counter。** `offer_tracker_ai_fence_rejected_total`。

这个方法被放宽为包级可见，原因写在注释里：这条分支只在真实并发竞争下才会触发，**无法通过公开 API 稳定构造**，所以测试（`AiInterviewFinalizeTest`）直接调它。

## 为什么不能只靠锁：临界区里有 90 秒的 AI 调用

租约是 **30 秒**（`RedisAiSessionLock.LEASE`），而单次 AI 调用最坏可以跑满 `offer-tracker.ai.request-timeout`（默认 **90 秒**）。如果在锁里调 AI：

> 租约到期后另一个请求可以拿到锁、也调完 AI、先写入结果。原来那个持有者随后拿着**已经过期的结果**回来写 —— 它仍然认为自己持有锁，因为本地代码路径并没有被打断。

**租约到期是一个超时信号，不是停止信号。** 这就是 fencing 令牌存在的理由。

但反过来说：**只要临界区里没有耗时操作，30 秒租约本来就绰绰有余**，不需要把租约调长，也不需要续租。所以收尾被拆成了三段（见 `ot-ai-interview-domain` 的 `session-lifecycle.md`）：

```
① 短事务：校验 + 算平均分 + 拼提示词        （毫秒）
② 出事务、出锁：调 AI                        （最长 90 秒，不持任何锁）
③ 短临界区：取会话锁 → 发令牌 → 条件写入      （毫秒）
```

由此得到几个不再需要的复杂度：**不需要 watchdog 续租**（临界区只有毫秒，租约不会过期）、**不会有 90 秒的长事务**、**不会长时间占用 HikariCP 连接与行锁**。

### 为什么不加 watchdog 续租

这是被明确否掉的方案。除了"复杂度上升"，它还有几条实质弊端：

| 弊端 | 说明 |
|---|---|
| **把临时故障变成永久故障** | 租约过期本来是自愈机制：持有者卡死，30 秒后锁自动释放。有了续租，「卡死」和「很慢」在 Redis 眼里一样，于是锁永远不释放，该会话永久不可用。必须额外设续租上限才安全 |
| **让长事务成为常态** | 续租等于承认"一次请求可以合法地占 90 秒"。HikariCP 默认池只有 10，10 个并发收尾就能把连接池占满；同时行锁被持有时长变成 90 秒，而 MySQL `innodb_lock_wait_timeout` 默认 50 秒，同会话的 `answer`/`evaluate` 会**必然**撞锁等待超时 |
| **续租写错会比不加更糟** | 续租必须是"比对 token 后再续"。若写成无条件 `SET key myToken EX lease`，进程 GC 停顿恢复后会直接抢走新持有者的锁，制造出**两个同时以为自己持锁的实例** |
| **调度器变成隐性单点** | 项目上有 `@EnableScheduling`，Spring 默认只给**一个线程**，而它已经在跑 `RedisAiTaskWorker.poll()`（内含 500ms 阻塞读）等任务。把续租挂进去，等于把「锁的正确性」和「任务派发的及时性」耦合成一件事 |
| **最需要正确性的代码测不到** | 锁的 Redis 实现只在 `redis` profile 生效，而现有 Redis 集成测试默认被 skip |

## 并发正确性在默认 profile 下测不出来

JVM `ReentrantLock` 会一直持有到操作结束，**不存在"租约过期但操作还在跑"**这种情况，所以默认 profile 下 `executeFenced` 永远不会真的触发 supersession。

这意味着：

- `AiSessionFenceTest.rejectsWriteFromSupersededHolder` 直接打 mapper 而不是走锁，这个选择是**对的**，也是唯一的办法；
- 但要验证真实的 fencing 行为，必须上 `redis` profile + 真实 Redis。目前**没有**这样的集成测试，这是一个已知缺口。

## 相关但独立：`RedisVersionedState`

`RedisVersionedState.compareAndSet(key, expectedVersion, state, ttl)` 用 Lua 做版本化 CAS，服务异步任务 Worker 的状态推进：**只有版本号匹配的 Worker 才能覆盖状态**，与 fencing 令牌是同一类思路的另一处应用。
