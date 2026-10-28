# Observability Sample (Console mode, offline)

A fully offline, single-process demo of the `a2a-t-observability` module: Console exporters print one JSON line per
signal to the console — no OTLP backend, no real LLM, no server required.

Entry point: `net.openan.a2at.sample.observability.ObservabilitySampleMain`

## Wiring

Four pieces wire the observability module into a client-side workflow (see
`src/main/java/net/openan/a2at/sample/observability/ObservabilitySampleMain.java`):

1. **Auto-setup** — configures the global OpenTelemetry SDK once. Without `OTEL_EXPORTER_OTLP_ENDPOINT` it falls back
   to Console exporters (dev mode) and warns on startup:

   ```java
   ObservabilitySetup.ensureConfigured(A2ATObservabilityConfig.fromEnv());
   ```

2. **Facade hook** — every `A2ATClient`/`A2ATServer` public method is wrapped as a span named
   `a2at.sdk.{role}.{method}`:

   ```java
   A2ATClient client = new A2ATClient(envPath, new SdkOperationSpans());
   ```

3. **LLM decorator** — records token usage from any `LLMClient` response into the `gen_ai.client.token.usage`
   histogram:

   ```java
   LLMClient observed = new ObservabilityLLMClient(new SampleLLM());
   observed.structured(List.of(Map.of("role", "user", "content", "hello")), Map.of(), null, null);
   ```

4. **Manual spans + negotiation metric** — an outer `A2ATSpan` roots the workflow; `NegotiationMetrics` reports the
   terminal round count into `a2at.negotiation.total_rounds`:

   ```java
   try (A2ATSpan root = new A2ATSpan("sample.workflow").open()) {
       // ... LLM call + client.getPrompts() from pieces 2 and 3 ...
       try (A2ATSpan rounds = new A2ATSpan("sample.negotiation-rounds").open()) {
           NegotiationMetrics.reportRounds("sample-negotiation", "accept", 3);
       }
   }
   ```

The demo generates a temporary `.env` at runtime (`A2AT_LANGUAGE=zh-CN`, `A2AT_NEGOTIATION_STATE_STORE_TYPE=in_memory`,
plus `A2AT_LLM_*` keys pointing at a scripted offline `SampleLLM` registered under the provider name
`a2at-observability-sample` — the facade constructor validates the provider even though `getPrompts()` never calls the
LLM).

## Running

```bash
mvn -pl a2a-t-sample compile exec:java -Dexec.mainClass=net.openan.a2at.sample.observability.ObservabilitySampleMain
```

or as a smoke test (spans are captured with `InMemorySpanExporter` and asserted instead of printed):

```bash
mvn -pl a2a-t-sample test -Dtest=ObservabilitySampleSmokeTest
```

Single process, no second terminal, no network. The demo runs for roughly 30 seconds by design: the Console span
exporter runs on the batch processor schedule (first span lines after ~5 s) and the Console metric exporter on the
periodic reader interval (metric lines after ~30 s).

## Expected output

All output goes to the console as SLF4J lines from logger `net.openan.a2at.sdk.observability.console` (one JSON
object per line; values shown here are illustrative, not fixed):

- At startup, a dev-mode warning: `a2at: no OTLP endpoint configured; using Console exporters (dev mode)`.
- After ~5 s, one `"signal":"span"` JSON line per span — `{"name":"...","signal":"span","data":{"status":"OK",...}}` —
  for `sample.workflow`, `a2at.sdk.client.getPrompts`, `a2at.sdk.client.getPrompt` and `sample.negotiation-rounds`,
  all sharing one `traceId` (the workflow root is the parent of the others).
- After ~30 s, one `"signal":"metric"` JSON line per instrument — `gen_ai.client.token.usage` (unit `{token}`) and
  `a2at.negotiation.total_rounds` (unit `{round}`).

Nothing is printed by `main()` itself; every line comes from the Console exporters.

## Configuration switches

All switches are read from environment variables (or the process `.env` when using `A2ATConfig`); defaults match the
root `env.example`:

| Variable | Default | Effect |
|---|---|---|
| `OTEL_INSTRUMENTATION_A2AT_SDK_ENABLED` | `true` | Master switch; `false` disables every SDK signal |
| `A2AT_OBSERVABILITY_ENABLED` | `true` | Gates `ObservabilitySetup.ensureConfigured` auto-setup |
| `A2AT_TRACE_ENABLED` | `true` | Trace signal switch (spans) |
| `A2AT_METRIC_ENABLED` | `true` | Metric signal switch (histograms) |
| `A2AT_LOG_ENABLED` | `true` | Log signal switch (structured events) |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | unset | unset → Console exporters; set → OTLP exporters |
| `A2AT_EXPORTER_PROTOCOL` | `grpc` | `grpc` or `http/protobuf` (used when the endpoint is set) |
| `A2AT_CONSOLE_EXPORTER` | `true` | With no endpoint: `false` leaves all signals no-op |
| `OTEL_SERVICE_NAME` / `A2AT_SERVICE_NAME` | `a2a-t-agent` | `service.name` resource attribute (`A2AT_` wins) |
| `A2AT_AGENT_ROLE` | unset | Opt-in `agent.role` resource attribute |
| `A2AT_EXTRACT_REQUEST` | `false` | Extract request payloads into log events |
| `A2AT_EXTRACT_RESPONSE` | `false` | Extract response payloads into log events |
| `A2AT_LOGS_PAYLOAD_ENABLED` | `false` | Enables payload capture for the logs channel |
| `A2AT_LOGS_PAYLOAD_MAX_LENGTH` | `4096` | Max payload length (characters) kept in log events |

## Switching to an OTLP backend

Point the endpoint at any OTLP receiver and pick the protocol; no code changes:

```bash
# gRPC (default), e.g. an OTel collector on 4317
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317

# HTTP/protobuf: traces, metrics and logs are posted to
# <endpoint>/v1/traces, <endpoint>/v1/metrics and <endpoint>/v1/logs
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318
A2AT_EXPORTER_PROTOCOL=http/protobuf
```

With an endpoint set, the Console exporters are replaced by OTLP exporters and the dev-mode warning disappears.

## Notes

- Facade span names follow `a2at.sdk.{role}.{method}` (`a2at.sdk.client.getPrompts`, `a2at.sdk.server.validate...`);
  manual spans use the name passed to `A2ATSpan`.
- Metric instruments: `gen_ai.client.token.usage` and `a2at.negotiation.total_rounds`.
- Protocol-level decorators (A2A `SendMessage`/`message:send` transport spans and similar wiring inside the client and
  server transports) are planned as a follow-up PR (Plan B); this sample covers the SDK facade, LLM and manual-span
  surfaces only.
- The logs channel emits structured JSON events through SLF4J on logger `net.openan.a2at.sdk.observability`; payload
  extraction stays off until enabled via `A2AT_LOGS_PAYLOAD_ENABLED`.
- The demo is safe to run repeatedly: the scripted LLM provider registration is guarded, and each run creates a fresh
  temporary `.env`.
