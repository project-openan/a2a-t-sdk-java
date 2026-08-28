# 1 API参考

## 1.1 简介

A2A-T SDK 的对外 API 收敛在两个门面上：客户端门面 `A2ATClient`（提示词生成、协商消息生成与校验）与服务端门面 `A2ATServer`（提示词校验、协商消息生成与校验）。Negotiation-T 接口在两个门面上签名与语义完全对称；Task-T / Notification-T / Authorization-T 的生成接口位于 `A2ATClient`，校验接口位于 `A2ATServer`。

- **API 总览**：

| 分类 | API定义 | Client/Server | API含义 | 是否涉及LLM |
| ---- | ------- | -------- | ------- |---------|
| Negotiation-T | `generateNegotiationProposePromptFromText` | A2A-T Client / A2A-T Server | 从自然语言文本生成协商发起（propose）报文 | 是（1次LLM内容抽取） |
| Negotiation-T | `generateNegotiationAcceptPromptFromText` | A2A-T Client / A2A-T Server | 从自然语言文本生成协商接受（accept）报文 | 是（1次LLM内容抽取） |
| Negotiation-T | `generateNegotiationRejectPromptFromText` | A2A-T Client / A2A-T Server | 从自然语言文本生成协商拒绝（reject）报文 | 是（1次LLM内容抽取） |
| Negotiation-T | `generateNegotiationProposePromptFromData` | A2A-T Client / A2A-T Server | 从结构化数据确定性生成协商发起报文（不调用LLM） | 否 |
| Negotiation-T | `generateNegotiationAcceptPromptFromData` | A2A-T Client / A2A-T Server | 从结构化数据确定性生成协商接受报文（不调用LLM） | 否 |
| Negotiation-T | `generateNegotiationRejectPromptFromData` | A2A-T Client / A2A-T Server | 从结构化数据确定性生成协商拒绝报文（不调用LLM） | 否 |
| Negotiation-T | `validateProposePromptAndDataFilling` | A2A-T Client / A2A-T Server | 校验协商发起报文合规性并按Schema提取参数 | 是（1次LLM语义校验） |
| Negotiation-T | `validateAcceptPromptAndDataFilling` | A2A-T Client / A2A-T Server | 校验协商接受报文合规性并按Schema提取参数 | 是（1次LLM语义校验） |
| Negotiation-T | `validateRejectPromptAndDataFilling` | A2A-T Client / A2A-T Server | 校验协商拒绝报文合规性并按Schema提取参数 | 是（1次LLM语义校验） |
| Negotiation-T | `generateNegotiationAbortPromptFromText` | A2A-T Client / A2A-T Server | 从自然语言文本生成协商终止（abort）报文 | 是（1次LLM内容抽取） |
| Negotiation-T | `generateNegotiationAbortPromptFromData` | A2A-T Client / A2A-T Server | 从结构化数据确定性生成协商终止报文（不调用LLM） | 否 |
| Negotiation-T | `validateAbortPromptAndDataFilling` | A2A-T Client / A2A-T Server | 校验协商终止报文合规性并按Schema提取参数 | 是（1次LLM语义校验） |
| Task-T | `generateTaskPromptFromText` | A2A-T Client | 从自然语言文本按指定Task-T模板生成任务提示词（跳过场景识别） | 是（1次LLM槽位提取） |
| Task-T | `generateTaskPromptFromDataWithSchema` | A2A-T Client | 从结构化数据+语义Schema按指定Task-T模板生成任务提示词 | 是（1次LLM槽位提取） |
| Task-T | `validateTaskPromptAndDataFilling` | A2A-T Server | 校验Task-T任务提示词合规性并按Schema提取参数 | 是（1次LLM语义校验与提参） |
| Notification-T | `generateNotificationPromptFromText` | A2A-T Client | 从自然语言文本按指定Notification-T模板生成通知订阅提示词 | 是（1次LLM槽位提取） |
| Notification-T | `generateNotificationPromptFromDataWithSchema` | A2A-T Client | 从结构化数据+语义Schema按指定Notification-T模板生成通知订阅提示词 | 是（1次LLM槽位提取） |
| Notification-T | `validateNotificationPromptAndDataFilling` | A2A-T Server | 校验Notification-T提示词合规性并按Schema提取参数 | 是（1次LLM语义校验与提参） |
| Authorization-T | `generateAuthPromptFromText` | A2A-T Client | 从自然语言文本按指定Authorization-T模板生成授权提示词 | 是（1次LLM槽位提取） |
| Authorization-T | `generateAuthPromptFromDataWithSchema` | A2A-T Client | 从结构化数据+语义Schema按指定Authorization-T模板生成授权提示词 | 是（1次LLM槽位提取） |
| Authorization-T | `validateAuthPromptAndDataFilling` | A2A-T Server | 校验Authorization-T提示词合规性并按Schema提取参数 | 是（1次LLM语义校验与提参） |
| 通用接口 | `generateTaskPrompt` | A2A-T Client | 从自然语言或结构化输入经场景识别生成任务提示词 | 是（2次LLM调用：场景识别+槽位提取） |
| 通用接口 | `checkTaskPrompt` | A2A-T Server | 校验任务提示词的场景、模板与槽位合规性 | 是（3次LLM调用：场景识别+槽位提取+语义校验） |

**公共数据类型与约定**

- **TemplateUri：**模板 URI 值类型，推荐用 `net.openan.a2at.sdk.core.model.StandardTemplates` 常量构造，如 `StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE`（URI 为 `Negotiation-T/information-negotiation/propose/v1`）；来自外部的字符串用 `TemplateUri.parse(String)` 解析，返回 `Optional<TemplateUri>` 且不抛异常。
- **协商会话上下文：**`NegotiationContext(id, round, maxRounds, performative)`（id 为 UUID 形态、round 从 1 起、默认轮次预算 `DEFAULT_MAX_ROUNDS = 5`，performative 表明 context 随哪类协商消息出行：`PROPOSE` / `ACCEPT` / `REJECT` / `ABORT`），随消息 metadata 传输、不经 LLM；`NegotiationContext.of(id, round, performative)` 使用默认预算，`nextRound()` 推进轮次。
- **异常体系：**所有 SDK 处理失败均为 `A2ATError` 子类——生成失败抛 `PromptGenerationException`（Task-T / Notification-T / Authorization-T）或 `NegotiationGenerationException`（Negotiation-T），校验+提参失败抛 `ContentValidationException`（Task-T / Notification-T / Authorization-T，携带 `errors()` 槽位错误明细与 `params()` 部分提取参数）或 `NegotiationParamExtractionException`（Negotiation-T）。捕获 `A2ATError` 即可覆盖全部处理失败，`getCode()` 获取机器可读错误码。
- **SlotValidationError：**逐槽位校验错误明细，随校验失败异常或失败负载返回，各节“输出说明”中的失败结构均引用此定义：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| slotName | String | 出错槽位名 |
| code | String | 槽位级错误码，如 `missing_required`（必填缺失）、`invalid_value`（取值非法）、`format_error`（格式错误） |
| message | String | 槽位级错误说明 |


## 1.2 约束和限制

部分接口涉及到LLM调用，使用时需根据对接模型服务可提供的并发能力控制使用时的调用频率和并发

## 1.3 API说明

### 1.3.1 generateNegotiationProposePromptFromText

**API定义**

```java
public MetadataContent generateNegotiationProposePromptFromText(
        String text, NegotiationContext context, TemplateUri templateUri)
```

**典型场景**：服务端Agent收到参数不全的Task-T任务报文后，用自然语言向客户端Agent发起"补充缺失信息"的协商请求；也适用于客户端Agent向服务端发起目标澄清或可行性评估请求。

**功能说明**：从自然语言文本生成协商发起（propose）阶段的结构化协商报文。执行流程：先加载模板（缺失快速失败，不消耗 LLM 请求），再执行一次 LLM 内容抽取（在模板 URI 约束下将自由文本抽取为类型化内容，可重试错误码上最多重试至 `A2AT_LLM_MAX_ATTEMPTS` 配置的上限），最后确定性渲染模板。适用于信息/目标/可行性协商的发起方。`templateUri` 的 phase 段必须为 `propose`。

**输入说明**

| 参数 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| text | String | 是 | 描述协商发起内容的自然语言文本（如请求补充的缺失信息清单） |
| context | NegotiationContext | 是 | 协商会话上下文，不经 LLM 直接注入生成消息的 `negotiationContext` metadata |
| templateUri | TemplateUri | 是 | propose 模板，如 `StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE` |

**请求样例**

```java
import java.nio.file.Path;
import java.util.Map;
import net.openan.a2at.sdk.client.A2ATClient;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.StandardTemplates;

A2ATClient client = new A2ATClient(Path.of("client.env"));

NegotiationContext ctx = new NegotiationContext(
        "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3", 1, NegotiationContext.DEFAULT_MAX_ROUNDS,
        NegotiationPerformative.PROPOSE);

MetadataContent propose = client.generateNegotiationProposePromptFromText(
        "请提供以下缺失信息：1. 接入端口名称：请提供业务接入端口名称；"
                + "2. 投诉分类：专线中断或专线质差。两个参数均为必选，缺少无法启动诊断。",
        ctx,
        StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE);

// 生成的metadata随A2A消息传输
Map<String, Object> metadata = propose.buildMetadataContent();
```

**输出说明**

成功时返回 `MetadataContent`：

| 字段/方法 | 类型 | 说明 |
| --------- | ---- | ---- |
| templateUri | String | 生成报文所用模板 URI，如 `Negotiation-T/information-negotiation/propose/v1` |
| promptText | String | 渲染后的协商报文文本，作为 A2A 消息 metadata 中扩展 URI 对应的值传输 |
| extensionUri | String | TMF 扩展 URI（`https://projects.tmforum.org/a2aproject/telecommunication/extensions/Negotiation-T/v1`），即报文在 metadata 中的 key |
| negotiationContext | NegotiationContext | 协商会话上下文（id / round / maxRounds），不经 LLM 随报文携带 |
| buildMetadataContent() | Map&lt;String, Object&gt; | 构建可直接放入 `Message.metadata` 的映射：扩展 URI → 报文文本、`templateUri` → 模板 URI、`negotiationContext` → 嵌套上下文对象 |

