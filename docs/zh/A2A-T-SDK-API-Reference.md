# A2A-T SDK Java 对外 API 文档

> 覆盖协商（Negotiation-T）、任务提示（Task-T）、授权（Authorization-T）、通知（Notification-T）四类扩展的对外 API。协商能力的完整说明见分册 [A2A-T-Negotiation-API-Reference.md](A2A-T-Negotiation-API-Reference.md)。

新集成建议采用协商内容生成与校验 API，由调用方管理内容 context，并通过 `MetadataContent.buildMetadataContent()` 交付消息。运行时状态机 API 独立管理自己的会话 ID、context 和 payload，不会自动关联内容层会话。

---

## 一、模块与依赖

### 1.1 Maven 坐标

```xml
<dependency>
    <groupId>net.openan.a2a-t.sdk</groupId>
    <artifactId>a2a-t-client</artifactId>
    <version>1.1.1</version>
</dependency>
```

Server 侧用 a2a-t-server 替代：

```xml
<dependency>
    <groupId>net.openan.a2a-t.sdk</groupId>
    <artifactId>a2a-t-server</artifactId>
    <version>1.1.1</version>
</dependency>
```

版本统一管理用 a2a-t-bom：

```xml
<dependency>
    <groupId>net.openan.a2a-t.sdk</groupId>
    <artifactId>a2a-t-bom</artifactId>
    <version>1.1.1</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

### 1.2 模块结构

| 模块 | 作用 |
|---|---|
| a2a-t-bom | 版本对齐 BOM |
| a2a-t-core | 共享配置加载、值类型、JSON 解析、异常体系 |
| a2a-t-resources | 打包资源和 classpath 加载 |
| a2a-t-llm | LLM 适配层（默认 OpenAI 兼容） |
| a2a-t-prompt | 场景识别、slot 抽取、模板渲染 |
| a2a-t-negotiation | 协商类型、运行时状态机、内容生成引擎、校验流水线 |
| a2a-t-client | 客户端 facade（A2ATClient） |
| a2a-t-server | 服务端 facade（A2ATServer） |
| a2a-t-sample | 可运行的 client/server 示例 |

a2a-t-client 和 a2a-t-server 通过传递依赖引入 negotiation 模块，内容生成和校验能力已可用，无需额外加依赖。

---

## 二、初始化

### 2.1 构造

Client 和 Server 都通过 .env 文件路径构造：

```java
import net.openan.a2at.sdk.client.A2ATClient;
import java.nio.file.Path;

A2ATClient client = new A2ATClient(Path.of("/path/to/.env"));

