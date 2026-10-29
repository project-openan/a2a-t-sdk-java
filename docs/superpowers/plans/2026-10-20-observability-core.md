# A2A-T Java SDK 可观测性（核心模块 + SPI + 门面接线）实施计划 — Plan A

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 按 `docs/superpowers/specs/2026-10-20-observability-java-port-design.md` 落地 `a2a-t-observability` 核心模块、`TelemetryHook` SPI 与门面接线（Plan B：a2a-java 协议层装饰器，单独计划，待本计划落地后编写）。

**Architecture:** 双新模块（core 观测 + a2a 协议层）；core 定义 `TelemetryHook` SPI，门面二级构造器注入 `@Nullable` hook 包装全部公开方法；OTel api 编译依赖 + sdk/exporter 可选；全同步 SDK，ThreadLocal 作 LLM 用量 stash。

**Tech Stack:** Java 17 / Maven 多模块 / OpenTelemetry 1.64.0（api 编译 + sdk、exporter-otlp `<optional>`；`exporter-logging` 不在本地 .m2 → Console 降级为内置 SLF4J JSON 导出器）/ JUnit 5（无 Mockito）/ SLF4J + Jackson。

## Global Constraints

- 构建/测试一律在仓库根目录：`mvn -q -pl <module> -am test`；提交前 `mvn -q spotless:apply`（Palantir 格式强制）。
- 每个新包必须有 `package-info.java` 且 `@NullMarked`（JSpecify）；可空参数/字段用 `org.jspecify.annotations.@Nullable`（NullAway ERROR）。
- 新代码不用 Lombok、不用 Mockito；测试用 JUnit 5 + 手写 stub + logback `ListAppender`。
- OTel 版本 1.64.0（root pom 属性 + bom import）；a2a-java 1.1.0.Final。
- 观测失败永不破坏业务：插桩点 catch RuntimeException → WARN（logger `net.openan.a2at.sdk.observability`）→ 吞掉；业务异常原样抛。
- 命名注册表与 spec §5 逐字一致；环境变量与 spec §7 一致。
- 开关读取顺序（全部键）：`System.getProperty(key)` → `System.getenv(key)` → 默认值（比 Python 多一级 getProperty，便于测试覆盖；行为等价）。
- 提交信息 conventional commits（`feat(observability): ...`）。

## File Structure（Plan A 产出）

```
pom.xml                                          (改: modules + 属性 + otel bom import + a2a-java 版本管理)
a2a-t-bom/pom.xml                                (改: +2 模块)
a2a-t-observability/pom.xml                      (新)
a2a-t-observability/src/main/java/net/openan/a2at/sdk/observability/
  package-info.java, A2ATObservabilityConfig.java, A2ATAttributes.java,
  AttributeExtractors.java, EventInfo.java, ObservabilityRuntime.java,
  A2ATSpan.java, A2ATCurrentSpan.java, CurrentSpans.java, TracePropagation.java,
  PayloadExtractor.java, PayloadSanitizer.java, LlmUsageStash.java, LlmUsageStashHolder.java,
  ObservabilityLLMClient.java, MetricHistograms.java, NegotiationMetrics.java,
  ObservabilityLogs.java, ConsoleExporters.java, ObservabilitySetup.java
a2a-t-observability/src/test/java/net/openan/a2at/sdk/observability/ (镜像测试)
a2a-t-observability-a2a/pom.xml                  (新: 骨架，Plan B 填充)
a2a-t-core/src/main/java/net/openan/a2at/sdk/core/telemetry/
  package-info.java, TelemetryHook.java, TelemetryOperation.java
a2a-t-client/src/main/java/net/openan/a2at/sdk/client/A2ATClient.java   (改: hook 接线)
a2a-t-server/src/main/java/net/openan/a2at/sdk/server/A2ATServer.java   (改: hook 接线)
env.example                                      (改: 可观测性小节)
```

> spec §3 的 attributes.py 在 Java 拆分为两半：core 模块放常量 + Map 级提取器 + `EventInfo`；a2a-java 类型化分类器（`StreamingEventKind`/`MessageSendParams` instanceof 分派）放 a2a 模块（Plan B）——依赖方向要求。

---

### Task 1: 模块骨架与依赖登记

**Files:**
- Modify: `pom.xml`（modules、properties、dependencyManagement）
- Modify: `a2a-t-bom/pom.xml`
- Create: `a2a-t-observability/pom.xml`、`a2a-t-observability/src/main/java/net/openan/a2at/sdk/observability/package-info.java`
- Create: `a2a-t-observability-a2a/pom.xml`、`a2a-t-observability-a2a/src/main/java/net/openan/a2at/sdk/observability/a2a/package-info.java`

**Interfaces:**
- Produces: 两个可编译空模块；root pom 属性 `opentelemetry.version=1.64.0`、`a2a-java.version=1.1.0.Final`；后续任务靠 root `dependencyManagement` 省略版本号。

- [ ] **Step 1: 根 pom 三处修改**

`<modules>` 中 `<module>a2a-t-server</module>` 之后追加：

```xml
        <module>a2a-t-observability</module>
        <module>a2a-t-observability-a2a</module>
```

`<properties>` 中 `<slf4j.version>2.0.17</slf4j.version>` 之后追加：

```xml
        <opentelemetry.version>1.64.0</opentelemetry.version>
        <a2a-java.version>1.1.0.Final</a2a-java.version>
```

`<dependencyManagement><dependencies>` 中 `<artifactId>jspecify</artifactId>` 条目之后追加：

```xml
            <dependency>
                <groupId>io.opentelemetry</groupId>
                <artifactId>opentelemetry-bom</artifactId>
                <version>${opentelemetry.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>org.a2aproject.sdk</groupId>
                <artifactId>a2a-java-sdk-client-transport-spi</artifactId>
                <version>${a2a-java.version}</version>
            </dependency>
            <dependency>
                <groupId>org.a2aproject.sdk</groupId>
                <artifactId>a2a-java-sdk-spec</artifactId>
                <version>${a2a-java.version}</version>
            </dependency>
            <dependency>
                <groupId>org.a2aproject.sdk</groupId>
                <artifactId>a2a-java-sdk-server-common</artifactId>
                <version>${a2a-java.version}</version>
            </dependency>
            <dependency>
                <groupId>org.a2aproject.sdk</groupId>
                <artifactId>a2a-java-sdk-jsonrpc-common</artifactId>
                <version>${a2a-java.version}</version>
            </dependency>
            <dependency>
                <groupId>org.a2aproject.sdk</groupId>
                <artifactId>a2a-java-sdk-transport-rest</artifactId>
                <version>${a2a-java.version}</version>
            </dependency>
```

- [ ] **Step 2: BOM 登记** — `a2a-t-bom/pom.xml` 的 `<artifactId>a2a-t-server</artifactId>` 条目后追加：

```xml
            <dependency>
                <groupId>net.openan.a2a-t.sdk</groupId>
                <artifactId>a2a-t-observability</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>net.openan.a2a-t.sdk</groupId>
                <artifactId>a2a-t-observability-a2a</artifactId>
                <version>${project.version}</version>
            </dependency>
```

- [ ] **Step 3: 核心模块 pom** — `a2a-t-observability/pom.xml`（父 POM + ${project.version} 依赖，模板同 `a2a-t-llm/pom.xml`）：

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>net.openan.a2a-t.sdk</groupId>
        <artifactId>a2a-t-parent</artifactId>
        <version>${revision}</version>
    </parent>
    <artifactId>a2a-t-observability</artifactId>
    <name>a2a-t Observability</name>
    <description>Observability core for the a2a-t Java SDK: config, setup, manual span API, propagation, logs, metrics, LLM decorator.</description>
    <dependencies>
        <dependency>
            <groupId>net.openan.a2a-t.sdk</groupId>
            <artifactId>a2a-t-core</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>net.openan.a2a-t.sdk</groupId>
            <artifactId>a2a-t-llm</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>io.opentelemetry</groupId>
            <artifactId>opentelemetry-api</artifactId>
        </dependency>
        <dependency>
            <groupId>io.opentelemetry</groupId>
            <artifactId>opentelemetry-sdk</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>io.opentelemetry</groupId>
            <artifactId>opentelemetry-exporter-otlp</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>ch.qos.logback</groupId>
            <artifactId>logback-classic</artifactId>
            <version>1.4.14</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.opentelemetry</groupId>
            <artifactId>opentelemetry-sdk-testing</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

（`opentelemetry-sdk-testing` 经 bom 管理 1.64.0，提供 `InMemorySpanExporter`/`InMemoryMetricReader`，测试基建统一走它。）

- [ ] **Step 4: a2a 模块骨架 pom** — `a2a-t-observability-a2a/pom.xml`：

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>net.openan.a2a-t.sdk</groupId>
        <artifactId>a2a-t-parent</artifactId>
        <version>${revision}</version>
    </parent>
    <artifactId>a2a-t-observability-a2a</artifactId>
    <name>a2a-t Observability A2A</name>
    <description>Protocol-level observability decorators for a2a-java (transport, request handler, executor, push sender). Filled by the observability-a2a plan.</description>
    <dependencies>
        <dependency>
            <groupId>net.openan.a2a-t.sdk</groupId>
            <artifactId>a2a-t-observability</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>org.a2aproject.sdk</groupId>
            <artifactId>a2a-java-sdk-client-transport-spi</artifactId>
        </dependency>
        <dependency>
            <groupId>org.a2aproject.sdk</groupId>
            <artifactId>a2a-java-sdk-spec</artifactId>
        </dependency>
        <dependency>
            <groupId>org.a2aproject.sdk</groupId>
            <artifactId>a2a-java-sdk-server-common</artifactId>
        </dependency>
        <dependency>
            <groupId>org.a2aproject.sdk</groupId>
            <artifactId>a2a-java-sdk-jsonrpc-common</artifactId>
        </dependency>
        <dependency>
            <groupId>org.a2aproject.sdk</groupId>
            <artifactId>a2a-java-sdk-transport-rest</artifactId>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 5: package-info** — 两个文件，内容模式一致：

```java
/** Observability core for the a2a-t Java SDK. */
@NullMarked
package net.openan.a2at.sdk.observability;

import org.jspecify.annotations.NullMarked;
```

（a2a 模块：`package net.openan.a2at.sdk.observability.a2a;`，注释 `/** Protocol-level observability decorators for a2a-java. */`）

- [ ] **Step 6: 验证编译** — 根目录运行 `mvn -q -pl a2a-t-observability,a2a-t-observability-a2a -am compile`，期望 BUILD SUCCESS（无 OTel/“a2a-java 缺失”报错）。

- [ ] **Step 7: 提交** — `mvn -q spotless:apply; git add pom.xml a2a-t-bom/pom.xml a2a-t-observability a2a-t-observability-a2a; git commit -m "feat(observability): add observability module skeletons and dependency registration"`

### Task 2: TelemetryHook SPI（a2a-t-core）

**Files:**
- Create: `a2a-t-core/src/main/java/net/openan/a2at/sdk/core/telemetry/package-info.java`
- Create: `a2a-t-core/src/main/java/net/openan/a2at/sdk/core/telemetry/TelemetryOperation.java`
- Create: `a2a-t-core/src/main/java/net/openan/a2at/sdk/core/telemetry/TelemetryHook.java`
- Test: `a2a-t-core/src/test/java/net/openan/a2at/sdk/core/telemetry/TelemetryHookTest.java`

**Interfaces:**
- Produces: `TelemetryHook.startSdkOperation(String role, String method) -> TelemetryOperation`、`TelemetryHook.reportNegotiationRounds(String negotiationId, String outcome, long totalRounds)`、`TelemetryHook.NO_OP`；`TelemetryOperation`（AutoCloseable）`setAttribute(String, Object)` / `markSuccess()` / `markError(Throwable)` / `close()` / `TelemetryOperation.NO_OP`。Task 10 门面与 Plan B `SdkOperationSpans` 依赖此面。

- [ ] **Step 1: 失败测试**

```java
package net.openan.a2at.sdk.core.telemetry;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TelemetryHookTest {

    @Test
    void noOpHookAbsorbsEverything() {
        TelemetryOperation operation = TelemetryHook.NO_OP.startSdkOperation("client", "getPrompts");
        assertSame(TelemetryOperation.NO_OP, operation);
        assertDoesNotThrow(() -> {
            operation.setAttribute("k", "v");
            operation.markSuccess();
            operation.markError(new RuntimeException("x"));
            operation.close();
            TelemetryHook.NO_OP.reportNegotiationRounds("n1", "accept", 3);
        });
    }

    @Test
    void customHookReceivesCalls() {
        List<String> started = new ArrayList<>();
        List<String> rounds = new ArrayList<>();
        TelemetryHook hook = new TelemetryHook() {
            @Override
            public TelemetryOperation startSdkOperation(String role, String method) {
                started.add(role + "." + method);
                return TelemetryOperation.NO_OP;
            }

            @Override
            public void reportNegotiationRounds(String negotiationId, String outcome, long totalRounds) {
                rounds.add(negotiationId + ":" + outcome + ":" + totalRounds);
            }
        };
        hook.startSdkOperation("server", "checkTaskPrompt").close();
        hook.reportNegotiationRounds("N1", "reject", 2);
        assertTrue(started.contains("server.checkTaskPrompt"));
        assertTrue(rounds.contains("N1:reject:2"));
    }
}
```

- [ ] **Step 2: 运行确认失败** — `mvn -q -pl a2a-t-core test "-Dtest=TelemetryHookTest"`，期望编译错误（类不存在）。

- [ ] **Step 3: 实现**

`TelemetryOperation.java`：

```java
package net.openan.a2at.sdk.core.telemetry;

/** One observed SDK operation; AutoCloseable for try-with-resources. */
public interface TelemetryOperation extends AutoCloseable {

    /** Attaches one attribute to the underlying observation. No-op values are ignored. */
    void setAttribute(String key, Object value);

    /** Marks the operation as business-successful before close. */
    void markSuccess();

    /** Marks the operation as failed; the Throwable is recorded verbatim. */
    void markError(Throwable error);

    /** Ends the operation. Idempotent. */
    @Override
    void close();

    TelemetryOperation NO_OP =
            new TelemetryOperation() {
                @Override
                public void setAttribute(String key, Object value) {}

                @Override
                public void markSuccess() {}

                @Override
                public void markError(Throwable error) {}

                @Override
                public void close() {}
            };
}
```

`TelemetryHook.java`：

