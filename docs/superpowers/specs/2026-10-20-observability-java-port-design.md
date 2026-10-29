# A2A-T Java SDK 可观测性设计（Python 版移植）

> **版本**: v1.0
> **日期**: 2026-10-20
> **定位**: 将 `a2a-t-sdk-python` 的可观测模块（v3.0 装饰器架构）忠实移植到 `a2a-t-sdk-java`
> **范围决策**: 完全对齐 Python（SDK 内部插桩 + a2a-java 协议层装饰器）；OTel 依赖采用 api 编译 + sdk/exporter 可选；双模块拆分；日志 JSON 行格式

---

## 1. 背景与目标

Python 版可观测模块（参照 `a2a-t-sdk-python/observity.md` 与 `src/a2a_t/observability/`）提供三大信号：

- **Trace**：协议层入口 Span（`SendMessage`/`GetTask` 等）、SSE per-event Span（同 trace 孤儿上下文 + Link）、W3C traceparent 传播
- **Metrics**：`gen_ai.client.operation.duration`、`gen_ai.client.token.usage`、`a2at.task.request.duration`（双端各记）、`a2at.negotiation.total_rounds`
- **Logs**：结构化事件（`task.request`、`task.status_changed`、`task.artifact`、`negotiation.message` 等）

### 1.1 Java 版的关键差异

| 差异点 | Python | Java |
|---|---|---|
| A2A 协议层归属 | SDK 内含（装饰 a2a-sdk 的 `ClientTransport`/`RequestHandler`） | SDK 模块**不含** a2a-java（仅 sample 依赖）；协议层插桩放入独立可选模块 |
| 鸭子类型 | observability 层用 `getattr` 结构式探测，零编译期 a2a 依赖 | Java 无法 duck-type，协议层模块必须编译期依赖 a2a-java 1.1.0.Final |
| 门面插桩 | `trace_facade()` 实例方法动态包装 | 门面为 final 类且 SDK 全同步，改为 `TelemetryHook` SPI + builder `@Nullable` 注入（沿用 `llmClient(@Nullable ...)` 惯例） |
| NoOp 降级 | 手写 `_NoOpTracer` 等类 | OTel API 天然 no-op（未装 SDK 时 `GlobalOpenTelemetry` 返回 no-op），只需开关短路 |
| LLM 用量桥接 | contextvar stash | ThreadLocal（SDK 全同步，无协程切换问题） |
| 客户端流式 | async generator | 回调式 `Consumer<StreamingEventKind>` |
| 服务端流式 | EventQueue 装饰 | `Flow.Publisher<StreamingEventKind>` 包装 |
| 日志 | Python logging JSON record（LoggingHandler 桥接 OTLP logs） | SLF4J + Jackson 序列化 JSON 行（**决策：JSON 而非仓库 key=value 惯例，对齐后端摄入管道**）+ OTel Logger API 双通道（§3） |

### 1.2 目标

- Span/Span 属性/Metric/Log 事件命名与 Python 版**逐字一致**，保证仪表盘查询零改动
- 永不破坏业务流：所有插桩点异常吞并 + WARN
- OTel 未安装 / 开关关闭时全链路 pass-through
- 核心 SDK 模块对 OTel 与 a2a-java 保持零依赖

## 2. 模块结构（方案 A，已确认）

```
a2a-t-observability            核心：配置/装配/手动 API/传播/日志/指标/LLM 装饰器
  依赖: a2a-t-core, a2a-t-llm,
        io.opentelemetry:opentelemetry-api (compile),
        opentelemetry-sdk + opentelemetry-exporter-otlp
        + opentelemetry-exporter-logging (<optional>true</optional>)

a2a-t-observability-a2a        协议层装饰器（可选模块）
  依赖: a2a-t-observability,
        org.a2aproject.sdk:a2a-java-sdk-client-transport-spi / -server-common / -spec / -jsonrpc-common
        / -transport-rest (1.1.0.Final，RestContextKeys.HEADERS_KEY 提取 headers)
```