// Server 侧
import net.openan.a2at.sdk.server.A2ATServer;
A2ATServer server = new A2ATServer(Path.of("/path/to/.env"));
```

构造时自动完成：加载 .env 配置 -> 创建 LLM client -> 组装 prompt 生成/合规编排器 -> 组装协商编排器 -> 组装内容生成编排器。

### 2.2 .env 配置项

| 配置项 | 默认值 | 说明 |
|---|---|---|
| A2AT_LANGUAGE | en-US | 消息语言，zh-CN 或 en-US |
| A2AT_PROMPT_SOURCE_TYPE | classpath | classpath 用打包资源，local_file 用本地目录 |
| A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR | （未设置） | local_file 的资源根目录，相对路径基于 .env 所在目录 |
| A2AT_PROMPT_COMPLIANCE_ENABLED | false | 是否启用 prompt 合规校验 |
| A2AT_LLM_PROVIDER | openai | 内置 OpenAI 兼容 provider，也可注册其他 provider |
| A2AT_LLM_MODEL | （必填） | 模型名 |
| A2AT_LLM_API_KEY | （必填） | API key |
| A2AT_LLM_BASE_URL | （可选） | OpenAI 兼容 API 的 base URL |
| A2AT_LLM_MAX_TOKENS | （可选） | 最大 token 数 |
| A2AT_LLM_TEMPERATURE | （可选） | 温度 |
| A2AT_LLM_TIMEOUT_SECONDS | （可选） | 超时秒数 |
| A2AT_LLM_MAX_ATTEMPTS | 3 | LLM 步骤尝试次数上限，限定为 1–10 |
| A2AT_LLM_REASONING_EFFORT | （可选） | 推理强度 |
| A2AT_LLM_DISABLE_SYSTEM_PROXY | （可选） | 禁用系统代理 |
| A2AT_LLM_SSL_VERIFY | true | 校验 LLM HTTPS 对端证书与主机名 |
| A2AT_LLM_DETAIL_LOG_ENABLED | false | 是否记录完整 LLM 请求与响应 |
| A2AT_INPUT_TEXT_MAX_CHARS | （可选） | fromText 输入的最大字符数 |
| A2AT_LLM_HISTORY_WINDOW | 10 | 对话历史窗口 |
| A2AT_LLM_SESSION_MAX_TOTAL | 300 | 会话总数上限 |
| A2AT_LLM_SESSION_MAX_PER_PROVIDER | 100 | 每 provider 会话数上限 |
| A2AT_NEGOTIATION_STATE_STORE_TYPE | in_memory | 协商状态存储类型 |

---

## 三、扩展 URI 常量

定义在 ExtensionUriConstants：

| 扩展 | URI |
|---|---|
| Task-T | https://projects.tmforum.org/a2aproject/telecommunication/extensions/Task-T/v1 |
| Authorization-T | https://projects.tmforum.org/a2aproject/telecommunication/extensions/Authorization-T/v1 |
| Notification-T | https://projects.tmforum.org/a2aproject/telecommunication/extensions/Notification-T/v1 |
| Negotiation-T（规范） | https://projects.tmforum.org/a2aproject/telecommunication/extensions/Negotiation-T/v1 |
| Negotiation-T（NL legacy） | https://projects.tmforum.org/a2aproject/telecommunication/extensions/Negotiation-T/NL/v1 |

协商内容生成返回的 `MetadataContent.extensionUri()` 使用规范 URI；运行时 `startNegotiation()` / `continueNegotiation()` 返回的 payload 使用 NL URI。`NegotiationPayloadMapper.extractContextMap()` 接收这两种 URI 的运行时 payload；它不负责转换内容 metadata 或同步两种 context。

---

## 四、协商运行时状态机 API

Client 和 Server 均提供以下三个方法，驱动协商状态机的 start/receive/continue 流程。`startNegotiation()` 自行生成会话 ID，与内容 API 的 context 无自动映射。完整说明（含状态校验顺序、payload 结构）见[分册的运行时状态机 API](A2A-T-Negotiation-API-Reference.md#三运行时状态机-api)。

```java
Map<String, Object> startNegotiation(NegotiationType type, String contentText, Map<String, Object> facts)
Map<String, Object> receiveNegotiation(String message, Map<String, Object> context)
Map<String, Object> continueNegotiation(NegotiationContext context, NegotiationStatus status, String contentText)
```

### 4.4 枚举类型

```java
enum NegotiationType   { INFORMATION, TARGET, FEASIBILITY }
enum NegotiationStatus { IN_PROGRESS, AGREED, REJECTED }
enum NegotiationRole   { CLIENT, SERVER }
```

### 4.5 运行时 Context（两套，不可混用）

| 类 | 用途 | 字段 |
|---|---|---|
| `net.openan.a2at.sdk.core.model.NegotiationContext` | 内容生成/校验 | `id, round, maxRounds, performative` |
| `net.openan.a2at.sdk.negotiation.types.model.NegotiationContext` | 状态机 | `negotiationType, negotiationId, round, status` |

内容生成 context 带 `of(id, round, performative)`、`nextRound()`、`isExhausted()`，`DEFAULT_MAX_ROUNDS = 5`；`performative` 必填（`PROPOSE` / `ACCEPT` / `REJECT` / `ABORT`）。

### 4.6 Payload 工具

```java
NegotiationContext ctx = NegotiationPayloadMapper.contextFromMap(contextMap);   // 反序列化
Map<String, Object> payload = NegotiationPayloadMapper.contextPayload(ctx);      // 序列化
Map<String, Object> contextMap = NegotiationPayloadMapper.extractContextMap(payload);  // 提取
Map<String, Object> payload = NegotiationPayloadMapper.payload(context, contentText, facts);  // 构建
```

---

## 五、协商内容生成 API

每条流水线都有 `fromData`（确定性，协商报文生成不调 LLM）和 `fromText`（含一步 LLM 结构化抽取）两个变体，覆盖 propose / accept / reject / abort 四个阶段：

```java
MetadataContent generateNegotiationProposePromptFromData(NegotiationProposeData data, String templateUri);
MetadataContent generateNegotiationAcceptPromptFromData (NegotiationEndingData  data, String templateUri);
MetadataContent generateNegotiationRejectPromptFromData (NegotiationEndingData  data, String templateUri);
MetadataContent generateNegotiationAbortPromptFromData  (NegotiationAbortData   data, String templateUri);

