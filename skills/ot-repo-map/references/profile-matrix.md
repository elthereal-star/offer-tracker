# Profile 矩阵

这里最容易出错的地方：**降级开关有两套轴，不是一套。**

- **`redis` 轴** —— 用于需要跨实例一致性的运行时设施。
- **`production` 轴** —— 用于需要真实外部依赖或安全加固的能力。

两者互相独立：`RedisAiTaskQueue` 挂在 `production` 而不是 `redis`，`RedisDeduplicationFilter` 则挂在 `redis` 而不是 `production`。判断"某实现是否生效"时必须看它自己的注解。

## redis 轴

| 接口 | `!redis`（默认） | `redis` |
|---|---|---|
| `AiSessionLock` | `AiInterviewOperationLock`（JVM `ReentrantLock`） | `RedisAiSessionLock`（`SETNX` + Lua 校验令牌释放） |
| `AiSessionFence` | `LocalAiSessionFence`（`AtomicLong` 逐会话发号） | `RedisAiSessionFence`（`INCR` 全局有序） |
| `AiDistributedSingleFlight` | `AiSingleFlightService`（`ConcurrentHashMap` + `CompletableFuture`） | `RedisAiSingleFlightService`（`SETNX` 抢占 + 结果缓存） |
| `AiMessageSequenceService` | 内部类 `Local`（`AtomicLong`） | 内部类 `Redis`（`INCR`） |
| `DeduplicationFilter` | `LocalDeduplicationFilter` | `RedisDeduplicationFilter` |

## production 轴

| 接口 | `!production`（默认） | `production` |
|---|---|---|
| `AiRequestLimiter` | `LocalAiRequestLimiter`（**空实现，不限流**） | `RedisAiRequestLimiter`（`INCR` + 60 秒窗口） |
| `LoginAttemptLimiter` | `LocalLoginAttemptLimiter` | `RedisLoginAttemptLimiter` |
| `SmsRequestLimiter` | `LocalSmsRequestLimiter` | `RedisSmsRequestLimiter` |
| `AiTaskQueue` | `LocalAiTaskQueue` | `RedisAiTaskQueue`（`XADD`） |
| 任务投递与消费 | 无（不存在 Dispatcher / Worker 的 Bean） | `AiTaskOutboxDispatcher` + `RedisAiTaskWorker` |
| IP 限流 | 无 `IpRateLimitFilter` | `IpRateLimitFilter` + `RedisIpRequestLimiter` |
| 鉴权装配 | `LocalSaTokenConfiguration` | `ProductionAuthenticationGuard` |

## 不分 profile 的部分

有些组件**在任何 profile 下都生效**，判断影响面时不要漏掉：

| 组件 | 说明 |
|---|---|
| LiteFlow 规则链 | `aiInterviewFollowUpChain` 与它的 5 个节点始终注册，追问裁决在所有环境下走同一条链 |
| `AiProviderEndpointPolicy` | 始终存在；只有 `production` 下会额外拒绝 HTTP / 本机 / IP 地址 |
| `AuthenticationContextFilter` | 始终存在，只是"是否强制登录"由 `offer-tracker.auth.required` 决定 |

## 其它单点 profile

| profile | 类 | 说明 |
|---|---|---|
| `realtime` | `AiInterviewEventHub`、`AiRealtimeWebSocketHandler`、`AiInterviewRealtimeController`、`AiRealtimeWebSocketConfig` | 不激活时实时通道整体不存在，主链路不受影响 |
| `mongo-archive` | `MongoAiRuntimeArchive` | 不激活时由 `NoopAiRuntimeArchive` 兜底 |
| `sms-cloud` | 影响 `LoggingSmsCodeSender`（`!production & !sms-cloud`）与 `UnavailableProductionSmsCodeSender`（`production & !sms-cloud`） | 未接真实短信通道时表现为不可用，而非静默成功 |

## 实践含义

- **默认 profile 下所有限流器都是空方法**。`LocalAiRequestLimiter` / `LocalLoginAttemptLimiter` / `LocalSmsRequestLimiter` 的 `checkAllowed` 都是空实现，所以本地/测试环境不会因限流失败 —— 写测试时不能依赖限流生效。
- **默认 profile 下没有异步任务的 Worker**。任务只会写库，不会被消费；验证异步链路必须显式激活 `production`。
- **测试里注入到的锁实现是 JVM 版**。要验证 Redis 版行为需要额外激活 `redis` 并提供 Redis 实例，这也是为什么这部分逻辑用 `@Profile("redis")` 隔离而非混在默认路径里。
