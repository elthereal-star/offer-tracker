# 重复提交与频率限制

## 幂等：`X-Idempotency-Key`

入口是 `AiInterviewService.answer(sessionId, questionId, requestKey, req)`：

```java
Long owner = CurrentUserContext.get() == null ? null : CurrentUserContext.get().id();
if (owner != null && requestKey != null && !requestKey.isBlank())
    return idempotency.execute(owner, sessionId, questionId, "ANSWER", requestKey, AiInterviewSessionResponse.class,
                               () -> answerInternal(sessionId, questionId, req));
return answerInternal(sessionId, questionId, req);
```

**只有「已登录」且「带了 key」才走幂等**。未登录或不带 key 时直接执行 —— 前者是因为幂等表 `owner_id NOT NULL`，后者是因为没有 key 无法判定"同一次"。

### 存储

表 `ai_interview_request_idempotency`（`V18__add_ai_interview_request_idempotency.sql`）：

```sql
CONSTRAINT uk_ai_interview_request UNIQUE (owner_id, session_id, question_id, operation, request_key)
```

`response_json TEXT NOT NULL` 存的是**完整响应体**，所以重放请求不需要再查业务表，直接把上次的 JSON 反序列化返回。

### 执行流程

```java
String flightKey = "idempotency:" + ownerId + ":" + sessionId + ":" + questionId + ":" + operation + ":" + normalized;
String responseJson = singleFlight.execute(flightKey, () -> { ① 查表命中则返回既有 JSON
                                                              ② 未命中则 action.get() 执行
                                                              ③ 把结果写入表 });
return read(responseJson, type);
```

三层防护叠在一起：

1. **single-flight** 把同 key 的并发请求折叠成一次，避免同一秒内两个请求都去查表、都未命中、都执行。
2. **查表命中** 直接返回历史响应。
3. **唯一约束**兜底：如果两个实例都越过了前两层，`insert` 会撞 `DuplicateKeyException`，此时**再查一次表取赢家的 JSON**（`executeOnce` 里的 catch）。

`normalized` 是 `requestKey.trim()`，长度上限 128，超了抛 `400 "X-Idempotency-Key 长度不能超过 128 个字符"`。

### 目前只在 `answer` 上挂了一个

注意 `AiInterviewIdempotencyService` 的 `operation` 参数是设计好的扩展点（`ANSWER` / `EVALUATE` / `FOLLOW_UP` …），但**当前只有 `answer` 走这条路**。`evaluate` / `followUp` / `finish` 的重复提交依靠的是各自的**业务幂等**（如追问的 `requireLatestQuestionGuard` 短路、`finish` 的终态判断），而不是这张表。别以为所有写接口都带幂等键。

同期还有一处**接口级**幂等：`AiTaskService.submit` 用 `uk_ai_tasks_owner_idempotency` 唯一键，见 `ot-async-tasks`。

## 频率限制一览

**默认 profile 下所有限流器都是空实现。** 这是统一的设计：本地开发不引入 Redis，也不希望限流妨碍调试。

| 接口 | 接口 | 默认（`!production`） | `production` |
|---|---|---|---|
| AI 调用（面试各阶段） | `AiRequestLimiter` | `LocalAiRequestLimiter` **空方法** | `RedisAiRequestLimiter`，**10 次/分钟**（`offer-tracker.ai.requests-per-minute`） |
| 按 IP（仅 `/api/auth/`） | `IpRequestLimiter` | 无实现（过滤器也不存在） | `RedisIpRequestLimiter`，**60 次/分钟** |
| 登录尝试 | `LoginAttemptLimiter` | `LocalLoginAttemptLimiter` **空方法** | `RedisLoginAttemptLimiter` |
| 短信下发 | `SmsRequestLimiter` | `LocalSmsRequestLimiter` **空方法** | `RedisSmsRequestLimiter`（多窗口） |

三个 Redis 实现都用 Lua 保证「计数 + 首次设置过期」的原子性，都使用了 Redis Cluster 哈希标签 `{...}` 让同一主体的多个键落在同一槽位：

| 实现 | key |
|---|---|
| `RedisAiRequestLimiter` | `offer-tracker:rate:ai:{userId}:minute` |
| `RedisIpRequestLimiter` | `offer-tracker:rate:ip:auth:{ip}` |
| `RedisLoginAttemptLimiter` | `offer-tracker:rate:login:{sha256(phone)}` |
| `RedisSmsRequestLimiter` | `offer-tracker:rate:sms:{digest}:{window}` |

两点细节：

- **登录限流的键用的是手机号的 SHA-256**，不是明文 —— 避免把手机号当 Redis key 泄露出去。
- **AI 限流的 key 用的是 `userId` 而不是用户名**，且 `userId == null` 时直接 401（见 `ot-ai-runtime`）。也就是说生产环境下 **AI 面试功能隐含要求登录**，这与 `offer-tracker.auth.required` 是两条独立的门槛。

## IP 过滤器的位置

`IpRateLimitFilter` 标了 `@Profile("production")` 和 `@Order(Ordered.HIGHEST_PRECEDENCE + 1)`，**只拦 `/api/auth/`** 下的非 `OPTIONS` 请求：

```java
if (path.startsWith("/api/auth/") && !"OPTIONS".equalsIgnoreCase(request.getMethod())) {
    try { limiter.checkAllowed(resolver.resolve(request)); }
    catch (BusinessException ex) { /* 写 429 + ApiResponse.error(429, ...) */ return; }
}
```

**IP 怎么算**由 `ClientIpResolver` 决定，规则是：

1. 取 `request.getRemoteAddr()` 并规范化；
2. 如果它**不在** `TrustedProxyProperties.trustedProxyCidrs()` 配置的可信代理网段里 → 直接返回它，**完全忽略 `X-Forwarded-For`**；
3. 在可信网段里才解析 `X-Forwarded-For`，**从右往左**找第一个不在可信网段里的地址作为客户端 IP。

关键是第 3 步的方向：从右往左。因为 `X-Forwarded-For` 是逐跳追加的，最左边是客户端可伪造的，最右边才是最后一个可信代理看到的值。如果反过来取第一个，任何人都能靠伪造头绕过 IP 限流。

`TrustedProxyProperties` 需要显式配置（前缀 `offer-tracker.security`，字段 `trusted-proxy-cidrs`）；不配置时可信集合为空（record 构造器把 `null` 归一成 `List.of()`），等价于"永远不信 `X-Forwarded-For`"，这是安全的默认值。

## 一个尚未接线的扩展点

`DeduplicationFilter`（`LocalDeduplicationFilter` / `RedisDeduplicationFilter`，挂在 `redis` 轴上）接口如下：

```java
/** Probabilistic duplicate hint; callers must still enforce uniqueness in MySQL. */
public interface DeduplicationFilter { boolean mightContain(String key); void put(String key); }
```

**当前没有任何业务代码注入它**（`grep DeduplicationFilter src/main/java` 只能找到它自己和两个实现）。它是一个**声明了但未接线**的布隆过滤器式预检缝，接口注释也写明了它只是「概率性提示」，唯一性仍必须由 MySQL 约束保证。

看到这个接口时不要以为简历上传或任务提交已经在用它 —— 没有。
