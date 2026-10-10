# 状态机

实现在 `AiInterviewStateMachine`，用 `EnumMap` + `EnumSet` 显式声明迁移表 —— 迁移关系是数据，不是散落在业务方法里的 `if`。

## 迁移表

```java
TRANSITIONS.put(ACTIVE,    EnumSet.of(COMPLETED));
TRANSITIONS.put(COMPLETED, EnumSet.noneOf(AiInterviewStatus.class));
```

`COMPLETED` 的后继集合为空，即**终态**。要新增阶段，必须同时改这张表与 `AiInterviewStatus` 枚举。

## 两个判定入口

| 方法 | 用途 | 失败表现 |
|---|---|---|
| `requireActive(status)` | 断言会话仍可变更 | 409「AI 面试会话已结束」 |
| `requireTransition(source, target)` | 断言这条迁移边合法 | 409「不允许的 AI 面试状态转移: A -> B」 |

注意 `requireTransition` 对未知来源状态会走 `from == null` 分支，同样抛 409 —— 这是防"数据库里出现了枚举里没有的值"。

## 为什么用 EnumMap 而不是 if-else

- 迁移关系集中在一处，新增状态时不会漏改某个分支。
- `EnumSet.noneOf` 让"终态"成为显式声明，而不是靠"没写分支"隐式表达。
- 判定失败统一走 `BusinessException(409)`，与全局异常处理器约定一致。

## 与守卫条件的关系

状态机只回答"状态允不允许"，不回答"数据齐不齐"。两者分层：

- 状态层：`requireActive` / `requireTransition`
- 数据层：题目存在性（404）、是否已有作答（409）、是否已评分（409）、是否有已评分题目（409）

改动时不要把数据层校验塞进状态机 —— 那样状态机会依赖 Mapper，失去可单测性。

## 幂等与终态的关系

`finish` 对已 `COMPLETED` 的会话**不抛异常**，而是直接返回既有结果：

```java
if ("COMPLETED".equals(session.getStatus())) return get(sessionId);
```

这是幂等，不是绕过状态机；`requireTransition` 在它之后才执行。若把两者顺序调换，重复收尾会变成 409，破坏幂等语义。
