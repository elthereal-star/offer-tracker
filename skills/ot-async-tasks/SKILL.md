---
name: ot-async-tasks
description: offer-tracker 异步 AI 任务 Skill。用于确认「AI 任务怎么提交、参数有什么限制」「任务状态与投递状态两个字段分别表示什么」「失败后重试几次、什么时候进死信」「租约过期的任务怎么被回收」；当调试任务卡住、重复执行、或想知道默认 profile 下任务到底有没有被消费时使用。
---

# ot-async-tasks

异步任务让耗时的 AI 面试操作可以「先受理、后执行」。这一层管的是**任务从入库到执行完毕的完整状态机**。

## 使用顺序

1. 先看 `references/task-lifecycle.md`，搞清楚 `status` 与 `dispatchStatus` 两个字段的分工和提交时的参数校验。
2. 涉及投递、重试、死信、租约时看 `references/dispatch-and-worker.md`。

## 这层管什么

- `AiTaskService.submit` 的参数白名单校验（任务类型、字段、大小、幂等键）。
- `ai_tasks` 表上 `status` / `dispatchStatus` / `attempts` / `leaseUntil` 四个字段的语义与合法组合。
- Outbox 投递器（`AiTaskOutboxDispatcher`）与 Worker（`RedisAiTaskWorker`）的职责分界。
- 重试上限、租约时长、死信流。

## 必守约束

- **默认 profile 下任务不会被消费。** `LocalAiTaskQueue.publish` 是空方法，`AiTaskOutboxDispatcher` 与 `RedisAiTaskWorker` 都标了 `@Profile("production")`。默认环境下提交任务只会得到一条 `PENDING` 记录，永远不动。看到"任务一直是 PENDING"先确认 profile，不要先去怀疑 Worker。
- **`status` 与 `dispatchStatus` 是两个独立的状态维度**，不要合成一个枚举。前者是业务生命周期（`PENDING` / `PROCESSING` / `SUCCEEDED` / `FAILED`），后者是投递生命周期（`NEW` / `DISPATCHING` / `PUBLISHED`）。混用会让"已投递但还没执行"这种中间态无处表达。
- **任务类型是白名单**，只允许 `GENERATE_QUESTION` / `EVALUATE_ANSWER` / `FOLLOW_UP` / `FINISH_INTERVIEW` / `REPAIR_TURN`。新增类型必须同时改 `SUPPORTED_TASK_TYPES` 和 `validateAndWritePayload` 里的字段白名单，否则会被 400 拒掉。
- **payload 只允许白名单字段**，且总大小不超过 4 KiB。这是刻意的收紧：payload 会原样落库并交给 Worker 反序列化，放开字段等于开放了一个绕过接口校验的入口。
- **所有抢占都是条件更新**（`update ... where status = 'PENDING'` 之类），靠受影响行数判断是否抢到。不要改成先查后改，那会在多实例下重复执行。

## 参考资料

- `references/task-lifecycle.md`
- `references/dispatch-and-worker.md`