- 两模块登记进根 `pom.xml` `<modules>` 与 `a2a-t-bom`
- 包名：`net.openan.a2at.sdk.observability`（及 `net.openan.a2at.sdk.observability.a2a`），包级 `@NullMarked`
- a2a-java 版本 1.1.0.Final（本地 `.m2` 已有；含 `ClientTransportWrapper` SPI、抽象类 `OpenTelemetryRequestHandlerDecorator` 先例、`PushNotificationSender`、`EventEnqueueHook`）

### 2.1 既有模块改动（最小侵入）

| 模块 | 改动 |
|---|---|
| `a2a-t-core` | 新增 `net.openan.a2at.sdk.core.telemetry.TelemetryHook` SPI 接口 + `NO_OP` 常量（见 §4） |
| `a2a-t-client` | `A2ATClient` 新增二级构造器 `A2ATClient(Path envPath, @Nullable TelemetryHook hook)`（既有构造器委托 NO_OP）；全部公开方法经 hook 包 `a2at.sdk.client.{method}` Span；`continueNegotiation` 终态时回调 `reportNegotiationRounds` |
| `a2a-t-server` | 同上（`A2ATServer(Path, @Nullable TelemetryHook)`，role=server） |
| `a2a-t-bom` / 根 pom | 登记两新模块与依赖管理 |

> 注：hook 注入点在**门面二级构造器**而非 builder——builder 只组装 orchestrator（非门面），hook 生命周期属于门面方法包装；与 §4 `trace_facade` 等价机制一致。

依赖方向保持单向：`observability → core/llm`，`observability-a2a → observability + a2a-java`。**`ObservabilityLLMClient` 由使用者/sample 组装时显式包装**（builder 不感知 OTel），文档给出示例。

## 3. 核心模块 `a2a-t-observability` 内部设计

与 Python `src/a2a_t/observability/` 文件一一对应：

