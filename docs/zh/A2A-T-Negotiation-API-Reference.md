# A2A-T 协商（Negotiation-T）API 文档

> 本文是 [A2A-T-SDK-API-Reference.md](A2A-T-SDK-API-Reference.md) 的协商分册，专项覆盖协商扩展的对外 API，便于按能力域查阅。

---

## 一、能力概览

协商扩展（Negotiation-T）让 client 与 server 在执行任务前对齐参数：信息收集、目标对齐、可行性确认，以及协商终止。SDK 通过无状态的内容生成和校验操作提供协商能力：

| 层 | 职责 | 入口 |
|---|---|---|
| 内容生成 | 按类型化内容渲染协商消息（fromData 确定性、fromText 含一步 LLM 抽取） | `generateNegotiation*PromptFromData/FromText` |
| 校验与参数提取 | 规则门 + 语义校验 + 参数合并，从已渲染消息提取结构化参数 | `validate*PromptAndDataFilling` |

两类协商操作通过 `A2ATClient` / `A2ATServer` 两个 facade 暴露，**方法签名完全对称**（见 [NegotiationV3ApiSurfaceTest](../../a2a-t-sample/src/test/java/net/openan/a2at/sdk/sample/api/NegotiationV3ApiSurfaceTest.java)）。server 还提供任务提示的合规校验。