```java
package net.openan.a2at.sdk.core.telemetry;

/**
 * Telemetry SPI consumed by SDK facades. Implementations live in optional observability modules;
 * the default {@link #NO_OP} keeps the core dependency-free.
 */
public interface TelemetryHook {

    /** Starts one facade-operation observation named {@code a2at.sdk.<role>.<method>}. */
    TelemetryOperation startSdkOperation(String role, String method);

    /** Reports one terminal negotiation round count (outcome: accept/reject). */
    void reportNegotiationRounds(String negotiationId, String outcome, long totalRounds);

    TelemetryHook NO_OP =
            new TelemetryHook() {
                @Override
                public TelemetryOperation startSdkOperation(String role, String method) {
                    return TelemetryOperation.NO_OP;
                }

                @Override
                public void reportNegotiationRounds(String negotiationId, String outcome, long totalRounds) {}
            };
}
```

`package-info.java`：`@NullMarked` 模式，注释 `/** Telemetry SPI for the a2a-t Java SDK core. */`。

- [ ] **Step 4: 运行确认通过** — `mvn -q -pl a2a-t-core test "-Dtest=TelemetryHookTest"`，期望 PASS。

- [ ] **Step 5: 提交** — `mvn -q spotless:apply; git add a2a-t-core; git commit -m "feat(observability): add TelemetryHook SPI to core"`

---

### Task 3: A2ATObservabilityConfig

**Files:**
- Create: `a2a-t-observability/src/main/java/net/openan/a2at/sdk/observability/A2ATObservabilityConfig.java`
- Test: `a2a-t-observability/src/test/java/net/openan/a2at/sdk/observability/A2ATObservabilityConfigTest.java`

**Interfaces:**
- Produces: record 15 字段（顺序即下码）+ `fromEnv()` / `fromMap(Map<String,String>)` / `builder()`；布尔键解析 truthy 集合 `{1,true,yes,on}`（大小写不敏感，其余 false）；协议别名 `http` → `http/protobuf`，非法 → `grpc`；`A2AT_SERVICE_NAME` 优先于 `OTEL_SERVICE_NAME`；正则字段仅编程式设置（无 env 键），空串 = opt-out、null = 用内置默认（消费者决定）。Task 4/8/9 与 Plan B 全部消费。

- [ ] **Step 1: 失败测试**

```java
package net.openan.a2at.sdk.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class A2ATObservabilityConfigTest {

    @Test
    void defaultsMatchSpec() {
        A2ATObservabilityConfig config = A2ATObservabilityConfig.fromEnv();
        assertTrue(config.enabled());
        assertTrue(config.traceEnabled());
        assertTrue(config.metricEnabled());
        assertTrue(config.logEnabled());
        assertEquals("grpc", config.protocol());
        assertEquals(A2ATObservabilityConfig.DEFAULT_PAYLOAD_LOG_MAX_LENGTH, config.payloadLogMaxLength());
        assertFalse(config.extractRequest());
        assertFalse(config.extractResponse());
        assertFalse(config.payloadLogEnabled());
    }

    @Test
    void mapValuesBeatDefaults() {
        A2ATObservabilityConfig config = A2ATObservabilityConfig.fromMap(Map.of(
                "A2AT_OBSERVABILITY_ENABLED", "false",
                "A2AT_TRACE_ENABLED", "false",
                "OTEL_EXPORTER_OTLP_ENDPOINT", "http://collector:4318",
                "A2AT_EXPORTER_PROTOCOL", "http",
                "A2AT_SERVICE_NAME", "svc",
                "A2AT_LOGS_PAYLOAD_ENABLED", "TRUE",
                "A2AT_LOGS_PAYLOAD_MAX_LENGTH", "128"));
        assertFalse(config.enabled());
        assertFalse(config.traceEnabled());
        assertTrue(config.metricEnabled());
        assertEquals("http/protobuf", config.protocol());
        assertEquals("http://collector:4318", config.endpoint());
        assertEquals("svc", config.serviceName());
        assertTrue(config.payloadLogEnabled());
        assertEquals(128, config.payloadLogMaxLength());
    }

    @Test
    void invalidProtocolFallsBackToGrpcAndInvalidIntFallsBackToDefault() {
        A2ATObservabilityConfig config = A2ATObservabilityConfig.fromMap(Map.of(
                "A2AT_EXPORTER_PROTOCOL", "udp", "A2AT_LOGS_PAYLOAD_MAX_LENGTH", "abc"));
        assertEquals("grpc", config.protocol());
        assertEquals(4096, config.payloadLogMaxLength());
    }

    @Test
    void builderBeatsMap() {
        A2ATObservabilityConfig config = A2ATObservabilityConfig.fromMap(Map.of("A2AT_SERVICE_NAME", "from-map"))
                .toBuilder()
                .serviceName("explicit")
                .taskTypeRegex("")
                .build();
        assertEquals("explicit", config.serviceName());
        assertEquals("", config.taskTypeRegex());
    }
}
```

- [ ] **Step 2: 运行确认失败** — `mvn -q -pl a2a-t-observability test "-Dtest=A2ATObservabilityConfigTest"`（编译错误）。

- [ ] **Step 3: 实现** — `A2ATObservabilityConfig.java`：

```java
package net.openan.a2at.sdk.observability;

import java.util.Locale;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Observability configuration: explicit builder > .env map > system env > spec defaults. */
public record A2ATObservabilityConfig(
        boolean enabled,
        boolean traceEnabled,
        boolean metricEnabled,
        boolean logEnabled,
        @Nullable String endpoint,
        String protocol,
        @Nullable String serviceName,
        @Nullable String taskTypeRegex,
        @Nullable String notificationTopicRegex,
        @Nullable String authorizationPolicyOperationTypeRegex,
        boolean extractRequest,
        boolean extractResponse,
        boolean payloadLogEnabled,
        int payloadLogMaxLength,
        @Nullable UnaryOperator<String> payloadRedactor) {

    private static final Logger LOG = LoggerFactory.getLogger(A2ATObservabilityConfig.class);

    public static final String ENV_ENABLED = "A2AT_OBSERVABILITY_ENABLED";
    public static final String ENV_TRACE = "A2AT_TRACE_ENABLED";
    public static final String ENV_METRIC = "A2AT_METRIC_ENABLED";
    public static final String ENV_LOG = "A2AT_LOG_ENABLED";
    public static final String ENV_ENDPOINT = "OTEL_EXPORTER_OTLP_ENDPOINT";
    public static final String ENV_PROTOCOL = "A2AT_EXPORTER_PROTOCOL";
    public static final String ENV_SERVICE_NAME = "A2AT_SERVICE_NAME";
    public static final String ENV_OTEL_SERVICE_NAME = "OTEL_SERVICE_NAME";
    public static final String ENV_AGENT_ROLE = "A2AT_AGENT_ROLE";
    public static final String ENV_EXTRACT_REQUEST = "A2AT_EXTRACT_REQUEST";
    public static final String ENV_EXTRACT_RESPONSE = "A2AT_EXTRACT_RESPONSE";
    public static final String ENV_PAYLOAD_LOG_ENABLED = "A2AT_LOGS_PAYLOAD_ENABLED";
    public static final String ENV_PAYLOAD_LOG_MAX_LENGTH = "A2AT_LOGS_PAYLOAD_MAX_LENGTH";
    public static final String ENV_CONSOLE_EXPORTER = "A2AT_CONSOLE_EXPORTER";

    public static final String DEFAULT_PROTOCOL = "grpc";
    public static final int DEFAULT_PAYLOAD_LOG_MAX_LENGTH = 4096;
    public static final String DEFAULT_SERVICE_NAME = "a2a-t-agent";

    public static A2ATObservabilityConfig fromEnv() {
        return fromMap(Map.of());
    }

    /** @param values .env file values (take precedence over system env); missing keys fall back to env then defaults */
    public static A2ATObservabilityConfig fromMap(Map<String, String> values) {
        Builder b = new Builder();
        b.enabled = bool(values, ENV_ENABLED, true);
        b.traceEnabled = bool(values, ENV_TRACE, true);
        b.metricEnabled = bool(values, ENV_METRIC, true);
        b.logEnabled = bool(values, ENV_LOG, true);
        b.endpoint = str(values, ENV_ENDPOINT);
        b.protocol = normalizeProtocol(str(values, ENV_PROTOCOL));
        String fromSdkKey = str(values, ENV_SERVICE_NAME);
        b.serviceName = fromSdkKey != null ? fromSdkKey : str(values, ENV_OTEL_SERVICE_NAME);
        b.extractRequest = bool(values, ENV_EXTRACT_REQUEST, false);
        b.extractResponse = bool(values, ENV_EXTRACT_RESPONSE, false);
        b.payloadLogEnabled = bool(values, ENV_PAYLOAD_LOG_ENABLED, false);
        b.payloadLogMaxLength = intOr(values, ENV_PAYLOAD_LOG_MAX_LENGTH, DEFAULT_PAYLOAD_LOG_MAX_LENGTH);
        return b.build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        Builder b = new Builder();
        b.enabled = enabled;
        b.traceEnabled = traceEnabled;
        b.metricEnabled = metricEnabled;
        b.logEnabled = logEnabled;
        b.endpoint = endpoint;
        b.protocol = protocol;
        b.serviceName = serviceName;
        b.agentRole = resolveAgentRole();
        b.taskTypeRegex = taskTypeRegex;
        b.notificationTopicRegex = notificationTopicRegex;
        b.authorizationPolicyOperationTypeRegex = authorizationPolicyOperationTypeRegex;
        b.extractRequest = extractRequest;
        b.extractResponse = extractResponse;
        b.payloadLogEnabled = payloadLogEnabled;
        b.payloadLogMaxLength = payloadLogMaxLength;
        b.payloadRedactor = payloadRedactor;
        return b;
    }

    /** Agent role: env-only opt-in (no builder default). */
    public @Nullable String resolveAgentRole() {
        String role = System.getProperty(ENV_AGENT_ROLE, System.getenv(ENV_AGENT_ROLE));
        return role == null || role.isBlank() ? null : role.trim();
    }

    static String normalizeProtocol(@Nullable String raw) {
        String value = (raw == null ? DEFAULT_PROTOCOL : raw).trim().toLowerCase(Locale.ROOT);
        if (value.equals("grpc")) {
            return "grpc";
        }
        if (value.equals("http") || value.equals("http/protobuf")) {
            return "http/protobuf";
        }
        LOG.warn("a2at: invalid exporter protocol {}; falling back to grpc", raw);
        return DEFAULT_PROTOCOL;
    }

    private static @Nullable String str(Map<String, String> values, String key) {
        String v = values.get(key);
        if (v != null && !v.isBlank()) {
            return v.trim();
        }
        String env = System.getenv(key);
        return env == null || env.isBlank() ? null : env.trim();
    }

    private static boolean bool(Map<String, String> values, String key, boolean def) {
        String v = str(values, key);
        if (v == null) {
            return def;
        }
        return switch (v.toLowerCase(Locale.ROOT)) {
            case "1", "true", "yes", "on" -> true;
            default -> false;
        };
    }

    private static int intOr(Map<String, String> values, String key, int def) {
        String v = str(values, key);
        if (v == null) {
            return def;
        }
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static final class Builder {
        private boolean enabled = true;
        private boolean traceEnabled = true;
        private boolean metricEnabled = true;
        private boolean logEnabled = true;
        private @Nullable String endpoint;
        private String protocol = DEFAULT_PROTOCOL;
        private @Nullable String serviceName;
        private @Nullable String agentRole;
        private @Nullable String taskTypeRegex;
        private @Nullable String notificationTopicRegex;
        private @Nullable String authorizationPolicyOperationTypeRegex;
        private boolean extractRequest;
        private boolean extractResponse;
        private boolean payloadLogEnabled;
        private int payloadLogMaxLength = DEFAULT_PAYLOAD_LOG_MAX_LENGTH;
        private @Nullable UnaryOperator<String> payloadRedactor;

        public Builder enabled(boolean v) { this.enabled = v; return this; }
        public Builder traceEnabled(boolean v) { this.traceEnabled = v; return this; }
        public Builder metricEnabled(boolean v) { this.metricEnabled = v; return this; }
        public Builder logEnabled(boolean v) { this.logEnabled = v; return this; }
        public Builder endpoint(@Nullable String v) { this.endpoint = v; return this; }
        public Builder protocol(String v) { this.protocol = normalizeProtocol(v); return this; }
        public Builder serviceName(@Nullable String v) { this.serviceName = v; return this; }
        public Builder agentRole(@Nullable String v) { this.agentRole = v; return this; }
        public Builder taskTypeRegex(@Nullable String v) { this.taskTypeRegex = v; return this; }
        public Builder notificationTopicRegex(@Nullable String v) { this.notificationTopicRegex = v; return this; }
        public Builder authorizationPolicyOperationTypeRegex(@Nullable String v) { this.authorizationPolicyOperationTypeRegex = v; return this; }
        public Builder extractRequest(boolean v) { this.extractRequest = v; return this; }
        public Builder extractResponse(boolean v) { this.extractResponse = v; return this; }
        public Builder payloadLogEnabled(boolean v) { this.payloadLogEnabled = v; return this; }
        public Builder payloadLogMaxLength(int v) { this.payloadLogMaxLength = v; return this; }
        public Builder payloadRedactor(@Nullable UnaryOperator<String> v) { this.payloadRedactor = v; return this; }

        public A2ATObservabilityConfig build() {
            if (payloadLogMaxLength <= 0) {
                throw new IllegalArgumentException("payloadLogMaxLength must be positive");
            }
            String name = serviceName != null ? serviceName : System.getenv(ENV_SERVICE_NAME);
            if (name == null || name.isBlank()) {
                name = System.getenv(ENV_OTEL_SERVICE_NAME);
            }
            return new A2ATObservabilityConfig(
                    enabled, traceEnabled, metricEnabled, logEnabled, endpoint, protocol,
                    name == null || name.isBlank() ? DEFAULT_SERVICE_NAME : name.trim(),
                    agentRole, taskTypeRegex, notificationTopicRegex, authorizationPolicyOperationTypeRegex,
                    extractRequest, extractResponse, payloadLogEnabled, payloadLogMaxLength, payloadRedactor);
        }
    }
}
```

注意：record 显式包含 `agentRole` 字段——在头部 15 字段列表中补上（`serviceName` 之后），构造器调用相应对齐。

- [ ] **Step 4: 运行确认通过** — `mvn -q -pl a2a-t-observability test "-Dtest=A2ATObservabilityConfigTest"`，期望 PASS。

