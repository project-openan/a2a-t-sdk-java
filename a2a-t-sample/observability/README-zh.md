# 可观测性样例（Console 模式，离线）

完全离线的单进程演示，展示 `a2a-t-observability` 模块的用法：Console exporter 把每个遥测信号打成一行 JSON 输出到
控制台——不需要 OTLP 后端、不需要真实 LLM、也不需要启动服务端。

入口类：`net.openan.a2at.sample.observability.ObservabilitySampleMain`

## 接线

四步把观测模块接入客户端工作流（见
`src/main/java/net/openan/a2at/sample/observability/ObservabilitySampleMain.java`）：

1. **自动装配**——一次性完成全局 OpenTelemetry SDK 配置。未设置 `OTEL_EXPORTER_OTLP_ENDPOINT` 时回退为 Console
   exporter（开发模式）并在启动时打 WARN：

   ```java
   ObservabilitySetup.ensureConfigured(A2ATObservabilityConfig.fromEnv());
   ```

2. **门面钩子**——`A2ATClient`/`A2ATServer` 的每个公开方法都会包一层 `a2at.sdk.{role}.{method}` 命名的 span：

   ```java
   A2ATClient client = new A2ATClient(envPath, new SdkOperationSpans());
   ```

3. **LLM 装饰器**——把任意 `LLMClient` 响应中的 token 用量记入 `gen_ai.client.token.usage` 直方图：

   ```java
   LLMClient observed = new ObservabilityLLMClient(new SampleLLM());
   observed.structured(List.of(Map.of("role", "user", "content", "hello")), Map.of(), null, null);
   ```

4. **手动 span + 协商指标**——外层 `A2ATSpan` 作为工作流根 span；`NegotiationMetrics` 把终止轮数记入
   `a2at.negotiation.total_rounds`：

   ```java
   try (A2ATSpan root = new A2ATSpan("sample.workflow").open()) {
       // ... 第 2、3 步的 LLM 调用与 client.getPrompts() ...
       try (A2ATSpan rounds = new A2ATSpan("sample.negotiation-rounds").open()) {
           NegotiationMetrics.reportRounds("sample-negotiation", "accept", 3);
       }
   }
   ```

样例在运行期生成临时 `.env`（`A2AT_LANGUAGE=zh-CN`、`A2AT_NEGOTIATION_STATE_STORE_TYPE=in_memory`，以及指向离线脚本
LLM `SampleLLM` 的 `A2AT_LLM_*` 键，provider 名为 `a2at-observability-sample`——门面构造器会校验 provider，虽然
`getPrompts()` 并不真正调用 LLM）。

## 运行

```bash
mvn -pl a2a-t-sample compile exec:java -Dexec.mainClass=net.openan.a2at.sample.observability.ObservabilitySampleMain
```

或以冒烟测试方式运行（span 用 `InMemorySpanExporter` 捕获并断言，而非打印）：

```bash
mvn -pl a2a-t-sample test -Dtest=ObservabilitySampleSmokeTest
```

单进程即可，无需另开终端、无需网络。演示整体耗时约 30 秒，这是 exporter 的固有节奏：Console span exporter 走
batch 处理器调度（约 5 秒后输出首批 span 行），Console metric exporter 走周期读取器（约 30 秒后输出指标行）。

## 预期输出

全部输出都是 logger `net.openan.a2at.sdk.observability.console` 打出的 SLF4J 行（每行一个 JSON 对象；具体取值仅为
示意，不保证逐字一致）：

- 启动时出现开发模式警告：`a2at: no OTLP endpoint configured; using Console exporters (dev mode)`。
- 约 5 秒后，每个 span 一行 `"signal":"span"` JSON——`{"name":"...","signal":"span","data":{"status":"OK",...}}`——
  分别为 `sample.workflow`、`a2at.sdk.client.getPrompts`、`a2at.sdk.client.getPrompt`、`sample.negotiation-rounds`，
  共享同一个 `traceId`（工作流根 span 是其余 span 的父节点）。