失败时抛 `NegotiationGenerationException`（`A2ATError` 子类，运行期异常）：

| 成员 | 类型 | 说明 |
| ---- | ---- | ---- |
| getCode() | String | 机器可读错误码，取值见下 |
| getMessage() | String | 人类可读的失败描述 |
| getCause() | Throwable | 根因异常，可为 null |

错误码：

- `template_not_found`（模板或提示词资源缺失）

- `negotiation_content_extract_failed`（无法从文本抽取结构化内容，可重试）

- `negotiation_llm_infrastructure_error`（LLM 基础设施故障，可重试）

- `negotiation_invalid_input`（文本为空、抽取内容与阶段不符、确认请求与其它板块组合矛盾）

- `negotiation_slot_missing`（缺少必需槽位）

入参为 null 抛 `NullPointerException`，templateUri 的 phase 段不是 `propose` 抛 `IllegalArgumentException`。

**响应样例**

```text
templateUri : Negotiation-T/information-negotiation/propose/v1
extensionUri: https://projects.tmforum.org/a2aproject/telecommunication/extensions/Negotiation-T/v1
promptText  :
## 信息协商
请根据<所需信息项>补充相关内容。

## 所需信息项
1. 接入端口名称：举例：P533-珠江旧城-PTN3900-23-TPA1EG24-1
2. 投诉分类：举例：专线质差
3. 专线业务标识
缺失项之间的关系：OR
```

### 1.3.2 generateNegotiationAcceptPromptFromText

**API定义**

```java
public MetadataContent generateNegotiationAcceptPromptFromText(
        String text, NegotiationContext context, TemplateUri templateUri)
```

**典型场景**：协商响应方（通常是客户端Agent）收到对端的信息协商请求后，用自然语言补充/交付所请求的信息并生成接受报文回传，如补齐接入端口名称与投诉分类后确认启动诊断。

**功能说明**：从自然语言文本生成协商接受（accept）报文。一次 LLM 内容抽取（抽取出的结论必须为 `ACCEPT`，否则以 `negotiation_invalid_input` 拒绝）+ 确定性渲染。适用于协商响应方补充/交付信息。`templateUri` 的 phase 段必须为 `accept-reject`。

**输入说明**

| 参数 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| text | String | 是 | 描述接受内容的自然语言文本（如补充交付的信息清单） |
| context | NegotiationContext | 是 | 协商会话上下文 |
| templateUri | TemplateUri | 是 | accept-reject 模板，如 `StandardTemplates.INFORMATION_NEGOTIATION_ACCEPT_REJECT` |

**请求样例**

```java
MetadataContent accept = client.generateNegotiationAcceptPromptFromText(
        "同意补充以下信息：1. 接入端口名称：P533-珠江旧城-PTN3900-23-TPA1EG24-1；"
                + "2. 投诉分类：专线质差。信息已完整，可以启动诊断。",
        ctx,
        StandardTemplates.INFORMATION_NEGOTIATION_ACCEPT_REJECT);
```

**输出说明**

