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

### 1.1 Maven 坐标

```xml
<dependency>
    <groupId>net.openan.a2a-t.sdk</groupId>
    <artifactId>a2a-t-client</artifactId>
    <version>1.0.0</version>
</dependency>
```

Server 侧用 `a2a-t-server` 替代。协商模块由二者传递依赖引入，无需单独加依赖。

### 1.2 协商相关 `.env` 配置项

| 配置项 | 默认值 | 说明 |
|---|---|---|
| A2AT_LANGUAGE | en-US | 消息语言，`zh-CN` 或 `en-US` |
| A2AT_PROMPT_SOURCE_TYPE | local_file | `classpath` 用打包资源，`local_file` 用本地目录 |
| A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR | （打包目录） | 本地资源根目录，相对路径基于 .env 所在目录 |
| A2AT_LLM_PROVIDER | （必填） | 仅支持 `openai`（兼容 DeepSeek、Azure 等） |
| A2AT_LLM_MODEL | （必填） | 模型名 |
| A2AT_LLM_API_KEY | （必填） | API key |
| A2AT_LLM_BASE_URL | （可选） | OpenAI 兼容 API 的 base URL |
| A2AT_NEGOTIATION_STATE_STORE_TYPE | in_memory | 协商状态存储类型，当前仅 `in_memory` |

---

## 二、扩展 URI

定义在 [ExtensionUriConstants](../../a2a-t-core/src/main/java/net/openan/a2at/sdk/core/model/ExtensionUriConstants.java)：

| 用途 | URI |
|---|---|
| Negotiation-T（规范，发送用） | `https://projects.tmforum.org/a2aproject/telecommunication/extensions/Negotiation-T/v1` |
| Negotiation-T（NL legacy，接收兼容） | `https://projects.tmforum.org/a2aproject/telecommunication/extensions/Negotiation-T/NL/v1` |

SDK 用规范 URI 作为 `MetadataContent.extensionUri()` 发送新消息；接收侧的 payload mapper 同时识别 NL 别名以兼容旧消息。

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

内部生成 UUID 作为 `negotiationId`，创建 round=1、status=`in-progress` 的 context，存入 store，返回初始轮 payload：

```json
{
  "https://.../Negotiation-T/NL/v1": {
    "message": "协商消息文本",
    "negotiationType": "target",
    "negotiationId": "uuid",
    "round": 1,
    "status": "in-progress",
    "extra": {}
  },
  "facts": {}
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

校验：stored record 存在、context round 与 stored round 一致、stored status 仍为 in-progress。通过后 round+1，存新 record，返回 payload。**facts 固定为空 map**。状态转换仅允许 in-progress → agreed / rejected；终态不可再变。

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
MetadataContent generateNegotiationProposePromptFromData(NegotiationProposeData data, TemplateUri templateUri);
MetadataContent generateNegotiationAcceptPromptFromData (NegotiationEndingData  data, TemplateUri templateUri);
MetadataContent generateNegotiationRejectPromptFromData (NegotiationEndingData  data, TemplateUri templateUri);
MetadataContent generateNegotiationAbortPromptFromData  (NegotiationAbortData   data, TemplateUri templateUri);
```

流程：解析 `templateUri` → 加载模板 → `GeneratorRegistry` 精确类型匹配 dispatch → Generator 渲染 → 返回 `MetadataContent`。

- accept 要求内容 conclusion 必须为 `ACCEPT`，reject 必须为 `REJECT`，否则抛 `NegotiationContentException`
- abort 与协商类型无关，由唯一的 common abort 模板渲染，只需携带终止原因（见 §4.4）

### 4.2 fromText 生成（含一步 LLM 抽取）

```java
MetadataContent generateNegotiationProposePromptFromText(String text, NegotiationContext context, TemplateUri templateUri);
MetadataContent generateNegotiationAcceptPromptFromText (String text, NegotiationContext context, TemplateUri templateUri);
MetadataContent generateNegotiationRejectPromptFromText (String text, NegotiationContext context, TemplateUri templateUri);
MetadataContent generateNegotiationAbortPromptFromText  (String text, NegotiationContext context, TemplateUri templateUri);
```

注意此处的 `NegotiationContext` 是内容生成用的（`net.openan.a2at.sdk.core.model.NegotiationContext`，见 §7.1），只注入到渲染消息里，不经过 LLM。

流程：解析 `templateUri` → 加载模板（LLM 调用前先加载）→ `DefaultNegotiationContentExtractor` 一步 LLM 结构化抽取 → `mapContent` 映射到类型化 record → 渲染 → 返回 `MetadataContent`。

