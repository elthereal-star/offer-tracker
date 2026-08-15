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

## Windows 便携版

安装 JDK 21 后可在 Windows PowerShell 中构建内置 Java 运行环境的免安装版本：

```powershell
.\packaging\package-windows.ps1
```

产物位于 `dist/OfferTracker-Windows-x64-<version>.zip`。用户解压后双击
`OfferTracker.exe` 即可；程序会自动选择空闲端口、打开默认浏览器，并通过系统托盘提供
“打开”和“退出”操作。便携版数据保存在 `%LOCALAPPDATA%\OfferTracker\data`，不会写入安装目录。

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

## API 一览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
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
- [ ] 多用户与登录认证（Spring Security + JWT）

## License

MIT
## Recent workflow updates

- 前端已适配灵动版工作台视觉，提供看板、投递分析、面试日历和表格四种真实数据视图，并支持深色模式。
- 可从顶部“收藏公司”提前保存心仪公司，岗位可留空，卡片会显示“岗位待确定”；切换到正式投递状态前补充岗位即可。
- 添加第一轮面试后，处于已收藏、已投递或历史笔试状态的记录会自动进入“面试中”；笔试作为面试类型保留，不再单独占用看板列。
- 面试记录在关闭详情、拖拽卡片或刷新后会重新从后端加载并保持。
- 筛选工具栏支持换行，看板按屏幕宽度自动多行排列，避免页面级横向滚动。