| Python | Java 类 | 要点 |
|---|---|---|
| `config.py` | `A2ATObservabilityConfig` | record + builder + `fromMap(DotEnvConfigSource)`；显式 > 环境变量 > 默认 |
| `setup.py` | `ObservabilitySetup` | `ensureConfigured()`：自动装配 `SdkTracerProvider`/`SdkMeterProvider`/`SdkLoggerProvider` + `BatchSpanProcessor`/`PeriodicExportingMetricReader`/`BatchLogRecordProcessor`（**三信号**，对齐 Python）；OTLP gRPC（默认）或 HTTP/protobuf（endpoint 自动拼 `/v1/traces`、`/v1/metrics`、`/v1/logs`；Java `opentelemetry-exporter-otlp` 单 artifact 含三种 exporter）；无 endpoint 降级 Console（`opentelemetry-exporter-logging` 的 `LoggingSpanExporter`/`LoggingMetricExporter`/`LoggingLogRecordExporter`）；幂等（Java 无 Proxy provider 概念：内部 once 标志 + `GlobalOpenTelemetry.set()` 已设置时抛 `IllegalStateException` 捕获后跳过并 DEBUG）；`sdk` 类缺失时 `Class.forName` 探测跳过自动装配 |
| `_otel_compat.py` | `ObservabilityRuntime` | 总开关 `OTEL_INSTRUMENTATION_A2AT_SDK_ENABLED`、tracer/meter 访问器（scope `a2at-observability`/`1.0.0`）、trace/metric/log 三信号独立开关、`sameTraceOrphanContext`（同 trace 随机 span_id 无父上下文 + 0 值守卫） |
| `span.py` | `A2ATSpan implements AutoCloseable` | try-with-resources；kind 枚举 `INTERNAL/CLIENT/SERVER/PRODUCER/CONSUMER`；`setAttribute`/`addLink(A2ATSpan)`。**异常语义**（Java try-with-resources 的 close() 收不到业务异常，与 Python `__exit__(exc)` 不同）：增加 `recordError(Throwable)` 方法，惯用法在 catch 中调用；`close()` 按状态收尾——已 `recordError` → record_exception + ERROR，否则 OK。**若使用者既未调用 recordError 又发生异常，Span 以 UNSET 结束**（内部装饰器不经过此路径，状态语义由 §6 的显式 try/finally 全量保证） |
| `current_span.py` | `CurrentSpans.current()` → `A2ATCurrentSpan` | 只读视图：`traceId()`/`spanId()`/`isValid()`/`setAttribute()`；非 recording 时返回空 |
| `attributes.py` | `A2ATAttributes` + `AttributeExtractors` | 全部属性常量 + 提取器（§5 命名注册表） |
| `propagation.py` | `TracePropagation` | `inject(Map<String,String>)` / `extract(Map<String,String>)`；W3C traceparent；key 小写容错；extract 返回 null 当 span context 无效 |
| `payload.py` | `PayloadExtractor` + `PayloadSanitizer` | **提取**：a2a-java spec 对象（Jackson 注解 POJO）经 Jackson 序列化为 JSON（对齐 Python `MessageToDict`）；**净化**：先脱敏后截断（安全顺序）；默认 4096 + `[truncated]` 后缀；自定义 redactor 函数接口 |
| `logs.py` | `ObservabilityLogs` | SLF4J logger `net.openan.a2at.sdk.observability`；**JSON 行**（Jackson 序列化，`ensure_ascii=false` 等价）：`{"event": ..., <业务字段>, "trace_id", "span_id", "payload"?}`；事件名沿用 Python；log_enabled 双门控（运行时开关 + config）；**OTel 日志信号**：log_enabled 时同步写入 OTel `Logger` API（`SdkLoggerProvider` + OTLP/Console exporter），SLF4J 与 OTel 双通道共享同一 JSON 语义，对齐 Python LoggingHandler 桥接 |
| `llm_decorator.py` + `llm_stash.py` | `ObservabilityLLMClient implements LLMClient` + `LlmUsageStash` | `structured()` 返回后：从 `LLMResponse.usage`（键 `prompt_tokens`/`completion_tokens`，与 Python stash 读法一致）+ `model()` 取数；记 `gen_ai.client.token.usage` 直方图（unit `{token}`，按 `gen_ai.token.type`=input/output 各一样本）+ `LlmUsageStash`（ThreadLocal）暂存（input/output tokens、request model）供协议层入口 Span 取用（**取后清空**；trace 关闭时入口侧直接丢弃 stash） |
| `negotiation_metrics.py` | `NegotiationMetrics` | `a2at.negotiation.total_rounds` 直方图（unit `{round}`）；属性 `gen_ai.agent.a2at.extension.name="Negotiation-T"`、`negotiation.id`、`outcome`(accept/reject/abort)；仅终态触发；outcome 映射：Java `NegotiationStatus` 仅 `IN_PROGRESS/AGREED/REJECTED`（无 ABORT）→ `AGREED→accept`、`REJECTED→reject`（Python 的 abort 在 Java 无对应，不产生样本）；**数据路径差异**：Python 从返回的 `MetadataContent.negotiation_context` 取，Java 从门面入参/结果的 `NegotiationContext(type, negotiationId, round, NegotiationStatus)` 取（终态 status 映射 outcome），结果等价 |
| `sdk_trace.py` | `SdkOperationSpans` | `TelemetryHook` 的 OTel 实现：`a2at.sdk.{role}.{method}` INTERNAL Span |

指标 instrument 缓存沿用 Python 的惰性单例模式（`ConcurrentHashMap<String, LongHistogram>`）。

## 4. `TelemetryHook` SPI（a2a-t-core）

