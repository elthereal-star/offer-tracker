# 配置索引

配置真相源是 `src/main/resources/application*.yml`，代码侧通过 `@Value("${key:default}")` 读取。**默认值只写在代码里**，yml 里不重复声明 —— 所以判断"当前生效值"要看两处。

## 基础运行参数

| 键 | 默认值 | 位置 | 说明 |
|---|---|---|---|
| `server.port` | `8080` | 代码 | HTTP 端口 |
| `offer-tracker.auth.required` | `false` | yml + 代码 | 是否强制登录。**默认关闭**，所以本地直连不受鉴权约束 |
| `offer-tracker.auth.config-encryption-key` | 本地占位串 | yml | AI 配置里 API Key 的 AES-GCM 加密密钥 |
| `offer-tracker.id.worker-id` | `1` | 代码 | 雪花算法 workerId，多实例部署必须各不相同 |

## AI 调用

| 键 | 默认值 | 说明 |
|---|---|---|
| `offer-tracker.ai.connect-timeout` | `10s` | 建连超时 |
| `offer-tracker.ai.request-timeout` | `90s` | 读超时。**这个值决定长事务的最坏时长** |
| `offer-tracker.ai.requests-per-minute` | `10` | 每用户每分钟 AI 请求上限；仅 `production` 生效 |

## 异步任务

| 键 | 默认值 | 说明 |
|---|---|---|
| `offer-tracker.ai.task-dispatch-delay` | `1000` (ms) | Outbox 派发轮询间隔 |
| `offer-tracker.ai.task-poll-delay` | `1000` (ms) | Worker 消费轮询间隔 |
| `offer-tracker.ai.task-lease-scan-delay` | `30000` (ms) | 租约扫描间隔 |
| `offer-tracker.ai.task-dispatch-retry-delay` | `1m` | 派发失败后的退避 |

以上四项只在 `production` profile 下被读取 —— 对应的 `@Scheduled` 方法本身也在 `production` 专属的 Bean 上。

## 存储

| 键 | 默认值 | 说明 |
|---|---|---|
| `offer-tracker.storage.type` | `local` | `local` 或 S3 兼容 |
| `offer-tracker.storage.resume-dir` | `./data/resumes` | 本地简历目录 |
| `offer-tracker.storage.ai-config-file` | `./data/ai-config.json` | 本地 AI 配置落盘位置 |
| `offer-tracker.storage.s3.{endpoint,region,bucket,prefix,path-style-access}` | 无默认（`prefix` 默认 `resumes`，`path-style-access` 默认 `true`） | 选择 S3 时必填 |
| `offer-tracker.resume-migration.batch-size` | `100` | 本地→S3 迁移批大小 |

## 规则链

| 键 | 默认值 | 位置 | 说明 |
|---|---|---|---|
| `liteflow.rule-source` | 无（必填） | yml | 追问裁决链的 XML 位置：`classpath:liteflow/ai-interview-followup.xml` |
| `liteflow.print-banner` | `true`（LiteFlow 默认），本项目设为 `false` | yml | 关闭启动 ASCII banner |
| `liteflow.print-execution-log` | `true`（LiteFlow 默认），本项目设为 `false` | yml | 关闭每次链执行的日志 |

**`rule-source` 没配会启动失败**（LiteFlow 在 `PARSE_ALL_ON_START` 阶段解析不到规则）。并且因为 `src/test/resources/application.yml` 会**整体覆盖**主配置，**测试侧必须再声明一次** —— 漏掉就会出现"主程序能跑、测试全挂"的诡异现象。

## 环境变量对照

yml 中大量使用 `${ENV:default}` 形式，便于容器化部署：

`DB_URL`、`DB_USER`、`DB_PASSWORD`、`REDIS_HOST`、`REDIS_PORT`、`REDIS_USERNAME`、`REDIS_PASSWORD`、`REDIS_SSL_ENABLED`、`MONGODB_URI`、`S3_ENDPOINT`、`S3_REGION`、`S3_BUCKET`、`S3_PREFIX`、`S3_PATH_STYLE_ACCESS`、`AI_CONNECT_TIMEOUT`、`AI_REQUEST_TIMEOUT`、`AI_REQUESTS_PER_MINUTE`、`AI_CONFIG_ENCRYPTION_KEY`、`AI_CONFIG_FILE`、`AI_TASK_POLL_DELAY`、`AI_TASK_DISPATCH_RETRY_DELAY`、`AUTH_REQUIRED`、`RESUME_STORAGE_TYPE`、`RESUME_STORAGE_DIR`、`TRUSTED_PROXY_CIDRS`

## 代理与客户端地址

`config/ClientIpResolver` + `config/TrustedProxyProperties` 决定是否采信 `X-Forwarded-For`：**只有直连地址命中 `TRUSTED_PROXY_CIDRS` 时才解析**，否则一律使用 socket 地址。这条规则有专项测试覆盖（`ClientIpResolverTest`），改动前先看测试。

## 改动这些配置时的注意点

1. `offer-tracker.ai.request-timeout` 调大 = 长事务最长时长变长 = 连接池占用窗口变长。它与 `AiSessionLock` 的租约时长需要一起看。
2. `offer-tracker.ai.requests-per-minute` 只在 `production` 生效，本地测不出限流行为。
3. 新增配置项时同时更新本文件，否则这里会变成误导性文档。