### 4.3 返回值 MetadataContent

```java
record MetadataContent(
    String templateUri,    // 模板 URI
    String promptText,     // 渲染后的消息文本
    String extensionUri    // TMF 扩展 URI（规范 Negotiation-T URI）
)
```

直接生成 A2A-T metadata map：

```java
MetadataContent mc = client.generateNegotiationProposePromptFromData(data, StandardTemplates.TARGET_NEGOTIATION_PROPOSE);
Map<String, String> metadata = mc.buildMetadataContent();
// { extensionUri -> promptText, "template_uri" -> templateUri }
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

模板 URI 层把四个 performative 归并为三段：`ACCEPT` 与 `REJECT` 共享 `accept-reject` 段（由 conclusion 值区分），`ABORT` 由类型无关的 common 模板承载。类型化模板对应 `StandardTemplates` 中的常量（如 `INFORMATION_NEGOTIATION_PROPOSE`），abort 对应 `StandardTemplates.NEGOTIATION_ABORT`。

URI 解析规则（`NegotiationReference.parse`）：恰好 4 个 slash 分隔段；第 1 段必须为 `Negotiation-T`；第 2 段为 `{type}-negotiation` 或 `common`（common 仅用于 abort）；第 3 段必须为 `propose` 或 `accept-reject`，abort 模板为 `abort`；第 4 段必须为 `v1`。

### 5.1 模板查询 API

```java
List<PromptTemplate>       getNegotiationPrompts();              // 当前语言的全部协商模板，固定顺序
Optional<PromptTemplate>   getNegotiationPrompt(String uri);    // 按 URI 查单个
List<PromptTemplate>       getPrompts();                         // 跨所有扩展
Optional<PromptTemplate>   getPrompt(String uri);                // 跨扩展按 URI 查
```

这些方法不抛异常，缺失返回空列表或 empty。

---

## 六、校验与参数提取 API

### 6.1 方法签名

```java
FilledParamData validateProposePromptAndDataFilling(String prompt, NegotiationContext context, Map<String, Object> schema, TemplateUri templateUri);
FilledParamData validateAcceptPromptAndDataFilling (String prompt, NegotiationContext context, Map<String, Object> schema, TemplateUri templateUri);
FilledParamData validateRejectPromptAndDataFilling (String prompt, NegotiationContext context, Map<String, Object> schema, TemplateUri templateUri);
FilledParamData validateAbortPromptAndDataFilling  (String prompt, NegotiationContext context, Map<String, Object> schema, TemplateUri templateUri);
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
   - 按 `##` 分段，识别是否含协商上下文段（通过 `Vocabulary` 的 `section.context` 匹配）
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

状态机方法（start/receive/continue）用 `types.model` 层的；内容生成/校验用 `core.model` 层的。内容生成 context 是 record，带 `of(id, round, performative)`、`nextRound()`、`isExhausted()`，`DEFAULT_MAX_ROUNDS = 5`；`performative` 必填，表达该 context 随哪类消息出行（`PROPOSE` 提出提案，`ACCEPT` / `REJECT` 回应提案，`ABORT` 终止协商），生成 API 渲染出站消息时以此为准。

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
| `NegotiationContentException` | 编程错误 | message + field（出错字段路径） |
| `NegotiationGenerationException` | 生成失败 | code + message + cause |
| `NegotiationParamExtractionException` | 校验失败 | code + message + errors（逐 slot 错误） |
| `NegotiationStateException` | 状态机错误 | message（运行时状态机层） |

### 8.2 错误码

| 码 | 含义 | 可重试 |
|---|---|---|
| `template_not_found` | 模板/prompt 资源缺失 | 否 |
| `negotiation_content_extract_failed` | LLM 内容抽取响应不可解析 | 是 |
| `negotiation_llm_infrastructure_error` | LLM 调用基础设施失败 | 是 |
| `negotiation_slot_missing` | 必填字段缺失 | 否 |
| `negotiation_invalid_input` | 输入与 phase/action 矛盾 | 否 |
| `negotiation_rule_violation` | 规则门校验失败 | 否 |
| `negotiation_semantic_rejected` | 语义校验拒绝 | 否 |
| `input_text_too_long` | fromText 输入超出配置的最大长度 | 否 |

### 8.3 重试策略

`NegotiationGenerationOrchestrator.withRetry` 对 LLM 步骤统一重试：只有 `negotiation_content_extract_failed` 与 `negotiation_llm_infrastructure_error` 可重试，其他错误码立即抛出；耗尽后原样重抛（保留原始 error code）；默认 `maxAttempts` 来自 LLM config。

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

