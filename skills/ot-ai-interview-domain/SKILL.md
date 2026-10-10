---
name: ot-ai-interview-domain
description: offer-tracker AI 面试领域 Skill。用于处理面试会话的五阶段链路（出题/作答/评分/追问/收尾）、会话状态机迁移、作答与评分的守卫条件、追问裁决规则；当需求涉及面试流程变更、状态流转、或"某操作在当前状态下是否允许"时使用。
---

# ot-ai-interview-domain

这一个领域只有一条主链路，但每个阶段的守卫条件都不同，改动前必须先确认自己动的是哪一段。

## 使用顺序

1. 先看 `references/session-lifecycle.md`，确认五个阶段各自的入口、前置条件与副作用。
2. 涉及"能不能做"的判断时看 `references/state-machine.md`。
3. 需要确认并发与写入安全时转 `ot-ai-runtime`。

## 这层管什么

- `AiInterviewService` 的五个阶段：`create` / `answer` / `evaluate` / `followUp` / `finish`。
- `AiInterviewStateMachine` 的状态迁移合法性。
- 追问裁决的判定顺序与短路条件。
- 运行态快照的写入时机。

## 必守约束

- **终态不可逆**：`COMPLETED` 的后继集合为空，任何针对已结束会话的作答/评分/追问都必须抛 409，而不是静默忽略。
- **状态机与守卫条件不要重复实现**：`requireActive` 与 `requireTransition` 是唯一判定入口，不要在业务方法里手写 `"ACTIVE".equals(status)`。
- **`answer` 不调用 AI**，它是纯数据库写入；`evaluate` / `followUp` / `finish` 才会调 AI。判断"这个改动会不会拉长事务"时先看这一点。
- 追问的短路规则（非最新题直接返回当前会话）是**幂等语义**，不是错误分支，不要改成抛异常。
- 新增阶段时必须同时补：状态机迁移边、守卫条件、幂等键的 operation 名称、幂等表唯一约束的对应维度。

## 参考资料

- `references/session-lifecycle.md`
- `references/state-machine.md`
