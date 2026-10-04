# Offer Tracker 投递进度追踪器

用于管理校招和实习投递全流程的单体应用，包含公司库、投递看板、自由状态调整、面试轮次和数据统计。

## 技术栈

- Java 21、Spring Boot 3、MyBatis-Plus
- H2（默认）或 MySQL（`mysql` profile）
- Flyway 数据库迁移、springdoc-openapi
- Vue 3、Element Plus、Vite
- JUnit 5、Testcontainers、Playwright
- Maven、GitHub Actions、Docker

## 快速开始

环境要求：JDK 21+、Maven 3.8+。首次构建会由 Maven 自动下载项目专用的 Node.js 和 npm。

```bash
# 一个命令安装前端依赖、运行前后端测试、构建页面并生成完整 JAR
mvn package

java -jar target/offer-tracker-0.1.0.jar
```

启动后访问：

- 应用首页：http://localhost:8080
- Swagger：http://localhost:8080/swagger-ui.html
- H2 控制台：http://localhost:8080/h2-console

## 登录认证

生产环境使用手机号 + 密码注册/登录，注册成功后会直接建立 Sa-Token 会话；浏览器会保存会话令牌，并在 API 请求中使用 Bearer 认证。生产会话存放在 Redis，默认有效期为 30 天；本地开发使用进程内会话。

当前手机号只是登录名，注册时不会验证号码归属，也没有密码找回。公开开放注册前，必须先接入短信验证，或限制为邀请注册并提供可靠的账号恢复流程。切换到 Sa-Token 后，先前签发的 JWT 会话需要重新登录。

## Windows 便携版

安装 JDK 21 后可在 Windows PowerShell 中构建内置 Java 运行环境的免安装版本：

```powershell
.\packaging\package-windows.ps1
```

产物位于 `dist/OfferTracker-Windows-x64-<version>.zip`。用户解压后双击
`OfferTracker.exe` 即可；程序会自动选择空闲端口、打开默认浏览器，并通过系统托盘提供
“打开”和“退出”操作。便携版数据保存在 `%LOCALAPPDATA%\OfferTracker\data`，不会写入安装目录。

## AI 面试增强版

`ai-interview` 分支是在基础投递看板上增加 AI 面试能力的增强版本；基础版不依赖 AI 服务，也可以独立使用公司、投递、看板、统计、日历和备份功能。

### AI-Meeting 策略适配状态

增强版已按 AI-Meeting 的可靠性思路适配，但保留轻量版可直接启动的特性：

- AI 面试使用显式 `ACTIVE -> COMPLETED` 状态机，结束操作有会话级并发保护。
- 相同用户、会话和问题的并发 AI 请求使用 single-flight 合并，避免重复调用和重复计费。
- 每次回答、评分、追问和结束操作都会写入 MySQL 运行态快照；Redis 热状态丢失时，MySQL 快照可作为恢复源。
- AI 任务继续使用 MySQL outbox + Redis Streams + lease recovery + dead-letter；系统不宣称第三方调用 exactly-once。
- 长对话归档、Redis 多实例 single-flight、SSE 流式输出、WebSocket 语音转写和 MongoDB 归档通过适配器保留扩展边界；它们不会成为本地 H2/轻量版的强制依赖。

策略适配记录见 [AI-Meeting 策略适配 ADR](docs/adr/0003-ai-meeting-strategy-adaptation.md)。

### 配置 AI 服务

启动应用后，打开右上角的“AI 设置”，填写服务地址、模型名称和 API Key。服务地址必须是 OpenAI 兼容 Chat Completions 接口，例如 `https://api.deepseek.com/v1`；模型名称必须是服务商实际支持的模型。项目支持 DeepSeek、OpenAI、通义等兼容服务，费用、速率限制和数据处理规则以所选服务商为准。

API Key 只保存在本机数据目录，页面只显示脱敏后的 Key，不会提交到 GitHub。不要把 API Key 写入代码、提交到仓库或发送到聊天中。

### 开始一次 AI 面试