MetadataContent generateNegotiationProposePromptFromText(String text, NegotiationContext context, String templateUri);
MetadataContent generateNegotiationAcceptPromptFromText (String text, NegotiationContext context, String templateUri);
MetadataContent generateNegotiationRejectPromptFromText (String text, NegotiationContext context, String templateUri);
MetadataContent generateNegotiationAbortPromptFromText  (String text, NegotiationContext context, String templateUri);
```

### 5.3 返回值 MetadataContent

```java
record MetadataContent(
    String templateUri,    // 模板 URI
    String promptText,     // 渲染后的消息文本
    String extensionUri,   // TMF 扩展 URI（规范 Negotiation-T URI）
    NegotiationContext negotiationContext // 非协商消息为 null
)
```

### 5.4 输入数据模型

```java
record NegotiationProposeData(NegotiationContext context, NegotiationProposeContent content)
record NegotiationEndingData (NegotiationContext context, NegotiationEndingContent content)
record NegotiationAbortData  (NegotiationContext context, NegotiationAbortContent content)
```

### 5.5 类型化内容模型

```java
record InformationProposeContent(List<NegotiationItem> items, String relationship)

record TargetProposeContent(
    String targetNegotiationDescription,
    List<NegotiationItem> intentUnderstanding,
    List<NegotiationItem> alignmentAndClarification,
    List<NegotiationItem> requestForClarification,
    String targetConfirmRequest)

record FeasibilityProposeContent(
    String feasibilityNegotiationDescription,
    NegotiationAction action,
    List<NegotiationItem> contentsToEvaluate,
    List<NegotiationItem> infeasibilityDetailsAndProposal,
    String feasibilityConfirmRequest)

record InformationEndingContent(NegotiationConclusion conclusion, List<NegotiationItem> items)
record TargetEndingContent(NegotiationConclusion conclusion, String confirmedIntent, String failureReason)
record FeasibilityEndingContent(NegotiationConclusion conclusion, String feasibilitySummary)
record NegotiationAbortContent(String terminationReason)
```

字段约束与各字段的语义见分册 §4.4-4.6。

### 5.6 辅助类型

```java
record NegotiationItem(String name, String value)

enum NegotiationConclusion { ACCEPT, REJECT, ABORT }
enum NegotiationAction { REQUEST_FEASIBILITY_EVALUATION, PROPOSE_ALTERNATIVE_ON_FAILURE }
```

### 5.7 模板 URI 格式

类型化模板：`Negotiation-T/{type}-negotiation/{propose|accept-reject}/v1`；协商终止模板与类型无关：`Negotiation-T/common/abort/v1`。`StandardTemplates` 提供全部常量（如对外接口使用的 `INFORMATION_NEGOTIATION_PROPOSE_URI`、`NEGOTIATION_ABORT_URI`）。

---

## 六、协商校验与参数提取 API

```java
FilledParamData validateProposePromptAndDataFilling(String prompt, NegotiationContext context, Map<String, Object> schema, String templateUri);
FilledParamData validateAcceptPromptAndDataFilling (String prompt, NegotiationContext context, Map<String, Object> schema, String templateUri);
FilledParamData validateRejectPromptAndDataFilling (String prompt, NegotiationContext context, Map<String, Object> schema, String templateUri);
FilledParamData validateAbortPromptAndDataFilling  (String prompt, NegotiationContext context, Map<String, Object> schema, String templateUri);
```

校验管线（规则门 → LLM 语义校验 → 参数合并）与返回值 `FilledParamData(Map<String, Object> data)` 的完整说明见分册 §6。

---

## 七、模板查询 API

```java
List<PromptTemplate>       getPrompts();                         // 跨所有扩展
Optional<PromptTemplate>   getPrompt(String uri);                // 跨扩展按 URI 查
```

对于格式正确的模板 URI，缺失返回空列表或 `Optional.empty()`。URI 为 null 时抛 `NullPointerException`，空白或格式错误时抛 `IllegalArgumentException`。

---

## 八、任务提示 API

### 8.1 Client 侧

```java
// 从用户输入生成任务提示
PromptGenerationResult generateTaskPrompt(Object userInput)