```java
package net.openan.a2at.sdk.core.telemetry;

public interface TelemetryHook {
    TelemetryOperation startSdkOperation(String role, String method);
    void reportNegotiationRounds(String negotiationId, String outcome, long totalRounds);

    TelemetryHook NO_OP = new TelemetryHook() { ... }; // 全 no-op
}
```

- `TelemetryOperation`：AutoCloseable（try-with-resources 结束 Span）+ `setAttribute(String, Object)`；调用方在业务成功后先调 `markSuccess()` 再 close（close 时若未 markSuccess 且无异常标记则记 ERROR——防御性默认，内部调用点全部受控、两条路径必走其一），异常路径由 try/catch 中的 `markError(Throwable)` 标记。与手动 API `A2ATSpan` 的差异：后者面向使用者，无法感知未上报的异常，未记录时 UNSET 结束（§3），两者语义各自明确
- 调用点一律 try/catch 包裹 + WARN，业务异常原样抛出
- `startSdkOperation` 等价 Python `trace_facade`（opt-in L4）：注入 hook 后每个门面方法包 Span，名 `a2at.sdk.client.generateTaskPrompt` / `a2at.sdk.server.checkTaskPrompt` 等

## 5. 命名注册表（与 Python 逐字一致）

### 5.1 Span 名（kind）

| Span 名 | kind | 端 |
|---|---|---|
| `SendMessage` / `SendStreamingMessage` | CLIENT / SERVER | 双端 |
| `SendStreamingMessage-event` | CLIENT / SERVER | 双端（同 trace 孤儿上下文 + Link 指向入口 Span） |
| `SendStreamingMessage-error` | CLIENT | 错误回调（`error.type` 属性；取消类异常豁免） |
| `SendMessage-negotiation` / `SendStreamingMessage-negotiation` | CLIENT / SERVER | 双端，协商 Message 触发（**父子**挂入口 Span；client 由响应/事件观测发，server 流式由 Publisher 侧发、sync 由收尾阶段发，避免双发） |
| `SubscribeToTask` / `GetTask` / `ListTasks` / `CancelTask` | CLIENT / SERVER | 双端 |
| `CreateTaskPushNotificationConfig` / `GetTaskPushNotificationConfig` / `ListTaskPushNotificationConfigs` / `DeleteTaskPushNotificationConfig` | CLIENT / SERVER | 双端 |
| `GetExtendedAgentCard` | CLIENT / SERVER | 双端 |
| `SendMessage-pushNotification` | CLIENT | PushSender 装饰器（Link 入口 Span） |
| `a2at.sdk.{role}.{method}` | INTERNAL | SDK 门面（opt-in） |

流结束判定：终态事件（`completed`/`failed`/`canceled`/`rejected`）。状态语义：取消 → ERROR "cancelled"；其他异常 → record_exception + ERROR；成功 → OK。

### 5.2 属性

- A2A-T 扩展（前缀 `gen_ai.agent.a2at.`）：`extension.name`（由扩展 URI 解析，基址 `https://projects.tmforum.org/a2aproject/telecommunication/extensions/` → `Negotiation-T` 等）、`task.id`、`task.status`、`task.type`、`negotiation.id/round/max_rounds/performative/total_rounds`、`notification.topic`、`streaming.event.kind`、`push.notification.url`、`authorization.policy.operation.type`
- 预授权（无前缀，对齐 Python）：`authorization.policy.id`、`authorization.operation_type`、`authorization.operation_risk_level`
- a2a-java 仪表盘双写（前缀 `gen_ai.agent.a2a.`，与 `A2AObservabilityNames` 对齐）：`operation.name`、`context_id`、`task_id`、`message_id`、`role`、`extensions`、`parts.number`、`protocol`、`request`、`response`
- GenAI semconv：`gen_ai.operation.name`、`gen_ai.conversation.id`、`gen_ai.usage.input_tokens/output_tokens`、`gen_ai.request.model`、`gen_ai.response.model`、`gen_ai.token.type`、`gen_ai.provider.name`、`streaming`、`error.type`
- 侧别：`a2at.span.side` = `client` | `server`
- 报文正则提取（默认值照搬 Python）：任务类型 `##\s*任务类型\(Task Type\)`、通知主题 `##\s*通知主题`、授权操作类型 `##\s*授权策略的操作类型`