1. 在“简历管理”中上传文字版 PDF 简历。
2. 在简历记录上点击“AI 面试”；如需岗位定制，可同时选择对应的投递记录。
3. 首题会参考简历以及公司、岗位、城市和薪资信息生成。
4. 提交回答后点击“获取 AI 评分”，评分完成后可以生成下一道追问。
5. 使用“上一题/下一题”查看本次面试的全部题目、回答和评分。
6. 至少完成一道题的 AI 评分后，点击“结束面试并生成总结”。结束后的面试为只读状态。

开始面试前，页面会提示简历文本和后续回答将发送给你配置的 AI 服务商；请确认服务商的数据处理政策后再继续。

### 历史记录和数据保留

- AI 面试会保存题目、回答、评分、反馈和总结，便于复盘。
- 删除简历文件或简历记录**不会删除**关联的 AI 面试历史。
- 清除 AI 配置只删除本机保存的服务地址、模型和 API Key，不会删除已有面试记录。
- 默认 H2 数据、简历文件和 AI 配置位于 `data/` 目录；Windows 便携版位于 `%LOCALAPPDATA%\OfferTracker\data`。备份时请保留整个数据目录。

### AI 配置文件位置

通常不需要环境变量；如需调整位置，可在启动前设置：

```powershell
$env:AI_CONFIG_FILE = 'D:\OfferTrackerData\ai-config.json'
$env:RESUME_STORAGE_DIR = 'D:\OfferTrackerData\resumes'
java -jar target/offer-tracker-0.1.0.jar
```

AI 配置文件包含敏感凭据，应限制文件访问权限，不要加入 JSON 备份、日志或版本控制。

## 开发模式

后端使用默认端口 `8080`：

```bash
mvn "-Dskip.frontend=true" spring-boot:run
```

前端开发服务器使用端口 `5173`，并将 `/api` 代理到后端：

```bash
cd web
npm install
npm run dev
```

统一运行前后端单元与集成测试：

```bash
mvn test
```

仅运行前端单元测试：

```bash
cd web
npm test
```

看板拖拽端到端测试会在 `18080` 端口启动独立 JAR，并使用内存 H2，不会修改开发数据：

```bash
mvn package
cd web
npx playwright install chromium
npm run test:e2e
```

如果本机已安装 Edge，也可以跳过 Chromium 下载并指定浏览器渠道；端口被占用时可同时覆盖测试端口：

```powershell
$env:E2E_BROWSER_CHANNEL = 'msedge'
$env:E2E_PORT = '19080'
npm run test:e2e
```

## 数据库

默认 H2 数据文件位于项目根目录的 `data/offer-tracker.mv.db`。停服后复制整个 `data/` 目录即可备份，恢复时将备份放回同一路径。

使用 MySQL 前先创建数据库：

```sql
CREATE DATABASE offer_tracker CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

```bash
DB_USER=root DB_PASSWORD=your-password \
  java -jar target/offer-tracker-0.1.0.jar --spring.profiles.active=mysql
```

Windows PowerShell：

```powershell
$env:DB_USER = 'root'
$env:DB_PASSWORD = 'your-password'
java -jar target/offer-tracker-0.1.0.jar --spring.profiles.active=mysql
```

Flyway 会在启动时自动执行 `src/main/resources/db/migration` 中的版本化迁移。

页面右上角的“数据备份与恢复”支持完整 JSON 备份、CSV 投递导出和 JSON 恢复。导入文件会先预检公司、投递、面试之间的引用和重复轮次；确认恢复时可以选择追加到现有数据，或清空现有数据后恢复。CSV 只用于表格分析，完整恢复应使用 JSON 备份。

## Docker

Dockerfile 使用多阶段构建，会在镜像中自动完成前端依赖安装、前端构建、后端测试和 JAR 打包，不依赖本机 `target/`。

```bash
docker build -t offer-tracker .
docker run --name offer-tracker -p 8080:8080 \
  -v offer-tracker-data:/app/data \
  offer-tracker
