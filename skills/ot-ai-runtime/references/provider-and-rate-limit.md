# AI 目标地址校验、请求限流与失败语义

## 目标地址校验：`AiProviderEndpointPolicy`

用户可以在设置里填自定义 AI 服务地址（`AiConfigService`），这个类负责把它规范化并做安全校验。它在**所有 profile 下都生效**，是唯一一处 `production` 与非 `production` 行为不同但组件本身始终存在的地方：

```java
public AiProviderEndpointPolicy(Environment environment) {
    this.production = environment.acceptsProfiles(Profiles.of("production"));
}
```

### 所有 profile 都会拒绝的地址

必须满足全部条件，否则抛 `400 "AI 服务地址不合法"`：

- 是绝对 URI，且 scheme 存在、host 存在且非空白；
- scheme 只能是 `http` 或 `https`（大小写不敏感）；
- **不允许携带 userinfo、query、fragment**（`http://user:pass@host`、`?a=1`、`#x` 都会被拒）。

通过后返回 `uri.toASCIIString()` 并去掉结尾多余的 `/`。

### 仅 `production` 额外拒绝

抛 `400 "生产环境 AI 服务地址必须使用 HTTPS 域名，不支持本机或 IP 地址"`：

- scheme 不是 `https`；
- host 是 `localhost`、`*.localhost`、`*.local`、`*.internal`、`*.svc`；
- host 是 IP 字面量（含 IPv6，`isIpLiteral` 会先剥掉方括号，再判断是否含 `:` 或全由数字和点组成）。

**这挡住的是 SSRF**：即使攻击者能改自己的 AI 配置，也无法让服务端去请求内网地址或云厂商元数据端点。

## 请求限流：`AiRequestLimiter`

```java
public interface AiRequestLimiter { void checkAllowed(Long userId); }
```

| 实现 | 生效 profile | 行为 |
|---|---|---|
| `LocalAiRequestLimiter` | `!production`（默认） | **空方法，完全不限流** |
| `RedisAiRequestLimiter` | `production` | 固定窗口 60 秒，上限 `offer-tracker.ai.requests-per-minute`（默认 10） |

`RedisAiRequestLimiter` 的两条重要语义：

- `userId == null` 直接抛 `401 "AI 面试功能需要登录账户"` —— 所以**生产环境下 AI 面试强制要求登录**，与 `AuthenticationContextFilter` 的默认放行是两回事。
- key 是 `offer-tracker:rate:ai:{userId}:minute`，注意其中的 **`{...}` 是 Redis Cluster 的哈希标签语法**，保证同一用户的计数落在同一个槽位。
- 计数用 Lua 保证 `INCR` 与首次 `EXPIRE` 的原子性，超限抛 `429 "AI 请求过于频繁，请 1 分钟后再试"`。

调用点在 `AiInterviewService.checkAiRequestAllowed()`，`create` / `evaluate` / `followUp` / `finish` 都会先过这道闸。

## AI 客户端超时与失败语义

`OpenAiCompatibleClient` 的两个超时来自配置：

```yaml
offer-tracker:
  ai:
    connect-timeout: ${AI_CONNECT_TIMEOUT:10s}
    request-timeout: ${AI_REQUEST_TIMEOUT:90s}
```

失败一律是 **502**，但有两种类型要区分：

| 类型 | 含义 | 是否重试 |
|---|---|---|
| `AiProviderException(code, message, retryable)` | 上游返回可重试状态 | `retryable` 为真时最多重试 3 次 |
| `BusinessException(502, "AI 服务连接失败，请检查地址和网络")` | 非预期异常兜底 | 否 |

其他 502 文案：`"AI 服务返回了空回答"`（choices 为空）、`"AI 服务请求失败（HTTP xxx）"`、`"AI 请求重试被中断"`。

`AiProviderHandler.chat(config, messages)` 是 provider 中立的边界；`AiProviderHandlerFactory.forConfig(...)` 目前**无条件返回 OpenAI 兼容实现**，是给多 provider 预留的扩展点，不要以为它已经在按配置分流。

## 与超时相关的连带影响

`request-timeout` 默认 90 秒，而 `RedisAiSessionLock.LEASE` 只有 30 秒。这个不一致是 fencing 令牌存在的直接原因，详见 `locking-and-fencing.md`。同时它也决定了一次 `finish()` 的**最坏长事务时长**：行锁 + 业务锁 + 一次最长 90 秒的 AI 调用都在同一个事务里。