### 5.3 指标

| 指标 | 类型/unit | 属性 |
|---|---|---|
| `gen_ai.client.operation.duration` | Histogram, s | `gen_ai.operation.name`（client 端） |
| `gen_ai.client.token.usage` | Histogram, `{token}` | `gen_ai.token.type`=input/output |
| `a2at.task.request.duration` | Histogram, s | `gen_ai.operation.name` + `a2at.span.side`（双端各记） |
| `a2at.negotiation.total_rounds` | Histogram, `{round}` | `extension.name` + `negotiation.id` + `outcome` |

### 5.4 日志事件

`task.request`(DEBUG)、`task.response`(DEBUG)、`task.status_changed`(INFO)、`task.artifact`(INFO)、`notification.push`(INFO)、`negotiation.message`(DEBUG)；字段：`task.id`、`task.status`、`artifact.name`、`extension.name`、`streaming.event.kind`（notification.push 携带）、`negotiation.id/round/performative`（negotiation.message 携带）、`trace_id`、`span_id`、`payload?`。

## 6. 协议层 `a2a-t-observability-a2a` 设计

### 6.1 Client：`A2ATClientTransportDecorator implements ClientTransport`

- 覆写全部 12 方法；入口 Span kind=CLIENT，名见 §5.1；**构造器防双重装饰守卫**（内层已是本装饰器则解包，杜绝双重 Span，对齐 Python factory-guard）
- **传播**：从 `ClientCallContext.getHeaders()`（小写容错）提取调用方 traceparent 为父（业务根传播 mode B）；无则显式 ROOT（`SpanContext` INVALID 语义等价物）；向 delegate 传递**新建** `ClientCallContext`（原 state + 注入入口 Span traceparent 的 headers，不改传入对象 —— 相比 Python 原地覆写 `service_parameters` 更安全，消除"覆写调用方业务根"缺陷）
- **流式**（`sendMessageStreaming`/`subscribeToTask`）：包装两个 Consumer；每事件发 `SendStreamingMessage-event`（同 trace 孤儿上下文 + Link + `gen_ai.conversation.id`）；错误 Consumer 发 `SendStreamingMessage-error`（`error.type`）；**取消/中止类异常（`CancellationException`）不发 error span**（对齐 Python CancelledError 豁免）。**注意**：Java 回调式流式模型下装饰器只观测、不可中断内层流——"终态事件"仅用于内部判定流尾（记录 final_event 供响应属性/`task.response` 日志），流的终止由 a2a-java 客户端层控制（与 Python 生成器 `break` 的能力差异，行为等价性由 a2a-java 保证）
- **协商 Span（client 侧）**：sync 响应与流式事件中的协商 Message 发 `{操作名}-negotiation`（CLIENT kind，**父子**挂入口 Span，即时结束；属性 `gen_ai.operation.name`/`extension.name`/`gen_ai.conversation.id`/`task.id`/`negotiation.*`）+ `negotiation.message` 日志
- **属性**：请求结构化提取 + 双写 + stash 中 LLM token 用量 + 正则提取（DEFAULT_* 回退、空串 opt-out、尾括号清洗）；**trace 关闭时 stash 必须丢弃**（防泄漏到下一请求入口 Span，对齐 Python）
- **指标**：`gen_ai.client.operation.duration` + `a2at.task.request.duration`(side=client)，双门控（运行时开关 + config 实例开关）
- 另提供 `A2ATA2ATClientTransportWrapper implements ClientTransportWrapper`（a2a-java 原生 SPI，可注册自动包装，等价 Python `register_client_factory`）