// 从自由文本生成（绕过场景识别，直接用指定模板）
MetadataContent generateTaskPromptFromText(String text, String templateUri)

// 从结构化数据生成（带可选 schema）
MetadataContent generateTaskPromptFromDataWithSchema(
    Map<String, Object> data, Map<String, Object> schema, String templateUri)

// 从自由文本生成授权提示（实验性）
MetadataContent generateAuthPromptFromText(String text, String templateUri)

// 从结构化数据生成授权提示（实验性）
MetadataContent generateAuthPromptFromDataWithSchema(
    Map<String, Object> data, Map<String, Object> schema, String templateUri)

// 从自由文本生成通知提示
MetadataContent generateNotificationPromptFromText(String text, String templateUri)

// 从结构化数据生成通知提示
MetadataContent generateNotificationPromptFromDataWithSchema(
    Map<String, Object> data, Map<String, Object> schema, String templateUri)
```

PromptGenerationResult 结构：

```java
record PromptGenerationResult(
    boolean success,
    String promptText,           // 成功时的渲染文本
    PromptGenerationFailure failure  // 失败时的详情
)

record PromptGenerationFailure(String code, String message, String stage)
```

### 8.2 Server 侧

```java
// 校验已处理的任务提示
PromptComplianceResult checkTaskPrompt(String processedPromptText)
```

PromptComplianceResult 结构：

```java
record PromptComplianceResult(
    boolean success,
    PromptComplianceFailure failure  // 失败时携带 code/message/stage
)
```

---

## 九、异常体系

### 9.1 异常分类

| 异常类型 | 性质 | 携带信息 |
|---|---|---|
| IllegalArgumentException / NullPointerException | 编程错误 | 指出非法或缺失参数的 message |
| NegotiationGenerationException | 生成失败 | code + message + facts + cause |
| NegotiationParamExtractionException | 校验失败 | code + message + facts + errors（逐 slot 错误） |

### 9.2 错误码

定义在 A2ATErrorCodes：

| 码 | 含义 | 可重试 |
|---|---|---|
| template.not_found | 模板/prompt 资源缺失 | 否 |
| negotiation.content_extract_failed | LLM 内容抽取响应不可解析 | 是 |
| llm.invocation_failed | LLM 调用基础设施失败 | 是 |
| llm.response_invalid | LLM 响应结构无效 | 是 |
| negotiation.field_missing | 必填字段缺失 | 否 |
| negotiation.invalid_input | 输入与 phase/action 矛盾 | 否 |
| negotiation.rule_violation | 规则门校验失败 | 否 |
| negotiation.semantic_rejected | 语义校验拒绝 | 否 |
| input.text_too_long | fromText 输入超出配置的最大长度 | 否 |
| slot.not_provided | 参数提取默认失败码 | 否 |

### 9.3 重试策略

NegotiationGenerationOrchestrator.withRetry 对 LLM 步骤统一重试：
- negotiation.content_extract_failed、llm.invocation_failed 和 llm.response_invalid 可重试
- 其他错误码立即抛出
- 耗尽后原样重抛（保留原始 error code）
- 默认 maxAttempts 来自 LLM config（A2AT_LLM_MAX_ATTEMPTS）

---

## 十、LLM 适配层

### 10.1 LLMClient 接口

```java
public interface LLMClient {
    LLMResponse structured(
        List<Map<String, String>> messages,
        Map<String, Object> jsonSchema,
        Double temperature,
        Integer maxTokens
    );
}

