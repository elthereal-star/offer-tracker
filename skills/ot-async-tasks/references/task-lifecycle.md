# 任务的生命周期与提交校验

表：`ai_tasks`，实体 `AiTask`。迁移来源 `V13`–`V16`。

## 字段语义

| 字段 | 含义 |
|---|---|
| `status` | 业务生命周期：`PENDING` / `PROCESSING` / `SUCCEEDED` / `FAILED` |
| `dispatchStatus` | 投递生命周期：`NEW` / `DISPATCHING` / `PUBLISHED` |
| `attempts` | 已尝试执行次数，上限 `MAX_ATTEMPTS = 3` |
| `availableAt` | 最早可执行时间，投递器用它做延迟重试 |
| `leaseUntil` | 执行租约到期时间，只有 `PROCESSING` 状态才有值 |
| `idempotencyKey` | 由调用方提供，与 `ownerId` 组成唯一约束 |
| `payload` / `result` / `errorMessage` | 入参 JSON、成功结果 JSON、失败原因 |

`status` 与 `dispatchStatus` **必须分开看**。典型组合：

| status | dispatchStatus | 含义 |
|---|---|---|
| `PENDING` | `NEW` | 刚入库，等着被投递 |
| `PENDING` | `DISPATCHING` | 投递器已抢占，正在发消息 |
| `PENDING` | `PUBLISHED` | 已投递到队列，等 Worker 取 |
| `PROCESSING` | `PUBLISHED` | Worker 已认领，持有租约 |
| `SUCCEEDED` / `FAILED` | `PUBLISHED` | 终态 |

## 提交：`AiTaskService.submit(taskType, idempotencyKey, payload)`

校验顺序如下，任一步失败直接 400：

1. `requireOwner()` —— 未登录抛 `401 "AI 任务需要登录账户"`。**异步任务强制要求登录**，不像面试接口在默认 profile 下允许匿名。
2. `taskType` 与 `idempotencyKey` 都不能为空。
3. `taskType` 必须在 `SUPPORTED_TASK_TYPES` 内，否则 `"不支持的 AI 任务类型"`。
4. `idempotencyKey` 长度 ≤ 128。
5. `validateAndWritePayload`（见下）。
6. 入库，`status=PENDING`、`dispatchStatus=NEW`、`attempts=0`、`availableAt=now`。

### payload 校验

- 必须能序列化成 **JSON 对象**（数组、标量都拒绝：`"任务参数必须是 JSON 对象"`）。
- 序列化后 UTF-8 字节数 ≤ **4096**（`MAX_PAYLOAD_BYTES`），否则 `"任务参数不能超过 4 KiB"`。
- **字段白名单**按任务类型：

| taskType | 允许字段 | 必填正整数 |
|---|---|---|
| `GENERATE_QUESTION` | `resumeId`、`applicationId` | `resumeId`；`applicationId` 可选但给了就必须为正 |
| `EVALUATE_ANSWER` / `FOLLOW_UP` / `REPAIR_TURN` | `sessionId`、`questionId` | 两者都必须为正 |
| `FINISH_INTERVIEW` | `sessionId` | `sessionId` |

多出任何字段都会抛 `"任务参数包含不支持的字段"`。

## 幂等：靠数据库唯一键，不靠先查后插

唯一约束是 `uk_ai_tasks_owner_idempotency UNIQUE (owner_id, idempotency_key)`（定义在 `V13__add_ai_task_state.sql`）。

```java
try { tasks.insert(task); return toResponse(task); }
catch (DuplicateKeyException ex) {
    AiTask existing = tasks.selectOne(... ownerId + idempotencyKey ...);
    if (existing == null) throw ex;
    return toResponse(existing);
}
```

语义是：**同一用户用同一个 `idempotencyKey` 重复提交，返回第一次那条任务，不新建。** 用 `DuplicateKeyException` 兜底而不是先 `selectOne` 再 `insert`，是因为后者在并发下会双插。

## 读取：`get(id)`

`tasks.selectById(id)` 后校验 `requireOwner().equals(task.getOwnerId())`，不匹配一律 `404 "AI 任务不存在: {id}"`。

**这里用 404 而不是 403 是刻意的**：不向调用方泄露"这个 id 存在但不属于你"。