- [ ] **Step 5: 提交** — `mvn -q spotless:apply; git add a2a-t-observability; git commit -m "feat(observability): add A2ATObservabilityConfig"`

---

### Task 4: A2ATAttributes + EventInfo + AttributeExtractors

**Files:**
- Create: `.../observability/A2ATAttributes.java`、`.../observability/EventInfo.java`、`.../observability/AttributeExtractors.java`
- Test: `.../observability/AttributeExtractorsTest.java`

**Interfaces:**
- Produces（后续所有任务与 Plan B 消费）：
  - `A2ATAttributes`：全部属性常量（spec §5.2 逐字值）
  - `EventInfo(String kind, @Nullable String taskId, @Nullable String taskStatus, boolean finalEvent, boolean terminal, @Nullable Map<String,Object> messageMetadata, @Nullable String artifactName)` + `EventInfo.UNKNOWN`
  - `AttributeExtractors.extensionNameFromUri(@Nullable String)`、`extensionNameFromMetadata(Map)`、`negotiationAttributes(Map)`、`isNegotiationMessage(Message)`、`normalizeTaskState(@Nullable TaskState)`、`classifyEvent(StreamingEventKind)`、`requestAttributes(String operationName, @Nullable Object params)`、`messageDoubleAttributes(Message, boolean clientSide)`、`regexAttributes(Map, A2ATObservabilityConfig)`
  - 常量 `NEGOTIATION_EXTENSION = "Negotiation-T"`、`NEGOTIATION_SUFFIX = "-negotiation"`、`EVENT_LOG_KINDS`

> 类型化提取器（`Message`/`StreamingEventKind`/`MessageSendParams` 参数）**不引 a2a-java 依赖就无法编译**——但 a2a-java 不在本模块依赖里。按 spec §3 的 Java 拆分：本任务只落 **Map 级**部分（extension/negotiation/regex/常量/EventInfo）；`classifyEvent`/`requestAttributes`/`messageDoubleAttributes` 等类型化分派放 **a2a 模块**（Plan B 的 `A2AJavaAttributeExtractors`）。本任务接口因此收窄为：

- `extensionNameFromUri(@Nullable String uri) -> @Nullable String`
- `extensionNameFromMetadata(Map<String,Object> metadata) -> @Nullable String`
- `isNegotiationMessage(Map<String,Object> metadata) -> boolean`（metadata 键含 Negotiation-T 扩展 URI）
- `negotiationAttributes(Map<String,Object> metadata) -> Map<String,Object>`（读 `negotiationContext.id/round/maxRounds/performative`，终态 performative 附 `total_rounds`）
- `normalizeTaskStateName(@Nullable String enumName) -> @Nullable String`（`TASK_STATE_COMPLETED` → `completed`）
- `regexAttributes(Map<String,Object> metadata, A2ATObservabilityConfig config) -> Map<String,Object>`（null regex → DEFAULT 回退、空串 opt-out、尾括号清洗）

- [ ] **Step 1: 失败测试**

```java
package net.openan.a2at.sdk.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AttributeExtractorsTest {

    private static final String NEGOTIATION_URI =
            "https://projects.tmforum.org/a2aproject/telecommunication/extensions/Negotiation-T";

    @Test
    void extensionNameFromUriParsesBase() {
        assertEquals("Negotiation-T", AttributeExtractors.extensionNameFromUri(NEGOTIATION_URI));
        assertEquals("Task-T", AttributeExtractors.extensionNameFromUri(
                "https://projects.tmforum.org/a2aproject/telecommunication/extensions/Task-T/sub"));
        assertNull(AttributeExtractors.extensionNameFromUri("https://other.example/ext"));
        assertNull(AttributeExtractors.extensionNameFromUri(null));
    }

    @Test
    void negotiationAttributesExtractedFromMetadata() {
        Map<String, Object> metadata = Map.of(NEGOTIATION_URI, "v1", "negotiationContext",
                Map.of("id", "N001", "round", 3, "maxRounds", 5, "performative", "ACCEPT"));
        Map<String, Object> attrs = AttributeExtractors.negotiationAttributes(metadata);
        assertEquals("N001", attrs.get(A2ATAttributes.ATTR_NEGOTIATION_ID));
        assertEquals(3, attrs.get(A2ATAttributes.ATTR_NEGOTIATION_ROUND));
        assertEquals(5, attrs.get(A2ATAttributes.ATTR_NEGOTIATION_MAX_ROUNDS));
        assertEquals("ACCEPT", attrs.get(A2ATAttributes.ATTR_NEGOTIATION_PERFORMATIVE));
        assertEquals(3, attrs.get(A2ATAttributes.ATTR_NEGOTIATION_TOTAL_ROUNDS));
    }

    @Test
    void nonTerminalPerformativeOmitsTotalRounds() {
        Map<String, Object> attrs = AttributeExtractors.negotiationAttributes(
                Map.of("negotiationContext", Map.of("id", "N1", "round", 1, "performative", "PROPOSE")));
        assertFalse(attrs.containsKey(A2ATAttributes.ATTR_NEGOTIATION_TOTAL_ROUNDS));
        assertTrue(AttributeExtractors.isNegotiationMessage(Map.of(NEGOTIATION_URI, "v1")));
        assertFalse(AttributeExtractors.isNegotiationMessage(Map.of("k", "v")));
    }

    @Test
    void regexAttributesFallBackToDefaultsAndHonorOptOut() {
        Map<String, Object> metadata = Map.of("text", "## 任务类型(Task Type)\n\n配置下发\n## 通知主题\n\nalarm");
        A2ATObservabilityConfig fallback = A2ATObservabilityConfig.fromEnv();
        Map<String, Object> attrs = AttributeExtractors.regexAttributes(metadata, fallback);
        assertEquals("配置下发", attrs.get(A2ATAttributes.ATTR_TASK_TYPE));
        assertEquals("alarm", attrs.get(A2ATAttributes.ATTR_NOTIFICATION_TOPIC));

        Map<String, Object> optedOut = AttributeExtractors.regexAttributes(
                metadata, A2ATObservabilityConfig.fromEnv().toBuilder().taskTypeRegex("").build());
        assertFalse(optedOut.containsKey(A2ATAttributes.ATTR_TASK_TYPE));
    }

    @Test
    void terminalStateNamesNormalize() {
        assertEquals("completed", AttributeExtractors.normalizeTaskStateName("TASK_STATE_COMPLETED"));
        assertEquals("canceled", AttributeExtractors.normalizeTaskStateName("TASK_STATE_CANCELED"));
        assertNull(AttributeExtractors.normalizeTaskStateName(null));
        assertTrue(A2ATAttributes.EVENT_LOG_KINDS.contains("status"));
        assertTrue(A2ATAttributes.EVENT_LOG_KINDS.contains("artifact"));
        assertTrue(A2ATAttributes.EVENT_LOG_KINDS.contains("completed"));
    }
}
```

- [ ] **Step 2: 运行确认失败** — `mvn -q -pl a2a-t-observability test "-Dtest=AttributeExtractorsTest"`。

- [ ] **Step 3: 实现**

`A2ATAttributes.java`（常量值与 spec §5.2 / Python attributes.py 逐字一致）：

```java
package net.openan.a2at.sdk.observability;

import java.util.Set;

/** Span attribute keys and shared constants (values byte-identical to the Python SDK). */
public final class A2ATAttributes {

    public static final String ATTR_EXTENSION_NAME = "gen_ai.agent.a2at.extension.name";
    public static final String ATTR_TASK_ID = "gen_ai.agent.a2at.task.id";
    public static final String ATTR_TASK_STATUS = "gen_ai.agent.a2at.task.status";
    public static final String ATTR_TASK_TYPE = "gen_ai.agent.a2at.task.type";
    public static final String ATTR_NEGOTIATION_ID = "gen_ai.agent.a2at.negotiation.id";
    public static final String ATTR_NEGOTIATION_ROUND = "gen_ai.agent.a2at.negotiation.round";
    public static final String ATTR_NEGOTIATION_MAX_ROUNDS = "gen_ai.agent.a2at.negotiation.max_rounds";
    public static final String ATTR_NEGOTIATION_PERFORMATIVE = "gen_ai.agent.a2at.negotiation.performative";
    public static final String ATTR_NEGOTIATION_TOTAL_ROUNDS = "gen_ai.agent.a2at.negotiation.total_rounds";
    public static final String ATTR_NOTIFICATION_TOPIC = "gen_ai.agent.a2at.notification.topic";
    public static final String ATTR_STREAMING_EVENT_KIND = "gen_ai.agent.a2at.streaming.event.kind";
    public static final String ATTR_PUSH_NOTIFICATION_URL = "gen_ai.agent.a2at.push.notification.url";
    public static final String ATTR_SPAN_SIDE = "a2at.span.side";
    public static final String ATTR_AUTHORIZATION_POLICY_OPERATION_TYPE =
            "gen_ai.agent.a2at.authorization.policy.operation.type";
    public static final String ATTR_AUTHORIZATION_POLICY_ID = "authorization.policy.id";
    public static final String ATTR_AUTHORIZATION_OPERATION_TYPE = "authorization.operation_type";
    public static final String ATTR_AUTHORIZATION_OPERATION_RISK_LEVEL = "authorization.operation_risk_level";
    public static final String ATTR_A2A_OPERATION_NAME = "gen_ai.agent.a2a.operation.name";
    public static final String ATTR_A2A_CONTEXT_ID = "gen_ai.agent.a2a.context_id";
    public static final String ATTR_A2A_TASK_ID = "gen_ai.agent.a2a.task_id";
    public static final String ATTR_A2A_MESSAGE_ID = "gen_ai.agent.a2a.message_id";
    public static final String ATTR_A2A_ROLE = "gen_ai.agent.a2a.role";
    public static final String ATTR_A2A_EXTENSIONS = "gen_ai.agent.a2a.extensions";
    public static final String ATTR_A2A_PARTS_NUMBER = "gen_ai.agent.a2a.parts.number";
    public static final String ATTR_A2A_PROTOCOL = "gen_ai.agent.a2a.protocol";
    public static final String ATTR_A2A_REQUEST = "gen_ai.agent.a2a.request";
    public static final String ATTR_A2A_RESPONSE = "gen_ai.agent.a2a.response";
    public static final String ATTR_GEN_AI_OPERATION_NAME = "gen_ai.operation.name";
    public static final String ATTR_GEN_AI_CONVERSATION_ID = "gen_ai.conversation.id";
    public static final String ATTR_GEN_AI_USAGE_INPUT_TOKENS = "gen_ai.usage.input_tokens";
    public static final String ATTR_GEN_AI_USAGE_OUTPUT_TOKENS = "gen_ai.usage.output_tokens";
    public static final String ATTR_GEN_AI_REQUEST_MODEL = "gen_ai.request.model";
    public static final String ATTR_GEN_AI_RESPONSE_MODEL = "gen_ai.response.model";
    public static final String ATTR_GEN_AI_TOKEN_TYPE = "gen_ai.token.type";
    public static final String ATTR_GEN_AI_PROVIDER_NAME = "gen_ai.provider.name";
    public static final String ATTR_STREAMING = "streaming";
    public static final String ATTR_ERROR_TYPE = "error.type";

    public static final String NEGOTIATION_EXTENSION = "Negotiation-T";
    public static final String NEGOTIATION_SUFFIX = "-negotiation";

    public static final Set<String> EVENT_LOG_KINDS = Set.of(
            "status", "artifact", "completed", "canceled", "failed", "rejected");

    private A2ATAttributes() {}
}
```

`EventInfo.java`：

```java
package net.openan.a2at.sdk.observability;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Classified view of one A2A stream event (protocol-agnostic). */
public record EventInfo(
        String kind,
        @Nullable String taskId,
        @Nullable String taskStatus,
        boolean finalEvent,
        boolean terminal,
        @Nullable Map<String, Object> messageMetadata,
        @Nullable String artifactName) {

    public static final EventInfo UNKNOWN =
            new EventInfo("unknown", null, null, false, false, null, null);

    public boolean isNegotiationCandidate() {
        return "message".equals(kind);
    }
}
```

`AttributeExtractors.java`：

