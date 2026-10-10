# 会话锁与 Fencing 令牌

这两件事经常被混为一谈，但它们解决的问题不同：

- **会话锁**回答「同一时刻谁可以改这个会话」；
- **Fencing 令牌**回答「锁已经不属于它了，它还拿着旧结果回来写怎么办」。

## 会话锁：`AiSessionLock`

```java
public interface AiSessionLock {
    <T> T execute(Long sessionId, Supplier<T> operation);          // 不带令牌
    <T> T executeFenced(Long sessionId, LongFunction<T> operation); // 把令牌交给调用方
}
```

| 实现 | 生效 profile | 机制 | 抢不到锁 |
|---|---|---|---|
| `AiInterviewOperationLock` | `!redis`（默认） | `ConcurrentHashMap<Long, ReentrantLock>` | 阻塞等待 |
| `RedisAiSessionLock` | `redis` | `SET key value NX EX 30` + Lua 释放 | 轮询 25ms，累计 15 秒后抛「AI 面试会话操作繁忙，请稍后重试」 |

两点实现细节：

- 本地实现里 `locks` 是**按会话 id 惰性创建**的，`release` 时如果 `!lock.hasQueuedThreads()` 会把 entry 删掉，避免 map 无限增长。
- Redis 实现释放锁时用 Lua 比对 value：`if get(KEYS[1]) == ARGV[1] then del`。value 是每次调用新生成的 UUID，这样**不会误删别人的租约**。

目前**只有 `finish()` 走 `executeFenced`**；`create` / `answer` / `evaluate` / `followUp` 走的是 `execute`（`answer`/`evaluate`/`followUp` 另外还有 `selectIdForUpdate` 的行锁）。

## Fencing 令牌：`AiSessionFence`

```java
public interface AiSessionFence {
    long issue(Long sessionId);                       // 发号，单调递增
    boolean isCurrent(Long sessionId, long token);    // 该令牌是否仍是最新
}
```

| 实现 | 生效 profile | 载体 |
|---|---|---|
| `LocalAiSessionFence` | `!redis`（默认） | `ConcurrentHashMap<Long, AtomicLong>` |
| `RedisAiSessionFence` | `redis` | `offer:ai:fence:{sessionId}`，用 `opsForValue().increment` |

`issue(null)` 返回 0，0 约定为「未参与防护」。

## 发号时机是这套机制的命门

令牌在**拿到锁之后**才发：

```java
// AiInterviewOperationLock
public <T> T executeFenced(Long sessionId, LongFunction<T> operation) {
    ReentrantLock lock = acquire(sessionId);
    try { return operation.apply(fence.issue(sessionId)); } finally { release(sessionId, lock); }
}

// RedisAiSessionLock
private <T> T withLease(Long sessionId, Supplier<T> operation) { /* 抢租约 */ 
    return operation.get();   // 内部再 fence.issue
}
```

这样「令牌更大」等价于「持锁更晚」。如果反过来先发号再抢锁，两个线程可能拿到 A<B 的令牌但 B 先拿到锁，条件写入就会用错误的顺序判定先后。

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
- 返回受影响行数，调用方据此判断：
  ```java
  int fenced = sessions.completeWithFence(sessionId, COMPLETED, average, report, finishedAt, fenceToken);
  if (fenced == 0) throw new BusinessException(409, "会话已被更晚的操作接管，本次收尾写入已丢弃");
  ```
- 被拒绝的写入**不会推进 `fence_token`**，所以真正的赢家仍然可以后续写入。

对应的迁移是 `V19__add_ai_interview_session_fence_token.sql`，列定义 `BIGINT NOT NULL DEFAULT 0`。

## 为什么不能只靠锁

`RedisAiSessionLock.LEASE` 是 **30 秒**，而单次 AI 调用最坏可以跑满 `offer-tracker.ai.request-timeout`（默认 **90 秒**）。也就是说：

> finish 持锁期间调 AI 生成报告，如果耗时超过 30 秒，租约已经到期；此时另一个请求可以拿到锁、也调完 AI、先写入结果。原来那个持有者随后拿着**已经过期的结果**回来写 —— 它仍然认为自己持有锁，因为本地代码路径并没有被打断。

**租约到期是一个超时信号，不是停止信号。** 旧持有者的线程不会因为 Redis 键过期就中止。Fencing 令牌就是用来在存储层把这种"迟到的写入"挡掉的。

这条链路的行为由 `AiSessionFenceTest` 覆盖，其中 `rejectsWriteFromSupersededHolder` 直接断言旧令牌写入返回 0 且不覆盖已有报告。

## 相关但独立：`RedisVersionedState`

`RedisVersionedState.compareAndSet(key, expectedVersion, state, ttl)` 用 Lua 做版本化 CAS，服务异步任务 Worker 的状态推进：**只有版本号匹配的 Worker 才能覆盖状态**，与 fencing 令牌是同一类思路的另一处应用。
