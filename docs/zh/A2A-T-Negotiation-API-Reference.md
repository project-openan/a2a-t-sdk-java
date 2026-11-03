# A2A-T 协商（Negotiation-T）API 文档

> 本文是 [A2A-T-SDK-API-Reference.md](A2A-T-SDK-API-Reference.md) 的协商分册，专项覆盖协商扩展的对外 API，便于按能力域查阅。

---

## 一、能力概览

协商扩展（Negotiation-T）让 client 与 server 在执行任务前对齐参数：信息收集、目标对齐、可行性确认，以及协商终止。SDK 把协商能力拆成三层对外暴露：

| 层 | 职责 | 入口 |
|---|---|---|
| 运行时状态机 | 管理 turn/round 推进、状态校验、上下文持久化 | `startNegotiation` / `receiveNegotiation` / `continueNegotiation` |
| 内容生成 | 按类型化内容渲染协商消息（fromData 确定性、fromText 含一步 LLM 抽取） | `generateNegotiation*PromptFromData/FromText` |
| 校验与参数提取 | 规则门 + 语义校验 + 参数合并，从已渲染消息提取结构化参数 | `validate*PromptAndDataFilling` |

三层通过 `A2ATClient` / `A2ATServer` 两个 facade 暴露，**方法签名完全对称**（见 [NegotiationV3ApiSurfaceTest](../../a2a-t-sample/src/test/java/net/openan/a2at/sdk/sample/api/NegotiationV3ApiSurfaceTest.java)）。差异仅在角色绑定与 server 额外的 prompt 合规校验。