```java
package net.openan.a2at.sdk.observability;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** Metadata-level attribute extractors (Map in / attributes out); never raises. */
public final class AttributeExtractors {

    public static final String EXTENSION_BASE =
            "https://projects.tmforum.org/a2aproject/telecommunication/extensions/";
    public static final String DEFAULT_TASK_TYPE_REGEX = "##\\s*任务类型\\(Task Type\\)\\s*\\n+\\s*(\\S[^\\n]*)";
    public static final String DEFAULT_NOTIFICATION_TOPIC_REGEX = "##\\s*通知主题\\s*\\n+\\s*(\\S[^\\n]*)";
    public static final String DEFAULT_AUTHORIZATION_REGEX = "##\\s*授权策略的操作类型\\s*\\n+\\s*(\\S[^\\n]*)";
    private static final java.util.Set<String> TERMINAL_PERFORMATIVES = java.util.Set.of("ACCEPT", "REJECT", "ABORT");

    private AttributeExtractors() {}

    public static @Nullable String extensionNameFromUri(@Nullable String uri) {
        if (uri == null || !uri.startsWith(EXTENSION_BASE)) {
            return null;
        }
        String rest = uri.substring(EXTENSION_BASE.length());
        int slash = rest.indexOf('/');
        String name = slash >= 0 ? rest.substring(0, slash) : rest;
        return name.isEmpty() ? null : name;
    }

    public static @Nullable String extensionNameFromMetadata(Map<String, Object> metadata) {
        for (String key : metadata.keySet()) {
            String name = extensionNameFromUri(key);
            if (name != null) {
                return name;
            }
        }
        return null;
    }

    public static boolean isNegotiationMessage(Map<String, Object> metadata) {
        return AttributeExtractors.NEGOTIATION_EXTENSION.equals(extensionNameFromMetadata(metadata));
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> negotiationAttributes(Map<String, Object> metadata) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        Object contextObj = metadata.get("negotiationContext");
        if (!(contextObj instanceof Map<?, ?> context)) {
            return attrs;
        }
        Map<String, Object> ctx = (Map<String, Object>) context;
        Object id = ctx.get("id");
        if (id instanceof String s && !s.isEmpty()) {
            attrs.put(A2ATAttributes.ATTR_NEGOTIATION_ID, s);
        }
        Integer round = wireInt(ctx.get("round"));
        if (round != null) {
            attrs.put(A2ATAttributes.ATTR_NEGOTIATION_ROUND, round);
        }
        Integer maxRounds = wireInt(ctx.get("maxRounds"));
        if (maxRounds != null) {
            attrs.put(A2ATAttributes.ATTR_NEGOTIATION_MAX_ROUNDS, maxRounds);
        }
        Object performative = ctx.get("performative");
        if (performative instanceof String p && !p.isEmpty()) {
            attrs.put(A2ATAttributes.ATTR_NEGOTIATION_PERFORMATIVE, p);
            if (TERMINAL_PERFORMATIVES.contains(p) && round != null) {
                attrs.put(A2ATAttributes.ATTR_NEGOTIATION_TOTAL_ROUNDS, round);
            }
        }
        return attrs;
    }

    private static @Nullable Integer wireInt(Object value) {
        if (value instanceof Integer i) {
            return i;
        }
        if (value instanceof Number n && n.longValue() == n.doubleValue()) {
            return n.intValue();
        }
        return null;
    }

    public static @Nullable String normalizeTaskStateName(@Nullable String enumName) {
        if (enumName == null) {
            return null;
        }
        String prefix = "TASK_STATE_";
        return enumName.startsWith(prefix) ? enumName.substring(prefix.length()).toLowerCase(java.util.Locale.ROOT)
                : enumName.toLowerCase(java.util.Locale.ROOT);
    }

    public static Map<String, Object> regexAttributes(Map<String, Object> metadata, A2ATObservabilityConfig config) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        putRegex(attrs, A2ATAttributes.ATTR_TASK_TYPE, metadata,
                config.taskTypeRegex() != null ? config.taskTypeRegex() : DEFAULT_TASK_TYPE_REGEX, false);
        putRegex(attrs, A2ATAttributes.ATTR_NOTIFICATION_TOPIC, metadata,
                config.notificationTopicRegex() != null ? config.notificationTopicRegex()
                        : DEFAULT_NOTIFICATION_TOPIC_REGEX,
                false);
        String authorization = findFirst(metadata, config.authorizationPolicyOperationTypeRegex() != null
                ? config.authorizationPolicyOperationTypeRegex() : DEFAULT_AUTHORIZATION_REGEX);
        if (authorization != null) {
            String cleaned = authorization.replaceAll("[（(][^）)]*[）)]\\s*$", "").trim();
            if (!cleaned.isEmpty()) {
                attrs.put(A2ATAttributes.ATTR_AUTHORIZATION_POLICY_OPERATION_TYPE, cleaned);
            }
        }
        return attrs;
    }

    private static void putRegex(Map<String, Object> attrs, String key, Map<String, Object> metadata,
            @Nullable String pattern, boolean unused) {
        if (pattern == null || pattern.isEmpty()) {
            return;
        }
        String found = findFirst(metadata, pattern);
        if (found != null && !found.isEmpty()) {
            attrs.put(key, found);
        }
    }

    private static @Nullable String findFirst(Map<String, Object> metadata, @Nullable String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return null;
        }
        Pattern compiled = Pattern.compile(pattern);
        for (Object value : metadata.values()) {
            if (value instanceof String s) {
                Matcher matcher = compiled.matcher(s);
                if (matcher.find()) {
                    return matcher.groupCount() > 0 ? matcher.group(1) : matcher.group();
                }
            }
        }
        return null;
    }
}
```

- [ ] **Step 4: 运行确认通过** — `mvn -q -pl a2a-t-observability test "-Dtest=AttributeExtractorsTest"`，期望 PASS。

- [ ] **Step 5: 提交** — `mvn -q spotless:apply; git add a2a-t-observability; git commit -m "feat(observability): add attribute constants and metadata extractors"`

---

### Task 5: ObservabilityRuntime

**Files:**
- Create: `.../observability/ObservabilityRuntime.java`
- Test: `.../observability/ObservabilityRuntimeTest.java`

**Interfaces:**
- Consumes: Task 3 常量键名。
- Produces: `isEnabled()` / `isTraceEnabled()` / `isMetricEnabled()` / `isLogEnabled()`（每次调用动态读 `System.getProperty` → `System.getenv`，无 import 期冻结——比 Python 可测性更好，行为等价）；`tracer()` / `meter()`（`GlobalOpenTelemetry.getTracer/getMeter("a2at-observability", "1.0.0")`，未装 SDK 时 OTel API 天然 no-op）；`sameTraceOrphanContext(@Nullable SpanContext) -> @Nullable Context`（同 trace 随机 span_id、无父、0 值守卫）。Task 6-10 与 Plan B 全部消费。

- [ ] **Step 1: 失败测试**

```java
package net.openan.a2at.sdk.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.api.trace.Span;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ObservabilityRuntimeTest {

    @AfterEach
    void clearSwitches() {
        System.clearProperty("OTEL_INSTRUMENTATION_A2AT_SDK_ENABLED");
        System.clearProperty("A2AT_TRACE_ENABLED");
    }

    @Test
    void masterSwitchDisablesAllSignals() {
        System.setProperty("OTEL_INSTRUMENTATION_A2AT_SDK_ENABLED", "false");
        assertFalse(ObservabilityRuntime.isEnabled());
        assertFalse(ObservabilityRuntime.isTraceEnabled());
        assertFalse(ObservabilityRuntime.isMetricEnabled());
        assertFalse(ObservabilityRuntime.isLogEnabled());
    }

    @Test
    void traceToggleIsIndependentOfMasterOn() {
        System.setProperty("A2AT_TRACE_ENABLED", "false");
        assertTrue(ObservabilityRuntime.isEnabled());
        assertFalse(ObservabilityRuntime.isTraceEnabled());
        assertTrue(ObservabilityRuntime.isMetricEnabled());
    }

    @Test
    void sameTraceOrphanContextKeepsTraceIdAndDropsParent() {
        SpanContext parent = SpanContext.create(
                "0123456789abcdef0123456789abcdef", "0123456789abcdef",
                TraceFlags.getSampled(), TraceState.getDefault());
        Context orphan = ObservabilityRuntime.sameTraceOrphanContext(parent);
        assertNotNull(orphan);
        SpanContext orphanSpan = Span.fromContext(orphan).getSpanContext();
        assertEquals(parent.getTraceId(), orphanSpan.getTraceId());
        assertNotEquals(parent.getSpanId(), orphanSpan.getSpanId());
        assertTrue(orphanSpan.isValid());
    }

    @Test
    void invalidParentYieldsNull() {
        assertNull(ObservabilityRuntime.sameTraceOrphanContext(SpanContext.getInvalid()));
        assertNull(ObservabilityRuntime.sameTraceOrphanContext(null));
    }

    @Test
    void tracerAndMeterScopeNames() {
        try (Scope scope = Span.fromContext(Context.root()).makeCurrent()) {
            assertNotNull(ObservabilityRuntime.tracer());
            assertNotNull(ObservabilityRuntime.meter());
        }
    }
}
```

- [ ] **Step 2: 运行确认失败** — `mvn -q -pl a2a-t-observability test "-Dtest=ObservabilityRuntimeTest"`。

- [ ] **Step 3: 实现**

```java
package net.openan.a2at.sdk.observability;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import java.util.concurrent.ThreadLocalRandom;
import org.jspecify.annotations.Nullable;

/** Master/signal switches + OTel accessors. Switches read dynamically (property > env > default). */
public final class ObservabilityRuntime {

    public static final String ENV_MASTER = "OTEL_INSTRUMENTATION_A2AT_SDK_ENABLED";
    public static final String ENV_TRACE = "A2AT_TRACE_ENABLED";
    public static final String ENV_METRIC = "A2AT_METRIC_ENABLED";
    public static final String ENV_LOG = "A2AT_LOG_ENABLED";

    public static final String SCOPE_NAME = "a2at-observability";
    public static final String SCOPE_VERSION = "1.0.0";

    private ObservabilityRuntime() {}

    private static boolean switchOn(String key) {
        String raw = System.getProperty(key, System.getenv(key));
        if (raw == null) {
            return true;
        }
        return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "1", "true", "yes", "on" -> true;
            default -> false;
        };
    }

    public static boolean isEnabled() {
        return switchOn(ENV_MASTER);
    }

    public static boolean isTraceEnabled() {
        return isEnabled() && switchOn(ENV_TRACE);
    }

    public static boolean isMetricEnabled() {
        return isEnabled() && switchOn(ENV_METRIC);
    }

    public static boolean isLogEnabled() {
        return isEnabled() && switchOn(ENV_LOG);
    }

    public static Tracer tracer() {
        return GlobalOpenTelemetry.getTracer(SCOPE_NAME, SCOPE_VERSION);
    }

    public static Meter meter() {
        return GlobalOpenTelemetry.getMeter(SCOPE_NAME, SCOPE_VERSION);
    }

    /** Same-trace orphan context: new span context shares trace_id, random span_id, no parent. */
    public static @Nullable Context sameTraceOrphanContext(@Nullable SpanContext parent) {
        if (parent == null || !parent.isValid()) {
            return null;
        }
        long random = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
        SpanContext orphan = SpanContext.create(
                parent.getTraceId(), String.format("%016x", random),
                TraceFlags.fromByte(parent.getTraceFlags().asByte()), parent.getTraceState());
        return Context.root().with(io.opentelemetry.api.trace.Span.wrap(orphan));
    }
}
```

- [ ] **Step 4: 运行确认通过** — `mvn -q -pl a2a-t-observability test "-Dtest=ObservabilityRuntimeTest"`。

- [ ] **Step 5: 提交** — `mvn -q spotless:apply; git add a2a-t-observability; git commit -m "feat(observability): add ObservabilityRuntime switches and orphan context"`

---

### Task 6: A2ATSpan + A2ATCurrentSpan + CurrentSpans

**Files:**
- Create: `.../observability/A2ATSpan.java`、`.../observability/A2ATCurrentSpan.java`、`.../observability/CurrentSpans.java`
- Test: `.../observability/A2ATSpanTest.java`

**Interfaces:**
- Consumes: Task 5。
- Produces: `A2ATSpan(String name)` / `A2ATSpan(String, SpanKind)`，链式 `setAttribute(String, Object)` / `addLink(A2ATSpan)`，`open() -> A2ATSpan`（启动并 makeCurrent；trace 关时吸收），`recordError(Throwable)`，`spanContext()`，`close()`（recordError→ERROR+record_exception，否则 OK；try-with-resources）；`CurrentSpans.current() -> @Nullable A2ATCurrentSpan`；`A2ATCurrentSpan.traceId()/spanId()/isValid()/setAttribute(String,Object)`。手动 API 面合同（spec §9）。

- [ ] **Step 1: 失败测试**

```java
package net.openan.a2at.sdk.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class A2ATSpanTest {

    private InMemorySpanExporter exporter;
    private SdkTracerProvider provider;

    @BeforeEach
    void setUp() {
        exporter = InMemorySpanExporter.create();
        provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        GlobalOpenTelemetry.set(OpenTelemetrySdk.builder().setTracerProvider(provider).build());
    }

    @AfterEach
    void tearDown() {
        provider.close();
        GlobalOpenTelemetry.reset();
    }

    @Test
    void tryWithResourcesEndsWithOk() {
        try (A2ATSpan span = new A2ATSpan("manual.op", SpanKind.INTERNAL).setAttribute("k", "v").open()) {
            span.setAttribute("k2", 2);
            assertNotNull(CurrentSpans.current());
            assertNotNull(CurrentSpans.current().traceId());
        }
        List<SpanData> ended = exporter.getFinishedSpanItems();
        assertEquals(1, ended.size());
        assertEquals("manual.op", ended.get(0).getName());
        assertEquals(io.opentelemetry.api.trace.StatusCode.OK, ended.get(0).getStatus().getStatusCode());
        assertEquals("v", ended.get(0).getAttributes().get(
                io.opentelemetry.api.common.AttributeKey.stringKey("k")));
    }

    @Test
    void recordErrorEndsWithErrorAndException() {
        RuntimeException failure = new IllegalStateException("boom");
        try (A2ATSpan span = new A2ATSpan("manual.fail").open()) {
            span.recordError(failure);
        }
        SpanData data = exporter.getFinishedSpanItems().get(0);
        assertEquals(io.opentelemetry.api.trace.StatusCode.ERROR, data.getStatus().getStatusCode());
        assertFalse(data.getEvents().isEmpty());
        assertTrue(data.getStatus().getDescription().contains("boom"));
    }

    @Test
    void addLinkBeforeOpenCreatesLink() {
        try (A2ATSpan entry = new A2ATSpan("entry").open()) {
            try (A2ATSpan event = new A2ATSpan("event").addLink(entry).open()) {
                // linked at creation
            }
        }
        List<SpanData> ended = exporter.getFinishedSpanItems();
        SpanData event = ended.stream().filter(d -> d.getName().equals("event")).findFirst().orElseThrow();
        assertEquals(1, event.getLinks().size());
        assertEquals(
                ended.stream().filter(d -> d.getName().equals("entry")).findFirst().orElseThrow()
                        .getSpanContext(),
                event.getLinks().get(0).getSpanContext());
    }

    @Test
    void singleUseAndClosedAbsorb() {
        A2ATSpan span = new A2ATSpan("once").open();
        assertThrows(IllegalStateException.class, span::open);
        span.close();
        span.close();
    }

    @Test
    void currentSpanNullOutsideAndValidInside() {
        assertNull(CurrentSpans.current());
        try (A2ATSpan span = new A2ATSpan("s").open()) {
            A2ATCurrentSpan current = CurrentSpans.current();
            assertNotNull(current);
            assertTrue(current.isValid());
            assertEquals(32, current.traceId().length());
            assertEquals(16, current.spanId().length());
            current.setAttribute("cur", "x");
        }
    }
}
```

- [ ] **Step 2: 运行确认失败** — `mvn -q -pl a2a-t-observability test "-Dtest=A2ATSpanTest"`。

- [ ] **Step 3: 实现**

`A2ATSpan.java`：