### 6.2 Server：`A2ATRequestHandlerDecorator implements RequestHandler`

- `onMessageSend`→`SendMessage`；`onMessageSendStream`→`SendStreamingMessage`（Span 存活至流结束，try/finally）；`onSubscribeToTask`→`SubscribeToTask`（仅入口，不重复观测——事件由产生请求侧观测）；其余简单包装；kind=SERVER
- **父上下文**：`ServerCallContext.getState()` 按 `RestContextKeys.HEADERS_KEY`（transport-rest）提取 headers，找不到时通用探测 `headers` key（小写容错，兼容 jsonrpc transport）
- **流式 per-event**：包装返回的 `Flow.Publisher<StreamingEventKind>`（订阅者包装，**仅流式操作**——同步 `SendMessage` 不产生幻影 event Span，对齐 Python `_start_event_span` 门）；每事件 `SendStreamingMessage-event` SERVER Span（孤儿 + Link）；协商 Message 事件发 `SendStreamingMessage-negotiation`（**父子**）+ `negotiation.message` 日志
- **同步 `SendMessage` 的协商 Span**：响应分类后在收尾阶段发（对齐 Python `_capture_response`，EventQueue/Publisher 侧只管流式，避免双发）
- **入口 Span 注册表**：有界 map（上限 2048，FIFO 淘汰），task_id 与 context_id 双 key 注册；**context_id fallback 仅当该记录的入口 Span 仍在 recording 时可被采纳**（防止复用上一请求的记录，对齐 Python `_find_record`）
- **指标**：`a2at.task.request.duration`（side=server，双门控）

### 6.3 协作装饰器

- `A2ATAgentExecutorDecorator implements AgentExecutor`：激活入口 Span（业务代码可见 `current_span()`）。用法：**先包 executor 再 `DefaultRequestHandler.create(...)`，后包 handler**（create 显式收 executor，无需反射）
- `A2ATPushSenderDecorator implements PushNotificationSender`：`SendMessage-pushNotification` CLIENT Span + Link 入口 Span + `push.notification.url` 属性。**注入点**：a2a-java 的 push 发送经 `MainEventBusProcessor(MainEventBus, TaskStore, PushNotificationSender, QueueManager)` 公共构造器注入 → 先装饰 sender 再构造。**URL 提取**：a2a-java `sendNotification(event, task)` 不携带 URL（URL 在内部私有 dispatch 中）；装饰器持有 `PushNotificationConfigStore`，发 Span 时按 task_id 查询配置取 URL（对齐 Python 经 `push_info.url` 的语义）
- `PayloadSanitizer` 复用核心模块；payload 提取（request/response JSON）受 `A2AT_EXTRACT_REQUEST/RESPONSE` 门控；span 属性通道（`gen_ai.agent.a2a.request/response`）与日志通道（`task.request`/`task.response`）共享同一 redact→truncate 语义

### 6.4 与 a2a-java 自带 OTel 插桩的关系

**二选一，不叠加**：使用本模块装饰器时，不得同时注册 `OpenTelemetryClientTransportWrapper` / `OpenTelemetryClientPropagatorTransportWrapper` / `OpenTelemetryRequestHandlerDecorator`，否则双重 Span。README 明示。

## 7. 配置