### 10.1 发起协商并生成提议消息

```java
A2ATClient client = new A2ATClient(Path.of(".env"));

// 1. 用内容生成引擎渲染结构化协商消息
NegotiationContext contentCtx = new NegotiationContext(
    UUID.randomUUID().toString(), 1,
    NegotiationContext.DEFAULT_MAX_ROUNDS, NegotiationPerformative.PROPOSE);
TargetProposeContent content = new TargetProposeContent(
    "跨城市专线中断诊断",
    List.of(new NegotiationItem("intent", "恢复城市1到城市2的SPN专线")),
    null,
    List.of(new NegotiationItem("city1_omc", "请提供城市1 OMC告警详情")),
    null);
MetadataContent mc = client.generateNegotiationProposePromptFromData(
    new NegotiationProposeData(contentCtx, content),
    StandardTemplates.TARGET_NEGOTIATION_PROPOSE);

// 2. 用渲染好的消息文本初始化状态机
Map<String, Object> payload = client.startNegotiation(
    NegotiationType.TARGET, mc.promptText(), Map.of("agent", "city1-agent"));

// 3. 合并 metadata
Map<String, String> metadata = mc.buildMetadataContent();
```

### 10.2 接收并校验协商消息

```java
Map<String, Object> receiveResult = client.receiveNegotiation(message, contextMap);

if (Boolean.TRUE.equals(receiveResult.get("needResponse"))) {
    Map<String, Object> schema = Map.of(
        "type", "object",
        "properties", Map.of(
            "city1_omc_alarm", Map.of("type", "string"),
            "optical_power", Map.of("type", "number")));
    FilledParamData params = client.validateProposePromptAndDataFilling(
        message, contentCtx, schema, StandardTemplates.TARGET_NEGOTIATION_PROPOSE);
    // params.data() 含 id, round, maxRounds + LLM 提取参数
}
```

### 10.3 推进协商到同意

```java
net.openan.a2at.sdk.negotiation.types.model.NegotiationContext ctx =
    NegotiationPayloadMapper.contextFromMap(
        (Map<String, Object>) receiveResult.get("context"));
Map<String, Object> payload = client.continueNegotiation(
    ctx, NegotiationStatus.AGREED, "参数已确认，开始执行");
```

### 10.4 从自由文本生成协商消息

```java
NegotiationContext contentCtx = NegotiationContext.of("session-123", 1, NegotiationPerformative.PROPOSE);
MetadataContent mc = client.generateNegotiationProposePromptFromText(
    "城市1到城市2的SPN专线中断，城市1 OMC告警端口Down，光功率-28dBm",
    contentCtx,
    StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE);
// LLM 自动抽取 InformationProposeContent（items + relationship）并渲染
```

### 10.5 生成终态消息

```java
// 接受
MetadataContent acceptMc = client.generateNegotiationAcceptPromptFromData(
    new NegotiationEndingData(contentCtx,
        new TargetEndingContent(NegotiationConclusion.ACCEPT, "确认意图：恢复专线", null)),
    StandardTemplates.TARGET_NEGOTIATION_ACCEPT_REJECT);

// 拒绝
MetadataContent rejectMc = client.generateNegotiationRejectPromptFromData(
    new NegotiationEndingData(contentCtx,
        new TargetEndingContent(NegotiationConclusion.REJECT, null, "资源不足无法执行")),
    StandardTemplates.TARGET_NEGOTIATION_ACCEPT_REJECT);

// 终止协商
MetadataContent abortMc = client.generateNegotiationAbortPromptFromData(
    new NegotiationAbortData(contentCtx,
        new NegotiationAbortContent("双方无法就诊断范围达成一致，终止协商")),
    StandardTemplates.NEGOTIATION_ABORT);
```

完整可运行样例见 [a2a-t-sample 协商样例](../../a2a-t-sample/README.zh-CN.md#协商negotiation样例)。

---

## 十一、当前支持范围与局限

| 能力 | 状态 |
|---|---|
| 协商运行时状态机（start/receive/continue） | 完整，三种类型均注册 |
| 协商内容生成（fromData/fromText） | 完整，三种类型 × propose/accept/reject/abort 八个生成器均实现 |
| 协商校验参数提取（validateAndFilling） | 完整，propose/accept/reject/abort 四个阶段均支持 |
| Information 运行时 handler | 有实质逻辑（含 compliance checker） |
| Target / Feasibility 运行时 handler | 空壳 echo（内容层已完整，运行时未对接） |
| 状态持久化 | 仅 in-memory |
| 语言覆盖 | zh-CN 和 en-US |