```java
package net.openan.a2at.sdk.observability;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Manual A2A-T span; try-with-resources + open(). Absorbs everything when trace is disabled. */
public final class A2ATSpan implements AutoCloseable {

    private final String name;
    private final SpanKind kind;
    private final Map<String, Object> attributes = new LinkedHashMap<>();
    private final List<SpanContext> pendingLinks = new ArrayList<>();
    private Span span;
    private Scope scope;
    private boolean opened;
    private boolean closed;
    private boolean errorRecorded;

    public A2ATSpan(String name) {
        this(name, SpanKind.INTERNAL);
    }

    public A2ATSpan(String name, SpanKind kind) {
        this.name = name;
        this.kind = kind;
    }

    public A2ATSpan setAttribute(String key, Object value) {
        if (span != null) {
            applyAttribute(span, key, value);
        } else {
            attributes.put(key, value);
        }
        return this;
    }

    public A2ATSpan addLink(A2ATSpan other) {
        SpanContext context = other == null ? null : other.spanContext();
        if (context == null || !context.isValid()) {
            return this;
        }
        if (span != null) {
            span.addLink(context);
        } else {
            pendingLinks.add(context);
        }
        return this;
    }

    /** Starts the span and makes it current; idempotent-guarded, absorbs when trace is off. */
    public A2ATSpan open() {
        if (opened) {
            throw new IllegalStateException("A2ATSpan is single-use; create a new instance for each span");
        }
        opened = true;
        if (!ObservabilityRuntime.isTraceEnabled()) {
            return this;
        }
        SpanBuilder builder = ObservabilityRuntime.tracer().spanBuilder(name).setSpanKind(kind);
        for (SpanContext link : pendingLinks) {
            builder.addLink(link);
        }
        span = builder.startSpan();
        attributes.forEach(this::applyOnSpan);
        scope = span.makeCurrent();
        return this;
    }

    private void applyOnSpan(String key, Object value) {
        if (span != null) {
            applyAttribute(span, key, value);
        }
    }

    private static void applyAttribute(Span target, String key, Object value) {
        switch (value) {
            case String s -> target.setAttribute(key, s);
            case Integer i -> target.setAttribute(key, i);
            case Long l -> target.setAttribute(key, l);
            case Boolean b -> target.setAttribute(key, b);
            case Double d -> target.setAttribute(key, d);
            case null, default -> target.setAttribute(key, String.valueOf(value));
        }
    }

    public void recordError(Throwable error) {
        if (span == null || error == null) {
            return;
        }
        errorRecorded = true;
        span.recordException(error);
        span.setStatus(StatusCode.ERROR, String.valueOf(error.getMessage()));
    }

    public SpanContext spanContext() {
        return span != null ? span.getSpanContext() : SpanContext.getInvalid();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try (Scope restore = scope) {
            if (span != null && span.isRecording()) {
                if (!errorRecorded) {
                    span.setStatus(StatusCode.OK);
                }
                span.end();
            }
        }
    }
}
```

注意：`errorRecorded` 守卫保证 `recordError` 后 `close` 不再覆盖为 OK（Python `__exit__(exc)` 语义的 Java 等价）。

`A2ATCurrentSpan.java` + `CurrentSpans.java`：

```java
package net.openan.a2at.sdk.observability;

import io.opentelemetry.api.trace.Span;

/** Read/append view of the active span. */
public final class A2ATCurrentSpan {
    private final Span span;

    A2ATCurrentSpan(Span span) {
        this.span = span;
    }

    public String traceId() { return span.getSpanContext().getTraceId(); }
    public String spanId() { return span.getSpanContext().getSpanId(); }
    public boolean isValid() { return span.getSpanContext().isValid(); }
    public void setAttribute(String key, Object value) { span.setAttribute(key, String.valueOf(value)); }
}
```

```java
package net.openan.a2at.sdk.observability;

import io.opentelemetry.api.trace.Span;
import org.jspecify.annotations.Nullable;

/** Accessor for the active A2A-T span. */
public final class CurrentSpans {

    private CurrentSpans() {}

    public static @Nullable A2ATCurrentSpan current() {
        Span span = Span.current();
        if (!span.getSpanContext().isValid() || !span.isRecording()) {
            return null;
        }
        return new A2ATCurrentSpan(span);
    }
}
```

- [ ] **Step 4: 运行确认通过** — `mvn -q -pl a2a-t-observability test "-Dtest=A2ATSpanTest"`。

- [ ] **Step 5: 提交** — `mvn -q spotless:apply; git add a2a-t-observability; git commit -m "feat(observability): add manual span API"`

---

### Task 7: TracePropagation + PayloadExtractor + PayloadSanitizer

**Files:**
- Create: `.../observability/TracePropagation.java`、`.../observability/PayloadExtractor.java`、`.../observability/PayloadSanitizer.java`
- Test: `.../observability/PropagationAndPayloadTest.java`

**Interfaces:**
- Produces: `TracePropagation.TRACEPARENT_HEADER="traceparent"`；`inject(Map<String,String>)`（当前上下文）/ `inject(Map<String,String>, Context)`；`extract(Map<String,String>) -> @Nullable Context`（key 小写容错；无效返回 null）。`PayloadExtractor.toJson(@Nullable Object) -> @Nullable String`（Jackson；失败 DEBUG+null）。`PayloadSanitizer.prepare(String, config)`（先脱敏后截断，`[truncated]` 后缀）。

- [ ] **Step 1: 失败测试**

```java
package net.openan.a2at.sdk.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PropagationAndPayloadTest {

    @AfterEach
    void reset() {
        io.opentelemetry.api.GlobalOpenTelemetry.reset();
        System.clearProperty("OTEL_INSTRUMENTATION_A2AT_SDK_ENABLED");
    }

    @Test
    void injectExtractRoundtripSurvivesLowercasing() {
        io.opentelemetry.api.GlobalOpenTelemetry.set(io.opentelemetry.sdk.OpenTelemetrySdk.builder()
                .setTracerProvider(io.opentelemetry.sdk.trace.SdkTracerProvider.builder().build()).build());
        Map<String, String> headers = new HashMap<>();
        String traceId;
        try (var span = new A2ATSpan("root", io.opentelemetry.api.trace.SpanKind.CLIENT).open()) {
            traceId = Span.current().getSpanContext().getTraceId();
            TracePropagation.inject(headers);
        }
        Map<String, String> mixed = Map.of("TRACEPARENT", headers.get("traceparent"));
        Context extracted = TracePropagation.extract(mixed);
        org.junit.jupiter.api.Assertions.assertNotNull(extracted);
        assertEquals(traceId, Span.fromContext(extracted).getSpanContext().getTraceId());
        assertTrue(headers.get("traceparent").startsWith("00-" + traceId));
    }

    @Test
    void extractReturnsNullWhenInvalidOrDisabled() {
        assertNull(TracePropagation.extract(Map.of("traceparent", "00-abc-def-01")));
        System.setProperty("OTEL_INSTRUMENTATION_A2AT_SDK_ENABLED", "false");
        assertNull(TracePropagation.extract(Map.of("traceparent", "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01")));
    }

    @Test
    void payloadRedactsBeforeTruncation() {
        A2ATObservabilityConfig config = A2ATObservabilityConfig.fromEnv().toBuilder()
                .payloadLogMaxLength(10)
                .payloadRedactor(value -> value.replace("secret", "***"))
                .build();
        String prepared = PayloadSanitizer.prepare("secret-abcdefghijklmnopqrstuvwxyz", config);
        assertFalse(prepared.contains("secret"));
        assertTrue(prepared.endsWith("[truncated]"));
        assertTrue(prepared.length() <= 10 + "[truncated]".length());
    }

    @Test
    void payloadExtractorSerializesRecordsAndNullFails() {
        record Point(int x, int y) {}
        assertTrue(PayloadExtractor.toJson(new Point(1, 2)).contains("\"x\":1"));
        assertNull(PayloadExtractor.toJson(null));
        assertNull(PayloadExtractor.toJson(new Object() {
            @Override
            public String toString() {
                throw new IllegalStateException("unserializable");
            }
        }));
    }
}
```

注意：Jackson 序列化匿名类默认会抛 `InvalidDefinitionException` → `toJson` 捕获返回 null，正是断言路径。

- [ ] **Step 2: 运行确认失败** — `mvn -q -pl a2a-t-observability test "-Dtest=PropagationAndPayloadTest"`。

- [ ] **Step 3: 实现**

```java
package net.openan.a2at.sdk.observability;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** W3C TraceContext propagation over plain header maps. */
public final class TracePropagation {

    public static final String TRACEPARENT_HEADER = "traceparent";

    private static final TextMapSetter<Map<String, String>> SETTER = Map::put;
    private static final TextMapGetter<Map<String, String>> GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
            return carrier.keySet();
        }

        @Override
        public @Nullable String get(@Nullable Map<String, String> carrier, String key) {
            return carrier == null ? null : carrier.get(key);
        }
    };

    private TracePropagation() {}

    public static void inject(Map<String, String> headers) {
        if (headers == null || !ObservabilityRuntime.isEnabled()) {
            return;
        }
        GlobalOpenTelemetry.getPropagators().getTextMapPropagator()
                .inject(Context.current(), headers, SETTER);
    }

    public static void inject(Map<String, String> headers, Context context) {
        if (headers == null || context == null || !ObservabilityRuntime.isEnabled()) {
            return;
        }
        GlobalOpenTelemetry.getPropagators().getTextMapPropagator().inject(context, headers, SETTER);
    }

    public static @Nullable Context extract(@Nullable Map<String, String> headers) {
        if (headers == null || !ObservabilityRuntime.isEnabled()) {
            return null;
        }
        Map<String, String> lowered = new HashMap<>();
        headers.forEach((key, value) -> lowered.put(key.toLowerCase(Locale.ROOT), value));
        Context extracted = GlobalOpenTelemetry.getPropagators().getTextMapPropagator()
                .extract(Context.root(), lowered, GETTER);
        SpanContextProbe probe = new SpanContextProbe(extracted);
        return probe.isValid() ? extracted : null;
    }

    private static final class SpanContextProbe {
        private final boolean valid;

        SpanContextProbe(Context context) {
            this.valid = io.opentelemetry.api.trace.Span.fromContext(context).getSpanContext().isValid();
        }

        boolean isValid() {
            return valid;
        }
    }
}
```

（实现可内联为单行 `Span.fromContext(extracted).getSpanContext().isValid() ? extracted : null`，无需内部类——以简洁版为准。）

```java
package net.openan.a2at.sdk.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Serializes request/event objects to JSON for payload channels. */
public final class PayloadExtractor {

    private static final Logger LOG = LoggerFactory.getLogger(PayloadExtractor.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PayloadExtractor() {}

    public static @Nullable String toJson(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            LOG.debug("a2at: payload extraction failed", e);
            return null;
        }
    }
}
```

```java
package net.openan.a2at.sdk.observability;

import java.util.function.UnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Shared payload preparation: redact first, then truncate (security order). */
public final class PayloadSanitizer {

    private static final Logger LOG = LoggerFactory.getLogger(PayloadSanitizer.class);

    private PayloadSanitizer() {}

    public static String prepare(String payload, A2ATObservabilityConfig config) {
        String value = payload;
        UnaryOperator<String> redactor = config.payloadRedactor();
        if (redactor != null) {
            try {
                value = redactor.apply(value);
            } catch (RuntimeException e) {
                LOG.warn("a2at: payload redactor failed; using unredacted value", e);
            }
        }
        int max = config.payloadLogMaxLength();
        if (value.length() > max) {
            return value.substring(0, max) + "[truncated]";
        }
        return value;
    }
}
```

- [ ] **Step 4: 运行确认通过** — `mvn -q -pl a2a-t-observability test "-Dtest=PropagationAndPayloadTest"`。

- [ ] **Step 5: 提交** — `mvn -q spotless:apply; git add a2a-t-observability; git commit -m "feat(observability): add propagation and payload channels"`

---

### Task 8: LlmUsageStash + ObservabilityLLMClient + MetricHistograms + NegotiationMetrics

**Files:**
- Create: `.../observability/LlmUsageStash.java`、`.../observability/LlmUsageStashHolder.java`、`.../observability/MetricHistograms.java`、`.../observability/NegotiationMetrics.java`、`.../observability/ObservabilityLLMClient.java`
- Test: `.../observability/LlmObservabilityTest.java`、`.../observability/NegotiationMetricsTest.java`

**Interfaces:**
- Consumes: `LLMClient`/`LLMResponse`（a2a-t-llm；`usage` 键 `prompt_tokens`/`completion_tokens`）、Task 4 常量、Task 5 开关。
- Produces:
  - `record LlmUsageStash(@Nullable Integer inputTokens, @Nullable Integer outputTokens, @Nullable String requestModel)`
  - `LlmUsageStashHolder.set(stash)` / `getAndClear()` / `clear()`（ThreadLocal）
  - `ObservabilityLLMClient implements LLMClient`（包装 delegate；调用后 stash + 记 `gen_ai.client.token.usage` 直方图，`gen_ai.token.type`=input/output；metric 关时仅 stash）
  - `MetricHistograms.histogram(name, unit, description) -> LongHistogram`（ConcurrentHashMap 惰性缓存；package-private）
  - `NegotiationMetrics.reportRounds(@Nullable String negotiationId, @Nullable String outcome, int rounds)`（outcome ∈ {accept,reject,abort}；`extension.name`+`negotiation.id`+`outcome` 属性；metric 关 → 跳过；永不抛）

- [ ] **Step 1: 失败测试**

```java
package net.openan.a2at.sdk.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicExportingMetricReader;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LlmObservabilityTest {

    private InMemoryMetricReader reader;

    @BeforeEach
    void setUp() {
        reader = InMemoryMetricReader.create();
        SdkMeterProvider provider = SdkMeterProvider.builder()
                .registerMetricReader(reader)
                .build();
        GlobalOpenTelemetry.set(OpenTelemetrySdk.builder().setMeterProvider(provider).build());
    }

    @AfterEach
    void tearDown() {
        GlobalOpenTelemetry.reset();
    }

    @Test
    void structuredStashesUsageAndRecordsTokenHistogram() {
        LLMClient scripted = (messages, schema, temperature, maxTokens) -> new LLMResponse(
                "ok", "gpt-x", Map.of("prompt_tokens", 10, "completion_tokens", 5), Map.of());
        ObservabilityLLMClient client = new ObservabilityLLMClient(scripted);
        client.structured(List.of(Map.of("role", "user", "content", "hi")), Map.of(), null, null);

        LlmUsageStash stash = LlmUsageStashHolder.getAndClear();
        assertNotNull(stash);
        assertEquals(10, stash.inputTokens());
        assertEquals(5, stash.outputTokens());
        assertEquals("gpt-x", stash.requestModel());
        assertNull(LlmUsageStashHolder.getAndClear());

        var metrics = reader.collectAllMetrics().join();
        assertTrue(metrics.stream().anyMatch(m -> "gen_ai.client.token.usage".equals(m.getName())
                && !m.getHistogramData().getDataPoints().isEmpty()),
                "token usage histogram must be recorded");
    }

    @Test
    void metricDisabledStillStashes() {
        System.setProperty("A2AT_METRIC_ENABLED", "false");
        try {
            LLMClient scripted = (messages, schema, temperature, maxTokens) ->
                    new LLMResponse("ok", "m", Map.of("prompt_tokens", 1, "completion_tokens", 2), Map.of());
            new ObservabilityLLMClient(scripted).structured(List.of(), Map.of(), null, null);
            assertNotNull(LlmUsageStashHolder.getAndClear());
            assertEquals(0, reader.collectAllMetrics().join().size());
        } finally {
            System.clearProperty("A2AT_METRIC_ENABLED");
        }
    }

    @Test
    void stashFailurePathKeepsSemantics() {
        // delegate 异常原样抛出，不写 stash（观测永不破坏业务）
        LLMClient throwing = (messages, schema, temperature, maxTokens) -> {
            throw new IllegalStateException("llm down");
        };
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> new ObservabilityLLMClient(throwing).structured(List.of(), Map.of(), null, null));
        assertNull(LlmUsageStashHolder.getAndClear());
    }
}
```