成功时返回 `MetadataContent`（结构同 [1.3.1](#131-generatenegotiationproposepromptfromtext)）。

失败时抛 `NegotiationGenerationException`（结构同 1.3.1）。错误码：

- `template_not_found`（模板或提示词资源缺失）

- `negotiation_content_extract_failed`（无法从文本抽取结构化内容，可重试）

- `negotiation_llm_infrastructure_error`（LLM 基础设施故障，可重试）

- `negotiation_invalid_input`（文本为空或抽取结论不是 `ACCEPT`）

- `negotiation_slot_missing`（缺少必需槽位）

**响应样例**

```text
templateUri : Negotiation-T/information-negotiation/accept-reject/v1
extensionUri: https://projects.tmforum.org/a2aproject/telecommunication/extensions/Negotiation-T/v1
promptText  :
## 信息协商结果
Accept

## 信息协商结果内容
1. 接入端口名称：P533-珠江旧城-PTN3900-23-TPA1EG24-1
2. 投诉分类：专线质差
```

### 1.3.3 generateNegotiationRejectPromptFromText

**API定义**

```java
public MetadataContent generateNegotiationRejectPromptFromText(
        String text, NegotiationContext context, TemplateUri templateUri)
```

**典型场景**：协商响应方（通常是客户端Agent）无法满足对端的协商请求时，用自然语言生成拒绝报文回传并结束本轮协商，如因站点清单不可用无法提供接入端口名称。

**功能说明**：从自然语言文本生成协商拒绝（reject）报文。一次 LLM 内容抽取（抽取出的结论必须为 `REJECT`）+ 确定性渲染。`templateUri` 的 phase 段必须为 `accept-reject`。

**输入说明**：同 [generateNegotiationAcceptPromptFromText](#132-generatenegotiationacceptpromptfromtext)，text 为描述拒绝原因的自然语言文本。

**请求样例**

```java
MetadataContent reject = client.generateNegotiationRejectPromptFromText(
        "拒绝补充信息：接入端口名称因站点清单不可用而无法提供，本次协商结束。",
        ctx,
        StandardTemplates.INFORMATION_NEGOTIATION_ACCEPT_REJECT);
```

**输出说明**

成功时返回 `MetadataContent`（结构同 [1.3.1](#131-generatenegotiationproposepromptfromtext)）。

失败时抛 `NegotiationGenerationException`（结构同 1.3.1）。错误码：

- `template_not_found`（模板或提示词资源缺失）

- `negotiation_content_extract_failed`（无法从文本抽取结构化内容，可重试）

- `negotiation_llm_infrastructure_error`（LLM 基础设施故障，可重试）

- `negotiation_invalid_input`（文本为空或抽取结论不是 `REJECT`）

- `negotiation_slot_missing`（缺少必需槽位）

**响应样例**

```text
templateUri : Negotiation-T/information-negotiation/accept-reject/v1
extensionUri: https://projects.tmforum.org/a2aproject/telecommunication/extensions/Negotiation-T/v1
promptText  :
## 信息协商结果
Reject

## 信息协商结果内容
1. 接入端口名称：无法提供，工作台侧端口资源台账暂不可查
```

### 1.3.4 generateNegotiationProposePromptFromData

**API定义**

```java
public MetadataContent generateNegotiationProposePromptFromData(
        NegotiationProposeData data, TemplateUri templateUri)
```

**典型场景**：与 fromText 相同的发起场景，但输入为业务系统构造的结构化数据（如服务端Agent根据 `validateTaskPromptAndDataFilling` 检出的缺失槽位清单自动生成协商请求条目），适合对报文内容有确定性要求、不希望引入 LLM 抽取不确定性的场景。

**功能说明**：从类型化数据确定性生成协商发起报文，**不调用 LLM**：类型化内容先经校验，再分发给模板 URI 所指协商类型的生成器渲染。`content` 必须与 `templateUri` 的协商类型匹配（information / target / feasibility），phase 段必须为 `propose`。

**输入说明**

| 参数 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| data | `NegotiationProposeData(context, content)` | 是 | 协商上下文 + 类型化发起内容；内容类型按协商类型选择 |
| templateUri | TemplateUri | 是 | propose 模板 |

三种协商类型的发起内容：

| 协商类型 | Propose 内容类型 | 字段 |
| -------- | ---------------- | ---- |
| 信息协商 | `InformationProposeContent` | `items`（缺失项清单）、`relationship`（缺失项间关系，可空） |
| 目标协商 | `TargetProposeContent` | `targetNegotiationDescription`（必填）、`intentUnderstanding`、`alignmentAndClarification`、`requestForClarification`（三个条目列表均可空，空则省略对应章节）、`targetConfirmRequest`（可空 string，非空表示本轮消息类别为"目标已澄清并请求对方确认"，渲染"目标澄清后的确认请求"章节，内容固定为"目标已经澄清，是否同意按照此目标继续执行？"；非空时 `intentUnderstanding` / `alignmentAndClarification` / `requestForClarification` 必须全为空） |
| 可行性协商 | `FeasibilityProposeContent` | `feasibilityNegotiationDescription`（必填）、`action`（`NegotiationAction.REQUEST_FEASIBILITY_EVALUATION` / `PROPOSE_ALTERNATIVE_ON_FAILURE`，两值不变）、`contentsToEvaluate`、`infeasibilityDetailsAndProposal`、`feasibilityConfirmRequest`（可空 string，非空表示本轮消息类别为"评估可行并请求确认"：`action` 须取 `REQUEST_FEASIBILITY_EVALUATION` 且 `contentsToEvaluate` / `infeasibilityDetailsAndProposal` 皆为空；内容按评估类别固定为"评估目标可行，是否同意按照此目标继续执行？"（目标达成）或"评估方案可行，是否同意按照此方案继续执行？"（方案可行性）） |

**请求样例**

```java
import java.util.List;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.negotiation.content.InformationProposeContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;
import net.openan.a2at.sdk.negotiation.content.NegotiationProposeData;

NegotiationContext ctx = new NegotiationContext("3dbc13b5-bd57-4c2b-b503-24e381b6c8d3", 2, 5,
        NegotiationPerformative.PROPOSE);

MetadataContent propose = client.generateNegotiationProposePromptFromData(
        new NegotiationProposeData(
                ctx,
                new InformationProposeContent(
                        List.of(
                                new NegotiationItem("接入端口名称", "举例：P533-珠江旧城-PTN3900-23-TPA1EG24-1"),
                                new NegotiationItem("投诉分类", "举例：专线质差"),
                                new NegotiationItem("专线业务标识", null)),
                        "OR")),
        StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE);
```

**输出说明**

成功时返回 `MetadataContent`（结构同 [1.3.1](#131-generatenegotiationproposepromptfromtext)，协商报文均携带 `negotiationContext`）。

失败时抛 `NegotiationGenerationException`（结构同 1.3.1）。错误码：

- `template_not_found`（模板缺失）

- `negotiation_slot_missing`（渲染缺少必需槽位）

另有两类编程错误（`A2ATError` 树外，标准 JDK 异常）：入参或其 context 为 null 抛 `NullPointerException`；内容类型与模板协商类型不符、phase 段不是 `propose` 抛 `IllegalArgumentException`。

**响应样例**

```text
templateUri : Negotiation-T/information-negotiation/propose/v1
extensionUri: https://projects.tmforum.org/a2aproject/telecommunication/extensions/Negotiation-T/v1
promptText  :
## 信息协商
请根据<所需信息项>补充相关内容。

## 所需信息项
1. 接入端口名称：举例：P533-珠江旧城-PTN3900-23-TPA1EG24-1
2. 投诉分类：举例：专线质差
3. 专线业务标识
缺失项之间的关系：OR
```

### 1.3.5 generateNegotiationAcceptPromptFromData

**API定义**

```java
public MetadataContent generateNegotiationAcceptPromptFromData(
        NegotiationEndingData data, TemplateUri templateUri)
```

**典型场景**：协商响应方（通常是客户端Agent）按对端请求的槽位清单程序化补参后，以结构化条目生成接受报文回传。

**功能说明**：从类型化数据确定性生成协商接受报文，**不调用 LLM**。`content.conclusion()` 必须为 `ACCEPT`，其他结论（含 `ABORT`）以 `IllegalArgumentException` 拒绝。

**输入说明**

| 参数 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| data | `NegotiationEndingData(context, content)` | 是 | 协商上下文 + 类型化接受内容（结论为 `ACCEPT`） |
| templateUri | TemplateUri | 是 | accept-reject 模板 |

三种协商类型的接受内容：`InformationEndingContent(ACCEPT, items)`（交付的信息项清单）、`TargetEndingContent(ACCEPT, confirmedIntent, null)`（最终确认的意图）、`FeasibilityEndingContent(ACCEPT, feasibilitySummary)`（可行性评估结论摘要）。

**请求样例**

```java
import net.openan.a2at.sdk.negotiation.content.InformationEndingContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationConclusion;
import net.openan.a2at.sdk.negotiation.content.NegotiationEndingData;

MetadataContent accept = client.generateNegotiationAcceptPromptFromData(
        new NegotiationEndingData(
                ctx,
                new InformationEndingContent(
                        NegotiationConclusion.ACCEPT,
                        List.of(
                                new NegotiationItem("接入端口名称", "P533-珠江旧城-PTN3900-23-TPA1EG24-1"),
                                new NegotiationItem("投诉分类", "专线质差")))),
        StandardTemplates.INFORMATION_NEGOTIATION_ACCEPT_REJECT);
```

**输出说明**

成功时返回 `MetadataContent`（结构同 [1.3.1](#131-generatenegotiationproposepromptfromtext)）。

失败时抛 `NegotiationGenerationException`（结构同 1.3.1）。错误码：

- `template_not_found`（模板缺失）

- `negotiation_slot_missing`（渲染缺少必需槽位）

编程错误：入参或其 context 为 null 抛 `NullPointerException`；`conclusion` 不是 `ACCEPT`、phase 段不是 `accept-reject` 或内容类型不符抛 `IllegalArgumentException`。

**响应样例**

```text
templateUri : Negotiation-T/information-negotiation/accept-reject/v1
extensionUri: https://projects.tmforum.org/a2aproject/telecommunication/extensions/Negotiation-T/v1
promptText  :
## 信息协商结果
Accept

## 信息协商结果内容
1. 接入端口名称：P533-珠江旧城-PTN3900-23-TPA1EG24-1
2. 投诉分类：专线质差
```

### 1.3.6 generateNegotiationRejectPromptFromData

**API定义**

```java
public MetadataContent generateNegotiationRejectPromptFromData(
        NegotiationEndingData data, TemplateUri templateUri)
```

**典型场景**：协商响应方程序化判定无法满足对端请求后，以结构化条目（无法提供的项及原因）生成拒绝报文回传。

**功能说明**：从类型化数据确定性生成协商拒绝报文，**不调用 LLM**。`content.conclusion()` 必须为 `REJECT`，其他结论以 `IllegalArgumentException` 拒绝。

**输入说明**：同 [generateNegotiationAcceptPromptFromData](#135-generatenegotiationacceptpromptfromdata)，但结论为 `REJECT`。拒绝内容：`InformationEndingContent(REJECT, items)`（无法提供的项及原因）、`TargetEndingContent(REJECT, null, failureReason)`（拒绝原因）、`FeasibilityEndingContent(REJECT, feasibilitySummary)`（不可行结论摘要）。

**请求样例**

```java
MetadataContent reject = client.generateNegotiationRejectPromptFromData(
        new NegotiationEndingData(
                ctx,
                new InformationEndingContent(
                        NegotiationConclusion.REJECT,
                        List.of(new NegotiationItem("接入端口名称", "无法提供，工作台侧端口资源台账暂不可查")))),
        StandardTemplates.INFORMATION_NEGOTIATION_ACCEPT_REJECT);
```

**输出说明**

成功时返回 `MetadataContent`（结构同 [1.3.1](#131-generatenegotiationproposepromptfromtext)）。

失败时抛 `NegotiationGenerationException`（结构同 1.3.1）。错误码：

- `template_not_found`（模板缺失）

- `negotiation_slot_missing`（渲染缺少必需槽位）

编程错误：入参或其 context 为 null 抛 `NullPointerException`；`conclusion` 不是 `REJECT`、phase 段不是 `accept-reject` 或内容类型不符抛 `IllegalArgumentException`。

**响应样例**

```text
templateUri : Negotiation-T/information-negotiation/accept-reject/v1
extensionUri: https://projects.tmforum.org/a2aproject/telecommunication/extensions/Negotiation-T/v1
promptText  :
## 信息协商结果
Reject

## 信息协商结果内容
1. 接入端口名称：无法提供，工作台侧端口资源台账暂不可查
```

### 1.3.7 validateProposePromptAndDataFilling

**API定义**

```java
public FilledParamData validateProposePromptAndDataFilling(
        String prompt, NegotiationContext context, Map<String, Object> schema, TemplateUri templateUri)
```

**典型场景**：发起方（服务端Agent）发送协商请求前的出站自检；或接收方（客户端Agent）校验收到的协商请求并提取需补充的槽位清单，驱动后续补参。

**功能说明**：校验一条协商发起（propose）报文是否为格式正确的协商消息，并按调用方提供的 JSON Schema 从中提取参数。流水线：模板 URI phase 段检查 → 确定性规则门禁（context 的 id 为 UUID 形态、轮次不超预算；context 为 null 报 `negotiation_invalid_input`）→ 一次 LLM 语义校验调用（同时完成参数提取，可重试错误码上最多重试至配置上限）→ 参数合并（协商上下文参数 `id` / `round` / `maxRounds` 与提取参数合并，键冲突时**上下文参数优先**）。

**输入说明**

| 参数 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| prompt | String | 是 | 待校验的协商发起报文文本（`MetadataContent.promptText()`） |
| context | NegotiationContext | 否 | 随报文传输的协商上下文；null 报 `negotiation_invalid_input` |
| schema | Map&lt;String, Object&gt; | 是 | 调用方提供的参数 JSON Schema，声明要提取的参数 |
| templateUri | TemplateUri | 是 | propose 模板 |

**请求样例**

```java
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.client.A2ATClient;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.StandardTemplates;

A2ATClient client = new A2ATClient(Path.of("client.env"));

Map<String, Object> schema = Map.of(
        "type", "object",
        "properties", Map.of(
                "接入端口名称", Map.of("type", "string"),
                "投诉分类", Map.of("type", "string")),
        "required", List.of("接入端口名称"));

// proposePrompt 为对端发来的协商发起报文文本
FilledParamData requested = client.validateProposePromptAndDataFilling(
        proposePrompt, ctx, schema, StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE);

// 过滤掉上下文参数（id/round/maxRounds）后即为需补充的槽位清单
requested.data().keySet().removeAll(List.of("id", "round", "maxRounds"));
```

**输出说明**

成功时返回 `FilledParamData`：

| 字段/方法 | 类型 | 说明 |
| --------- | ---- | ---- |
| data() | Map&lt;String, Object&gt; | 合并后的参数：协商上下文参数（`id` / `round` / `maxRounds`，键冲突时**优先**）+ 按调用方 Schema 从报文中提取的参数 |

失败时抛 `NegotiationParamExtractionException`（`A2ATError` 子类）：

| 成员 | 类型 | 说明 |
| ---- | ---- | ---- |
| getCode() | String | 机器可读错误码，取值见下 |
| getMessage() | String | 人类可读的失败描述 |
| getErrors() | List&lt;SlotValidationError&gt; | 逐槽位错误明细，结构见公共约定 |

错误码：

- `negotiation_invalid_input`（报文不是协商消息或 context 为 null）

- `negotiation_rule_violation`（协商上下文违反结构规则，如 id 非 UUID 形态、轮次超预算）

- `negotiation_semantic_rejected`（语义校验拒绝）

- `negotiation_llm_infrastructure_error`（LLM 基础设施故障，可重试）

- `template_not_found`（校验提示词资源缺失）

编程错误：prompt 或 schema 为 null 抛 `NullPointerException`，prompt 为空白或 phase 段不匹配抛 `IllegalArgumentException`。

**响应样例**

```text
requested.data() =
{
  接入端口名称=举例：P533-珠江旧城-PTN3900-23-TPA1EG24-1,
  投诉分类=举例：专线质差,
  id=3dbc13b5-bd57-4c2b-b503-24e381b6c8d3,
  round=1,
  maxRounds=5
}
```

### 1.3.8 validateAcceptPromptAndDataFilling

**API定义**

```java
public FilledParamData validateAcceptPromptAndDataFilling(
        String prompt, NegotiationContext context, Map<String, Object> schema, TemplateUri templateUri)
```

**典型场景**：发起方（服务端Agent）校验对端回传的接受报文，提取交付的参数值并与期望补齐值核对，确认无误后继续任务执行。

**功能说明**：校验一条协商接受（accept）报文并按 Schema 提取参数。流水线同 [validateProposePromptAndDataFilling](#137-validateproposepromptanddatafilling)，区别仅在于：`templateUri` 的 phase 段必须为 `accept-reject`，且报文须满足 accept 阶段的语义约束（结论为 Accept、携带交付内容）。

**输入说明**：同 1.3.7，prompt 为 accept 报文文本，templateUri 为 accept-reject 模板。

**请求样例**

```java
import net.openan.a2at.sdk.server.A2ATServer;

A2ATServer server = new A2ATServer(Path.of("server.env"));

// acceptPrompt 为客户端回传的接受报文文本，acceptContext 为其协商上下文
FilledParamData acceptParams = server.validateAcceptPromptAndDataFilling(
        acceptPrompt, acceptContext, schema, StandardTemplates.INFORMATION_NEGOTIATION_ACCEPT_REJECT);
```

**输出说明**

成功时返回 `FilledParamData`（结构同 [1.3.7](#137-validateproposepromptanddatafilling)），`data()` 携带交付内容中按 Schema 提取的参数与上下文参数。

失败时抛 `NegotiationParamExtractionException`（结构同 1.3.7）。错误码：

- `negotiation_invalid_input`（报文不是 accept 协商消息或 context 为 null）

- `negotiation_rule_violation`（协商上下文违反结构规则）

- `negotiation_semantic_rejected`（结论不是 Accept 或内容不满足 accept 阶段约束）

- `negotiation_llm_infrastructure_error`（LLM 基础设施故障，可重试）

- `template_not_found`（校验提示词资源缺失）

编程错误：prompt 或 schema 为 null 抛 `NullPointerException`，prompt 为空白或 phase 段不是 `accept-reject` 抛 `IllegalArgumentException`。

**响应样例**

```text
acceptParams.data() =
{
  接入端口名称=P533-珠江旧城-PTN3900-23-TPA1EG24-1,
  投诉分类=专线质差,
  id=3dbc13b5-bd57-4c2b-b503-24e381b6c8d3,
  round=1,
  maxRounds=5
}
```

### 1.3.9 validateRejectPromptAndDataFilling

**API定义**

```java
public FilledParamData validateRejectPromptAndDataFilling(
        String prompt, NegotiationContext context, Map<String, Object> schema, TemplateUri templateUri)
```

**典型场景**：发起方（服务端Agent）校验对端回传的拒绝报文，提取拒绝原因，据此终止任务或转入人工处理。

**功能说明**：校验一条协商拒绝（reject）报文并按 Schema 提取参数。流水线同 1.3.7，区别仅在于：报文须满足 reject 阶段的语义约束（结论为 Reject、携带拒绝原因）。

**输入说明**：同 1.3.7，prompt 为 reject 报文文本，templateUri 为 accept-reject 模板。

**请求样例**

```java
FilledParamData rejectParams = server.validateRejectPromptAndDataFilling(
        rejectPrompt, ctx, schema, StandardTemplates.INFORMATION_NEGOTIATION_ACCEPT_REJECT);
```

**输出说明**

成功时返回 `FilledParamData`（结构同 [1.3.7](#137-validateproposepromptanddatafilling)），`data()` 携带拒绝原因中按 Schema 提取的参数与上下文参数。

失败时抛 `NegotiationParamExtractionException`（结构同 1.3.7）。错误码：

- `negotiation_invalid_input`（报文不是 reject 协商消息或 context 为 null）

- `negotiation_rule_violation`（协商上下文违反结构规则）

- `negotiation_semantic_rejected`（结论不是 Reject 或内容不满足 reject 阶段约束）

- `negotiation_llm_infrastructure_error`（LLM 基础设施故障，可重试）

- `template_not_found`（校验提示词资源缺失）

编程错误：prompt 或 schema 为 null 抛 `NullPointerException`，prompt 为空白或 phase 段不是 `accept-reject` 抛 `IllegalArgumentException`。

**响应样例**

```text
rejectParams.data() =
{
  接入端口名称=无法提供，工作台侧端口资源台账暂不可查,
  id=3dbc13b5-bd57-4c2b-b503-24e381b6c8d3,
  round=1,
  maxRounds=5
}
```

### 1.3.10 generateTaskPromptFromText

**API定义**

```java
public MetadataContent generateTaskPromptFromText(String text, TemplateUri templateUri)
```

**典型场景**：客户端Agent将用户的自然语言任务描述（如专线投诉诊断诉求）转换为指定场景的Task-T协议报文，适合已确定目标模板、需跳过场景识别的场景。

**功能说明**：从自然语言文本按指定 Task-T 模板生成任务提示词报文，**跳过场景识别**（模板由调用方显式指定）。执行一次 LLM 槽位提取后确定性渲染模板。生成阶段即校验内置槽位 Schema（必填槽缺失或取值非法时以 `slot_validation_error` 快速失败）。

**输入说明**

| 参数 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| text | String | 是 | 自然语言任务描述 |
| templateUri | TemplateUri | 是 | Task-T 模板，如 `StandardTemplates.PRIVATE_LINE_COMPLAINT`（`Task-T/network-layer/private-line-complaint/v1`） |

**请求样例**

```java
import java.nio.file.Path;
import net.openan.a2at.sdk.client.A2ATClient;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.StandardTemplates;

A2ATClient client = new A2ATClient(Path.of("client.env"));

MetadataContent metadata = client.generateTaskPromptFromText(
        "帮我创建专线投诉诊断任务，P781-珠江新城-PTN7900-23-TPA1EG24-17 这个端口，"
                + "客户报的是专线质差，从2026年5月11号早上8点半开始，深圳访问广州的核心系统非常慢，"
                + "时延从12ms飙到320ms，柜面和手机银行老是报连接超时，OSS流水号是event-id-20260511-09013。",
        StandardTemplates.PRIVATE_LINE_COMPLAINT);
```

**输出说明**

成功时返回 `MetadataContent`：

| 字段/方法 | 类型 | 说明 |
| --------- | ---- | ---- |
| templateUri | String | 生成报文所用模板 URI，如 `Task-T/network-layer/private-line-complaint/v1` |
| promptText | String | 渲染后的任务提示词报文文本，作为 A2A 消息 metadata 中扩展 URI 对应的值传输 |
| extensionUri | String | TMF 扩展 URI（`https://projects.tmforum.org/a2aproject/telecommunication/extensions/Task-T/v1`），即报文在 metadata 中的 key |
| negotiationContext | NegotiationContext | 恒为 null（非协商报文） |
| buildMetadataContent() | Map&lt;String, Object&gt; | 构建可直接放入 `Message.metadata` 的两键映射：扩展 URI → 报文文本、`templateUri` → 模板 URI |

失败时抛 `PromptGenerationException`（`A2ATError` 子类，运行期异常）：

| 成员 | 类型 | 说明 |
| ---- | ---- | ---- |
| getCode() | String | 机器可读错误码，取值见下 |
| getMessage() | String | 人类可读的失败描述 |
| failedParameters() | List&lt;SlotValidationError&gt; | 槽位校验失败的明细（`slot_validation_error` 时非空），结构见公共约定 |

错误码：

- `template_not_found`（模板缺失）

- `prompt_resource_load_error`（提示词资源加载失败）

- `slot_schema_not_found`（槽位 Schema 缺失）

- `llm_invocation_failed`（LLM 调用失败）

- `slot_validation_error`（必填槽缺失或取值非法）

- `render_failed`（模板渲染失败）

编程错误：text 或 templateUri 为 null 抛 `NullPointerException`。

**响应样例**

```text
templateUri : Task-T/network-layer/private-line-complaint/v1
extensionUri: https://projects.tmforum.org/a2aproject/telecommunication/extensions/Task-T/v1
promptText  :
## 任务类型(Task Type)
传输专线业务投诉诊断

## 任务描述(Task Description)
基于<任务对象>、<任务上下文> 进行投诉场景的网络侧故障根因诊断, 达成<任务目标>中定义的投诉诊断目标，按照<预期输出>中定义的结构返回任务处理结果。

## 任务目标(Task Target)
对网络侧故障进行诊断，返回故障根因和修复建议等诊断结果信息。

## 任务对象(Task Object)
接入端口名称：P781-珠江新城-PTN7900-23-TPA1EG24-17

## 任务上下文(Task Context)
1. 投诉分类：专线质差
2. 问题发生时间：2026-05-11T08:21:46Z
3. OSS侧事件流水号：event-id-20260511-09013
4. 投诉详情：从5月11号早上8点半开始，深圳访问广州的响应时延从平均12ms骤升至320ms

## 预期输出
要求投诉诊断任务的结果包含如下信息：
1. 诊断结果；参数的取值范围包括：成功、失败；(必选)
2. 诊断结果详细信息； (必选)
3. 修复建议； (可选)
4. 故障根因列表，每个故障根因包含故障根因名称、详细描述、修复建议、故障根因点位置等信息； (可选)
```

### 1.3.11 generateTaskPromptFromDataWithSchema

**API定义**

```java
public MetadataContent generateTaskPromptFromDataWithSchema(
        Map<String, Object> data, Map<String, Object> schema, TemplateUri templateUri)
```

**典型场景**：客户端Agent将上游系统的结构化任务参数（字段名可与模板槽位不同，由Schema描述字段语义）转换为Task-T协议报文，适合任务参数已结构化持有的场景。

**功能说明**：从结构化数据 + 语义 Schema 按指定 Task-T 模板生成任务提示词，**跳过场景识别**。`schema` 描述每个输入字段的业务含义（description / examples / enum 等），指导槽位填充与取值约束；每个 data 的 key 对应一个槽位值。生成阶段同样执行内置槽位 Schema 校验（必填槽缺失或取值非法时以 `slot_validation_error` 快速失败）。

**输入说明**

| 参数 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| data | Map&lt;String, Object&gt; | 是 | 结构化业务字段输入，key 为业务字段名，value 为字段值 |
| schema | Map&lt;String, Object&gt; | 是（非空） | 字段语义 JSON Schema，描述各字段含义与约束 |
| templateUri | TemplateUri | 是 | Task-T 模板 |

**请求样例**

```java
import java.util.Map;

Map<String, Object> data = Map.of(
        "portName", "P781-福田中心-PTN7900-2-TPA1EG24-03",
        "complaintScenario", "专线质差",
        "faultStartTime", "2026-05-11T08:21:46Z",
        "ticketNo", "event-id-20260511-09013",
        "faultDetailText", "从5月11号早上8点半开始，深圳访问广州的响应时延从平均12ms骤升至320ms");

Map<String, Object> semanticsSchema = Map.of(
        "type", "object",
        "properties", Map.of(
                "portName", Map.of(
                        "type", "string",
                        "description", "业务字段：接入端口名称，唯一标识被投诉的专线对象"),
                "complaintScenario", Map.of(
                        "type", "string",
                        "description", "业务字段：投诉分类场景，专线中断或专线质差二者必选其一",
                        "enum", List.of("专线中断", "专线质差")),
                "faultStartTime", Map.of("type", "string", "description", "业务字段：问题发生时间"),
                "ticketNo", Map.of("type", "string", "description", "业务字段：OSS 侧受理的投诉工单或事件流水号"),
                "faultDetailText", Map.of("type", "string", "description", "业务字段：用户对故障现象的自由描述")),
        "required", List.of("portName", "complaintScenario"));

MetadataContent metadata = client.generateTaskPromptFromDataWithSchema(
        data, semanticsSchema, StandardTemplates.PRIVATE_LINE_COMPLAINT);
```

**输出说明**

成功时返回 `MetadataContent`（结构同 [1.3.10](#1310-generatetaskpromptfromtext)）。

失败时抛 `PromptGenerationException`（结构同 1.3.10）。编程错误：入参为 null 抛 `NullPointerException`，schema 为空 Map 抛 `IllegalArgumentException`。

**响应样例**（与 1.3.10 同模板，槽位值来自结构化输入，`faultDetail` 为示例截断值）

```text
templateUri : Task-T/network-layer/private-line-complaint/v1
extensionUri: https://projects.tmforum.org/a2aproject/telecommunication/extensions/Task-T/v1
promptText  :
## 任务类型(Task Type)
传输专线业务投诉诊断

## 任务描述(Task Description)
基于<任务对象>、<任务上下文> 进行投诉场景的网络侧故障根因诊断, 达成<任务目标>中定义的投诉诊断目标，按照<预期输出>中定义的结构返回任务处理结果。

## 任务目标(Task Target)
对网络侧故障进行诊断，返回故障根因和修复建议等诊断结果信息。

## 任务对象(Task Object)
接入端口名称：P781-福田中心-PTN7900-2-TPA1EG24-03

## 任务上下文(Task Context)
1. 投诉分类：专线质差
2. 问题发生时间：2026-05-11T08:21:46Z
3. OSS侧事件流水号：event-id-20260511-09013
4. 投诉详情：从5月11号早上8点半开始，深圳访问广州的响应时延从平均12ms骤升至320ms

## 预期输出
要求投诉诊断任务的结果包含如下信息：
1. 诊断结果；参数的取值范围包括：成功、失败；(必选)
2. 诊断结果详细信息； (必选)
3. 修复建议； (可选)
4. 故障根因列表，每个故障根因包含故障根因名称、详细描述、修复建议、故障根因点位置等信息； (可选)
```

### 1.3.12 validateTaskPromptAndDataFilling

**API定义**

```java
public FilledParamData validateTaskPromptAndDataFilling(
        String prompt, Map<String, Object> schema, TemplateUri templateUri)
```

**典型场景**：服务端Agent收到Task-T报文后、进入业务执行前的参数校验与提取入口；也是协商流程中"缺槽检测"的判定点——required参数缺失时由调用方决定是否发起协商补参。

**功能说明**：校验一条 Task-T 任务提示词报文是否匹配模板与槽位约束，并按调用方提供的 JSON Schema 提取参数。流水线：确定性规则门禁（模板内置槽位校验）→ 一次 LLM 语义校验调用（同时完成参数提取，可重试错误码上最多重试至配置上限）。

**输入说明**

| 参数 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| prompt | String | 是（非空） | 待校验的任务提示词报文文本（`MetadataContent.promptText()`） |
| schema | Map&lt;String, Object&gt; | 是 | 调用方提供的参数 JSON Schema，声明要提取/校验的参数及 required 约束 |
| templateUri | TemplateUri | 是 | Task-T 模板（前缀段必须为 `Task-T`） |

**请求样例**

```java
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.server.A2ATServer;

A2ATServer server = new A2ATServer(Path.of("server.env"));

// 服务端参数Schema（key为服务端业务字段名，与客户端字段名可不同，SDK完成跨字段适配）
Map<String, Object> validationSchema = Map.of(
        "type", "object",
        "properties", Map.of(
                "accessPort", Map.of("type", "string", "description", "接入端口名称，唯一标识被投诉的专线对象"),
                "bizScenario", Map.of(
                        "type", "string",
                        "description", "投诉分类场景，必填，仅允许取值：专线中断、专线质差",
                        "enum", List.of("专线中断", "专线质差")),
                "faultTime", Map.of("type", "string", "description", "问题发生时间"),
                "eventSerialNo", Map.of("type", "string", "description", "OSS 侧受理的投诉工单或事件流水号"),
                "faultDetail", Map.of("type", "string", "description", "投诉/故障现象详情描述")),
        "required", List.of("accessPort", "bizScenario"));

Map<String, Object> extracted = server
        .validateTaskPromptAndDataFilling(
                metadata.promptText(), validationSchema, StandardTemplates.PRIVATE_LINE_COMPLAINT)
        .data();
```

**输出说明**

成功时返回 `FilledParamData`：

| 字段/方法 | 类型 | 说明 |
| --------- | ---- | ---- |
| data() | Map&lt;String, Object&gt; | 按 Schema 提取的参数，key 为 Schema 中声明的参数名 |

失败时抛 `ContentValidationException`（`A2ATError` 子类）：

| 成员 | 类型 | 说明 |
| ---- | ---- | ---- |
| getCode() | String | 机器可读错误码，取值见下 |
| getMessage() | String | 人类可读的失败描述 |
| errors() | List&lt;SlotValidationError&gt; | 逐槽位错误明细（槽位级错误码如 `missing_required`、`invalid_value`、`format_error`），结构见公共约定 |
| params() | Map&lt;String, Object&gt; | 被拒前的部分提取参数（无法提取的槽位值为 null） |

错误码：

- `validation_semantic_rejected`（语义校验拒绝，含 required 参数缺失或取值非法）

- `validation_llm_infrastructure_error`（LLM 基础设施故障，可重试）

- `validation_prompt_resource_not_found`（校验提示词资源缺失）

编程错误：prompt / schema / templateUri 为 null 抛 `NullPointerException`，prompt 为空白抛 `IllegalArgumentException`。

**响应样例**

```text
extracted =
{
  accessPort=P781-珠江新城-PTN7900-23-TPA1EG24-17,
  bizScenario=专线质差,
  faultTime=2026-05-11,
  eventSerialNo=event-id-20260511-09013,
  faultDetail=320ms
}
```

校验拒绝时（缺少关键槽位的负例，来自 `TaskTDemoMain` 用例三）：

```text
ContentValidationException: [validation_semantic_rejected] ...
    slot=任务对象 code=missing_required message=...
```

### 1.3.13 generateNotificationPromptFromText

**API定义**

```java
public MetadataContent generateNotificationPromptFromText(String text, TemplateUri templateUri)
```

**典型场景**：客户端Agent将自然语言订阅需求（如业务抢通事件订阅）转换为指定场景的Notification-T订阅报文。

**功能说明**：从自然语言文本按指定 Notification-T 模板生成通知订阅提示词报文，跳过场景识别。执行一次 LLM 槽位提取后确定性渲染模板，生成阶段执行内置槽位 Schema 校验。

**输入说明**

| 参数 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| text | String | 是 | 自然语言订阅描述（通知主题、订阅条件、上报通知数据格式等） |
| templateUri | TemplateUri | 是 | Notification-T 模板，如 `StandardTemplates.SUBSCRIBE_INCIDENT`、`StandardTemplates.SERVICE_RECOVERY` |

**请求样例**

```java
import java.nio.file.Path;
import net.openan.a2at.sdk.client.A2ATClient;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.core.model.TemplateUri;

A2ATClient client = new A2ATClient(Path.of("client.env"));
TemplateUri templateUri = TemplateUri.parse("Notification-T/network-layer/service-recovery/v1").orElseThrow();

MetadataContent result = client.generateNotificationPromptFromText(
        "我想订阅一下业务抢通事件。上报通知数据格式如下："
                + "1. 业务抢通方案执行状态，取值范围：未启动、已结束；"
                + "2. 投诉诊断任务流水号；3. OSS侧事件流水号；4. 接入端口名称；"
                + "5. 是否已授权OMC自动抢通，取值范围：是、否；6. 业务抢通方案名称；7. 业务抢通方案详情",
        templateUri);
```

**输出说明**

成功时返回 `MetadataContent`（结构同 [1.3.10](#1310-generatetaskpromptfromtext)，`extensionUri` 为 `https://projects.tmforum.org/a2aproject/telecommunication/extensions/Notification-T/v1`）。

失败时抛 `PromptGenerationException`（结构同 1.3.10）。编程错误：text 或 templateUri 为 null 抛 `NullPointerException`。

**响应样例**（按模板渲染，实际文本随 LLM 槽位提取结果变化；本例输入未指定订阅条件，对应槽位留空）

```text
templateUri : Notification-T/network-layer/service-recovery/v1
extensionUri: https://projects.tmforum.org/a2aproject/telecommunication/extensions/Notification-T/v1
promptText  :
## 订阅描述
请根据以下 <通知主题>、<订阅条件>、<上报通知数据格式> 及 <预期输出> 信息，完成网络侧业务抢通事件的订阅与上报任务。

## 通知主题
业务抢通事件

## 订阅条件
（可选）
1. 子网名称；举例：xx子网；

## 上报通知数据格式
1. 业务抢通方案执行状态，取值范围：未启动、已结束
2. 投诉诊断任务流水号
3. OSS侧事件流水号
4. 接入端口名称
5. 是否已授权OMC自动抢通，取值范围：是、否
6. 业务抢通方案名称
7. 业务抢通方案详情

## 预期输出
1. 订阅结果，取值范围：成功、失败
2. 订阅失败原因（可选）
3. 订阅成功后，按照<上报通知数据格式>上报消息
```

### 1.3.14 generateNotificationPromptFromDataWithSchema

**API定义**

```java
public MetadataContent generateNotificationPromptFromDataWithSchema(
        Map<String, Object> data, Map<String, Object> schema, TemplateUri templateUri)
```

**典型场景**：客户端Agent将上游系统/界面提交的结构化订阅参数转换为Notification-T订阅报文，适合订阅参数已结构化持有的场景。

**功能说明**：从结构化数据 + 语义 Schema 按指定 Notification-T 模板生成通知订阅提示词，跳过场景识别。语义约束同 1.3.11。

**输入说明**

| 参数 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| data | Map&lt;String, Object&gt; | 是 | 结构化订阅输入（如订阅条件、上报数据格式字段列表） |
| schema | Map&lt;String, Object&gt; | 是（非空） | 字段语义 JSON Schema |
| templateUri | TemplateUri | 是 | Notification-T 模板 |

**请求样例**

```java
Map<String, Object> data = Map.of(
        "condition", "子网名称：xx子网",
        "reportFormat", List.of(
                Map.of("name", "业务抢通方案执行状态", "values", List.of("未启动", "已结束"), "required", true),
                Map.of("name", "投诉诊断任务流水号", "required", true),
                Map.of("name", "OSS侧事件流水号", "required", true),
                Map.of("name", "接入端口名称", "required", true),
                Map.of("name", "是否已授权OMC自动抢通", "values", List.of("是", "否"), "required", true),
                Map.of("name", "业务抢通方案名称", "required", true),
                Map.of("name", "业务抢通方案详情", "required", true),
                Map.of("name", "业务抢通方案执行结束时间", "required", false)));

Map<String, Object> dataSchema = Map.of(
        "type", "object",
        "properties", Map.of(
                "condition", Map.of("type", "string", "description", "订阅条件，可选。待订阅的条件描述。"),
                "reportFormat", Map.of(
                        "type", "array",
                        "description", "上报通知数据格式，必选。描述所需上报内容的字段列表。",
                        "items", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "name", Map.of("type", "string"),
                                        "values", Map.of("type", "array"),
                                        "required", Map.of("type", "boolean")),
                                "required", List.of("name")))),
        "required", List.of("reportFormat"));

MetadataContent result = client.generateNotificationPromptFromDataWithSchema(
        data, dataSchema, templateUri);
```

**输出说明**

成功时返回 `MetadataContent`（结构同 [1.3.10](#1310-generatetaskpromptfromtext)，`extensionUri` 为 `https://projects.tmforum.org/a2aproject/telecommunication/extensions/Notification-T/v1`）。

失败时抛 `PromptGenerationException`（结构同 1.3.10）。编程错误：入参为 null 抛 `NullPointerException`，schema 为空 Map 抛 `IllegalArgumentException`。

**响应样例**（与 1.3.13 同模板，槽位值来自结构化输入：`订阅条件` 填充 `condition`，`上报通知数据格式` 按 `reportFormat` 列表渲染，字段排列样式示意）

```text
templateUri : Notification-T/network-layer/service-recovery/v1
extensionUri: https://projects.tmforum.org/a2aproject/telecommunication/extensions/Notification-T/v1
promptText  :
## 订阅描述
请根据以下 <通知主题>、<订阅条件>、<上报通知数据格式> 及 <预期输出> 信息，完成网络侧业务抢通事件的订阅与上报任务。

## 通知主题
业务抢通事件

## 订阅条件
子网名称：xx子网（可选）
1. 子网名称；举例：xx子网；

## 上报通知数据格式
1. 业务抢通方案执行状态，取值范围：未启动、已结束（必选）
2. 投诉诊断任务流水号（必选）
3. OSS侧事件流水号（必选）
4. 接入端口名称（必选）
5. 是否已授权OMC自动抢通，取值范围：是、否（必选）
6. 业务抢通方案名称（必选）
7. 业务抢通方案详情（必选）
8. 业务抢通方案执行结束时间（可选）

## 预期输出
1. 订阅结果，取值范围：成功、失败
2. 订阅失败原因（可选）
3. 订阅成功后，按照<上报通知数据格式>上报消息
```

### 1.3.15 validateNotificationPromptAndDataFilling

**API定义**

```java
public FilledParamData validateNotificationPromptAndDataFilling(
        String prompt, Map<String, Object> schema, TemplateUri templateUri)
```

**典型场景**：服务端Agent收到Notification-T订阅报文后校验合规性并提取订阅参数（主题/条件/上报格式），据此建立订阅关系。

**功能说明**：校验一条 Notification-T 通知订阅提示词报文是否匹配模板与槽位约束，并按调用方 Schema 提取参数（订阅主题、订阅条件、上报通知数据格式等）。流水线与异常语义同 [validateTaskPromptAndDataFilling](#1312-validatetaskpromptanddatafilling)。

**输入说明**

| 参数 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| prompt | String | 是（非空） | 待校验的通知订阅提示词报文文本 |
| schema | Map&lt;String, Object&gt; | 是 | 调用方提供的参数 JSON Schema |
| templateUri | TemplateUri | 是 | Notification-T 模板（前缀段必须为 `Notification-T`） |

**请求样例**

```java
import net.openan.a2at.sdk.server.A2ATServer;

A2ATServer server = new A2ATServer(Path.of("server.env"));

Map<String, Object> validationSchema = Map.of(
        "type", "object",
        "properties", Map.of(
                "topic", Map.of("type", "string", "description", "订阅主题（必选）。订阅的事件主题名称。"),
                "subscriptionCondition", Map.of(
                        "type", "string", "description", "订阅条件（可选）。待订阅的条件描述。"),
                "notificationDataFormat", Map.of(
                        "type", "string", "description", "上报通知数据格式（必选）。待上报的通知数据格式描述。")),
        "required", List.of("topic", "notificationDataFormat"));

// promptText 为客户端生成的通知订阅提示词报文文本
FilledParamData result = server.validateNotificationPromptAndDataFilling(
        promptText, validationSchema, templateUri);
```

**输出说明**

成功时返回 `FilledParamData`（结构同 [1.3.12](#1312-validatetaskpromptanddatafilling)），`data()` 为按 Schema 提取的参数（订阅主题、订阅条件、上报通知数据格式等）。

失败时抛 `ContentValidationException`（结构同 1.3.12）。错误码：

- `validation_semantic_rejected`（必选参数缺失或取值非法）

- `validation_llm_infrastructure_error`（LLM 基础设施故障，可重试）

- `validation_prompt_resource_not_found`（校验提示词资源缺失）

编程错误：prompt / schema / templateUri 为 null 抛 `NullPointerException`，prompt 为空白抛 `IllegalArgumentException`。

**响应样例**

```text
result.data() =
{
  topic=业务抢通事件,
  subscriptionCondition=子网名称：xx子网,
  notificationDataFormat=业务抢通事件数据包含：业务抢通方案执行状态（未启动、已结束）、投诉诊断任务流水号、OSS侧事件流水号、接入端口名称、是否已授权OMC自动抢通（是、否）、业务抢通方案名称、业务抢通方案详情。
}
```

### 1.3.16 generateAuthPromptFromText

**API定义**

```java
public MetadataContent generateAuthPromptFromText(String text, TemplateUri templateUri)
```

**典型场景**：客户端Agent将自然语言授权诉求（新增/修改/删除/查询动网操作授权策略）转换为Authorization-T报文。

**功能说明**：从自然语言文本按指定 Authorization-T 模板生成授权策略操作提示词报文，跳过场景识别。执行一次 LLM 槽位提取后确定性渲染模板。Authorization-T 槽位 Schema 随 `a2a-t-resources` 内置，classpath 资源源下开箱可用。

**输入说明**

| 参数 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| text | String | 是 | 自然语言授权描述（操作类型 + 动网操作的授权策略内容） |
| templateUri | TemplateUri | 是 | Authorization-T 模板：`StandardTemplates.AUTHORIZATION_POLICY_MANAGEMENT`（`Authorization-T/authorization-policy-management/v1`） |

**请求样例**

```java
import java.nio.file.Path;
import net.openan.a2at.sdk.client.A2ATClient;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.StandardTemplates;

A2ATClient client = new A2ATClient(Path.of("client.env"));

MetadataContent result = client.generateAuthPromptFromText(
        "加个校园专网的授权，处置用业务抢通，做个隧道调优，有效期先不填后面补",
        StandardTemplates.AUTHORIZATION_POLICY_MANAGEMENT);
```

**输出说明**

成功时返回 `MetadataContent`（结构同 [1.3.10](#1310-generatetaskpromptfromtext)，`extensionUri` 为 `https://projects.tmforum.org/a2aproject/telecommunication/extensions/Authorization-T/v1`）。

失败时抛 `PromptGenerationException`（结构同 1.3.10），如操作类型不在"新增/修改/删除/查询授权策略"范围内时以 `slot_validation_error` 拒绝。编程错误：text 或 templateUri 为 null 抛 `NullPointerException`。

**响应样例**

```text
templateUri : Authorization-T/authorization-policy-management/v1
extensionUri: https://projects.tmforum.org/a2aproject/telecommunication/extensions/Authorization-T/v1
promptText  :
## 授权策略的操作类型
新增授权策略

## 授权策略的操作描述
请根据<授权策略的操作类型>和<动网操作的授权策略列表>完成相应的授权操作，按照<预期输出>中定义的结构返回授权策略的操作执行结果。<预期输出>表示预期返回内容。

## 动网操作的授权策略列表
校园专网，业务抢通，隧道调优

## 预期输出
1. 授权操作执行结果，取值范围： 成功、失败、部分成功；
2. 授权操作执行成功时，返回执行成功的<动网操作的授权策略列表>；
3. 授权操作执行失败或部分成功时，返回失败列表，包含授权策略和失败原因；
```

### 1.3.17 generateAuthPromptFromDataWithSchema

**API定义**

```java
public MetadataContent generateAuthPromptFromDataWithSchema(
        Map<String, Object> data, Map<String, Object> schema, TemplateUri templateUri)
```

**典型场景**：客户端Agent将授权管理界面/系统按字段提交的结构化授权策略数据转换为Authorization-T报文。

**功能说明**：从结构化数据 + 语义 Schema 按指定 Authorization-T 模板生成授权策略操作提示词，跳过场景识别。语义约束同 1.3.11。

**输入说明**

| 参数 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| data | Map&lt;String, Object&gt; | 是 | 结构化授权输入（操作类型、策略数量、策略详情列表等） |
| schema | Map&lt;String, Object&gt; | 是（非空） | 字段语义 JSON Schema |
| templateUri | TemplateUri | 是 | Authorization-T 模板 |

**请求样例**

```java
import java.util.List;
import java.util.Map;

Map<String, Object> data = Map.of(
        "操作类型", "新增授权策略",
        "策略数量", 2,
        "详情", List.of(
                Map.of("业务场景", "校园专网", "处置类型", "业务抢通",
                        "操作名称", "隧道调优", "有效期", "永久生效"),
                Map.of("业务场景", "医疗专线", "处置类型", "业务恢复",
                        "操作名称", "频段调整", "有效期", "2026-06-01~2030-06-18")));

Map<String, Object> schema = Map.of(
        "type", "object",
        "properties", Map.of(
                "操作类型", Map.of(
                        "type", "string",
                        "enum", List.of("新增授权策略", "修改授权策略", "删除授权策略", "查询授权策略")),
                "策略数量", Map.of("type", "integer", "description", "要新增的策略数量"),
                "详情", Map.of(
                        "type", "array",
                        "description", "策略详情列表",
                        "items", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "业务场景", Map.of("type", "string"),
                                        "处置类型", Map.of("type", "string"),
                                        "操作名称", Map.of("type", "string"),
                                        "有效期", Map.of("type", "string"))))));

MetadataContent result = client.generateAuthPromptFromDataWithSchema(
        data, schema, StandardTemplates.AUTHORIZATION_POLICY_MANAGEMENT);
```

**输出说明**

成功时返回 `MetadataContent`（结构同 [1.3.10](#1310-generatetaskpromptfromtext)，`extensionUri` 为 `https://projects.tmforum.org/a2aproject/telecommunication/extensions/Authorization-T/v1`）。

失败时抛 `PromptGenerationException`（结构同 1.3.10）。编程错误：入参为 null 抛 `NullPointerException`，schema 为空 Map 抛 `IllegalArgumentException`。

**响应样例**

```text
templateUri : Authorization-T/authorization-policy-management/v1
extensionUri: https://projects.tmforum.org/a2aproject/telecommunication/extensions/Authorization-T/v1
promptText  :
## 授权策略的操作类型
新增授权策略

## 授权策略的操作描述
请根据<授权策略的操作类型>和<动网操作的授权策略列表>完成相应的授权操作，按照<预期输出>中定义的结构返回授权策略的操作执行结果。<预期输出>表示预期返回内容。

## 动网操作的授权策略列表
校园专网，业务抢通，隧道调优，永久生效；医疗专线，业务恢复，频段调整，2026-06-01~2030-06-18

## 预期输出
1. 授权操作执行结果，取值范围： 成功、失败、部分成功；
2. 授权操作执行成功时，返回执行成功的<动网操作的授权策略列表>；
3. 授权操作执行失败或部分成功时，返回失败列表，包含授权策略和失败原因；
```

### 1.3.18 validateAuthPromptAndDataFilling

**API定义**

```java
public FilledParamData validateAuthPromptAndDataFilling(
        String prompt, Map<String, Object> schema, TemplateUri templateUri)
```

**典型场景**：服务端Agent收到Authorization-T报文后校验合规性并提取操作类型与策略列表，据此执行授权策略管理动作。

**功能说明**：校验一条 Authorization-T 授权提示词报文是否匹配模板与槽位约束，并按调用方 Schema 提取参数（操作类型、策略列表等）。流水线与异常语义同 1.3.12。按各操作类型的字段要求做差异化校验：新增条目须含业务场景/处置类型/操作名称/有效期，修改条目须含策略标识与新有效期，删除条目可为策略标识或条件字段。

**输入说明**

| 参数 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| prompt | String | 是（非空） | 待校验的授权提示词报文文本 |
| schema | Map&lt;String, Object&gt; | 是 | 调用方提供的参数 JSON Schema（含 `操作类型`、`策略列表` 等） |
| templateUri | TemplateUri | 是 | Authorization-T 模板（前缀段必须为 `Authorization-T`） |

**请求样例**

```java
import net.openan.a2at.sdk.server.A2ATServer;

A2ATServer server = new A2ATServer(Path.of("server.env"));

Map<String, Object> paramSchema = MAPPER.readValue(
        Path.of("param-schema.json").toFile(), new TypeReference<Map<String, Object>>() {});
// paramSchema 声明：操作类型（enum：新增/修改/删除/查询授权策略）、策略列表
//（数组，条目含 策略标识/业务场景/处置类型/操作名称/有效期）

FilledParamData result = server.validateAuthPromptAndDataFilling(
        metadata.promptText(), paramSchema, StandardTemplates.AUTHORIZATION_POLICY_MANAGEMENT);
```

**输出说明**

成功时返回 `FilledParamData`（结构同 [1.3.12](#1312-validatetaskpromptanddatafilling)），`data()` 为按 Schema 提取的参数（操作类型、策略列表等）。

失败时抛 `ContentValidationException`（结构同 1.3.12）。错误码：

- `validation_semantic_rejected`（必选参数缺失或取值非法，如新增条目缺少必填字段、有效期格式错误）

- `validation_llm_infrastructure_error`（LLM 基础设施故障，可重试）

- `validation_prompt_resource_not_found`（校验提示词资源缺失）

编程错误：prompt / schema / templateUri 为 null 抛 `NullPointerException`，prompt 为空白抛 `IllegalArgumentException`。

**响应样例**

```text
result.data() =
{
  操作类型=新增授权策略,
  策略列表=[
    {策略标识=null, 业务场景=校园专网, 处置类型=业务抢通, 操作名称=隧道调优, 有效期=永久生效},
    {策略标识=null, 业务场景=医疗专线, 处置类型=业务恢复, 操作名称=频段调整, 有效期=2026-06-01~2030-06-18}
  ]
}
```

### 1.3.19 generateTaskPrompt

**API定义**

```java
public PromptGenerationResult generateTaskPrompt(Object userInput)
```

**典型场景**：客户端Agent的场景自动路由入口：不指定模板，由SDK识别用户输入所属业务场景并生成对应报文，适合场景集合已知、希望简化接入的场景。

**功能说明**：通用任务提示词生成入口，**经场景识别**自动定位模板：自然语言或结构化输入先由 LLM 识别业务场景，再按场景对应的内置模板完成槽位提取与渲染。需要显式指定模板时使用 1.3.1x 的 FromText / FromDataWithSchema 系列接口。

**输入说明**

| 参数 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| userInput | Object | 是 | 任务描述：`String`（自然语言）或 `Map<String, Object>`（结构化输入），统一经 LLM 抽取，无零 LLM 捷径 |

**请求样例**

```java
A2ATClient client = new A2ATClient(Path.of("client.env"));

PromptGenerationResult result = client.generateTaskPrompt(
        "请生成一个Incident事件订阅任务：通知主题为Incident，"
                + "订阅级别为critical、medium、high、low，上报通知数据格式为DataPart");

if (result.success()) {
    String processedPrompt = result.promptText();  // 作为A2A消息metadata发送
} else {
    System.out.println(result.failure().code() + ": " + result.failure().message());
}
```

**输出说明**

成功时（`success()` 为 `true`，不抛异常，结果随 record 返回）：

| 字段/方法 | 类型 | 说明 |
| --------- | ---- | ---- |
| success() | boolean | 恒为 true |
| promptText() | String | 渲染后的任务提示词报文文本，作为 A2A 消息 metadata 发送 |
| failure() | PromptGenerationFailure | 恒为 null |

失败时（`success()` 为 `false`，不抛异常，失败负载随结果返回）：

| 字段/方法 | 类型 | 说明 |
| --------- | ---- | ---- |
| success() | boolean | 恒为 false |
| promptText() | String | 恒为 null |
| failure() | PromptGenerationFailure | 标准化失败负载，结构见下表 |

`PromptGenerationFailure` 结构：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| code | String | 机器可读错误码：`scenario_not_matched`（场景识别未命中）、`prompt_resource_load_error`（提示词资源加载失败）、`template_not_found`（模板缺失）、`render_failed`（渲染失败） |
| message | String | 人类可读的失败描述 |
| stage | String | 失败发生的阶段：`scenario`（场景识别）、`generation`（模板加载/渲染） |

**响应样例**

成功时：

```text
result.success() = true
result.promptText() =
## 订阅描述
请根据以下 <通知主题>、<订阅条件>、<上报通知数据格式>及<预期输出> 信息，完成网络侧智能故障Incident订阅与上报任务。

## 通知主题
通知主题该主题的名称是“incident”

## 订阅条件
故障级别为“critical”、“medium”、“high”、“low”

## 上报通知数据格式
通过DataPart上报Incident数据

## 预期输出
1、订阅结果，成功或失败
2、订阅失败原因（可选）
```

失败时（输入无法命中任何内置场景）：

```text
result.success() = false
result.failure() =
{ code=scenario_not_matched, message=Scenario recognition failed., stage=scenario }
```

### 1.3.20 checkTaskPrompt

**API定义**

```java
public PromptComplianceResult checkTaskPrompt(String processedPromptText)
```

**典型场景**：服务端Agent对收到的任务报文做协议完备性把关（场景/模板/槽位合规），只需通过与失败结论、无需提取参数的场景。

**功能说明**：通用任务提示词合规校验入口（服务端）：对客户端下发的 processed task prompt 执行场景匹配、模板遵从性校验与槽位校验（是否启用由 `A2AT_PROMPT_COMPLIANCE_ENABLED` 控制）。与 `validateTaskPromptAndDataFilling` 的区别：本接口不提取参数、不接收调用方 Schema，仅给出通过与失败的标准化结论。

**输入说明**

| 参数 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| processedPromptText | String | 是 | 客户端下发的 A2A-T 协议报文文本（`Message.metadata` 中以扩展 URI 为 key 的值） |

**请求样例**

```java
import net.openan.a2at.sdk.server.A2ATServer;
import net.openan.a2at.sdk.server.model.PromptComplianceResult;

A2ATServer server = new A2ATServer(Path.of("server.env"));

PromptComplianceResult result = server.checkTaskPrompt(processedPrompt);

if (result.success()) {
    System.out.println("prompt check passed");
} else {
    System.out.println(result.failure().message());
}
```

**输出说明**

成功时（`success()` 为 `true`，不抛异常，结果随 record 返回）：

| 字段/方法 | 类型 | 说明 |
| --------- | ---- | ---- |
| success() | boolean | 恒为 true |
| failure() | PromptComplianceFailure | 恒为 null |

失败时（`success()` 为 `false`，不抛异常，失败负载随结果返回）：

| 字段/方法 | 类型 | 说明 |
| --------- | ---- | ---- |
| success() | boolean | 恒为 false |
| failure() | PromptComplianceFailure | 标准化失败负载，结构见下表 |

`PromptComplianceFailure` 结构：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| code | String | 机器可读错误码：`processed_prompt_parse_error`（报文解析/场景识别失败）、`template_not_found`（模板缺失）、`slot_validation_error`（槽位校验失败，含必填缺失、取值越界、格式不匹配） |
| message | String | 人类可读的失败描述 |
| stage | String | 失败发生的阶段：`prompt_parse`（报文解析）、`generation`（模板加载）、`slot_validation`（槽位校验） |

**响应样例**

成功时：

```text
prompt check passed
```

失败时（报文缺少必填槽位，错误码与阶段取自服务端合规流水线）：

```text
result.success() = false
result.failure() =
{ code=slot_validation_error, message=Required slot '任务对象' is missing., stage=slot_validation }
```

### 1.3.21 generateNegotiationAbortPromptFromText

**API定义**

```java
public MetadataContent generateNegotiationAbortPromptFromText(
        String text, NegotiationContext context, TemplateUri templateUri)
```

**典型场景**：协商任一方判定本轮协商无法继续（达到协商轮次上限、超时、token 消耗超限等），从自然语言的终止说明生成协商终止（abort）报文通知对端。

**功能说明**：从自然语言文本生成协商终止报文，含一步 LLM 结构化抽取（抽取终止原因）。终止报文与协商类型无关，`templateUri` 必须为 common abort 模板（`StandardTemplates.NEGOTIATION_ABORT`，URI 为 `Negotiation-T/common/abort/v1`），context 的 performative 应为 `ABORT`。

**输入说明**

| 参数 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| text | String | 是 | 协商终止原因的自然语言描述 |
| context | `NegotiationContext` | 是 | 协商会话上下文（performative 为 `ABORT`） |
| templateUri | TemplateUri | 是 | common abort 模板 |

**请求样例**

```java
MetadataContent abort = client.generateNegotiationAbortPromptFromText(
        "达到协商轮次上限，本次协商确认结束。",
        ctx,
        StandardTemplates.NEGOTIATION_ABORT);
```

**输出说明**

成功时返回 `MetadataContent`（结构同 [1.3.1](#131-generatenegotiationproposepromptfromtext)）。

失败时抛 `NegotiationGenerationException`（结构同 1.3.1）。错误码：

- `template_not_found`（模板或提示词资源缺失）

- `negotiation_content_extract_failed`（无法从文本抽取终止原因，可重试）

- `negotiation_llm_infrastructure_error`（LLM 基础设施故障，可重试）

- `negotiation_invalid_input`（文本为空或抽取内容与 abort 阶段不符）

- `negotiation_slot_missing`（缺少必需槽位）

入参为 null 抛 `NullPointerException`，templateUri 的 phase 段不是 `abort` 抛 `IllegalArgumentException`。

**响应样例**

```text
templateUri : Negotiation-T/common/abort/v1
extensionUri: https://projects.tmforum.org/a2aproject/telecommunication/extensions/Negotiation-T/v1
promptText  :
## 协商结果
Abort

## 协商终止原因
达到协商轮次上限，本次协商确认结束。
```

### 1.3.22 generateNegotiationAbortPromptFromData

**API定义**

```java
public MetadataContent generateNegotiationAbortPromptFromData(
        NegotiationAbortData data, TemplateUri templateUri)
```

**典型场景**：协商任一方程序化判定终止条件成立后，以结构化终止原因生成协商终止报文回传。

**功能说明**：从类型化数据确定性生成协商终止报文，**不调用 LLM**。终止报文与协商类型无关，`templateUri` 必须为 common abort 模板。

**输入说明**

| 参数 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| data | `NegotiationAbortData(context, content)` | 是 | 协商上下文（performative 为 `ABORT`）+ 终止内容 |
| templateUri | TemplateUri | 是 | common abort 模板 |

终止内容：`NegotiationAbortContent(terminationReason)`，terminationReason 为协商终止原因（必填）。

**请求样例**

```java
import net.openan.a2at.sdk.negotiation.content.NegotiationAbortContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationAbortData;

MetadataContent abort = client.generateNegotiationAbortPromptFromData(
        new NegotiationAbortData(
                ctx,
                new NegotiationAbortContent("达到协商轮次上限，本次协商确认结束。")),
        StandardTemplates.NEGOTIATION_ABORT);
```

**输出说明**

成功时返回 `MetadataContent`（结构同 [1.3.1](#131-generatenegotiationproposepromptfromtext)）。

失败时抛 `NegotiationGenerationException`（结构同 1.3.1）。错误码：

- `template_not_found`（模板缺失）

- `negotiation_slot_missing`（渲染缺少必需槽位）

编程错误：入参或其 context 为 null 抛 `NullPointerException`；终止原因为空或 phase 段不是 `abort` 抛 `IllegalArgumentException`。

**响应样例**

```text
templateUri : Negotiation-T/common/abort/v1
extensionUri: https://projects.tmforum.org/a2aproject/telecommunication/extensions/Negotiation-T/v1
promptText  :
## 协商结果
Abort

## 协商终止原因
达到协商轮次上限，本次协商确认结束。
```

### 1.3.23 validateAbortPromptAndDataFilling

**API定义**

```java
public FilledParamData validateAbortPromptAndDataFilling(
        String prompt, NegotiationContext context, Map<String, Object> schema, TemplateUri templateUri)
```

**典型场景**：协商一方校验对端发来的终止报文，提取终止原因，据此结束本地协商状态并回收任务。

**功能说明**：校验一条协商终止（abort）报文并按 Schema 提取参数。流水线同 1.3.7，区别仅在于：报文须满足 abort 阶段的语义约束（结论为 Abort、携带协商终止原因板块），`templateUri` 必须为 common abort 模板。

**输入说明**：同 1.3.7，prompt 为终止报文文本，templateUri 为 common abort 模板。

**请求样例**

```java
FilledParamData abortParams = server.validateAbortPromptAndDataFilling(
        abortPrompt, ctx, schema, StandardTemplates.NEGOTIATION_ABORT);
```

**输出说明**

成功时返回 `FilledParamData`（结构同 [1.3.7](#137-validateproposepromptanddatafilling)），`data()` 携带终止原因中按 Schema 提取的参数与上下文参数。

失败时抛 `NegotiationParamExtractionException`（结构同 1.3.7）。错误码：

- `negotiation_invalid_input`（报文不是 abort 协商消息或 context 为 null）

- `negotiation_rule_violation`（协商上下文违反结构规则）

- `negotiation_semantic_rejected`（结论不是 Abort 或缺少终止原因板块）

- `negotiation_llm_infrastructure_error`（LLM 基础设施故障，可重试）

- `template_not_found`（校验提示词资源缺失）

编程错误：prompt 或 schema 为 null 抛 `NullPointerException`，prompt 为空白或 phase 段不是 `abort` 抛 `IllegalArgumentException`。

**响应样例**

```text
abortParams.data() =
{
  termination_reason=达到协商轮次上限，本次协商确认结束。,
  id=3dbc13b5-bd57-4c2b-b503-24e381b6c8d3,
  round=1,
  maxRounds=5
}
```
