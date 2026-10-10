---
name: ot-ai-runtime
description: offer-tracker AI 运行时设施 Skill。用于确认「同一份 AI 请求会不会被折叠成一次」「会话操作靠什么锁保护、fencing 令牌怎么防止过期持有者回写」「AI 调用频率与目标地址有什么限制」；当怀疑重复调用、并发写覆盖、锁超时或限流不生效时使用。
---

# ot-ai-runtime

AI 面试的业务规则写在 `ot-ai-interview-domain`；这一层管的是**让这些规则在多请求、多实例下仍然正确**的运行时设施。

四条设施各自解决一类问题，混在一起看会很难判断某条限制到底由谁负责：

| 设施 | 解决什么 | 默认 profile 下的实现 | 失败时的表现 |
|---|---|---|---|
| Single-flight | 同一份 AI 请求重复到达 | 进程内 `ConcurrentHashMap` | 直接放行，退化成各调各的 |
| 会话锁 | 同一会话的写操作互相踩 | `ReentrantLock` | 15s 抢不到锁则抛错 |
| Fencing 令牌 | 租约过期后旧持有者仍在跑 | `AtomicLong` | 存储层拒绝旧令牌回写 |
| AI 限流 | 单用户请求频率 | **空实现，不限流** | 无 |

## 使用顺序

1. 先看 `references/single-flight.md`，确认请求折叠的 key 怎么拼、命中与未命中的差别。
2. 涉及并发写与收尾时看 `references/locking-and-fencing.md`。
3. 涉及限流、超时、AI 目标地址校验时看 `references/provider-and-rate-limit.md`。

## 这层管什么

- `AiDistributedSingleFlight` 的两个实现（进程内 / Redis）与它们各自的 key 前缀。
- `AiSessionLock` 的 `execute` 与 `executeFenced` 两条路径的区别。
- `AiSessionFence` 的单调发号语义，以及它如何与 `AiInterviewSessionMapper.completeWithFence` 的条件写入咬合。
- `AiRequestLimiter`、`AiProviderEndpointPolicy` 生效的 profile。

## 必守约束

- **fencing 令牌必须在拿到锁之后发号。** `executeFenced` 的实现里，`fence.issue(sessionId)` 是在 `withLease` / `lock.lock()` 内部调用的。若改成先发号再抢锁，令牌大小就不再代表持锁先后，整个防护失去意义。
- **单进程的 single-flight 挡不住多实例。** 默认实现是 `ConcurrentHashMap`，只有激活 `redis` profile 才会切到跨实例版本。以为"加了 single-flight 就不会重复调用"是本仓库最容易踩的误解。
- **single-flight 的 key 必须包含用户维度。** 现有 key 形如 `follow-up:{ownerKey}:{sessionId}:{questionId}`，漏掉 `ownerKey` 会让不同用户拿到同一份结果。
- **默认 profile 下没有任何 AI 限流。** `LocalAiRequestLimiter.checkAllowed` 是空方法；限流只在 `production` 轴生效。压测时看到的"没有限流"不是 bug。
- **`RedisAiSessionLock.LEASE` 是 30 秒**，而单次 AI 调用最坏可到 90 秒（`offer-tracker.ai.request-timeout`）。这个不匹配是已知的：正因为租约可能先过期，才需要 fencing 令牌兜底。改这个常量前先读 `locking-and-fencing.md`。

## 参考资料

- `references/single-flight.md`
- `references/locking-and-fencing.md`
- `references/provider-and-rate-limit.md`
