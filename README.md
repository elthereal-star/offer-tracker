# offer-tracker 投递进度追踪器

管理校招/实习投递全流程的小系统：公司库、投递记录、状态推进、面试轮次、数据看板。
解决"投了哪些公司、进行到哪一步、哪些该跟进了"这一求职季真实痛点。

## 技术栈

- Java 21 / Spring Boot 3
- MyBatis-Plus（基于 MyBatis 的增强 ORM）
- H2（默认，开箱即用）/ MySQL（`mysql` profile 一键切换）
- springdoc-openapi（自动生成 Swagger 接口文档）
- JUnit 5 / GitHub Actions CI / Docker

## 快速开始

```bash
# 环境要求：JDK 21+，Maven 3.8+
mvn spring-boot:run
```

启动后访问：

- Swagger 接口文档：http://localhost:8080/swagger-ui.html
- H2 控制台：http://localhost:8080/h2-console （JDBC URL: `jdbc:h2:file:./data/offer-tracker`）

使用 MySQL（先建库 `CREATE DATABASE offer_tracker;`）：

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

打包与容器化：

```bash
mvn package
java -jar target/offer-tracker-0.1.0.jar
docker build -t offer-tracker .
docker run -p 8080:8080 offer-tracker
```

## API 一览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/companies` | 新增公司 |
| GET | `/api/companies` | 公司列表 |
| POST | `/api/applications` | 新增投递记录 |
| GET | `/api/applications` | 分页查询（支持 status / companyId 过滤） |
| GET | `/api/applications/{id}` | 投递详情 |
| PUT | `/api/applications/{id}/status` | 推进状态 |
| DELETE | `/api/applications/{id}` | 删除投递（级联删除面试记录） |
| POST | `/api/applications/{id}/interviews` | 记录一轮面试 |
| GET | `/api/applications/{id}/interviews` | 面试轮次列表 |
| PUT | `/api/applications/interviews/{roundId}/result` | 更新面试结果 |
| GET | `/api/stats/overview` | 投递漏斗统计（总数/各状态数/Offer 率） |

状态机：`SAVED -> APPLIED -> WRITTEN_TEST -> INTERVIEWING -> OFFER / REJECTED / WITHDRAWN`

统一返回结构：`{"code": 0, "message": "ok", "data": ...}`，`code != 0` 为错误。

## 项目结构

```
src/main/java/com/offertracker
├── controller/   # REST 接口层
├── service/      # 业务逻辑层
├── mapper/       # MyBatis-Plus 数据访问层
├── entity/       # 数据库实体
├── dto/          # 请求/响应对象（Java record）
├── enums/        # 状态、面试类型等业务枚举
├── common/       # 统一返回体、全局异常处理
└── config/       # MyBatis-Plus 分页插件等配置
```

## Roadmap

- [ ] 前端页面（Vue 3 + Element Plus，看板式拖拽推进状态）
- [ ] 面试前提醒（定时任务 + 邮件/Webhook 通知）
- [ ] 从招聘网站 URL 自动解析公司与岗位
- [ ] 投递数据导入导出（Excel / CSV）
- [ ] 多用户与登录认证（Spring Security + JWT）

## License

MIT
