# 实时通道契约

全套组件都标了 `@Profile("realtime")`，默认 profile 下**一个都不存在**。

| 组件 | 角色 |
|---|---|
| `AiInterviewEventHub` | 会话 → 订阅者集合的内存注册表，负责广播 |
| `AiInterviewRealtimeController` | SSE 出口 `GET /api/ai/interviews/{sessionId}/events` |
| `AiRealtimeWebSocketHandler` | WebSocket 入口 `/api/ai/interviews/events`，接收转写帧 |
| `AiRealtimeWebSocketConfig` | 注册 WebSocket 端点 |
| `AiMessageSequenceService` | 出站事件序号 |

## 事件的两个来源

| 事件名 | 触发点 | payload |
|---|---|---|
| `snapshot` | `AiInterviewRuntimeSnapshotService.checkpoint()` 写完快照后 | 会话+题目的完整 JSON（`snapshot.getStateJson()`） |
| `transcript` | WebSocket 收到一帧合法转写后 | `{sourceSequence, text}` |

推送方式是通过 `ObjectProvider` 拿总线，拿到才推：

```java
AiInterviewEventHub hub = events.getIfAvailable();
if (hub != null) hub.publish(session.getId(), "snapshot", snapshot.getStateJson());
```

## SSE 出口

```java
@GetMapping(value = "/{sessionId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter events(@PathVariable Long sessionId,
                        @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId)
```

- `Last-Event-ID` 请求头由 `EventSource` 在自动重连时带上，解析失败按 0 处理。
- `subscribe(sessionId, cursor)` 建一个 `SseEmitter(0L)`（**超时值为 0，即不超时**），加入该会话的订阅者集合，并注册 `onCompletion` / `onTimeout` / `onError` 三个回调来移除自己。
- 订阅成功先发一个 `ready` 事件：
  ```java
  emitter.send(SseEmitter.event().name("ready").id(String.valueOf(Math.max(lastEventId, 0))).data("connected"));
  ```
  这个事件的 id 是**客户端上次的游标**，不是新序号 —— 它只是握手，不推进游标。

### 序号语义

```java
public void publish(Long sessionId, String type, Object payload) {
    long sequence = sequences.next(sessionId);
    for (SseEmitter emitter : subscribers.getOrDefault(sessionId, Set.of())) { ... id(String.valueOf(sequence)) ... }
}
```

- 序号是**每次 publish 递增一次**，在广播给所有订阅者之前取号，所以同一次广播的所有订阅者看到的是同一个 id。
- `AiMessageSequenceService` 的两个实现：`Redis`（`offer:ai:sequence:{sessionId}` 自增，跨实例）与 `Local`（进程内 `AtomicLong`，`!redis`）。**两者都不持久**，`Local` 重启归零，`Redis` 的键没有 TTL 但会随会话堆积 —— 它是"轮次序号"而不是"全局消息 id"。
- 发送失败的 emitter（`IOException`）会被从订阅者集合里摘掉。

### 订阅者集合的清理

`subscribers` 是 `ConcurrentHashMap<Long, Set<SseEmitter>>`，内层用 `ConcurrentHashMap.newKeySet()`。`remove` 时如果集合空了会顺手把外层 entry 也删掉，避免 map 随会话数无限增长。

## WebSocket 入口

端点 `/api/ai/interviews/events`，`setAllowedOriginPatterns()` 无参调用 → **允许所有来源**。生产环境若要收紧，改这一行。

### 入站帧格式

```json
{ "sessionId": 1, "sequence": 12, "text": "..." }
```

### 校验与去重

```java
if (sessionId <= 0 || sequence <= 0 || text.isBlank() || text.length() > 12000) {
    session.close(CloseStatus.BAD_DATA); return;
}
String key = session.getId() + ":" + sessionId;
Long previous = lastSequences.get(key);
if (previous != null && sequence <= previous) return;   // 静默丢弃
lastSequences.put(key, sequence);
```

- 四个条件任一不满足就**关闭连接**（`BAD_DATA`），不是丢帧：`sessionId`/`sequence` 必须为正、文本非空、长度 ≤ 12000。
- 去重键是 `WebSocketSession.getId() + ":" + sessionId`，即**按连接 + 会话**记录最后见过的序号。
- `sequence <= previous` 直接 `return`（不关连接）—— 这处理的是**重传与乱序**：序号只增不减，落后或重复的帧被静默丢弃。
- 连接关闭时 `afterConnectionClosed` 会把自己那条 key 清掉，避免 `lastSequences` 泄漏。

注意这里的 `text.length() > 12000` 用的是 UTF-16 长度，而 `AiTaskService` 的 4 KiB 限制用的是 UTF-8 字节数，两者不是同一把尺子，改阈值时别互相参照。

## 为什么可以"不保证送达"

会话状态的权威副本在 MySQL 快照表（`ai_interview_runtime_snapshots`）里，`AiInterviewRuntimeSnapshotService.rehydrate` 会用它补齐读到的会话。所以：

- 事件丢失 → 前端重连后拉一次 `GET /api/ai/interviews/{id}` 即可对齐；
- 事件重复 → 前端按事件 id 去重；
- 事件乱序 → 前端按事件 id 排序。

**这套通道的定位是"降低延迟"，不是"保证一致性"。** 任何需要一致性的场景都应该直接读接口，而不是等事件。