新集成建议使用内容生成与校验 API，由调用方管理 `core.model.NegotiationContext`，通过 A2A 传输层发送返回的 metadata。运行时状态机是另一套 API：`startNegotiation()` 会创建自己的会话 ID 和 payload，不会自动关联内容层 context。参见[内容 API 示例](#101-使用内容-api-生成提议)与[独立状态机示例](#106-独立使用运行时状态机)。

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
| A2AT_NEGOTIATION_STATE_STORE_TYPE | in_memory | 协商状态存储类型，当前仅 `in_memory` |

---

## 二、扩展 URI

定义在 [ExtensionUriConstants](../../a2a-t-core/src/main/java/net/openan/a2at/sdk/core/model/ExtensionUriConstants.java)：

| 用途 | URI |
|---|---|
| Negotiation-T（内容 metadata） | `https://projects.tmforum.org/a2aproject/telecommunication/extensions/Negotiation-T/v1` |
| Negotiation-T NL（运行时 payload） | `https://projects.tmforum.org/a2aproject/telecommunication/extensions/Negotiation-T/NL/v1` |

内容生成返回的 `MetadataContent.extensionUri()` 使用规范 URI；运行时 `startNegotiation()` / `continueNegotiation()` 返回的 payload 则以 NL URI 为顶层扩展 key。`NegotiationPayloadMapper.extractContextMap()` 接收这两种 URI 的运行时 payload。URI 兼容不代表会自动转换内容 metadata 与运行时 payload，也不会同步两种 context。

---

## 三、运行时状态机 API

Client 与 Server 均提供以下三个方法，驱动协商状态机的 start/receive/continue 流程。它们操作 `types.model` 层的 `NegotiationContext`（非内容层 context，两者不可混用，见 §7.1）。

### 3.1 startNegotiation —— 发起协商

```java
Map<String, Object> startNegotiation(
    NegotiationType type,
    String contentText,
    Map<String, Object> facts)
```

- `type`：协商类型（`INFORMATION` / `TARGET` / `FEASIBILITY`）
- `contentText`：协商消息文本
- `facts`：附带的结构化事实，非空时进入 payload 顶层 `facts`

生成 UUID 作为 `negotiationId`，创建 round=1、status=`in-progress` 的 context，存储后返回首轮 payload（示例的 `facts` 为空，因此省略该 key）：

```json
{
  "https://.../Negotiation-T/NL/v1": {
    "message": "协商消息文本",
    "negotiationType": "target",
    "negotiationId": "uuid",
    "round": 1,
    "status": "in-progress",
    "extra": {}
  }
}
```

> 启动不做状态校验，不抛状态异常。

### 3.2 receiveNegotiation —— 接收对端协商消息

```java
Map<String, Object> receiveNegotiation(
    String message,
    Map<String, Object> context)
```

- `message`：接收到的协商消息文本
- `context`：对端发来的 transport context payload（即 payload 中扩展 URI 下的那层 map）

先用 `NegotiationPayloadMapper.contextFromMap` 反序列化 context，再执行状态校验（顺序固定）：

1. 回退校验：incoming round < stored round 时拒绝
2. 终态重开：stored 已终态且 incoming 为 in-progress 时拒绝
3. 终态变更：stored 已终态且 incoming status 不同时拒绝
4. 最大轮次：in-progress 且 round >= 8 时返回「已达最大轮次，请拒绝」建议（不更新 store）
5. 轮次跳跃：incoming round > stored round + 1 时拒绝

通过后按 type 分发到 handler，返回：

```json
{
  "needResponse": true,
  "facts": {},
  "message": "处理后的消息",
  "context": {
    "negotiationType": "...",
    "negotiationId": "...",
    "round": 1,
    "status": "in-progress",
    "extra": {}
  }
}
```

### 3.3 continueNegotiation —— 本地推进协商

```java
Map<String, Object> continueNegotiation(
    NegotiationContext context,
    NegotiationStatus status,
    String contentText)
```

- `context`：当前协商上下文快照（必须与本地存储完全匹配，防止分支分裂）
- `status`：下一轮状态（`IN_PROGRESS` / `AGREED` / `REJECTED`）
- `contentText`：下一轮消息内容

校验：stored record 必须存在，传入 context 必须与存储的 context 相等，stored status 仍为 in-progress。通过后 round+1 并存储新 record。下一状态可以为 `IN_PROGRESS`（继续协商）、`AGREED` 或 `REJECTED`；已处于终态的协商不能再继续。handler 向 mapper 传入空 facts，因此返回的 payload **省略 `facts` key**。

### 3.4 状态机枚举

```java
enum NegotiationType   { INFORMATION, TARGET, FEASIBILITY }
enum NegotiationStatus { IN_PROGRESS, AGREED, REJECTED }
enum NegotiationRole   { CLIENT, SERVER }
```

payload 中 type/status 取枚举名小写、下划线转连字符（如 `in-progress`）；反序列化时连字符转下划线再大写还原。

---

## 四、内容生成 API

Client 与 Server 均通过 `NegotiationContentService` 代理 `NegotiationGenerationOrchestrator`。每条流水线都有 `fromData`（确定性，协商报文生成不调 LLM）和 `fromText`（含一步 LLM 结构化抽取）两个变体，覆盖 propose / accept / reject / abort 四个阶段，两 facade 的这八个方法签名逐字相同。

> 注意："无 LLM"仅指**协商报文生成环节**。SDK 整体配置（`A2AT_LLM_API_KEY` 等）始终必填，其他环节（Task-T 槽位提取、语义校验）仍调用 LLM。

### 4.1 fromData 生成（确定性，协商报文生成不调 LLM）

```java
MetadataContent generateNegotiationProposePromptFromData(NegotiationProposeData data, String templateUri);
MetadataContent generateNegotiationAcceptPromptFromData (NegotiationEndingData  data, String templateUri);
MetadataContent generateNegotiationRejectPromptFromData (NegotiationEndingData  data, String templateUri);
MetadataContent generateNegotiationAbortPromptFromData  (NegotiationAbortData   data, String templateUri);
```

流程：解析 `templateUri` → 加载模板 → `GeneratorRegistry` 精确类型匹配 dispatch → Generator 渲染 → 返回 `MetadataContent`。

- accept 要求内容 conclusion 必须为 `ACCEPT`，reject 必须为 `REJECT`，否则抛 `IllegalArgumentException`
- abort 与协商类型无关，由唯一的 common abort 模板渲染，只需携带终止原因（见 §4.4）

### 4.2 fromText 生成（含一步 LLM 抽取）

```java
MetadataContent generateNegotiationProposePromptFromText(String text, NegotiationContext context, String templateUri);
MetadataContent generateNegotiationAcceptPromptFromText (String text, NegotiationContext context, String templateUri);
MetadataContent generateNegotiationRejectPromptFromText (String text, NegotiationContext context, String templateUri);
MetadataContent generateNegotiationAbortPromptFromText  (String text, NegotiationContext context, String templateUri);
```

注意此处的 `NegotiationContext` 是内容生成用的（`net.openan.a2at.sdk.core.model.NegotiationContext`，见 §7.1），通过 `MetadataContent.negotiationContext()` 和嵌套 `negotiationContext` metadata 与消息一起返回，不由 LLM 推断。

流程：解析 `templateUri` → 加载模板（LLM 调用前先加载）→ `DefaultNegotiationContentExtractor` 一步 LLM 结构化抽取 → `mapContent` 映射到类型化 record → 渲染 → 返回 `MetadataContent`。

### 4.3 返回值 MetadataContent

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

### 4.4 输入数据模型

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

### 4.5 类型化内容模型

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

### 4.6 辅助类型

```java
record NegotiationItem(String name, String value)  // name 非空，value 可空

enum NegotiationConclusion { ACCEPT, REJECT, ABORT }
enum NegotiationAction {
    REQUEST_FEASIBILITY_EVALUATION,
    PROPOSE_ALTERNATIVE_ON_FAILURE
}
```

accept 与 reject 共享 `accept-reject` 模板，由 `NegotiationConclusion` 值区分；ABORT 使用独立的 common abort 模板（见 §5）。

---

## 五、模板 URI

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

### 5.1 模板查询 API

```java
List<PromptTemplate>       getPrompts();                         // 跨所有扩展
Optional<PromptTemplate>   getPrompt(String uri);                // 跨扩展按 URI 查
```

对于格式正确的模板 URI，缺失返回空列表或 `Optional.empty()`。URI 为 null 时抛 `NullPointerException`，空白或格式错误时抛 `IllegalArgumentException`。

---

## 六、校验与参数提取 API

### 6.1 方法签名

```java
FilledParamData validateProposePromptAndDataFilling(String prompt, NegotiationContext context, Map<String, Object> schema, String templateUri);
FilledParamData validateAcceptPromptAndDataFilling (String prompt, NegotiationContext context, Map<String, Object> schema, String templateUri);
FilledParamData validateRejectPromptAndDataFilling (String prompt, NegotiationContext context, Map<String, Object> schema, String templateUri);
FilledParamData validateAbortPromptAndDataFilling  (String prompt, NegotiationContext context, Map<String, Object> schema, String templateUri);
```

- `prompt`：已渲染的协商消息文本
- `context`：内容生成用的 `NegotiationContext`（见 §7.1），参与规则门校验并合并进返回数据
- `schema`：调用方提供的参数 JSON schema，描述要提取的参数
- `templateUri`：声明预期的协商类型和阶段（phase 必须与方法匹配；abort 方法用 common abort 模板）

返回：

```java
record FilledParamData(Map<String, Object> data)
```

### 6.2 校验管线

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

## 七、两套 Context 与 Payload 工具

### 7.1 两套 NegotiationContext（不可混用）

| 类 | 用途 | 字段 |
|---|---|---|
| `net.openan.a2at.sdk.core.model.NegotiationContext` | 内容生成/校验 | `id, round, maxRounds, performative` |
| `net.openan.a2at.sdk.negotiation.types.model.NegotiationContext` | 状态机 | `negotiationType, negotiationId, round, status` |

状态机方法（start/receive/continue）用 `types.model` 层的；内容生成/校验用 `core.model` 层的。内容生成 context 是 record，带 `of(id, round, performative)`、`nextRound()`、`isExhausted()`，`DEFAULT_MAX_ROUNDS = 5`。生成操作在返回的 metadata context 上标记自身的 performative，不修改调用者传入的 context。accept/reject/abort 响应保留收到的 id 和 round；只有发起新一轮提案时才调用 `nextRound()`。

### 7.2 NegotiationPayloadMapper

```java
// 从 payload map 反序列化为状态机 context
NegotiationContext ctx = NegotiationPayloadMapper.contextFromMap(contextMap);

// 把状态机 context 序列化为 payload map（含 message/type/id/round/status/extra）
Map<String, Object> payload = NegotiationPayloadMapper.contextPayload(ctx);

// 从完整 payload 中提取协商 context map（兼容规范 URI 和 NL 别名）
Map<String, Object> contextMap = NegotiationPayloadMapper.extractContextMap(payload);

// 构建完整协商 payload（扩展 URI 为顶层 key，facts 非空时放顶层）
Map<String, Object> payload = NegotiationPayloadMapper.payload(context, contentText, facts);
```

---

## 八、异常与错误码

### 8.1 异常分类

| 异常类型 | 性质 | 携带信息 |
|---|---|---|
| `IllegalArgumentException` / `NullPointerException` | 编程错误 | 指出非法或缺失参数的 message |
| `NegotiationGenerationException` | 生成失败 | code + message + facts + cause |
| `NegotiationParamExtractionException` | 校验失败 | code + message + facts + errors（逐 slot 错误） |
| `NegotiationStateException` | 状态机错误 | message（运行时状态机层） |

### 8.2 错误码

| 码 | 含义 | 可重试 |
|---|---|---|
| `template.not_found` | 模板/prompt 资源缺失 | 否 |
| `negotiation.content_extract_failed` | LLM 内容抽取响应不可解析 | 是 |
| `llm.invocation_failed` | LLM 调用基础设施失败 | 是 |
| `llm.response_invalid` | LLM 响应结构无效 | 是 |
| `negotiation.field_missing` | 必填字段缺失 | 否 |
| `negotiation.invalid_input` | 输入与 phase/action 矛盾 | 否 |
| `negotiation.rule_violation` | 规则门校验失败 | 否 |
| `negotiation.semantic_rejected` | 语义校验拒绝 | 否 |
| `input.text_too_long` | fromText 输入超出配置的最大长度 | 否 |

### 8.3 重试策略

`NegotiationGenerationOrchestrator.withRetry` 对 LLM 步骤统一重试：`negotiation.content_extract_failed`、`llm.invocation_failed` 和 `llm.response_invalid` 可重试，其他错误码立即抛出；耗尽后原样重抛（保留原始 error code）。`A2AT_LLM_MAX_ATTEMPTS` 默认 3，取值限定为 1–10。

---

## 九、状态存储

```java
public interface NegotiationStore {
    NegotiationRecord get(String negotiationId);
    void save(NegotiationRecord record);   // negotiationId 不可为空
    void delete(String negotiationId);
    boolean cleanupExpired();
}
```

当前只有 `InMemoryNegotiationStore`，不保证持久化。可通过 `NegotiationHandler.Builder.store()` 注入自定义实现。

---

## 十、使用示例

10.1–10.5 使用内容 API，`NegotiationContext` 均指 `net.openan.a2at.sdk.core.model.NegotiationContext`。调用方负责会话存储、消息传输和路由；生成方法通过 metadata 返回出站 context。运行时 API 的独立示例见 10.6。

### 10.1 使用内容 API 生成提议

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

将 `metadata` 随 A2A 消息发送，其中包含规范扩展 URI 对应的渲染文本、`templateUri` 和 `negotiationContext`。调用方跟踪此会话的 `mc.negotiationContext()`。这条路径无需调用运行时 `startNegotiation()`，也无需创建第二个会话 ID。

### 10.2 校验收到的提议

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

### 10.3 保持收到的轮次生成同意响应

业务接受目标协商提议后，使用其入站 context 生成响应：

```java
MetadataContent accepted = client.generateNegotiationAcceptPromptFromData(
    new NegotiationEndingData(receivedContext,
        new TargetEndingContent(NegotiationConclusion.ACCEPT, "确认意图：恢复专线", null)),
    StandardTemplates.TARGET_NEGOTIATION_ACCEPT_REJECT_URI);
Map<String, Object> metadata = accepted.buildMetadataContent();
```

响应保留收到的会话 ID 和 round，生成方法将出站 context 的 performative 标为 `ACCEPT`。只有发起新提议才通过 `nextRound()` 推进轮次，并受当前会话的轮次预算约束。

### 10.4 从自由文本生成协商消息

```java
NegotiationContext contentCtx = NegotiationContext.of(
    UUID.randomUUID().toString(), 1, NegotiationPerformative.PROPOSE);
MetadataContent mc = client.generateNegotiationProposePromptFromText(
    "城市1到城市2的SPN专线中断，城市1 OMC告警端口Down，光功率-28dBm",
    contentCtx,
    StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE_URI);
// LLM 自动抽取 InformationProposeContent（items + relationship）并渲染
```

### 10.5 生成可选的结束响应

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

### 10.6 独立使用运行时状态机

本例独立使用运行时 payload 契约，与上面的内容 API 示例分开。接收方 facade 先保存收到的运行时 context，再基于该快照继续协商。`IN_PROGRESS` 表示在下一轮继续保持协商进行中。

```java
A2ATClient client = new A2ATClient(Path.of(".env"));
A2ATServer server = new A2ATServer(Path.of(".env"));
String message = "请确认诊断范围";
Map<String, Object> initialPayload = client.startNegotiation(
    NegotiationType.TARGET, message, Map.of());
Map<String, Object> contextMap = NegotiationPayloadMapper.extractContextMap(initialPayload);
server.receiveNegotiation(message, contextMap);
net.openan.a2at.sdk.negotiation.types.model.NegotiationContext runtimeContext =
    NegotiationPayloadMapper.contextFromMap(contextMap);
Map<String, Object> nextPayload = server.continueNegotiation(
    runtimeContext, NegotiationStatus.IN_PROGRESS, "请补充告警详情");
```

返回 payload 使用 NL 扩展 URI，facts 为空时省略该 key。会话 ID 与轮次推进由这套状态机管理；payload 不是 `MetadataContent` map，不能与独立生成的内容 context 当作同一个会话直接拼接。Target handler 仅回显消息并标记是否需要响应，不负责生成协商内容或语义校验。

可运行样例见 [a2a-t-sample 协商样例](../../a2a-t-sample/README.zh-CN.md#协商negotiation端到端样例)。

---

## 十一、当前支持范围与局限

| 能力 | 状态 |
|---|---|
| 协商运行时状态机（start/receive/continue） | 完整，三种类型均注册 |
| 协商内容生成（fromData/fromText） | 完整，8 个公开生成方法覆盖三类协商与 propose/accept/reject/abort |
| 协商校验参数提取（validateAndFilling） | 完整，propose/accept/reject/abort 四个阶段均支持 |
| Information 运行时 handler | 有实质逻辑（含 compliance checker） |
| Target / Feasibility 运行时 handler | 空壳 echo（内容层已完整，运行时未对接） |
| 状态持久化 | 仅 in-memory |
| 语言覆盖 | zh-CN 和 en-US |