| 键 | 默认 | 说明 |
|---|---|---|
| `OTEL_INSTRUMENTATION_A2AT_SDK_ENABLED` | `true` | 总开关，false → 全 no-op |
| `A2AT_OBSERVABILITY_ENABLED` | `true` | 装饰器门闸，false → 装饰器纯转发 |
| `A2AT_TRACE_ENABLED` / `A2AT_METRIC_ENABLED` / `A2AT_LOG_ENABLED` | `true` | 三信号独立（trace 关，metrics/logs 照常） |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | 无 | 无 → Console 降级 |
| `A2AT_EXPORTER_PROTOCOL` | `grpc` | `grpc` \| `http/protobuf`（别名 `http`；非法 → grpc + WARN） |
| `A2AT_CONSOLE_EXPORTER` | `true` | 关闭 Console 降级 |
| `OTEL_SERVICE_NAME` / `A2AT_SERVICE_NAME` | `a2a-t-agent` | Resource `service.name`；`A2AT_AGENT_ROLE` → Resource `agent.role`（opt-in） |
| `A2AT_EXTRACT_REQUEST` / `A2AT_EXTRACT_RESPONSE` | `false` | payload 上 Span 属性 |
| `A2AT_LOGS_PAYLOAD_ENABLED` | `false` | 日志 payload 通道 |
| `A2AT_LOGS_PAYLOAD_MAX_LENGTH` | `4096` | 截断上限（`[truncated]`） |

读取顺序：`.env`（复用 core `DotEnvConfigSource`）→ 系统环境变量 → 默认值。`A2ATObservabilityConfig` 可编程构造传入各装饰器；正则可覆盖（空串 = opt-out）。懒装配：首次装饰调用时 `ensureConfigured()`。

## 8. 降级与错误处理（三条铁律）

1. **永不破坏业务流**：插桩点 try/catch + WARN（logger `net.openan.a2at.sdk.observability`），业务异常原样抛出
2. **全链路 pass-through**：OTel SDK 未安装 / 开关关 → 装饰器纯转发；OTel API 层天然 no-op 兜底
3. **Span 激活防泄漏（Java 简化）**：SDK 全同步，`Span.makeCurrent()` 返回的 `Scope` 在同线程 close 即可靠恢复——不需要 Python 的快照+强制修复逻辑（§11#10）；跨线程路径（executor 跑在 worker 线程、push 跑在 MainEventBusProcessor 线程）均在线程入口处显式激活/关闭，不依赖 ambient 跨线程继承

## 9. 测试策略

沿用仓库惯例：JUnit 5 + 手写 scripted stub + logback `ListAppender`；无 Mockito。

- **核心模块**：配置解析矩阵（显式/env/默认/非法值）、装配幂等与降级（无 endpoint → Console；无 sdk 类 → 跳过）、传播 inject/extract 往返与容错、Payload 脱敏→截断顺序、LLM 装饰器 token 指标与 stash 取后清空、协商指标终态触发
- **协议层**：scripted `ClientTransport`/`RequestHandler`/`AgentExecutor`/`PushNotificationSender` fake + a2a-java spec 真实类型构造请求/事件；用 `SdkTracerProvider` + 内存 `SpanProcessor`（收集已结束 Span）断言：Span 名/kind/属性/状态/Link 拓扑/per-event 序列/双端指标；**双重装饰守卫**（装饰器套装饰器只出一层 Span）；**取消豁免**（CancellationException 不发 error span）；**context_id fallback 仅 recording 可采纳**
- **公共 API 导出面合同**（对齐 Python `test_public_api.py` 的 "nothing more, nothing less"），Java 导出面基准：
  - 装配：`ObservabilitySetup.setup / isConfigured`
  - 集成：`A2ATClientTransportWrapper`、`A2ATRequestHandlerDecorator`（+ Executor/PushSender 装饰器）
  - 手动 API：`A2ATSpan`（try-with-resources）、`CurrentSpans.current()` / `A2ATCurrentSpan`
  - 门面追踪：`TelemetryHook`（+ `SdkOperationSpans` 实现）
  - 传播：`TracePropagation.inject/extract`、`TRACEPARENT_HEADER`
  - 配置：`A2ATObservabilityConfig`
  - 属性常量：§5.2 全量（A2A-T 扩展 14 个 + 预授权 3 个 + GenAI 7 个 + `a2at.span.side`/`streaming`/`error.type`）