注意：`LLMResponse.usage` 类型为 `Map<String,Integer>`（键 `prompt_tokens`/`completion_tokens`，见 `a2a-t-llm` 的 `OpenAIClient`），类型约束由编译器保证，无需非法值用例。

```java
package net.openan.a2at.sdk.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NegotiationMetricsTest {

    private InMemoryMetricReader reader;

    @BeforeEach
    void setUp() {
        reader = InMemoryMetricReader.create();
        GlobalOpenTelemetry.set(OpenTelemetrySdk.builder()
                .setMeterProvider(SdkMeterProvider.builder().registerMetricReader(reader).build()).build());
    }

    @AfterEach
    void tearDown() {
        GlobalOpenTelemetry.reset();
    }

    @Test
    void terminalOutcomeRecordsRoundsWithAttributes() {
        NegotiationMetrics.reportRounds("N001", "accept", 3);
        var metrics = reader.collectAllMetrics().join();
        var histogram = metrics.stream()
                .filter(m -> "a2at.negotiation.total_rounds".equals(m.getName()))
                .findFirst().orElseThrow();
        assertEquals("a2at.negotiation.total_rounds", histogram.getName());
        assertTrue(!histogram.getHistogramData().getDataPoints().isEmpty());
    }

    @Test
    void nonTerminalOutcomeAndNegativeRoundsAreSkipped() {
        NegotiationMetrics.reportRounds("N1", "propose", 2);
        NegotiationMetrics.reportRounds("N1", "accept", -1);
        assertEquals(0, reader.collectAllMetrics().join().size());
    }
}
```

- [ ] **Step 2: 运行确认失败** — `mvn -q -pl a2a-t-observability test "-Dtest=LlmObservabilityTest,NegotiationMetricsTest"`。

> 依赖补充：`InMemoryMetricReader` 来自 `io.opentelemetry:opentelemetry-sdk-testing:1.64.0` —— 在 `a2a-t-observability/pom.xml` 增加测试依赖（版本经 bom 管理）：
> ```xml
> <dependency>
>     <groupId>io.opentelemetry</groupId>
>     <artifactId>opentelemetry-sdk-testing</artifactId>
>     <scope>test</scope>
> </dependency>
> ```

- [ ] **Step 3: 实现**

`LlmUsageStash.java` + `LlmUsageStashHolder.java`：

```java
package net.openan.a2at.sdk.observability;

import org.jspecify.annotations.Nullable;

/** Token usage captured from one LLM call; drained onto the next entry span. */
public record LlmUsageStash(
        @Nullable Integer inputTokens,
        @Nullable Integer outputTokens,
        @Nullable String requestModel) {}
```

```java
package net.openan.a2at.sdk.observability;

import org.jspecify.annotations.Nullable;

/** ThreadLocal bridge: LLM layer -> entry span (SDK is fully synchronous). */
public final class LlmUsageStashHolder {

    private static final ThreadLocal<LlmUsageStash> STASH = new ThreadLocal<>();

    private LlmUsageStashHolder() {}

    public static void set(LlmUsageStash usage) {
        STASH.set(usage);
    }

    public static @Nullable LlmUsageStash getAndClear() {
        LlmUsageStash stash = STASH.get();
        STASH.remove();
        return stash;
    }

    public static void clear() {
        STASH.remove();
    }
}
```

`MetricHistograms.java`（package-private）：

```java
package net.openan.a2at.sdk.observability;

import io.opentelemetry.api.metrics.LongHistogram;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Lazy histogram cache keyed by metric name (shared meter scope a2at-observability/1.0.0). */
final class MetricHistograms {

    private static final ConcurrentMap<String, LongHistogram> INSTRUMENTS = new ConcurrentHashMap<>();

    private MetricHistograms() {}

    static LongHistogram histogram(String name, String unit, String description) {
        return INSTRUMENTS.computeIfAbsent(name, metricName -> ObservabilityRuntime.meter()
                .histogramBuilder(metricName)
                .setUnit(unit)
                .setDescription(description)
                .ofLongs()
                .build());
    }
}
```

`NegotiationMetrics.java`：

```java
package net.openan.a2at.sdk.observability;

import io.opentelemetry.api.common.Attributes;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** a2at.negotiation.total_rounds histogram; terminal outcomes only; never raises. */
public final class NegotiationMetrics {

    public static final String METRIC_NAME = "a2at.negotiation.total_rounds";
    private static final Logger LOG = LoggerFactory.getLogger(NegotiationMetrics.class);
    private static final Set<String> OUTCOMES = Set.of("accept", "reject", "abort");

    private NegotiationMetrics() {}

    public static void reportRounds(@Nullable String negotiationId, @Nullable String outcome, int rounds) {
        try {
            if (!ObservabilityRuntime.isMetricEnabled() || rounds < 0) {
                return;
            }
            if (outcome == null || !OUTCOMES.contains(outcome.toLowerCase(java.util.Locale.ROOT))) {
                return;
            }
            var builder = Attributes.builder();
            builder.put(A2ATAttributes.ATTR_EXTENSION_NAME, A2ATAttributes.NEGOTIATION_EXTENSION);
            if (negotiationId != null && !negotiationId.isEmpty()) {
                builder.put(A2ATAttributes.ATTR_NEGOTIATION_ID, negotiationId);
            }
            builder.put("outcome", outcome.toLowerCase(java.util.Locale.ROOT));
            MetricHistograms.histogram(METRIC_NAME, "{round}", "Negotiation terminal message round count")
                    .record(rounds, builder.build());
        } catch (RuntimeException e) {
            LOG.warn("a2at: failed to report negotiation rounds", e);
        }
    }
}
```

`ObservabilityLLMClient.java`：

```java
package net.openan.a2at.sdk.observability;

import io.opentelemetry.api.common.Attributes;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMResponse;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** LLMClient decorator: usage stash + gen_ai.client.token.usage histogram; never breaks the call. */
public final class ObservabilityLLMClient implements LLMClient {

    public static final String METRIC_TOKEN_USAGE = "gen_ai.client.token.usage";
    private static final Logger LOG = LoggerFactory.getLogger(ObservabilityLLMClient.class);
    private final LLMClient delegate;

    public ObservabilityLLMClient(LLMClient delegate) {
        this.delegate = java.util.Objects.requireNonNull(delegate);
    }

    @Override
    public LLMResponse structured(
            List<Map<String, String>> messages, Map<String, Object> jsonSchema,
            @Nullable Double temperature, @Nullable Integer maxTokens) {
        LLMResponse response = delegate.structured(messages, jsonSchema, temperature, maxTokens);
        report(response);
        return response;
    }

    private void report(LLMResponse response) {
        try {
            Map<String, Integer> usage = response.usage() == null ? Map.of() : response.usage();
            Integer input = usage.get("prompt_tokens");
            Integer output = usage.get("completion_tokens");
            LlmUsageStashHolder.set(new LlmUsageStash(input, output, response.model()));
            if (ObservabilityRuntime.isMetricEnabled()) {
                recordTokenUsage(input, output);
            }
        } catch (RuntimeException e) {
            LOG.warn("a2at: failed to report llm token usage", e);
        }
    }

    private void recordTokenUsage(@Nullable Integer input, @Nullable Integer output) {
        if (input != null) {
            MetricHistograms.histogram(METRIC_TOKEN_USAGE, "{token}", "GenAI client token usage")
                    .record(input, Attributes.of(A2ATAttributes.ATTR_GEN_AI_TOKEN_TYPE, "input"));
        }
        if (output != null) {
            MetricHistograms.histogram(METRIC_TOKEN_USAGE, "{token}", "GenAI client token usage")
                    .record(output, Attributes.of(A2ATAttributes.ATTR_GEN_AI_TOKEN_TYPE, "output"));
        }
    }
}
```

（`LongHistogram unused` 残行已清理——以上码即最终版。）

- [ ] **Step 4: 运行确认通过** — `mvn -q -pl a2a-t-observability test "-Dtest=LlmObservabilityTest,NegotiationMetricsTest"`。

- [ ] **Step 5: 提交** — `mvn -q spotless:apply; git add a2a-t-observability; git commit -m "feat(observability): add LLM usage stash, token metric and negotiation rounds metric"`

---

### Task 9: ObservabilityLogs + ConsoleExporters + ObservabilitySetup

**Files:**
- Create: `.../observability/ObservabilityLogs.java`、`.../observability/ConsoleExporters.java`、`.../observability/ObservabilitySetup.java`
- Test: `.../observability/ObservabilityLogsTest.java`、`.../observability/ObservabilitySetupTest.java`

**Interfaces:**
- Consumes: Task 3 config、Task 5 开关、Task 7 sanitizer。
- Produces:
  - `ObservabilityLogs.logEvent(String event, org.slf4j.event.Level level, Map<String,Object> fields, @Nullable String payload, A2ATObservabilityConfig config)`：SLF4J JSON 行（logger `net.openan.a2at.sdk.observability`，字段 event + fields + trace_id/span_id + payload?）+ OTel `Logger` API 同步发射（`GlobalLoggerProvider.get().get("a2at-observability")`）；log_enabled 双门控；永不抛。
  - `ConsoleExporters`（package-private）：`spanExporter()` / `metricExporter()` / `logRecordExporter()` —— 内置 SLF4J JSON 导出器（`opentelemetry-exporter-logging` 不在 .m2，等效自研）。
  - `ObservabilitySetup.ensureConfigured()` / `ensureConfigured(A2ATObservabilityConfig)` / `isConfigured()`：懒装配三信号（SdkTracerProvider/SdkMeterProvider/SdkLoggerProvider + Batch* + PeriodicExportingMetricReader）；endpoint → OTLP gRPC/HTTP（HTTP 拼 `/v1/traces|metrics|logs`）；无 endpoint → Console（`A2AT_CONSOLE_EXPORTER=false` 且无 endpoint → 信号保持 no-op）；幂等（once 标志 + `GlobalOpenTelemetry.set` 抛 `IllegalStateException` 捕获跳过）；sdk 类缺失 `Class.forName` 探测跳过。

- [ ] **Step 1: 失败测试**

```java
package net.openan.a2at.sdk.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.GlobalOpenTelemetry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

class ObservabilityLogsTest {

    private ListAppender<ILoggingEvent> appender;
    private Logger logger;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void attach() {
        logger = (Logger) LoggerFactory.getLogger("net.openan.a2at.sdk.observability");
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
        GlobalOpenTelemetry.reset();
    }

    @AfterEach
    void detach() {
        logger.detachAppender(appender);
        System.clearProperty("A2AT_LOG_ENABLED");
        System.clearProperty("OTEL_INSTRUMENTATION_A2AT_SDK_ENABLED");
    }

    @Test
    void jsonRecordCarriesEventFieldsAndTraceIds() throws Exception {
        try (A2ATSpan span = new A2ATSpan("entry").open()) {
            ObservabilityLogs.logEvent("task.status_changed", Level.INFO,
                    Map.of("task.id", "t1", "task.status", "working"), null, A2ATObservabilityConfig.fromEnv());
        }
        ILoggingEvent event = appender.list.stream()
                .filter(e -> e.getLevel() == Level.INFO).findFirst().orElseThrow();
        Map<?, ?> json = mapper.readValue(event.getFormattedMessage(), Map.class);
        assertEquals("task.status_changed", json.get("event"));
        assertEquals("t1", json.get("task.id"));
        assertEquals("working", json.get("task.status"));
        assertNotNull(json.get("trace_id"));
        assertNotNull(json.get("span_id"));
    }

    @Test
    void payloadChannelHonorsConfigAndRedaction() throws Exception {
        A2ATObservabilityConfig config = A2ATObservabilityConfig.fromEnv().toBuilder()
                .payloadLogEnabled(true)
                .payloadRedactor(v -> v.replace("key", "***"))
                .build();
        ObservabilityLogs.logEvent("task.request", Level.DEBUG, Map.of(), "payload: key=1", config);
        Map<?, ?> json = mapper.readValue(appender.list.get(0).getFormattedMessage(), Map.class);
        assertTrue(String.valueOf(json.get("payload")).contains("***"));
        assertFalse(String.valueOf(json.get("payload")).contains("key=1"));
    }

    @Test
    void logSwitchOffEmitsNothing() {
        System.setProperty("A2AT_LOG_ENABLED", "false");
        ObservabilityLogs.logEvent("task.request", Level.DEBUG, Map.of(), null, A2ATObservabilityConfig.fromEnv());
        System.clearProperty("A2AT_LOG_ENABLED");
        assertEquals(0, appender.list.size());
    }
}
```

```java
package net.openan.a2at.sdk.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ObservabilitySetupTest {

    @AfterEach
    void reset() {
        GlobalOpenTelemetry.reset();
        System.clearProperty("A2AT_OBSERVABILITY_ENABLED");
    }

    @Test
    void consoleFallbackConfiguresAllThreeSignals() {
        GlobalOpenTelemetry.reset();
        ObservabilitySetup.ensureConfigured(A2ATObservabilityConfig.fromEnv());
        assertTrue(ObservabilitySetup.isConfigured());
        assertInstanceOf(OpenTelemetrySdk.class, GlobalOpenTelemetry.get());
        GlobalOpenTelemetry.getTracer("t").spanBuilder("s").startSpan().end();
    }

    @Test
    void existingGlobalProviderIsRespected() {
        GlobalOpenTelemetry.set(OpenTelemetrySdk.builder().build());
        ObservabilitySetup.ensureConfigured(A2ATObservabilityConfig.fromEnv());
        assertFalse(ObservabilitySetup.isConfigured());
    }

    @Test
    void disabledSkipsEverything() {
        System.setProperty("OTEL_INSTRUMENTATION_A2AT_SDK_ENABLED", "false");
        ObservabilitySetup.ensureConfigured(A2ATObservabilityConfig.fromEnv());
        assertFalse(ObservabilitySetup.isConfigured());
        assertFalse(ObservabilityRuntime.isEnabled());
    }
}
```

