---
name: ot-realtime-transport
description: offer-tracker 实时通道 Skill。用于确认「会话事件怎么推送、断线重连后怎么续、事件为什么可能丢」「语音/文本转写帧怎么去重、乱序帧怎么处理」；当调试前端收不到事件、重复事件或事件乱序时使用。
---

# ot-realtime-transport

实时通道是**可选的附加能力**：只在 `realtime` profile 下存在，默认 profile 完全没有这套东西。

## 使用顺序

1. 先看 `references/transport-contract.md`，确认 SSE 与 WebSocket 各自的输入、输出与顺序保证。

## 这层管什么

- `AiInterviewEventHub` 的 SSE 订阅与广播模型；
- 事件序号 `AiMessageSequenceService` 的产生方式与它保证的范围；
- `AiRealtimeWebSocketHandler` 对入站转写帧的去重规则；
- `AiInterviewRuntimeSnapshotService` 如何在**不确定事件总线是否存在**的情况下推送。

## 必守约束

- **实时事件是"建议性"的，MySQL 快照才是权威源。** 这句话写在 `AiInterviewEventHub` 的类注释里，也是整个设计的底线：**丢事件不算故障**。前端重连后必须能从 `GET /api/ai/interviews/{id}` 拿到完整状态。任何"靠事件补全状态"的改法都会破坏这个前提。
- **事件序号只在单次进程生命周期内单调。** 默认实现 `AiMessageSequenceService.Local` 用进程内 `ConcurrentHashMap<Long, AtomicLong>`，重启即归零。它**不是**持久化的 turn 号，不要拿它做跨重启的幂等依据。
- **注入事件总线必须用 `ObjectProvider`。** `AiInterviewRuntimeSnapshotService` 里写成 `ObjectProvider<AiInterviewEventHub>` + `events.getIfAvailable()`，这样默认 profile 下（总线的 Bean 不存在）快照逻辑照常工作。改成构造器直接注入会让非 `realtime` 环境启动失败。
- **帧序号与事件序号是两个概念。** WebSocket 入站帧带的是客户端自己产生的 `sequence`（`sourceSequence`），出站事件带的是服务端 `AiMessageSequenceService` 分配的 id。不要把两者混用。

## 参考资料

- `references/transport-contract.md`