- **日志 JSON 字段合同**（ListAppender + Jackson 解析断言 event/trace_id/span_id/payload）
- **既有模块回归**：builder `@Nullable` 注入点新增注入测试；全量 `mvn test`
- **Sample**：`a2a-t-sample` 新增 `observability` 示例场景（对标 Python `observability-sample`：Console 模式端到端跑通 trace/metrics/logs）

## 10. 交付边界（不做的事）

- 不做 Micrometer/Prometheus 桥、StatsD、Zipkin
- 不做 ByteBuddy/Java Agent 字节码插桩
- 不做动态实例包装（Python `trace_facade` 的 Java 等价由 builder 注入 hook 实现）
- 不叠加 a2a-java 自带 OTel 插桩（二选一）
- 协议层模块不追求兼容 a2a-java 1.0.0.Beta1（锁定 1.1.0.Final）

## 11. 可行性核查结论（2026-10-20，源码级验证）

| # | 接缝 | 结论 |
|---|---|---|
| 1 | Client 12 方法装饰 + `ClientCallContext(state, headers)` 新建实例注入 traceparent | ✅ 可行 |
| 2 | Server 11 方法装饰 + `Flow.Publisher` 订阅者包装 per-event Span | ✅ 可行 |
| 3 | traceparent 提取：`RestContextKeys.HEADERS_KEY`（transport-rest 公共常量）+ 通用探测回退 | ✅ 可行（依赖清单已补 transport-rest） |
| 4 | PushNotificationSender 注入：`MainEventBusProcessor(MainEventBus, TaskStore, PushNotificationSender, QueueManager)` 公共构造器 | ✅ 可行（DefaultRequestHandler 不直接收 sender；先装饰再构造） |
| 5 | `push.notification.url`：`sendNotification(event, task)` 无 URL 参数 | ⚠️ 调整：装饰器持 `PushNotificationConfigStore` 按 task_id 查 URL（§6.3） |
| 6 | `A2ATAgentExecutorDecorator`：`DefaultRequestHandler.create` 显式收 executor | ✅ 先包后建，无需反射 |
| 7 | LLM token：`LLMResponse.usage` 键 `prompt_tokens`/`completion_tokens` + `model()` | ✅ 与 Python stash 读法一致 |
| 8 | 协商轮次指标：Java 从入参 `NegotiationContext` 取（Python 从返回值取） | ✅ 数据路径小异，结果等价 |
| 9 | 同 trace 孤儿上下文：`SpanContext.create(traceId, randomSpanId, flags, state)` + `spanBuilder.setParent` | ✅ 可行（含 0 值 span_id 守卫） |
| 10 | 防泄漏激活：Java SDK 全同步，`Span.makeCurrent()`/`Scope.close()` 同步可靠 | ✅ 简化（Python 的跨 async 上下文修复逻辑不需要） |

## 12. 参考

- Python 实现：`a2a-t-sdk-python/src/a2a_t/observability/`（对照文件清单见 §3 表格；`__init__.py` 公共导出 24 符号为 API 面合同基准）
- Python 设计大纲：`a2a-t-sdk-python/observity.md`
- a2a-java 接缝（javap + 常量池扫描验证 1.1.0.Final）：`ClientTransport`、`ClientTransportWrapper`、`ClientCallContext(Map state, Map headers)`、`RequestHandler`（流式返回 `Flow.Publisher`）、`ServerCallContext.getState()` + `RestContextKeys.HEADERS_KEY`、`AgentExecutor`、`PushNotificationSender`/`BasePushNotificationSender`（私有 `dispatch(event, task, TaskPushNotificationConfig, headers)` 含 URL）、`MainEventBusProcessor` 构造器、`DefaultRequestHandler.create(AgentExecutor, TaskStore, QueueManager, PushNotificationConfigStore, MainEventBusProcessor, Executor, Executor)`、`A2AObservabilityNames`