- [ ] **Step 2: 运行确认失败** — `mvn -q -pl a2a-t-observability test "-Dtest=ObservabilityLogsTest,ObservabilitySetupTest"`。

- [ ] **Step 3: 实现**

`ObservabilityLogs.java`：

```java
package net.openan.a2at.sdk.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.SeverityNumber;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.event.Level;

/** Structured JSON events: SLF4J channel + OTel logs channel sharing identical semantics. */
public final class ObservabilityLogs {

    public static final String LOGGER_NAME = "net.openan.a2at.sdk.observability";
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(LOGGER_NAME);
    private static final Logger OTEL_LOGGER = io.opentelemetry.api.logs.GlobalLoggerProvider.get()
            .get(ObservabilityRuntime.SCOPE_NAME);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ObservabilityLogs() {}

    public static void logEvent(String event, Level level, Map<String, Object> fields,
            @Nullable String payload, A2ATObservabilityConfig config) {
        if (!ObservabilityRuntime.isLogEnabled() || !config.logEnabled()) {
            return;
        }
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("event", event);
            if (fields != null) {
                data.putAll(fields);
            }
            var spanContext = io.opentelemetry.api.trace.Span.current().getSpanContext();
            if (spanContext.isValid()) {
                data.put("trace_id", spanContext.getTraceId());
                data.put("span_id", spanContext.getSpanId());
            }
            if (payload != null && config.payloadLogEnabled()) {
                data.put("payload", PayloadSanitizer.prepare(payload, config));
            }
            String json = MAPPER.writeValueAsString(data);
            LOG.atLevel(level).log(json);
            emitOtel(level, data, json);
        } catch (Exception e) {
            LOG.debug("a2at: log_event failed", e);
        }
    }

    private static void emitOtel(Level level, Map<String, Object> data, String json) {
        try {
            var builder = OTEL_LOGGER.logRecordBuilder()
                    .setSeverity(severity(level))
                    .setSeverityText(level.name())
                    .setBody(json);
            data.forEach((key, value) -> {
                if (!"event".equals(key) && !"trace_id".equals(key) && !"span_id".equals(key)) {
                    builder.setAttribute(key, String.valueOf(value));
                }
            });
            builder.emit();
        } catch (RuntimeException e) {
            LOG.debug("a2at: otel log emit failed", e);
        }
    }

    private static SeverityNumber severity(Level level) {
        return switch (level) {
            case DEBUG -> SeverityNumber.DEBUG;
            case INFO -> SeverityNumber.INFO;
            case WARN -> SeverityNumber.WARN;
            case ERROR -> SeverityNumber.ERROR;
            default -> SeverityNumber.INFO;
        };
    }
}
```

`ConsoleExporters.java`（package-private，先 `javap -cp <otel-sdk jar> io.opentelemetry.sdk.metrics.export.MetricExporter io.opentelemetry.sdk.logs.export.LogRecordExporter io.opentelemetry.sdk.trace.export.SpanExporter` 核对 1.64 方法签名，若与下码不符按实际调整）：

```java
package net.openan.a2at.sdk.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** In-house dev-mode exporters (JSON lines on SLF4J); replaces opentelemetry-exporter-logging. */
final class ConsoleExporters {

    private static final Logger LOG = LoggerFactory.getLogger("net.openan.a2at.sdk.observability.console");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ConsoleExporters() {}

    static SpanExporter spanExporter() {
        return new SpanExporter() {
            @Override
            public CompletableResultCode export(Collection<SpanData> spans) {
                spans.forEach(span -> LOG.info(json("span", span.getName(),
                        java.util.Map.of(
                                "traceId", span.getTraceId(), "spanId", span.getSpanId(),
                                "kind", span.getKind().name(),
                                "status", span.getStatus().getStatusCode().name(),
                                "attributes", span.getAttributes().asMap()))));
                return CompletableResultCode.success();
            }

            @Override
            public CompletableResultCode flush() {
                return CompletableResultCode.success();
            }

            @Override
            public CompletableResultCode shutdown() {
                return CompletableResultCode.success();
            }
        };
    }

    static MetricExporter metricExporter() {
        return new MetricExporter() {
            @Override
            public CompletableResultCode export(Collection<MetricData> metrics) {
                metrics.forEach(metric -> LOG.info(json("metric", metric.getName(),
                        java.util.Map.of("unit", String.valueOf(metric.getUnit()),
                                "description", String.valueOf(metric.getDescription())))));
                return CompletableResultCode.success();
            }

            @Override
            public CompletableResultCode flush() {
                return CompletableResultCode.success();
            }

            @Override
            public CompletableResultCode shutdown() {
                return CompletableResultCode.success();
            }

            @Override
            public AggregationTemporality getAggregationTemporality(InstrumentType instrumentType) {
                return AggregationTemporality.CUMULATIVE;
            }
        };
    }

    static LogRecordExporter logRecordExporter() {
        return new LogRecordExporter() {
            @Override
            public CompletableResultCode export(Collection<LogRecordData> records) {
                records.forEach(record -> LOG.info(json("log", String.valueOf(record.getBodyValue()),
                        java.util.Map.of(
                                "severity", String.valueOf(record.getSeverityText()),
                                "attributes", record.getAttributes().asMap()))));
                return CompletableResultCode.success();
            }

            @Override
            public CompletableResultCode flush() {
                return CompletableResultCode.success();
            }

            @Override
            public CompletableResultCode shutdown() {
                return CompletableResultCode.success();
            }
        };
    }

    private static String json(String signal, String name, Object data) {
        try {
            return MAPPER.writeValueAsString(java.util.Map.of("signal", signal, "name", name, "data", data));
        } catch (Exception e) {
            return "{\"signal\":\"" + signal + "\",\"error\":\"serialize_failed\"}";
        }
    }
}
```

`ObservabilitySetup.java`：

```java
package net.openan.a2at.sdk.observability;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.metrics.PeriodicExportingMetricReader;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Lazy auto-config of all three signals; user-registered providers are never overridden. */
public final class ObservabilitySetup {

    private static final Logger LOG = LoggerFactory.getLogger(ObservabilitySetup.class);
    private static final AtomicBoolean CONFIGURED = new AtomicBoolean(false);

    private ObservabilitySetup() {}

    public static boolean isConfigured() {
        return CONFIGURED.get();
    }

    public static void ensureConfigured() {
        ensureConfigured(A2ATObservabilityConfig.fromEnv());
    }

    public static void ensureConfigured(A2ATObservabilityConfig config) {
        if (CONFIGURED.get() || !ObservabilityRuntime.isEnabled() || !config.enabled()) {
            return;
        }
        if (!sdkAvailable()) {
            return;
        }
        try {
            configure(config);
            CONFIGURED.set(true);
        } catch (IllegalStateException alreadySet) {
            LOG.debug("a2at: global OTel already registered; skipping auto-config");
        } catch (RuntimeException e) {
            LOG.warn("a2at: otel auto-config failed", e);
        }
    }

    private static boolean sdkAvailable() {
        try {
            Class.forName("io.opentelemetry.sdk.OpenTelemetrySdk");
            return true;
        } catch (ClassNotFoundException e) {
            LOG.warn("a2at: opentelemetry-sdk not on classpath; auto-config skipped");
            return false;
        }
    }

    private static void configure(A2ATObservabilityConfig config) {
        Resource resource = buildResource(config);
        Exporters exporters = buildExporters(config);
        SdkTracerProvider tracerProvider = null;
        SdkMeterProvider meterProvider = null;
        SdkLoggerProvider loggerProvider = null;
        if (exporters.span != null && config.traceEnabled() && ObservabilityRuntime.isTraceEnabled()) {
            tracerProvider = SdkTracerProvider.builder()
                    .setResource(resource)
                    .addSpanProcessor(BatchSpanProcessor.builder(exporters.span)
                            .setExporterTimeout(2, TimeUnit.SECONDS).build())
                    .build();
        }
        if (exporters.metric != null && config.metricEnabled() && ObservabilityRuntime.isMetricEnabled()) {
            meterProvider = SdkMeterProvider.builder()
                    .setResource(resource)
                    .registerMetricReader(PeriodicExportingMetricReader.builder(exporters.metric)
                            .setInterval(30, TimeUnit.SECONDS).build())
                    .build();
        }
        if (exporters.log != null && config.logEnabled() && ObservabilityRuntime.isLogEnabled()) {
            loggerProvider = SdkLoggerProvider.builder()
                    .setResource(resource)
                    .addLogRecordProcessor(BatchLogRecordProcessor.builder(exporters.log).build())
                    .build();
        }
        OpenTelemetrySdk.Builder builder = OpenTelemetrySdk.builder();
        if (tracerProvider != null) {
            builder.setTracerProvider(tracerProvider);
        }
        if (meterProvider != null) {
            builder.setMeterProvider(meterProvider);
        }
        if (loggerProvider != null) {
            builder.setLoggerProvider(loggerProvider);
        }
        GlobalOpenTelemetry.set(builder.build());
        if (exporters.console) {
            LOG.warn("a2at: no OTLP endpoint configured; using Console exporters (dev mode)");
        }
    }

    private static Resource buildResource(A2ATObservabilityConfig config) {
        Attributes.Builder attributes = Attributes.builder();
        attributes.put("service.name", config.serviceName() == null
                ? A2ATObservabilityConfig.DEFAULT_SERVICE_NAME : config.serviceName());
        String version = ObservabilitySetup.class.getPackage() == null
                ? null : ObservabilitySetup.class.getPackage().getImplementationVersion();
        attributes.put("service.version", version == null ? "dev" : version);
        if (config.resolveAgentRole() != null) {
            attributes.put("agent.role", config.resolveAgentRole());
        }
        return Resource.getDefault().merge(Resource.create(attributes.build()));
    }

    private static Exporters buildExporters(A2ATObservabilityConfig config) {
        Exporters exporters = new Exporters();
        if (config.endpoint() == null) {
            boolean consoleEnabled = switchOn(A2ATObservabilityConfig.ENV_CONSOLE_EXPORTER);
            if (!consoleEnabled) {
                LOG.info("a2at: console exporter disabled and no OTLP endpoint; signals stay no-op");
                return exporters;
            }
            exporters.span = ConsoleExporters.spanExporter();
            exporters.metric = ConsoleExporters.metricExporter();
            exporters.log = ConsoleExporters.logRecordExporter();
            exporters.console = true;
            return exporters;
        }
        exporters.console = false;
        if ("http/protobuf".equals(config.protocol())) {
            String base = config.endpoint().replaceAll("/$", "");
            exporters.span = OtlpHttpSpanExporter.builder()
                    .setEndpoint(base + "/v1/traces").build();
            exporters.metric = OtlpHttpMetricExporter.builder()
                    .setEndpoint(base + "/v1/metrics").build();
            exporters.log = OtlpHttpLogRecordExporter.builder()
                    .setEndpoint(base + "/v1/logs").build();
        } else {
            exporters.span = OtlpGrpcSpanExporter.builder()
                    .setEndpoint(config.endpoint()).build();
            exporters.metric = OtlpGrpcMetricExporter.builder()
                    .setEndpoint(config.endpoint()).build();
            exporters.log = OtlpGrpcLogRecordExporter.builder()
                    .setEndpoint(config.endpoint()).build();
        }
        return exporters;
    }

    private static boolean switchOn(String key) {
        String raw = System.getProperty(key, System.getenv(key));
        if (raw == null) {
            return true;
        }
        return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "1", "true", "yes", "on" -> true;
            default -> false;
        };
    }

    private static final class Exporters {
        @Nullable SpanExporter span;
        @Nullable MetricExporter metric;
        @Nullable LogRecordExporter log;
        boolean console;
    }
}
```

（`Exporters` 为可变静态内部类——record 不可变与逐字段赋值冲突，故不用 record。）

- [ ] **Step 4: 运行确认通过** — `mvn -q -pl a2a-t-observability test "-Dtest=ObservabilityLogsTest,ObservabilitySetupTest"`（若 OTLP exporter builder API 与 1.64 签名不符，按 javap 调整；console 路径不依赖 OTLP 类加载——但 configure 在 endpoint=null 时不触碰 OTLP 类，`Class.forName` 保护仅在 sdkAvailable 一层，OTLP 类加载发生在 buildExporters 的 http/grpc 分支，endpoint=null 不会加载 ✓）。

- [ ] **Step 5: 提交** — `mvn -q spotless:apply; git add a2a-t-observability; git commit -m "feat(observability): add structured logs, console exporters and OTel auto-setup"`

---

### Task 10: 门面接线（A2ATClient / A2ATServer）+ env.example + 全量回归

**Files:**
- Modify: `a2a-t-client/src/main/java/net/openan/a2at/sdk/client/A2ATClient.java`
- Modify: `a2a-t-server/src/main/java/net/openan/a2at/sdk/server/A2ATServer.java`
- Modify: `env.example`（追加可观测性小节）
- Test: `a2a-t-client/src/test/java/net/openan/a2at/sdk/client/api/A2ATClientObservabilityTest.java`、`a2a-t-server/src/test/java/net/openan/a2at/sdk/server/api/A2ATServerObservabilityTest.java`

**Interfaces:**
- Consumes: Task 2 `TelemetryHook`/`TelemetryOperation`。
- Produces:
  - `A2ATClient(Path envPath, @Nullable TelemetryHook hook)` 新二级构造器；既有 `A2ATClient(Path)` 委托 `NO_OP`。字段 `private final TelemetryHook hook;`。
  - `A2ATServer(Path envPath, @Nullable TelemetryHook hook)` 同构。
  - 包私有 `static @Nullable String terminalOutcome(NegotiationStatus status)`：`AGREED→"accept"`、`REJECTED→"reject"`、其余 null（client/server 各一份 4 行副本，因 observability 模块不依赖 negotiation）。
  - 门面全部公开方法经 `observed(...)` 包 Span：名 `a2at.sdk.client.{method}` / `a2at.sdk.server.{method}`；成功 `markSuccess()`，RuntimeException 路径 `markError` + 原样抛。
  - `continueNegotiation(NegotiationContext, NegotiationStatus, String)` 成功后按 `terminalOutcome(status)` 回调 `hook.reportNegotiationRounds(context.negotiationId(), outcome, context.round())`。

