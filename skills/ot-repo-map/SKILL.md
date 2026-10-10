---
name: ot-repo-map
description: offer-tracker 仓库地图与运行画像 Skill。用于确认「这段逻辑在哪个模块」「当前 profile 实际启用的是哪一套实现」「某个配置项的真相源在哪里」；当需要新增功能、判断改动影响面，或怀疑改了实现但运行时不生效时使用。
---

# ot-repo-map

先定位模块与运行画像，再动手改代码。

跳过这一步最常见的失败是：改了一个实现类，但当前 profile 激活的是它的兄弟实现 —— 代码"改对了却不生效"。

## 使用顺序

1. 先看 `references/module-map.md`，确认包边界与各包职责。
2. 再看 `references/profile-matrix.md`，确认当前环境激活的是哪一套实现。
3. 涉及具体参数时看 `references/config-index.md`。

## 这层管什么

- 六个业务域的分界：投递追踪、简历存储、身份认证、AI 面试、AI 运行时、异步任务。
- 「接口 + 多 profile 实现」的成对关系，以及两套独立的 profile 轴。
- Flyway 迁移 `V1`–`V19` 的归属与顺序。

## 必守约束

- **两条 profile 轴不要混为一谈**：`redis` 轴管锁、single-flight、去重、发号、事件序号；`production` 轴管限流、任务队列与 Worker、鉴权守卫、IP 限流。同一个功能不一定挂在同一条轴上。
- 新增实现类之前，先确认它挂在哪条 profile 轴上；否则在测试环境（默认 profile）可能根本不生效。
- 本仓库默认分支是 `ai-interview`（增强版）；`main` 是「免登录、免外部依赖、克隆即跑」的轻量版，**AI 能力只存在于前者**。
- 迁移脚本只增不改。已发布的 `V1`–`V19` 不允许回改，只能追加新版本号。

## 参考资料

- `references/module-map.md`
- `references/profile-matrix.md`
- `references/config-index.md`
