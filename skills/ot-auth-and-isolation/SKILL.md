---
name: ot-auth-and-isolation
description: offer-tracker 身份与数据隔离 Skill。用于确认「当前用户身份从哪来、未登录会怎样」「资源归属怎么在查询层强制」「重复提交怎么防」「API key 怎么落库」；当出现越权读取、匿名用户互相看到数据、重复请求产生多条记录、或要新增一个需要归属校验的接口时使用。
---

# ot-auth-and-isolation

这一层横跨三件事，它们的目标都是「**默认安全**」：

1. **身份**：`AuthenticationContextFilter` 把 token 解析成 `CurrentUserContext` 里的 `CurrentUser`。
2. **归属**：所有资源查询都带上 `ownerId` 条件，不靠调用方自觉。
3. **重复**：用唯一约束 + 幂等表把重复提交收敛成同一次结果。

## 使用顺序

1. 先看 `references/identity-and-ownership.md`，确认身份来源与归属校验的落地方式。
2. 再看 `references/idempotency-and-limits.md`，确认重复提交与频率限制的处理。

## 这层管什么

- `AuthenticationContextFilter` 的解析流程、401 的四种触发条件、以及它放行哪些路径。
- 两条「是否强制登录」的独立开关：`offer-tracker.auth.required`（默认 false）与 `production` profile。
- 归属校验的三种落地形式：SQL 条件、Mapper 条件、`toResponse` 前的手工比对。
- `X-Idempotency-Key` 的处理链路与 `AiInterviewRequestIdempotency` 表。
- AI 配置里 api key 的加密方式。

## 必守约束

- **默认 profile 下不强制登录。** `offer-tracker.auth.required` 默认 `false`，`AuthenticationContextFilter` 在没有 `Authorization` 头时会**直接放行**。这是为了让项目「克隆即跑」，但代价是**所有匿名用户共享同一份数据空间**——`ownerKey()` 会返回字符串 `"anonymous"`。本地调试时看到别人的数据不是 bug。
- **`production` profile 拒绝关闭鉴权。** `ProductionAuthenticationGuard` 在 `afterPropertiesSet` 里检查，如果 `offer-tracker.auth.required=false` 就抛 `IllegalStateException` 让应用**启动失败**。这是有意的 fail-fast，不要为了跑起来把它去掉。
- **归主校验必须写在 SQL 的 where 里，不能只写在 Java 里。** 例如 `selectIdForUpdate(id, ownerId)` 把 `owner_id = ?` 带进 `FOR UPDATE` 语句。只在拿到实体后再比 `ownerId`，会留下一个"先读到别人的数据、再拒绝"的窗口，也会让行锁锁错范围。
- **不匹配一律返回 404，不要返回 403。** `AiTaskService.get` 用的是 `404 "AI 任务不存在"`，`AiInterviewService.get` 也是 404。区别对待会泄露"该 id 存在"这一信息。
- **新增资源时必须同步加 `owner_id` 列与索引**，`V9__add_resource_ownership.sql` 之后的表都遵循这个约定。

## 参考资料

- `references/identity-and-ownership.md`
- `references/idempotency-and-limits.md`