record LLMResponse(
    String content,
    String model,
    Map<String, Integer> usage,
    Map<String, Object> metadata
)
```

### 10.2 默认实现

LLMClientFactory 注册了 openai provider：

```java
// 注册自定义 provider
LLMClientFactory.register("my-provider", MyClient.class);

// 创建 client
LLMClient client = LLMClientFactory.create(provider, config);
```

默认 OpenAIClient 兼容所有 OpenAI 规范的 API（DeepSeek、Azure OpenAI 等），通过 A2AT_LLM_BASE_URL 配置。

---

## 十一、多语言词汇表（Vocabulary）

```java
Vocabulary vocab = Vocabulary.forLanguage("zh-CN");
String sectionTitle = vocab.get("section.target");        // "目标协商概述"
String slotName = vocab.get("slot.target");               // "目标协商概述"
String punct = vocab.get("punct.list_colon");             // "："
```

支持 zh-CN 和 en-US，两者暴露相同的 37 个 canonical key 集合（其中 section.* 18 个）。section.* 值是模板中 ## 标题的逐字副本，slot.* 值是 {{...}} 占位符名。不支持的语言抛 IllegalArgumentException。

---

## 十二、状态存储

```java
public interface NegotiationStore {
    NegotiationRecord get(String negotiationId);
    void save(NegotiationRecord record);
    void delete(String negotiationId);
    boolean cleanupExpired();
}
```

当前只有 InMemoryNegotiationStore 实现，不保证持久化。可通过 NegotiationHandler.Builder.store() 注入自定义实现。

---

## 十三、当前支持范围与局限

| 能力 | 状态 |
|---|---|
| 协商运行时状态机（start/receive/continue） | 完整，三种类型均注册 |
| 协商内容生成（fromData/fromText） | 完整，8 个公开生成方法覆盖三类协商与 propose/accept/reject/abort |
| 协商校验参数提取（validateAndFilling） | 完整，propose/accept/reject/abort 四个阶段均支持 |
| Information 运行时 handler | 有实质逻辑（含 compliance checker） |
| Target / Feasibility 运行时 handler | 空壳 echo（内容层已完整，运行时未对接） |
| LLM 适配 | 仅 OpenAI 兼容 |
| 资源加载 | 仅 local file 和 classpath |
| 状态持久化 | 仅 in-memory |
| 远程资源加载 | 不支持（无 registry-center） |
| Authorization-T slot schema | 已打包 authorization-policy-management/v1 的 zh-CN、en-US 资源；其他授权场景不在此范围内 |
| 语言覆盖 | zh-CN 和 en-US |

---

## 十四、使用示例

协商完整示例集中在分册维护，按所用 API 选择对应流程：

| 场景 | 示例 |
|---|---|
| 内容 API 生成提议并交付 metadata | [使用内容 API 生成提议](A2A-T-Negotiation-API-Reference.md#101-使用内容-api-生成提议) |
| 从同一入站消息取得上下文并校验 | [校验收到的提议](A2A-T-Negotiation-API-Reference.md#102-校验收到的提议) |
| 保持会话 ID 和轮次生成同意响应 | [生成同意响应](A2A-T-Negotiation-API-Reference.md#103-保持收到的轮次生成同意响应) |
| 自由文本与其他结束响应 | [自由文本生成](A2A-T-Negotiation-API-Reference.md#104-从自由文本生成协商消息)、[结束响应](A2A-T-Negotiation-API-Reference.md#105-生成可选的结束响应) |
| 独立的运行时 start/receive/continue 流程 | [运行时状态机示例](A2A-T-Negotiation-API-Reference.md#106-独立使用运行时状态机) |

内容 API 与运行时状态机使用不同的 context 和 payload 契约；不要将两条路径中各自生成的会话 ID 当作同一个会话直接拼接。

### 14.1 查询可用模板

```java
// 列出所有协商模板
List<PromptTemplate> templates = client.getPrompts().stream()
    .filter(t -> t.templateUri().uri().startsWith("Negotiation-T/"))
    .toList();
