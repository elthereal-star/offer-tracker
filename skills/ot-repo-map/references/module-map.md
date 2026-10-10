# 模块地图

后端是单个 Maven 模块，源码根为 `src/main/java/com/offertracker/`。

## 包边界

| 包 | 职责 | 代表类 |
|---|---|---|
| `common/` | 统一响应体、业务异常、当前用户上下文 | `ApiResponse`、`BusinessException`、`CurrentUserContext`、`GlobalExceptionHandler` |
| `config/` | 过滤器与装配：鉴权、IP 限流、请求追踪、存储、WebSocket | `AuthenticationContextFilter`、`ClientIpResolver`、`IpRateLimitFilter`、`LocalSaTokenConfiguration`、`AiRealtimeWebSocketConfig` |
| `controller/` | REST 入口，共 10 个 | `AiInterviewController`、`AiInterviewRealtimeController`、`AiTaskController`、`AuthController`、`ResumeController` |
| `dto/` | 请求/响应载体，大量使用 record | `CreateAiInterviewRequest`、`SubmitAiInterviewAnswerRequest`、`AiInterviewSessionResponse`、`AiChatMessage` |
| `entity/` | MyBatis-Plus 实体，与表一一对应 | `AiInterviewSession`、`AiInterviewQuestion`、`AiTask`、`Resume` |
| `enums/` | 业务枚举 | `AiInterviewStatus`、`ApplicationStatus`、`InterviewResult`、`InterviewType` |
| `mapper/` | MyBatis Mapper，含少量手写 SQL | `AiInterviewSessionMapper`、`AiTaskMapper` |
| `service/` | 业务编排与运行时设施（本仓库最重的一层，65 个类） | `AiInterviewService`、`AiSessionLock`、`AiDistributedSingleFlight`、`RedisAiTaskWorker` |
| `service/rule/` | LiteFlow 规则链节点：追问的守卫与裁决 | `RequireActiveGuardNode`、`AiFollowUpContext`、`AiFollowUpOutcome` |
| `storage/` | 简历文件存储抽象 | 本地实现与 S3 兼容实现 |

非 Java 资源里有两处与规则链相关：

| 路径 | 内容 |
|---|---|
| `src/main/resources/liteflow/ai-interview-followup.xml` | 追问裁决链的链定义（`THEN(...)`） |
| `application.yml` 的 `liteflow.rule-source` | 指向上面的 XML；**测试用的 `src/test/resources/application.yml` 会整体覆盖主配置，因此那里也声明了一份** |

## service 包的几组内聚位置

`service/` 数量较多，实际按用途分成四组：

1. **AI 面试主链路**：`AiInterviewService`、`AiInterviewStateMachine`、`AiInterviewRuntimeSnapshotService`、`AiInterviewIdempotencyService`、`AiInterviewRepairService`。
2. **AI 运行时设施**：`AiSessionLock`、`AiSessionFence`、`AiDistributedSingleFlight`、`AiRequestLimiter`、`AiProviderHandler` / `AiProviderHandlerFactory`、`AiProviderEndpointPolicy`、`OpenAiCompatibleClient`、`AiMessageSequenceService`、`RedisVersionedState`。
3. **异步任务链路**：`AiTaskService`、`AiTaskQueue`、`AiTaskOutboxDispatcher`、`RedisAiTaskWorker`。
4. **通用业务**：`JobApplicationService`、`ResumeService`、`CompanyService`、`StatsService`、`IdentityService`、`InterviewService`，以及各类限流器与去重过滤器。
5. **规则链节点**：`service/rule/` 下的 LiteFlow 节点，目前只有追问裁决链一条。节点靠 `@LiteflowComponent("节点名")` 注册，节点名必须与 XML 里 `THEN(...)` 中写的名字一致。

## 数据库迁移归属

迁移文件位于 `src/main/resources/db/migration/`，Flyway 顺序执行。

| 版本 | 内容 |
|---|---|
| `V1` / `V2` | 基础 schema 与完整性约束 |
| `V3` | 简历存储 |
| `V4` | AI 面试会话与题目 |
| `V5` | 评分与反馈字段 |
| `V6` | 平均分与报告字段 |
| `V7`–`V12` | 身份基座、验证码、资源归属、AI 用户配置、公司名按用户唯一 |
| `V13`–`V16` | 异步任务状态、Outbox 状态、恢复索引 |
| `V17` | 运行态快照表（含单调递增 `version`） |
| `V18` | 请求幂等表 |
| `V19` | 会话级 fencing token 列 |
| `V20` | 题目编号在会话内唯一（`uk_ai_interview_questions_no`），建约束前先清理历史重复行 |

## 容易混淆的两处

- `InterviewService` 与 `AiInterviewService` 不是一件事：前者是**人工记录**面试结果（`InterviewType` / `InterviewResult`），后者是 **AI 驱动的模拟面试**（`AiInterviewStatus`）。改功能时先确认改的是哪一个。
- `AiRuntimeArchive` 有 `Noop` 与 `Mongo` 两个实现，默认 profile 下是 `Noop`。**不要假设归档一定在写。**
- `AiInterviewService` 里**只有 `finish` 不带 `@Transactional`**，它的两段事务由方法内部的 `TransactionTemplate` 控制（为了让 90 秒的 AI 调用留在事务外）。给别的方法加 `@Transactional` 是安全的，但**不要给 `finish` 加回去** —— 有一个反射断言在守这条线。