```

数据卷 `offer-tracker-data` 对应容器内的 `/app/data`。删除容器不会删除该卷；需要备份时应单独备份此卷。

## 公网生产启动

公网部署必须使用 MySQL、Redis 与 `production` profile。先在密钥管理系统中配置 `DB_URL`、`DB_USER`、`DB_PASSWORD`、`REDIS_HOST`、`REDIS_PASSWORD`、`AI_CONFIG_ENCRYPTION_KEY` 和对象存储的 `S3_ENDPOINT`、`S3_REGION`、`S3_BUCKET`；AI 加密密钥至少 32 个字符，需备份并保持稳定。Sa-Token 会话由 Redis 共享。Redis 默认启用 TLS，可通过 `REDIS_SSL_ENABLED=false` 覆盖；ACL 用户名可用 `REDIS_USERNAME` 配置。对象存储凭据使用 AWS SDK 默认凭据链（例如注入 `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY` 或实例角色）。生产 profile 强制启用认证；若任何配置将 `AUTH_REQUIRED` / `offer-tracker.auth.required` 设为 `false`，应用会拒绝启动：

生产环境中的用户 AI 服务地址必须使用 HTTPS 域名；应用拒绝 localhost 和 IP 字面量，但部署仍须在网络层禁止访问内网、链路本地地址及云元数据地址，以防用户控制的域名解析到内部网络。

```bash
java -jar target/offer-tracker-0.1.0.jar --spring.profiles.active=mysql,production
```

生产实例的业务端口为 `8080`，管理端口默认为 `8081`（可用 `MANAGEMENT_PORT` 修改）。负载均衡器使用 `/actuator/health/readiness` 检查就绪，`/actuator/health/liveness` 用于存活检查；Prometheus 从 `/actuator/prometheus` 抓取指标。管理端口只应对负载均衡器、编排平台和监控网络开放，不应映射到公网。AI 请求超时可用 `AI_CONNECT_TIMEOUT` 和 `AI_REQUEST_TIMEOUT` 调整，默认分别为 10 秒和 90 秒；生产环境 AI 生成/评分接口默认按用户限流为每分钟 10 次，可通过 `AI_REQUESTS_PER_MINUTE` 调整。同步 AI 接口不会对计费 POST 请求做盲目自动重试；异步任务 worker 会对失败任务最多尝试 3 次（包含首次），投递与执行语义为至少一次。如果供应商已处理请求但应用未能持久化结果，后续重试可能再次调用并产生额外费用；调用方应使用稳定的幂等键，运维方应监控重试和死信指标。

Prometheus 告警规则模板见 [生产告警规则](docs/ops/prometheus-alerts.yml)，部署时需接入现有 Prometheus/Alertmanager，并根据真实流量调整阈值。

备份恢复演练步骤见 [恢复演练 Runbook](docs/ops/restore-drill.md)。演练必须在隔离环境执行，不会自动删除源数据库、对象存储或历史文件。

只读容量测试基线见 [k6 容量测试说明](docs/ops/capacity-test.md)，默认不写入业务数据、不发送短信、不调用计费 AI 接口。

异步 AI 任务通过 `POST /api/ai/tasks` 提交，通过 `GET /api/ai/tasks/{id}` 查询；请求必须携带登录令牌和调用方生成的幂等键（最多 128 个字符）。目前支持 `GENERATE_QUESTION`（`resumeId`，可选 `applicationId`）、`EVALUATE_ANSWER` / `FOLLOW_UP`（`sessionId`、`questionId`）和 `FINISH_INTERVIEW`（`sessionId`）；所有 ID 必须为正整数，payload 仅允许对应字段且不超过 4 KiB。生产 worker 使用 Redis Streams，任务状态以 MySQL 为准，失败任务会有限重试并进入死信流。

应用还会对 `/api/auth/**` 执行共享 Redis IP 限流（默认每个客户端 IP 每分钟 60 次）。如果应用位于反向代理后面，请通过 `TRUSTED_PROXY_CIDRS` 配置代理的 CIDR（多个网段用逗号分隔），例如 `10.0.0.0/8,192.168.0.0/16`。只有直接连接地址命中这些网段时，应用才会从 `X-Forwarded-For` 解析客户端 IP；未配置或直连来源不可信时会忽略该请求头。必须阻止公网绕过负载均衡器直连应用端口，否则攻击者可以伪造代理来源。

不要在公网部署中使用默认 H2 或本地加密密钥。生产注册暂时是手机号 + 密码，不验证手机号所有权；用户可占用他人号码作为登录名，因此公开开放注册前必须接入短信验证或改为受控邀请注册。当前没有短信验证与密码找回流程。生产 profile 默认启用 S3 兼容简历存储，可通过 `RESUME_STORAGE_TYPE=local` 覆盖，但本地磁盘不适用于无共享存储的多实例部署。已有简历使用本地文件 locator，切换 S3 前必须先迁移对象并更新数据库 locator，不能只修改配置。上线前需实际验证对象存储权限、连通性、备份和生命周期策略。升级含历史数据的实例前，必须按 [历史数据归属切换说明](docs/LEGACY-DATA-CUTOVER.md) 明确旧数据所有者；不要让首个注册用户自动认领。

当前认证使用手机号作为登录名、密码登录和 Sa-Token 会话；登录失败会限流。手机号暂未验证，短信验证、密码找回和账号恢复列为后续工作，未完成前不应将开放注册用于公网正式服务。短信接入注意事项见[短信适配器后续方案](docs/ops/sms-adapter.md)。

## API 一览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/auth/register` | 手机号 + 密码注册并登录 |
| POST | `/api/auth/login` | 手机号 + 密码登录，返回 Bearer token |
| POST | `/api/auth/logout` | 注销当前 Sa-Token 会话 |
| POST | `/api/companies` | 新增公司 |
| GET | `/api/companies` | 公司列表 |
| PUT | `/api/companies/{id}` | 编辑公司 |
| DELETE | `/api/companies/{id}` | 删除未关联投递的公司 |
| POST | `/api/applications` | 新增投递记录 |
| GET | `/api/applications` | 分页查询，支持关键词、状态、公司、城市、渠道和日期区间筛选 |
| GET | `/api/applications/{id}` | 投递详情 |
| PUT | `/api/applications/{id}` | 编辑投递信息 |
| PUT | `/api/applications/{id}/status` | 自由调整投递状态 |
| DELETE | `/api/applications/{id}` | 删除投递并级联删除面试记录 |
| POST | `/api/applications/{id}/interviews` | 记录一轮面试 |
| GET | `/api/applications/{id}/interviews` | 面试轮次列表 |
| PUT | `/api/applications/interviews/{roundId}/result` | 更新面试结果 |
| GET | `/api/stats/overview` | 投递统计和 Offer 率 |
| GET | `/api/data/export` | 下载完整 JSON 备份 |
| GET | `/api/data/export.csv` | 下载投递 CSV |
| POST | `/api/data/import/validate` | 预检 JSON 备份 |
| POST | `/api/data/import` | 追加或替换恢复 JSON 备份 |
| POST | `/api/resumes` | 上传 PDF 简历 |
| GET | `/api/resumes` | 查询简历列表 |
| GET | `/api/resumes/{id}/file` | 下载简历文件 |
| DELETE | `/api/resumes/{id}` | 删除简历文件和记录，但保留 AI 面试历史 |
| GET | `/api/ai/config` | 查询 AI 配置状态和脱敏 Key |
| PUT | `/api/ai/config` | 保存 OpenAI 兼容服务配置 |
| DELETE | `/api/ai/config` | 清除本机 AI 配置 |
| POST | `/api/ai/interviews` | 创建 AI 面试并生成首题 |
| GET | `/api/ai/interviews?resumeId={id}` | 查询简历关联的 AI 面试历史 |
| GET | `/api/ai/interviews/{id}` | 查看全部题目、回答和评分 |
| PUT | `/api/ai/interviews/{sessionId}/questions/{questionId}/answer` | 保存回答 |
| POST | `/api/ai/interviews/{sessionId}/questions/{questionId}/evaluate` | 请求 AI 评分 |
| POST | `/api/ai/interviews/{sessionId}/questions/{questionId}/follow-up` | 生成下一道追问 |
| POST | `/api/ai/interviews/{sessionId}/finish` | 结束面试并生成总结 |

状态可以根据实际情况自由调整：`SAVED / APPLIED / WRITTEN_TEST / INTERVIEWING / OFFER / REJECTED / WITHDRAWN`。

统一响应结构为 `{"code": 0, "message": "ok", "data": ...}`，`code != 0` 表示失败，并配合正确的 HTTP 状态码。

## 构建与 CI

`mvn package` 是本项目的统一交付入口，生成的 `target/offer-tracker-0.1.0.jar` 已包含 `BOOT-INF/classes/static` 下的前端资源。只在前端已构建且需要单独调试后端时，可使用 `-Dskip.frontend=true` 跳过前端步骤。

GitHub Actions 会自动执行：

1. 前后端测试与完整 JAR 构建
2. JAR 静态资源内容校验
3. Chromium 看板拖拽端到端测试
4. Docker 多阶段镜像构建

## 项目结构

```text
src/main/java/com/offertracker
├── controller/   REST 接口
├── service/      业务逻辑
├── mapper/       数据访问
├── entity/       数据库实体
├── dto/          请求和响应对象
├── enums/        业务枚举
├── common/       统一响应和异常处理
└── config/       MyBatis-Plus 等配置

web/              Vue 前端
.github/workflows CI 工作流
```

## Roadmap

- [x] Vue 3 + Element Plus 看板与拖拽状态调整
- [x] 前端按需加载与代码分包（主 JS 由约 1.12 MB 降至约 100 KB）
- [x] 前后端统一构建、Docker 多阶段构建、自动化测试和 CI
- [x] 搜索筛选、公司管理与投递编辑
- [x] 完整 JSON 备份恢复与 CSV 导出
- [x] 加载失败重试、空状态、移动端与键盘可访问性
- [ ] 面试前提醒（定时任务 + 邮件/Webhook）
- [ ] 从招聘网站 URL 自动解析公司与岗位
- [ ] Excel 导入与逐行错误报告
- [ ] 手机号所有权验证与账号找回（短信服务商尚未接入）

## License

MIT
## Recent workflow updates

- 前端已适配灵动版工作台视觉，提供看板、投递分析、面试日历和表格四种真实数据视图，并支持深色模式。
- 可从顶部“收藏公司”提前保存心仪公司，岗位可留空，卡片会显示“岗位待确定”；切换到正式投递状态前补充岗位即可。
- 添加第一轮面试后，处于已收藏、已投递或历史笔试状态的记录会自动进入“面试中”；笔试作为面试类型保留，不再单独占用看板列。
- 面试记录在关闭详情、拖拽卡片或刷新后会重新从后端加载并保持。
- 筛选工具栏支持换行，看板按屏幕宽度自动多行排列，避免页面级横向滚动。

### 多实例协调配置

默认 profile 使用 JVM 内锁和 single-flight，适合本地开发及单实例轻量部署。多实例部署时启用 `redis` profile：

```bash
java -jar offer-tracker.jar --spring.profiles.active=redis
```

该 profile 使用 Redis 租约锁（30 秒自动过期，令牌校验释放）和 Redis single-flight（90 秒结果缓存与等待超时）。MySQL 仍是业务权威存储；运行态快照读取优先级为 MySQL，启用 `mongo-archive` 时才回退到 Mongo 冷归档。

实时面试事件可选启用 `realtime` profile，客户端通过 `/api/ai/interviews/{sessionId}/events` 建立 SSE 连接；事件带单调序号和 `Last-Event-ID` 游标。SSE 断线重连只用于补齐实时体验，恢复数据仍从 MySQL 快照读取。