for (PromptTemplate t : templates) {
    System.out.println(t.templateUri().uri() + " -> " + t.description());
}

// 查询特定模板
Optional<PromptTemplate> template = client.getPrompt(
    "Negotiation-T/target-negotiation/propose/v1");
```

---

## 十五、文件索引

### Facade 层

| 文件 | 作用 |
|---|---|
| a2a-t-client/.../A2ATClient.java | 客户端对外 facade |
| a2a-t-server/.../A2ATServer.java | 服务端对外 facade |

### 核心模型层（a2a-t-core）

| 文件 | 作用 |
|---|---|
| a2a-t-core/.../model/ExtensionUriConstants.java | 扩展 URI 常量 |
| a2a-t-core/.../model/NegotiationContext.java | 内容生成/校验 context（id, round, maxRounds, performative） |
| a2a-t-core/.../model/NegotiationPerformative.java | 消息言语行为枚举 |
| a2a-t-core/.../model/MetadataContent.java | 生成消息元数据 |
| a2a-t-core/.../model/FilledParamData.java | 校验参数提取结果 |
| a2a-t-core/.../model/StandardTemplates.java | 标准模板 URI 常量 |
| a2a-t-core/.../model/TemplateUri.java | 模板 URI 值类型 |
| a2a-t-core/.../model/A2ATConfig.java | 全局配置 |
| a2a-t-core/.../model/PromptRuntimeConfig.java | prompt 运行时配置 |
| a2a-t-core/.../exception/A2ATErrorCodes.java | 错误码常量 |

### 协商内容层

| 文件 | 作用 |
|---|---|
| a2a-t-negotiation/.../content/NegotiationType.java | 协商类型枚举 |
| a2a-t-negotiation/.../content/NegotiationConclusion.java | 终态结论枚举 |
| a2a-t-negotiation/.../content/NegotiationAction.java | feasibility 动作枚举 |
| a2a-t-negotiation/.../content/NegotiationContent.java | 内容 sealed 接口 |
| a2a-t-negotiation/.../content/NegotiationProposeContent.java | propose 内容 sealed 接口 |
| a2a-t-negotiation/.../content/NegotiationEndingContent.java | ending 内容 sealed 接口 |
| a2a-t-negotiation/.../content/InformationProposeContent.java | 信息协商 propose 内容 |
| a2a-t-negotiation/.../content/InformationEndingContent.java | 信息协商 ending 内容 |
| a2a-t-negotiation/.../content/TargetProposeContent.java | 目标协商 propose 内容 |
| a2a-t-negotiation/.../content/TargetEndingContent.java | 目标协商 ending 内容 |
| a2a-t-negotiation/.../content/FeasibilityProposeContent.java | 可行性协商 propose 内容 |
| a2a-t-negotiation/.../content/FeasibilityEndingContent.java | 可行性协商 ending 内容 |
| a2a-t-negotiation/.../content/NegotiationAbortContent.java | 协商终止内容 |
| a2a-t-negotiation/.../content/NegotiationItem.java | 协商项 |
| a2a-t-negotiation/.../content/NegotiationProposeData.java | propose 输入数据 |
| a2a-t-negotiation/.../content/NegotiationEndingData.java | ending 输入数据 |
| a2a-t-negotiation/.../content/NegotiationAbortData.java | abort 输入数据 |
| a2a-t-negotiation/.../content/Vocabulary.java | 多语言词汇表 |
| a2a-t-negotiation/.../content/NegotiationGenerationException.java | 生成异常 |
| a2a-t-negotiation/.../content/NegotiationParamExtractionException.java | 参数提取异常 |
| a2a-t-negotiation/.../content/NegotiationProcessingException.java | 处理异常 |

### 协商生成层

| 文件 | 作用 |
|---|---|
| a2a-t-negotiation/.../generation/NegotiationContentService.java | 内容层共享服务 |
| a2a-t-negotiation/.../generation/NegotiationGenerationOrchestrator.java | 生成引擎核心 |
| a2a-t-negotiation/.../generation/NegotiationGenerationOrchestratorBuilder.java | 生成引擎 builder |
| a2a-t-negotiation/.../generation/NegotiationGenerator.java | 生成器接口 |
| a2a-t-negotiation/.../generation/NegotiationGeneratorRegistry.java | 生成器 dispatch 表 |
| a2a-t-negotiation/.../generation/AbstractNegotiationGenerator.java | 生成器基类 |
| a2a-t-negotiation/.../generation/InformationProposeGenerator.java | 信息协商 propose 生成器 |
| a2a-t-negotiation/.../generation/InformationEndingGenerator.java | 信息协商 ending 生成器 |
| a2a-t-negotiation/.../generation/TargetProposeGenerator.java | 目标协商 propose 生成器 |
| a2a-t-negotiation/.../generation/TargetEndingGenerator.java | 目标协商 ending 生成器 |
| a2a-t-negotiation/.../generation/FeasibilityProposeGenerator.java | 可行性协商 propose 生成器 |
| a2a-t-negotiation/.../generation/FeasibilityEndingGenerator.java | 可行性协商 ending 生成器 |
| a2a-t-negotiation/.../generation/AbortGenerator.java | 协商终止生成器 |
| a2a-t-negotiation/.../generation/NegotiationContentExtractor.java | 内容抽取接口 |
| a2a-t-negotiation/.../generation/DefaultNegotiationContentExtractor.java | LLM 内容抽取（fromText 用） |
| a2a-t-negotiation/.../generation/NegotiationPromptRenderer.java | 模板渲染器 |
| a2a-t-negotiation/.../generation/NegotiationItemFormatter.java | 项列表格式化 |
| a2a-t-negotiation/.../generation/NegotiationMessageBuilder.java | LLM 消息构建 |
| a2a-t-negotiation/.../generation/NegotiationJsonSchemaBuilder.java | JSON schema 构建 |

### 协商校验层

| 文件 | 作用 |
|---|---|
| a2a-t-negotiation/.../validation/NegotiationComplianceChecker.java | 规则门接口 |
| a2a-t-negotiation/.../validation/DefaultNegotiationComplianceChecker.java | 规则门实现 |
| a2a-t-negotiation/.../validation/NegotiationSemanticValidator.java | 语义校验接口 |
| a2a-t-negotiation/.../validation/DefaultNegotiationSemanticValidator.java | LLM 语义校验实现 |
| a2a-t-negotiation/.../validation/ParamExtractor.java | 参数提取编排 |
| a2a-t-negotiation/.../validation/NegotiationRuleCheckResult.java | 规则门结果 |
| a2a-t-negotiation/.../validation/NegotiationRuleCheckerAdapter.java | 规则门适配器 |
| a2a-t-negotiation/.../validation/SemanticValidationResult.java | 语义校验结果 |
| a2a-t-negotiation/.../validation/NegotiationValidationException.java | 校验异常 |

### 协商运行时层

| 文件 | 作用 |
|---|---|
| a2a-t-negotiation/.../runtime/NegotiationHandler.java | 状态机 facade |
| a2a-t-negotiation/.../runtime/NegotiationRuntime.java | 状态机核心 |
| a2a-t-negotiation/.../runtime/RoleBoundNegotiationOrchestrator.java | 角色绑定 orchestrator |
| a2a-t-negotiation/.../runtime/helper/NegotiationPayloadMapper.java | payload 序列化/反序列化 |

### 协商 handler 层

| 文件 | 作用 |
|---|---|
| a2a-t-negotiation/.../handler/Negotiation.java | handler 接口 |
| a2a-t-negotiation/.../handler/InformationNegotiation.java | information handler（有合规校验逻辑） |
| a2a-t-negotiation/.../handler/TargetNegotiation.java | target handler（空壳 echo） |
| a2a-t-negotiation/.../handler/FeasibilityNegotiation.java | feasibility handler（空壳 echo） |

### 协商资源层

| 文件 | 作用 |
|---|---|
| a2a-t-negotiation/.../resources/NegotiationReference.java | 模板引用与 URI 解析 |
| a2a-t-negotiation/.../resources/NegotiationTemplateLoader.java | 模板加载接口 |
| a2a-t-negotiation/.../resources/DefaultNegotiationTemplateLoader.java | 模板加载实现 |
