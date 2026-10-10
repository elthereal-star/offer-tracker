# Single-flight：把重复的 AI 请求折叠成一次

接口：`AiDistributedSingleFlight.execute(String key, Supplier<T> supplier)`。

语义是：**同一个 key 上如果已经有一次调用在飞，后来的调用者不再发起新请求，而是等前一个的结果。** key 为空或空白时直接放行，不做任何协调。

## 两个实现

| 实现 | 生效 profile | 协调范围 | 载体 |
|---|---|---|---|
| `AiSingleFlightService` | `!redis`（默认） | **仅本进程** | `ConcurrentHashMap<String, CompletableFuture<Object>>` |
| `RedisAiSingleFlightService` | `redis` | 跨实例 | Redis 键 + 轮询 |

## 进程内实现（默认）

```java
CompletableFuture<Object> candidate = new CompletableFuture<>();
CompletableFuture<Object> existing = flights.putIfAbsent(key, candidate);
if (existing == null) { /* 我是第一个：真正执行，完成后 complete */ }
else { return (T) existing.join(); }            // 后来者：等结果
```

- 用 `putIfAbsent` 而不是 `get` + `put`，保证只有一个线程能成为「执行者」。
- 执行者无论成功还是抛异常都会 `complete`，并在 `finally` 里 `flights.remove(key, candidate)` —— 注意是**带值移除**，避免误删后来者放进去的新 candidate。
- 后来者拿到的异常会被解包：`CompletionException` 的 cause 是 `RuntimeException` 时原样抛出，保持业务异常语义。

指标：`offer_tracker_ai_singleflight_miss_total`（真正执行）/ `offer_tracker_ai_singleflight_hit_total`（折叠等待）。

## 跨实例实现（`redis` profile）

两个键，都带 90 秒 TTL：

| 键 | 作用 |
|---|---|
| `offer:ai:flight:lock:{key}` | 用 `setIfAbsent` 抢执行权，抢到的人负责调用 |
| `offer:ai:flight:result:{key}` | 存放调用结果，供后来者读取 |

失败时写哨兵值 `__ERROR__`（TTL 15 秒），后来者读到就抛 `IllegalStateException("AI 请求失败，请稍后重试")`，避免所有人都去重试打崩上游。

等待方是**轮询**：每 25ms 读一次 result 键，总上限 95 秒，超时抛「AI 请求协调超时，请重试」。

注意结果用 `mapper.convertValue(value, String.class)` 存 —— 这套实现只支持 String 响应，这也是它的注释所写的「for the String responses used by AI provider calls」。如果将来要让 `T` 是复杂对象，这里必须先改。

## key 的组成

key 由调用点自己拼，散落在 `AiInterviewService` 各方法里，规律是：

```
{操作名}:{ownerKey}:{sessionId}[:{questionId}[:{答案哈希}]]
```

| 调用点 | key |
|---|---|
| `create` | `generate:{ownerKey}:{resumeId}:{applicationId 或 未填写}` |
| `evaluate` | `evaluate:{ownerKey}:{sessionId}:{questionId}:{Integer.toHexString(answer.hashCode())}` |
| `followUp` | `follow-up:{ownerKey}:{sessionId}:{questionId}` |
| `finish` | `finish:{ownerKey}:{sessionId}` |

两点值得留意：

- `ownerKey()` 在未登录时返回字符串 `"anonymous"`，所以**默认免登录配置下所有匿名用户共享同一个 key 命名空间**。
- `evaluate` 的 key 里掺了答案的哈希，所以同一题改答案后再评分不会被折叠成旧结果。
- 幂等服务 `AiInterviewIdempotencyService` 复用同一个 single-flight，但 key 前缀是 `idempotency:`，见 `ot-auth-and-isolation`。
