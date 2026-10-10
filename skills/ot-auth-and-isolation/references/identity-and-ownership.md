# 身份与资源归属

## 身份从哪来

一个 `OncePerRequestFilter`：`AuthenticationContextFilter`。它是**唯一**写入 `CurrentUserContext` 的地方。

```java
String authorization = request.getHeader("Authorization");
if (authorization != null) {
    // 解析 Bearer token → StpUtil.getLoginIdByToken → 查 User 表 → CurrentUserContext.set(...)
} else if (authRequired && requiresAuthentication(request)) {
    writeUnauthorized(response, "请先登录"); return;
}
chain.doFilter(request, response);
```

`CurrentUserContext` 是一个 `ThreadLocal<CurrentUser>`，提供 `set` / `get` / `require` / `clear`。`require()` 在为空时抛 `401 "请先登录"`。

### token 解析的四道关

1. 头存在但**不以 `"Bearer "` 开头**，或去掉前缀后为空 → `401 "访问令牌格式无效"`。
2. `StpUtil.getLoginIdByToken(token)` 返回空 / 抛 `NotLoginException` → `401 "访问令牌无效或已过期"`。
3. `loginId` 不是合法数字（`NumberFormatException`）→ 同上 401。
4. 用户不存在，或 `status != "ACTIVE"` → **主动 `StpUtil.logoutByTokenValue(token)` 使其失效**，再返回 `401 "账户当前不可用"`。

第 4 条的意思是：**被封禁的用户不只是这次被拒，token 会被直接作废**，需要重新登录。

### 放行范围

`requiresAuthentication` 的判定是：

```java
!"OPTIONS".equalsIgnoreCase(method)
    && path.startsWith("/api/")
    && !path.startsWith("/api/auth/");
```

即：**只有 `/api/` 下、且不在 `/api/auth/` 下的非预检请求需要登录**。静态资源、`/actuator/health`、`/swagger-ui.html`、`/h2-console` 都不在范围内。预检请求（`OPTIONS`）永远放行。

### 清理

`finally { CurrentUserContext.clear(); }`。**这一点很重要**：Servlet 容器复用线程，不清理会让下一个请求继承上一个用户的身份。异步 Worker 线程里也需要手动 set/clear（见 `ot-async-tasks`）。

## 两条独立的「是否强制登录」控制

| 控制 | 位置 | 默认 | 作用 |
|---|---|---|---|
| `offer-tracker.auth.required` | `AuthenticationContextFilter` 构造参数 | `false` | 为 true 时无 token 请求被 401 |
| `production` profile | `ProductionAuthenticationGuard` | 无 | 启动时强制要求上面的值为 **true** |

```java
@Profile("production")
public class ProductionAuthenticationGuard implements InitializingBean {
    public void afterPropertiesSet() {
        if (!authenticationRequired) throw new IllegalStateException(
            "Authentication cannot be disabled while the production profile is active");
    }
}
```

所以「生产必须鉴权」不是靠文档约定，而是靠应用**起不来**来保证。

另外 `LocalSaTokenConfiguration`（`@Profile("!production")`）注册了一个 `SaTokenDaoDefaultImpl` 作为 `@Primary` —— 默认环境下 sa-token 的会话存在内存里，重启即失效；生产环境则用 sa-token 的 Redis 集成（引入 `sa-token-redis` 那类依赖时）。

## 归属校验的三种落地形式

### 1. SQL 条件（最严格，兼顾行锁）

```sql
SELECT id FROM ai_interview_sessions
 WHERE id = #{id} AND (#{ownerId} IS NULL OR owner_id = #{ownerId}) FOR UPDATE
```

`AiInterviewService.lockSessionOrThrow` 调用它。`ownerId IS NULL` 表示**不做归属校验**——只在匿名（未登录）时成立。

**为什么必须写在 SQL 里**：如果先 `SELECT ... FOR UPDATE` 查出记录、再在 Java 里比对 `ownerId`，行锁会锁住一个不属于当前用户的会话，而拒绝又发生在锁之后，白锁一场。写在条件里则锁不到别人的行。

### 2. Mapper 条件（列表查询）

```java
LambdaQueryWrapper<AiInterviewSession> wrapper = ...;
if (CurrentUserContext.get() != null) wrapper.eq(AiInterviewSession::getOwnerId, CurrentUserContext.get().id());
```

`AiInterviewService.list` 用这种写法。注意条件是**按当前用户是否有身份**来决定加不加，未登录时列出全部 —— 与上面 `ownerId IS NULL` 是同一种语义。

### 3. 手工比对（读取后校验）

```java
private boolean ownedByCurrentUser(AiInterviewSession session) {
    return CurrentUserContext.get() == null || CurrentUserContext.get().id().equals(session.getOwnerId());
}
```

`AiInterviewService.get` 与 `AiTaskService.get` 用这种。适合"已经按主键读出来了"的场景，但**不能替代写入路径的 SQL 条件校验**。

### 写入时的归属赋值

`create` 时：`if (CurrentUserContext.get() != null) session.setOwnerId(CurrentUserContext.get().id());` —— 未登录就留 `null`，这条会话对所有人可见。

## 需要登录才能用的功能

这些地方的 `null` 用户是**硬拒绝**，不享受"匿名放行"：

| 位置 | 行为 |
|---|---|
| `AiTaskService.requireOwner()` | `401 "AI 任务需要登录账户"` |
| `RedisAiRequestLimiter.checkAllowed(null)` | `401 "AI 面试功能需要登录账户"`（**仅 production**） |
| `CurrentUserContext.require()` | `401 "请先登录"` |

## AI 配置与密钥加密

`AiConfigService` 把用户自定义的 AI 配置存在 `ai_user_configs` 表，其中 `api_key_ciphertext` 是加密后的值：

- 算法 **AES/GCM/NoPadding**，tag 128 位，随机 12 字节 nonce；
- 密钥来自 `offer-tracker.auth.config-encryption-key`（环境变量 `AI_CONFIG_ENCRYPTION_KEY`），**长度必须 ≥ 32**，否则构造时抛 `IllegalArgumentException`；
- 通过 SHA-256 摘要成 16/24/32 字节的 AES 密钥；
- 存库格式是 `Base64(nonce ‖ ciphertext)`，解密时先切出前 12 字节当 nonce；
- **额外一道闸**：如果 `auth.required=true` 而密钥仍以 `local-development-only-` 开头，构造时直接拒绝启动。这是防止把开发默认密钥带上生产。

配置按用户隔离：`writeUser` 用 `UPDATE ... WHERE user_id = ?`，影响行数为 0 才 `INSERT`，读写都以 `user_id` 为键。

## 相关测试

- `AuthenticationContextFilterTest` —— 401 的四种触发条件与放行范围。
- `ProductionAuthenticationGuardTest` —— 生产强制鉴权。
- `AiInterviewOwnershipTest` / `CompanyOwnershipTest` —— 归属校验。
- `AiUserConfigIsolationTest` —— 用户配置隔离。
- `OwnershipMigrationRunnerTest` —— `owner_id` 迁移。