使用内容 API，由调用方管理 `core.model.NegotiationContext`，通过 A2A 传输层发送返回的 metadata。SDK 不持久化协商会话。旧状态机接口自 v1.1.0 起废弃，在 v1.2.0 移除（见[移除变更](https://github.com/project-openan/a2a-t-sdk-java/pull/209)）；即使旧版本中仍有实现，也不属于本文说明的对外接口。

### 1.1 Maven 坐标

```xml
<dependency>
    <groupId>net.openan.a2a-t.sdk</groupId>
    <artifactId>a2a-t-client</artifactId>
    <version>1.1.1</version>
</dependency>
```

Server 侧用 `a2a-t-server` 替代。协商模块由二者传递依赖引入，无需单独加依赖。

### 1.2 协商相关 `.env` 配置项

| 配置项 | 默认值 | 说明 |
|---|---|---|
| A2AT_LANGUAGE | en-US | 消息语言，`zh-CN` 或 `en-US` |
| A2AT_PROMPT_SOURCE_TYPE | classpath | `classpath` 用打包资源，`local_file` 用本地目录 |
| A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR | （未设置） | local_file 的资源根目录，相对路径基于 .env 所在目录 |
| A2AT_LLM_PROVIDER | openai | 内置 OpenAI 兼容 provider，也可通过 `LLMClientFactory` 注册其他 provider |
| A2AT_LLM_MODEL | （必填） | 模型名 |
| A2AT_LLM_API_KEY | （必填） | API key |
| A2AT_LLM_BASE_URL | （可选） | OpenAI 兼容 API 的 base URL |

---

## 二、扩展 URI

定义在 [ExtensionUriConstants](../../a2a-t-core/src/main/java/net/openan/a2at/sdk/core/model/ExtensionUriConstants.java)：

| 用途 | URI |
|---|---|
| Negotiation-T（内容 metadata） | `https://projects.tmforum.org/a2aproject/telecommunication/extensions/Negotiation-T/v1` |

内容生成返回的 `MetadataContent.extensionUri()` 使用规范 URI。`buildMetadataContent()` 将消息文本、模板 URI 和会话 context 放入出站 metadata。

---

## 三、内容生成 API

Client 与 Server 均通过 `NegotiationContentService` 代理 `NegotiationGenerationOrchestrator`。每条流水线都有 `fromData`（确定性，协商报文生成不调 LLM）和 `fromText`（含一步 LLM 结构化抽取）两个变体，覆盖 propose / accept / reject / abort 四个阶段，两 facade 的这八个方法签名逐字相同。

> 注意："无 LLM"仅指**协商报文生成环节**。SDK 整体配置（`A2AT_LLM_API_KEY` 等）始终必填，其他环节（Task-T 槽位提取、语义校验）仍调用 LLM。

### 3.1 fromData 生成（确定性，协商报文生成不调 LLM）

```java
MetadataContent generateNegotiationProposePromptFromData(NegotiationProposeData data, String templateUri);
MetadataContent generateNegotiationAcceptPromptFromData (NegotiationEndingData  data, String templateUri);
MetadataContent generateNegotiationRejectPromptFromData (NegotiationEndingData  data, String templateUri);
MetadataContent generateNegotiationAbortPromptFromData  (NegotiationAbortData   data, String templateUri);
```

流程：解析 `templateUri` → 加载模板 → `GeneratorRegistry` 精确类型匹配 dispatch → Generator 渲染 → 返回 `MetadataContent`。

- accept 要求内容 conclusion 必须为 `ACCEPT`，reject 必须为 `REJECT`；结论不匹配时抛 `NegotiationGenerationException`，错误码为 `negotiation.conclusion_mismatch`
- abort 与协商类型无关，由唯一的 common abort 模板渲染，只需携带终止原因（见 §3.4）

### 3.2 fromText 生成（含一步 LLM 抽取）

```java
MetadataContent generateNegotiationProposePromptFromText(String text, NegotiationContext context, String templateUri);
MetadataContent generateNegotiationAcceptPromptFromText (String text, NegotiationContext context, String templateUri);
MetadataContent generateNegotiationRejectPromptFromText (String text, NegotiationContext context, String templateUri);
MetadataContent generateNegotiationAbortPromptFromText  (String text, NegotiationContext context, String templateUri);
```

注意此处的 `NegotiationContext` 是内容生成用的（`net.openan.a2at.sdk.core.model.NegotiationContext`，见 §6），通过 `MetadataContent.negotiationContext()` 和嵌套 `negotiationContext` metadata 与消息一起返回，不由 LLM 推断。

流程：解析 `templateUri` → 加载模板（LLM 调用前先加载）→ `DefaultNegotiationContentExtractor` 一步 LLM 结构化抽取 → `mapContent` 映射到类型化 record → 渲染 → 返回 `MetadataContent`。

### 3.3 返回值 MetadataContent

```java
record MetadataContent(
    String templateUri,    // 模板 URI
    String promptText,     // 渲染后的消息文本
    String extensionUri,   // TMF 扩展 URI（规范 Negotiation-T URI）
    NegotiationContext negotiationContext // 非协商消息为 null
)
```

直接生成 A2A-T metadata map：

```java
MetadataContent mc = client.generateNegotiationProposePromptFromData(data, StandardTemplates.TARGET_NEGOTIATION_PROPOSE_URI);
Map<String, Object> metadata = mc.buildMetadataContent();
// { extensionUri -> promptText, "templateUri" -> templateUri, "negotiationContext" -> contextMap }
```

### 3.4 输入数据模型

**Propose 阶段**

```java
record NegotiationProposeData(
    NegotiationContext context,           // 内容生成 context
    NegotiationProposeContent content     // 类型化内容
)
```

**Ending 阶段（accept / reject）**

```java
record NegotiationEndingData(
    NegotiationContext context,
    NegotiationEndingContent content
)
```

**Abort 阶段（协商终止）**

```java
record NegotiationAbortData(
    NegotiationContext context,
    NegotiationAbortContent content       // 仅携带终止原因
)
```

### 3.5 类型化内容模型

**Propose 阶段内容**（sealed 接口 `NegotiationProposeContent`）

```java
// 信息协商：缺失信息项列表
record InformationProposeContent(
    List<NegotiationItem> items,   // 必填，至少一项
    String relationship            // 可选，项之间关系描述
)

// 目标协商：目标描述 + 意图理解 + 对齐澄清 + 待澄清 + 确认请求
record TargetProposeContent(
    String targetNegotiationDescription,               // 必填
    List<NegotiationItem> intentUnderstanding,          // 可选（首轮有，后续 null）
    List<NegotiationItem> alignmentAndClarification,    // 可选（后续轮有，首轮 null）
    List<NegotiationItem> requestForClarification,      // 可选
    String targetConfirmRequest                         // 可选，请求对端确认的文本
)

// 可行性协商：描述 + 动作 + 评估内容 / 不可行详情 + 确认请求
record FeasibilityProposeContent(
    String feasibilityNegotiationDescription,               // 必填
    NegotiationAction action,                                // 必填，选择条件段
    List<NegotiationItem> contentsToEvaluate,                // action=REQUEST_FEASIBILITY_EVALUATION 时必填
    List<NegotiationItem> infeasibilityDetailsAndProposal,   // action=PROPOSE_ALTERNATIVE_ON_FAILURE 时必填
    String feasibilityConfirmRequest                         // 可选，请求对端确认的文本
)
```

**Ending 阶段内容**（sealed 接口 `NegotiationEndingContent`）

```java
record InformationEndingContent(NegotiationConclusion conclusion, List<NegotiationItem> items)

record TargetEndingContent(NegotiationConclusion conclusion,
                           String confirmedIntent,   // accept 时必填
                           String failureReason)     // reject 时必填

record FeasibilityEndingContent(NegotiationConclusion conclusion, String feasibilitySummary)
```

**Abort 阶段内容**

```java
record NegotiationAbortContent(String terminationReason)   // 必填，协商终止原因
```

### 3.6 辅助类型

```java
record NegotiationItem(String name, String value)  // name 非空，value 可空

enum NegotiationConclusion { ACCEPT, REJECT, ABORT }
enum NegotiationAction {
    REQUEST_FEASIBILITY_EVALUATION,
    PROPOSE_ALTERNATIVE_ON_FAILURE
}
```

accept 与 reject 共享 `accept-reject` 模板，由 `NegotiationConclusion` 值区分；ABORT 使用独立的 common abort 模板（见[模板 URI](#四模板-uri)）。

---

## 四、模板 URI

类型化模板格式：`Negotiation-T/{type-segment}/{phase-segment}/v1`；abort 与类型无关，使用 common 模板。

| 场景 | URI |
|---|---|
| 信息协商提议 | `Negotiation-T/information-negotiation/propose/v1` |
| 信息协商接受/拒绝 | `Negotiation-T/information-negotiation/accept-reject/v1` |
| 目标协商提议 | `Negotiation-T/target-negotiation/propose/v1` |
| 目标协商接受/拒绝 | `Negotiation-T/target-negotiation/accept-reject/v1` |
| 可行性协商提议 | `Negotiation-T/feasibility-negotiation/propose/v1` |
| 可行性协商接受/拒绝 | `Negotiation-T/feasibility-negotiation/accept-reject/v1` |
| 协商终止（通用） | `Negotiation-T/common/abort/v1` |

模板 URI 层把四个 performative 归并为三段：`ACCEPT` 与 `REJECT` 共享 `accept-reject` 段（由 conclusion 值区分），`ABORT` 由类型无关的 common 模板承载。类型化模板对应 `StandardTemplates` 中的常量（对外接口使用 `INFORMATION_NEGOTIATION_PROPOSE_URI`），abort 对应 `StandardTemplates.NEGOTIATION_ABORT_URI`。

URI 解析规则（`NegotiationReference.tryParse`）：恰好 4 个 slash 分隔段；第 1 段必须为 `Negotiation-T`；第 2 段为 `{type}-negotiation` 或 `common`（common 仅用于 abort）；第 3 段必须为 `propose` 或 `accept-reject`，abort 模板为 `abort`；第 4 段必须为 `v1`。

## 五、校验与参数提取 API

### 5.1 方法签名

```java
FilledParamData validateProposePromptAndDataFilling(String prompt, NegotiationContext context, Map<String, Object> schema, String templateUri);
FilledParamData validateAcceptPromptAndDataFilling (String prompt, NegotiationContext context, Map<String, Object> schema, String templateUri);
FilledParamData validateRejectPromptAndDataFilling (String prompt, NegotiationContext context, Map<String, Object> schema, String templateUri);
FilledParamData validateAbortPromptAndDataFilling  (String prompt, NegotiationContext context, Map<String, Object> schema, String templateUri);
```

- `prompt`：已渲染的协商消息文本
- `context`：内容生成用的 `NegotiationContext`（见 §6），参与规则门校验并合并进返回数据
- `schema`：调用方提供的参数 JSON schema，描述要提取的参数
- `templateUri`：声明预期的协商类型和阶段（phase 必须与方法匹配；abort 方法用 common abort 模板）

返回：

```java
record FilledParamData(Map<String, Object> data)
```

### 5.2 校验管线

1. **规则门**（`DefaultNegotiationComplianceChecker`，确定性，无 LLM）
   - 校验随 metadata 单独传入的 `NegotiationContext`，不从 prompt 正文解析上下文段
   - 校验 `id` 为 UUID 格式（8-4-4-4-12 十六进制）
   - 校验 `round` 和 `maxRounds` 为正整数，且 round 不超 maxRounds
   - 不做类型推断、conclusion 值校验或条件段互斥
   - 成功返回 context 参数 `{id, round, maxRounds}`
2. **LLM 语义校验**（`DefaultNegotiationSemanticValidator`，可重试）
   - 一步结构化 LLM 调用，同时产出 verdict（通过/拒绝）、implied type、语义错误列表、按 callerSchema 提取的参数
   - verdict 为 true 时强制校验 implied type 与 reference type 一致
3. **参数合并**（确定性）
   - 先写 context 参数（id、round、maxRounds），再写 LLM 提取参数
   - 冲突时 context 参数优先，并记录 warning

---

## 六、会话上下文

内容 API 使用 `net.openan.a2at.sdk.core.model.NegotiationContext`，字段为 `id`、`round`、`maxRounds`、`performative`。它是不可变 record，提供 `of(id, round, performative)`、`nextRound()`、`isExhausted()`，`DEFAULT_MAX_ROUNDS = 5`。

调用方负责会话存储和消息路由。校验及响应生成均使用从入站 `negotiationContext` metadata 解码取得的 context。生成操作在出站 context 上标记自身的 performative，不修改调用者传入的 context。accept/reject/abort 响应保留收到的 ID 和 round；只有新提议才调用 `nextRound()`。SDK 不另外维护会话 ID、运行时状态机或持久化存储。

---

## 七、异常与错误码

### 7.1 异常分类

| 异常类型 | 性质 | 携带信息 |
|---|---|---|
| `IllegalArgumentException` / `NullPointerException` | 编程错误 | 指出非法或缺失参数的 message |
| `NegotiationGenerationException` | 生成失败 | code + message + facts + cause |
| `NegotiationParamExtractionException` | 校验失败 | code + message + facts + errors（逐 slot 错误） |

### 7.2 错误码

| 码 | 含义 | 可重试 |
|---|---|---|
| `template.not_found` | 模板/prompt 资源缺失 | 否 |
| `negotiation.content_extract_failed` | LLM 内容抽取响应不可解析 | 是 |
| `llm.invocation_failed` | LLM 调用基础设施失败 | 是 |
| `llm.response_invalid` | LLM 响应结构无效 | 是 |
| `negotiation.field_missing` | 必填字段缺失 | 否 |
| `negotiation.invalid_input` | 输入与 phase/action 矛盾 | 否 |
| `negotiation.conclusion_mismatch` | accept/reject 操作与内容结论不匹配 | 否 |
| `negotiation.rule_violation` | 规则门校验失败 | 否 |
| `negotiation.semantic_rejected` | 语义校验拒绝 | 否 |
| `input.text_too_long` | fromText 输入超出配置的最大长度 | 否 |

### 7.3 重试策略

`NegotiationGenerationOrchestrator.withRetry` 对 LLM 步骤统一重试：`negotiation.content_extract_failed`、`llm.invocation_failed` 和 `llm.response_invalid` 可重试，其他错误码立即抛出；耗尽后原样重抛（保留原始 error code）。`A2AT_LLM_MAX_ATTEMPTS` 默认 3，取值限定为 1–10。

---

## 八、使用示例

`NegotiationContext` 均指 `net.openan.a2at.sdk.core.model.NegotiationContext`。调用方负责会话存储、消息传输和路由；生成方法通过 metadata 返回出站 context。

### 8.1 使用内容 API 生成提议

```java
A2ATClient client = new A2ATClient(Path.of(".env"));

NegotiationContext contentCtx = NegotiationContext.of(
    UUID.randomUUID().toString(), 1, NegotiationPerformative.PROPOSE);
TargetProposeContent content = new TargetProposeContent(
    "跨城市专线中断诊断",
    List.of(new NegotiationItem("intent", "恢复城市1到城市2的SPN专线")),
    null,
    List.of(new NegotiationItem("city1_omc", "请提供城市1 OMC告警详情")),
    null);
MetadataContent mc = client.generateNegotiationProposePromptFromData(
    new NegotiationProposeData(contentCtx, content),
    StandardTemplates.TARGET_NEGOTIATION_PROPOSE_URI);
Map<String, Object> metadata = mc.buildMetadataContent();
```

将 `metadata` 随 A2A 消息发送，其中包含规范扩展 URI 对应的渲染文本、`templateUri` 和 `negotiationContext`。调用方跟踪此会话的 `mc.negotiationContext()`。校验及响应此提议时，使用同一份会话 context。

### 8.2 校验收到的提议

此处的 `message`、`receivedContext` 和 `receivedTemplateUri` 由传输集成从**同一条入站消息的 metadata** 解码取得。`receivedContext` 保留收到的 `id`、`round`、`maxRounds`、`performative`，不要用新建的本地会话 context 替代。示例接收 10.1 中的目标协商提议。

```java
Map<String, Object> schema = Map.of(
    "type", "object",
    "properties", Map.of(
        "city1_omc_alarm", Map.of("type", "string"),
        "optical_power", Map.of("type", "number")));
FilledParamData params = client.validateProposePromptAndDataFilling(
    message, receivedContext, schema, receivedTemplateUri);
// params.data() 含 id, round, maxRounds + 提取参数
```

校验通过后，由业务决定接受、拒绝或发起下一轮提议。

### 8.3 保持收到的轮次生成同意响应

业务接受目标协商提议后，使用其入站 context 生成响应：

```java
MetadataContent accepted = client.generateNegotiationAcceptPromptFromData(
    new NegotiationEndingData(receivedContext,
        new TargetEndingContent(NegotiationConclusion.ACCEPT, "确认意图：恢复专线", null)),
    StandardTemplates.TARGET_NEGOTIATION_ACCEPT_REJECT_URI);
Map<String, Object> metadata = accepted.buildMetadataContent();
```

响应保留收到的会话 ID 和 round，生成方法将出站 context 的 performative 标为 `ACCEPT`。只有发起新提议才通过 `nextRound()` 推进轮次，并受当前会话的轮次预算约束。

### 8.4 从自由文本生成协商消息

```java
NegotiationContext contentCtx = NegotiationContext.of(
    UUID.randomUUID().toString(), 1, NegotiationPerformative.PROPOSE);
MetadataContent mc = client.generateNegotiationProposePromptFromText(
    "城市1到城市2的SPN专线中断，城市1 OMC告警端口Down，光功率-28dBm",
    contentCtx,
    StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE_URI);
// LLM 自动抽取 InformationProposeContent（items + relationship）并渲染
```

### 8.5 生成可选的结束响应

以下是不同的响应选择，不应按顺序全部发送。每种响应均使用被回复提议的 context。

```java
// 接受
MetadataContent acceptMc = client.generateNegotiationAcceptPromptFromData(
    new NegotiationEndingData(receivedContext,
        new TargetEndingContent(NegotiationConclusion.ACCEPT, "确认意图：恢复专线", null)),
    StandardTemplates.TARGET_NEGOTIATION_ACCEPT_REJECT_URI);

// 拒绝
MetadataContent rejectMc = client.generateNegotiationRejectPromptFromData(
    new NegotiationEndingData(receivedContext,
        new TargetEndingContent(NegotiationConclusion.REJECT, null, "资源不足无法执行")),
    StandardTemplates.TARGET_NEGOTIATION_ACCEPT_REJECT_URI);

// 终止协商
MetadataContent abortMc = client.generateNegotiationAbortPromptFromData(
    new NegotiationAbortData(receivedContext,
        new NegotiationAbortContent("双方无法就诊断范围达成一致，终止协商")),
    StandardTemplates.NEGOTIATION_ABORT_URI);
```

可运行样例见 [a2a-t-sample 协商样例](../../a2a-t-sample/README.zh-CN.md#协商negotiation端到端样例)。

---

## 九、当前支持范围与局限

| 能力 | 状态 |
|---|---|
| 协商运行时状态机（start/receive/continue） | 完整，三种类型均注册 |
| 协商内容生成（fromData/fromText） | 完整，8 个公开生成方法覆盖三类协商与 propose/accept/reject/abort |
| 协商校验参数提取（validateAndFilling） | 完整，propose/accept/reject/abort 四个阶段均支持 |
| 会话状态 | 随消息 metadata 携带；持久化和生命周期由调用方管理 |
| 语言覆盖 | zh-CN 和 en-US |