**方法清单（必须全部包装，逐个修改，不得遗漏）：**
- client（24）：`generateTaskPrompt`、`generateTaskPromptFromText`、`generateTaskPromptFromDataWithSchema`、`generateAuthPromptFromText`、`generateAuthPromptFromDataWithSchema`、`generateNotificationPromptFromText`、`generateNotificationPromptFromDataWithSchema`、`startNegotiation`、`receiveNegotiation`、`continueNegotiation`、`generateNegotiationProposePromptFromData`、`generateNegotiationAcceptPromptFromData`、`generateNegotiationRejectPromptFromData`、`generateNegotiationAbortPromptFromData`、`generateNegotiationProposePromptFromText`、`generateNegotiationAcceptPromptFromText`、`generateNegotiationRejectPromptFromText`、`generateNegotiationAbortPromptFromText`、`getPrompts`、`getPrompt`、`validateProposePromptAndDataFilling`、`validateAcceptPromptAndDataFilling`、`validateRejectPromptAndDataFilling`、`validateAbortPromptAndDataFilling`
- server（21）：`checkTaskPrompt`、`startNegotiation`、`receiveNegotiation`、`continueNegotiation`、`generateNegotiationProposePromptFromData`、`generateNegotiationAcceptPromptFromData`、`generateNegotiationRejectPromptFromData`、`generateNegotiationAbortPromptFromData`、`generateNegotiationProposePromptFromText`、`generateNegotiationAcceptPromptFromText`、`generateNegotiationRejectPromptFromText`、`generateNegotiationAbortPromptFromText`、`getPrompts`、`getPrompt`、`validateProposePromptAndDataFilling`、`validateAcceptPromptAndDataFilling`、`validateRejectPromptAndDataFilling`、`validateAbortPromptAndDataFilling`、`validateTaskPromptAndDataFilling`、`validateNotificationPromptAndDataFilling`、`validateAuthPromptAndDataFilling`

- [ ] **Step 1: 失败测试**

```java
package net.openan.a2at.sdk.client.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.openan.a2at.sdk.client.A2ATClient;
import net.openan.a2at.sdk.core.telemetry.TelemetryHook;
import net.openan.a2at.sdk.core.telemetry.TelemetryOperation;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMClientFactory;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class A2ATClientObservabilityTest {

    private static final String MOCK_PROVIDER = "a2at-observability-mock";

    static final class NoopLLM implements LLMClient {
        @Override
        public LLMResponse structured(
                List<java.util.Map<String, String>> messages,
                java.util.Map<String, Object> schema, Double temperature, Integer maxTokens) {
            return new LLMResponse("{}", "mock", java.util.Map.of(), java.util.Map.of());
        }
    }

    static final class RecordingHook implements TelemetryHook {
        final List<String> started = new ArrayList<>();
        final List<String> succeeded = new ArrayList<>();
        final List<String> rounds = new ArrayList<>();

        @Override
        public TelemetryOperation startSdkOperation(String role, String method) {
            String name = role + "." + method;
            started.add(name);
            return new TelemetryOperation() {
                @Override public void setAttribute(String key, Object value) {}
                @Override public void markSuccess() { succeeded.add(name); }
                @Override public void markError(Throwable error) { started.add("FAILED:" + name); }
                @Override public void close() {}
            };
        }

        @Override
        public void reportNegotiationRounds(String negotiationId, String outcome, long totalRounds) {
            rounds.add(negotiationId + ":" + outcome + ":" + totalRounds);
        }
    }

    @TempDir
    Path tempDir;

    private A2ATClient newClient(TelemetryHook hook) throws IOException {
        if (!LLMClientFactory.availableProviders().contains(MOCK_PROVIDER)) {
            LLMClientFactory.register(MOCK_PROVIDER, NoopLLM.class);
        }
        Path envFile = tempDir.resolve("client.env");
        Files.writeString(envFile, """
                A2AT_LANGUAGE=zh-CN
                A2AT_LLM_PROVIDER=%s
                A2AT_LLM_MODEL=example-model
                A2AT_LLM_BASE_URL=https://llm.example.test/v1
                A2AT_LLM_API_KEY=test-key
                A2AT_NEGOTIATION_STATE_STORE_TYPE=in_memory
                """.formatted(MOCK_PROVIDER));
        return new A2ATClient(envFile, hook);
    }

    @Test
    void llmFreePathIsWrappedAsSdkOperation() throws IOException {
        RecordingHook hook = new RecordingHook();
        A2ATClient client = newClient(hook);
        client.getPrompts();
        assertTrue(hook.started.contains("client.getPrompts"));
        assertTrue(hook.succeeded.contains("client.getPrompts"));
    }

    @Test
    void runtimeFailureMarksErrorAndPropagates() throws IOException {
        RecordingHook hook = new RecordingHook();
        A2ATClient client = newClient(hook);
        assertThrows(NullPointerException.class, () -> client.generateTaskPromptFromText(null, null));
        assertTrue(hook.started.contains("client.generateTaskPromptFromText"));
        assertTrue(hook.started.contains("FAILED:client.generateTaskPromptFromText"));
    }

    @Test
    void legacyConstructorUsesNoOpHook() throws IOException {
        A2ATClient client = newClient(TelemetryHook.NO_OP);
        assertEquals(client.getPrompts().size(), client.getPrompts().size());
    }

    @Test
    void terminalOutcomeMapping() {
        assertEquals("accept", A2ATClient.terminalOutcome(NegotiationStatus.AGREED));
        assertEquals("reject", A2ATClient.terminalOutcome(NegotiationStatus.REJECTED));
        assertEquals(null, A2ATClient.terminalOutcome(NegotiationStatus.IN_PROGRESS));
    }
}
```

server 测试 `A2ATServerObservabilityTest`：镜像以上结构（fixture env 同形、`new A2ATServer(env, hook)`、断言 `server.getPrompts`/`server.checkTaskPrompt` 起止、`A2ATServer.terminalOutcome` 映射）。server 包下既有测试（`A2ATServerNegotiationApiTest` 等）已有同型 env fixture 可参照。

- [ ] **Step 2: 运行确认失败** — `mvn -q -pl a2a-t-client test "-Dtest=A2ATClientObservabilityTest"`（编译错误：二级构造器/terminalOutcome 不存在）。

- [ ] **Step 3: 实现（A2ATClient）**

1) 导入 + 字段 + 构造器改造：

```java
import net.openan.a2at.sdk.core.telemetry.TelemetryHook;
import net.openan.a2at.sdk.core.telemetry.TelemetryOperation;
import org.jspecify.annotations.Nullable;

public final class A2ATClient {
    private final ClientPromptGenerationOrchestrator promptGenerationOrchestrator;
    private final RoleBoundNegotiationOrchestrator negotiationOrchestrator;
    private final NegotiationContentService negotiationContentService;
    private final TemplateQueryService templateQueryService;
    private final TelemetryHook hook;

    public A2ATClient(Path envPath) {
        this(envPath, null);
    }

    public A2ATClient(Path envPath, @Nullable TelemetryHook hook) {
        Path resolvedEnvPath = envPath.toAbsolutePath().normalize();
        A2ATConfig config =
                A2ATConfig.resolvePromptResourceLocalRootDir(A2ATConfig.load(resolvedEnvPath), resolvedEnvPath);
        DefaultA2ATClientBuilder builder =
                DefaultA2ATClientBuilder.builder().envPath(resolvedEnvPath).config(config);
        this.hook = hook == null ? TelemetryHook.NO_OP : hook;
        this.promptGenerationOrchestrator = builder.buildPromptGenerationOrchestrator();
        this.negotiationOrchestrator = builder.buildNegotiationOrchestrator();
        this.negotiationContentService =
                new NegotiationContentService(builder.buildNegotiationGenerationOrchestrator());
        this.templateQueryService = builder.buildTemplateQueryService();
    }

    private <T> T observed(String method, java.util.function.Supplier<T> action) {
        try (TelemetryOperation operation = hook.startSdkOperation("client", method)) {
            try {
                T result = action.get();
                operation.markSuccess();
                return result;
            } catch (RuntimeException error) {
                operation.markError(error);
                throw error;
            }
        }
    }

    static @Nullable String terminalOutcome(NegotiationStatus status) {
        if (status == NegotiationStatus.AGREED) {
            return "accept";
        }
        return status == NegotiationStatus.REJECTED ? "reject" : null;
    }

    private void reportNegotiationRounds(NegotiationContext context, NegotiationStatus status) {
        try {
            String outcome = terminalOutcome(status);
            if (outcome != null) {
                hook.reportNegotiationRounds(context.negotiationId(), outcome, context.round());
            }
        } catch (RuntimeException error) {
            LOGGER.warn("a2at: negotiation metric report failed", error);
        }
    }
}
```

（client 现无 LOGGER 则加 `private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(A2ATClient.class);`）

2) 方法包装（保持既有方法体为 lambda 载荷，逐个改写；三个代表样例）：

```java
public PromptGenerationResult generateTaskPrompt(Object userInput) {
    return observed("generateTaskPrompt", () -> promptGenerationOrchestrator.generateTaskPrompt(userInput));
}

public Map<String, Object> continueNegotiation(
        NegotiationContext context, NegotiationStatus status, String contentText) {
    return observed("continueNegotiation", () -> {
        Map<String, Object> result = negotiationOrchestrator.continueNegotiation(context, status, contentText);
        reportNegotiationRounds(context, status);
        return result;
    });
}

public Optional<PromptTemplate> getPrompt(@NonNull String templateUri) {
    return observed("getPrompt", () -> templateQueryService.findTemplate(TemplateUri.of(templateUri)));
}
```

其余 21 个方法按同样模式改写（把现有方法体整体放入 `observed("<method-name>", () -> <body>)`；`getPrompts` 等无参方法同样处理）。**禁止改动任何业务语义**——只做包装。

3) A2ATServer 同构实现：role 用 `"server"`，24→21 个方法按清单包装，`continueNegotiation` 同样接 `reportNegotiationRounds`，`terminalOutcome` 副本同名。

- [ ] **Step 4: 运行确认通过 + 全量回归**

```
mvn -q -pl a2a-t-client test "-Dtest=A2ATClientObservabilityTest"
mvn -q -pl a2a-t-server test "-Dtest=A2ATServerObservabilityTest"
mvn -q test        # 全模块回归（corpus/sample 在内）
```

期望：全部 PASS，既有测试零回归。

- [ ] **Step 5: env.example 追加可观测性小节**

```
# --- Observability (a2a-t-observability) ---
OTEL_INSTRUMENTATION_A2AT_SDK_ENABLED=true
A2AT_OBSERVABILITY_ENABLED=true
A2AT_TRACE_ENABLED=true
A2AT_METRIC_ENABLED=true
A2AT_LOG_ENABLED=true
#OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317
A2AT_EXPORTER_PROTOCOL=grpc
A2AT_CONSOLE_EXPORTER=true
#OTEL_SERVICE_NAME=a2a-t-agent
#A2AT_AGENT_ROLE=
A2AT_EXTRACT_REQUEST=false
A2AT_EXTRACT_RESPONSE=false
A2AT_LOGS_PAYLOAD_ENABLED=false
A2AT_LOGS_PAYLOAD_MAX_LENGTH=4096
```

- [ ] **Step 6: 提交** — `mvn -q spotless:apply; git add a2a-t-client a2a-t-server env.example; git commit -m "feat(observability): wire TelemetryHook into client and server facades"`

---

## Plan A 自检清单（完成后执行）

- [ ] spec §3 表 12 行 → 逐行对应类已存在且被测试覆盖（`attributes.py` 拆分说明见 Task 4；`sdk_trace.py` 的 `SdkOperationSpans` OTel 实现移入 Plan B，因其属于可选实现侧）
- [ ] spec §5 命名注册表 → Task 4 常量 + Task 8 指标名 + Task 9 事件名逐字一致
- [ ] spec §7 配置表 → Task 3 常量 + Task 5 开关 + Task 9 console/env 处理
- [ ] spec §8 三条铁律 → Task 9 幂等/降级、Task 10 try/catch 包装、A2ATSpan scope close
- [ ] spec §9 测试策略 → 各 Task 测试 + Task 10 全量回归
- [ ] `mvn -q spotless:check` 全绿；NullAway 零报错

## 后续（Plan B，另行编写）

a2a-java 协议层装饰器（`A2ATClientTransportDecorator`、`A2ATRequestHandlerDecorator`、`A2ATAgentExecutorDecorator`、`A2ATPushSenderDecorator`、`A2ATA2ATClientTransportWrapper`、类型化提取器 `A2AJavaAttributeExtractors`、`SdkOperationSpans` hook 实现）+ sample 可观测场景 + README。待 Plan A 落地后按其真实签名编写独立计划。

### Plan B 必须吸收的终审遗留项（final review 2026-10-20）

1. `ensureConfigured()` 仅读系统环境变量；`.env` 接线（`fromMap(DotEnvConfigSource.load(path))`）由 Plan B 的 README/sample 负责，并注意 `A2AT_SERVICE_NAME` 未列入 env.example 示例
2. `PayloadSanitizer` redactor 失败时 fail-open（透传未脱敏值）→ 后续安全加固改 fail-closed（丢弃 payload）
3. `MetricHistograms` 静态缓存跨 `GlobalOpenTelemetry.resetForTest()` 存活 + 装配前缓存的直方图永久绑定 no-op meter → Plan B 设计 provider 感知缓存或在 `ObservabilitySetup.resetForTest` 中清缓存
4. 终审已修：门面 hook 回调全防护（ae2c526）；`getOrNoop()` 防 get() 副作用（ae2c526）——Plan B 的 `SdkOperationSpans` 必须沿用 `getOrNoop()` 模式且自身调用点全部 try/catch 防护；**`TelemetryOperation.close()` 是最后一个未防护 hook 点**（成功路径 try-with-resources 的 close 在 return 后触发，用户实现抛异常会把业务成功变失败）——Plan B 实现的 close() 必须自防护，或后续在门面 observed() 内补 guard
5. 遗留 follow-up（不阻塞合并）：console-exporter-disabled 分支跳过 `set()`、ISE 路径 provider 关闭、`replaceAll("/+$")` 多斜杠、`switchOn` 去重、`A2ATCurrentSpan.setAttribute` 类型化