- 约 30 秒后，每个指标一行 `"signal":"metric"` JSON——`gen_ai.client.token.usage`（单位 `{token}`）与
  `a2at.negotiation.total_rounds`（单位 `{round}`）。

`main()` 自身不打印任何内容，所有输出均来自 Console exporter。

## 配置开关

所有开关均从环境变量（或 `A2ATConfig` 加载的 `.env`）读取，默认值与根目录 `env.example` 一致：

| 变量 | 默认值 | 作用 |
|---|---|---|
| `OTEL_INSTRUMENTATION_A2AT_SDK_ENABLED` | `true` | 总开关；`false` 时关闭全部 SDK 信号 |
| `A2AT_OBSERVABILITY_ENABLED` | `true` | 控制 `ObservabilitySetup.ensureConfigured` 自动装配 |
| `A2AT_TRACE_ENABLED` | `true` | Trace 信号开关（span） |
| `A2AT_METRIC_ENABLED` | `true` | Metric 信号开关（直方图） |
| `A2AT_LOG_ENABLED` | `true` | Log 信号开关（结构化事件） |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | 未设置 | 未设置 → Console exporter；设置 → OTLP exporter |
| `A2AT_EXPORTER_PROTOCOL` | `grpc` | `grpc` 或 `http/protobuf`（设置了 endpoint 时生效） |
| `A2AT_CONSOLE_EXPORTER` | `true` | 无 endpoint 时：`false` 表示所有信号保持 no-op |
| `OTEL_SERVICE_NAME` / `A2AT_SERVICE_NAME` | `a2a-t-agent` | `service.name` 资源属性（`A2AT_` 优先） |
| `A2AT_AGENT_ROLE` | 未设置 | 可选启用的 `agent.role` 资源属性 |
| `A2AT_EXTRACT_REQUEST` | `false` | 将请求载荷提取进日志事件 |
| `A2AT_EXTRACT_RESPONSE` | `false` | 将响应载荷提取进日志事件 |
| `A2AT_LOGS_PAYLOAD_ENABLED` | `false` | 开启日志通道的载荷捕获 |
| `A2AT_LOGS_PAYLOAD_MAX_LENGTH` | `4096` | 日志事件保留的载荷最大长度（字符） |

## 切换 OTLP 后端

把 endpoint 指向任意 OTLP receiver 并选择协议即可，代码零改动：

```bash
# gRPC（默认），例如 4317 端口上的 OTel Collector
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317

# http/protobuf：traces、metrics、logs 分别发送到
# <endpoint>/v1/traces、<endpoint>/v1/metrics 与 <endpoint>/v1/logs
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318
A2AT_EXPORTER_PROTOCOL=http/protobuf
```

设置 endpoint 后，Console exporter 会被 OTLP exporter 替换，开发模式警告不再出现。

## 说明

- 门面 span 命名遵循 `a2at.sdk.{role}.{method}`（`a2at.sdk.client.getPrompts`、`a2at.sdk.server.validate...`）；
  手动 span 使用传入 `A2ATSpan` 的名字。
- 指标：`gen_ai.client.token.usage` 与 `a2at.negotiation.total_rounds`。
- 协议层装饰器（A2A `SendMessage`/`message:send` 传输 span 等客户端与服务端传输内部的埋点）计划在后续 PR（Plan B）
  落地；本样例只覆盖 SDK 门面、LLM 与手动 span 面。
- 日志通道通过 SLF4J 在 logger `net.openan.a2at.sdk.observability` 上输出结构化 JSON 事件；载荷提取默认关闭，需通过
  `A2AT_LOGS_PAYLOAD_ENABLED` 开启。
- 样例可重复运行：脚本 LLM 的 provider 注册有防重护栏，每次运行都会创建新的临时 `.env`。
