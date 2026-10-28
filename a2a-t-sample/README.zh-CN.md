# a2a-t-sample

`a2a-t-sample` 是 A2A-T Java SDK 的示例模块，包含客户端与服务端两个可直接运行的入口。

当前示例基于 `a2a-java v1.0.0.Beta1` 运行真实的 A2A `HTTP+JSON/REST` 链路：
- `a2a-t-client` 仅用于生成结构化 prompt
- `a2a-t-server` 仅用于校验结构化 prompt

## 入口类

- 服务恢复样例客户端：`net.openan.a2at.sample.service_recovery.client.ClientSampleMain`
- 服务恢复样例服务端：`net.openan.a2at.sample.service_recovery.server.ServerSampleMain`
- 订阅事件样例客户端：`net.openan.a2at.sample.subscribe_incident.client.ClientSampleMain`
- 订阅事件样例服务端：`net.openan.a2at.sample.subscribe_incident.server.ServerSampleMain`
- 协商端到端样例（4报文）：`net.openan.a2at.sample.negotiation.NegotiationDemoApp`
- 授权策略样例（Authorization-T）：`net.openan.a2at.sample.authz_policy.AuthzSampleMain`
- 可观测性样例（Console 离线模式）：`net.openan.a2at.sample.observability.ObservabilitySampleMain`，说明见 [observability/README-zh.md](observability/README-zh.md)

## 模块内资源

- 服务恢复样例客户端环境模板：`sample/service-recovery/client/client.env`
- 服务恢复样例服务端环境模板：`sample/service-recovery/server/server.env`
- 服务恢复样例客户端场景输入：`sample/service-recovery/client/input-with-text.txt`、`sample/service-recovery/client/input-with-data.json`、`sample/service-recovery/client/schema.json`
- 订阅事件客户端环境模板：`sample/subscribe_incident/client/client.env`
- 服务端环境模板：`sample/subscribe_incident/server/server.env`
- 客户端场景输入：`sample/subscribe_incident/client/scenario.json`
- 协商样例环境模板：`sample/negotiation/negotiation.env`
- 协商样例场景输入（slot schema + 参数缺失/补齐数据）：`sample/negotiation/scenario.json`

## 协商（Negotiation）端到端样例

协商样例是单进程端到端 demo，复用 `subscribe_incident` 的 a2a-java SDK 真实 HTTP+JSON 链路，覆盖 A2A-T 协议定义的"传输专线业务投诉诊断"4 报文信息协商流程：客户端 `A2ATClient` 与服务端 `A2ATServer` 通过 a2a-java `RestTransport`（`message:send`）+ `EmbeddedA2AHttpServer`（`DefaultRequestHandler` + `NegotiationAgentExecutor`）经 HTTP A2A 交互，协商 prompt 放进 A2A `Message.metadata`（Negotiation-T 扩展 URI 作 key），`A2A-Extensions` 头声明扩展。

4 报文流转：

| 报文 | 方向 | 内容 | 任务状态 |
|---|---|---|---|
| 1 | client→server | Task-T（参数缺失） | → |
| 2 | server→client | Negotiation-T 信息协商请求（动态列出缺失参数） | INPUT_REQUIRED |
| 3 | client→server | Task-T（参数补齐）+ Negotiation-T accept | → |
| 4 | server→client | 诊断结果（从提取参数动态生成） | COMPLETED |

**运行需要真实 LLM API key**：`fromData` 只让协商报文生成环节变成确定性规则渲染（不调 LLM），Task-T 槽位提取与语义校验仍调用 LLM。缺 key 时启动即报错退出。

详细 API 见 [A2A-T 协商 API 文档](../docs/zh/A2A-T-Negotiation-API-Reference.md)，设计说明见 [Negotiation-Sample-Design.md](docs/Negotiation-Sample-Design.md)。

### 协商样例结构

| 目录 | 作用 |
|---|---|
| `negotiation/` | 入口 `NegotiationDemoApp`：启动嵌入式 HTTP server + 跑 client，`--fromText` 切换策略 |
| `negotiation/client/` | `NegotiationClient`：4 报文编排 + 传输端点选择（按 AgentCard 能力走 message:stream / message:send） |
| `negotiation/server/` | `NegotiationAgentExecutor`（validateAndFillingTaskData→缺失检测→协商请求→诊断）+ `NegotiationServerRuntime`（HTTP server 装配）+ `DiagnosisService`（从 FilledParamData 动态生成诊断） |
| `negotiation/shared/` | 策略层（`NegotiationStrategy` + `FromDataStrategy`/`FromTextStrategy`）、A2A metadata 桥接（`NegotiationMessage`）、扩展/模板 URI 常量（`DemoConstants`）、场景数据加载器（`ScenarioData`，数据在 `scenario.json`）、协商校验 schema（`InformationNegotiationSchemas`，与 private-line 样例共用） |

### 协商样例启动

1. 复制 `a2a-t-sample/src/main/resources/sample/negotiation/negotiation.env`，补充可用的 `A2AT_LLM_API_KEY`
2. 启动协商样例（单进程，嵌入式 a2a-java HTTP server + client 经真实 HTTP A2A 交互）：

```bash
java @a2a-t-sample/target/negotiation.javaargs.txt /path/to/.env

# fromText 策略（协商报文由 LLM 生成）
java @a2a-t-sample/target/negotiation.javaargs.txt --fromText /path/to/.env

# 强制阻塞端点（默认按 AgentCard 能力优先 message:stream）
java @a2a-t-sample/target/negotiation.javaargs.txt --no-stream /path/to/.env
```

如果不传参数，`NegotiationDemoApp` 会回退到包内的 `sample/negotiation/negotiation.env`（该模板 key 为空，仅用于占位）。Windows 控制台如遇中文乱码，先执行 `chcp 65001`。

## 业务抢通（Service Recovery）样例

验证 `generateNotificationPromptFromText` / `generateNotificationPromptFromDataWithSchema` / `validateAndFillingNotificationData` 三个 API。客户端在一个进程内跑两轮订阅：轮① 自然语言输入，轮② 结构化输入 + 数据 schema，经服务端校验并建立真实订阅。每个订阅任务上报 5 个 notification 后自动完结。

支持 mock LLM 降级：未填 `A2AT_LLM_API_KEY` 时自动使用确定性 mock 响应，无需外部依赖即可完整跑通。

### 业务抢通样例启动

1. 修改仓库根目录下的 `client.env`，补充可用的 `A2AT_LLM_API_KEY`（可选，缺省时自动使用 mock LLM）
2. 启动服务端：

```bash
java @a2a-t-sample/target/service-recovery-server.javaargs.txt
```

3. 另开一个窗口启动客户端：

```bash
java @a2a-t-sample/target/service-recovery-client.javaargs.txt
```

如果不传参数，会回退到包内的 `sample/service-recovery/{client,server}/{client,server}.env`。

## 协商接口闭环评测（Negotiation Interface Eval）

`NegotiationEvalApp` 是纯 Java 评测入口，对 `eval-suite.json` 里的每个用例按报文顺序驱动协商 prompt 接口，逐步采集证据并输出可回放的 JSON 报告：

| 步骤 | 角色 | 接口 | 说明 |
|---|---|---|---|
| 1. Task-T 生成 | client | `generateTaskPromptFromDataWithSchema` / `FromText` | 按 case 的 `channel` 字段选通道；fromData 传入结构化 JSON（每个 key 对应一个槽位值）+ JSON schema |
| 2. 服务端校验 | server | `validateTaskPromptAndDataFilling` | 传入 JSON schema 做缺槽检测 |
| 3. Negotiation-T propose 生成 | server | `generateNegotiationProposePromptFromData` / `FromText` | 按 case 的 `negotiation_channel` 字段选通道 |
| 4. 出站 propose 自检 | server | `validateProposePromptAndDataFilling` | 发送前自检，显式 `NegotiationContext` |
| 5. 入站 propose 校验 | client | `validateProposePromptAndDataFilling` | 客户端校验收到的协商请求，提取"需要补充的槽位清单"，补槽由该清单驱动 |
| 6. 客户端补槽 + accept 生成 | client | `generateTaskPromptFromDataWithSchema` + `generateNegotiationAcceptPromptFromData` / `FromText` | 补槽值来自 suite 的 `client_fill_values` |
| 7. 入站 accept 校验 | server | `validateAcceptPromptAndDataFilling` | 校验抽取的槽值与补槽值一致 |
| 8. 二次 Task-T 校验 | server | `validateTaskPromptAndDataFilling` | 断言闭环后无缺槽 |

**API 调用证据**：报告的每个步骤都记录 `api_calls`（SDK 方法名 + 完整输入参数，含传入的 JSON schema 全文），可从报告直接核对每次调用是否携带 schema、用了哪个模板 URI 和协商上下文。

**LLM 调用证据**：每个步骤还记录 `llm_calls`——该步骤内每次大模型调用的完整请求（system + user prompt 全文、JSON schema、temperature、max_tokens）与响应（content、model、usage、耗时），调用失败时也记录请求与错误。用例失败时可直接从报告看到模型实际收到的 prompt（含槽位描述、值约束、schema 注入后的最终形态），据此反向定位是哪段提示词/约束导致了误判并调优。

**校验参数 schema**：专线投诉协商样例的三个校验接口使用独立的业务无关参数 schema，定义在 `shared/InformationNegotiationSchemas`：`propose` 提取 `items[{name, requirement}]` 及可选的 `relationship`，`accept` 提取 `items[{name, value}]`，`reject` 提取 `items[{name, reason}]`。schema 只约束提取结果的结构，具体信息项名称、要求、值和原因均从协商报文中提取，不包含场景固定字段或枚举值。

**协议约定**（两条常见问题）：

1. **服务端发起协商后，客户端是否还需调用 validate？** 是。SDK 在 client/server 两个门面上提供对称的 `validate*PromptAndDataFilling` API：发送方出站自检（步骤 4），接收方入站校验（步骤 5/7）。客户端收到 propose 后应调 `validateProposePromptAndDataFilling` 提取需要补充的槽位清单，再据此补槽。
2. **客户端接受协商并补充报文后，服务端如何合并原始模板与协商补充内容？** 无增量合并 API。合并发生在客户端：客户端持有完整参数集（round-1 提取参数 + 补槽值），用 `generateTaskPromptFromDataWithSchema` **重新渲染整份 Task-T**（步骤 6），随 accept 一并发送；服务端对完整报文做 `validateTaskPromptAndDataFilling`（步骤 8）提参，不做"原始模板+增量"拼接。

**槽位契约**：评测直接使用 SDK 内置的 private-line-complaint 模板（不做任何资源改动）。模板将"任务上下文"定义为一个组合槽，其内部要求 4 个子项（投诉分类[必选]、问题发生时间[可选]、OSS侧事件流水号[必选]、投诉详情[可选]）。评测的 `task_schema`（eval-suite.json 内）把该组合槽细化为 4 个独立子槽位用于**校验**。**生成门**（内置 slot.json 的 required）要求任务对象与任务上下文非空：任一为空即在生成期 fail-fast，协商不触发；**校验门**（调用方 schema 的 required）检出非空上下文内缺失或非法的必选子字段并触发协商。fromData 用例输入为结构化 JSON——每个 key 对应一个明确的子项值（如 `"OSS侧事件流水号": "event-id-20260511-09013"`）；fromText 用例若走到补槽环节则携带 `client_data`（客户端自身掌握的结构化知识，即其文本引用过的字段），补槽重渲染基于该知识而非服务端提取结果。

通道语义：fromData 与 fromText 走**同一条 LLM 槽位抽取管线**（`llm_calls` 证据可见）——fromData 的抽取做归一化映射（把结构化子字段组合为模板槽值），fromText 的抽取从自然语言中提取槽位；抽取完成后模板渲染均为确定性规则。协商报文的 fromData 生成则是纯确定性渲染（类型化数据 → 模板，零 LLM 调用），fromText 协商生成才走 LLM 抽取。所有校验接口均走 SDK 完整管线（规则门 + 语义 LLM 调用）。**需要真实 LLM API key**（env 文件参考根目录 `env.example`，至少配置 `A2AT_LLM_PROVIDER` / `A2AT_LLM_MODEL` / `A2AT_LLM_API_KEY` / `A2AT_LLM_BASE_URL`）。

运行命令：

```bash
# 1. 打包（首次，生成 target/eval.javaargs.txt）
mvn -pl a2a-t-sample -am -DskipTests package

# 2. 跑全量用例（fromData 和 fromText 两条用例轨，每个 case 按各自 channel 配置执行）
java @a2a-t-sample/target/eval.javaargs.txt /path/to/.env

# 3. 只跑指定 case（可重复传 --case）
java @a2a-t-sample/target/eval.javaargs.txt --case PLC-D03 /path/to/.env

# 4. 只跑其中一条用例轨：fromData 或 fromText 分开测评，报告可分别输出后横向对比
java @a2a-t-sample/target/eval.javaargs.txt --channel fromData --out eval-report-fromdata.json /path/to/.env
java @a2a-t-sample/target/eval.javaargs.txt --channel fromText --out eval-report-fromtext.json /path/to/.env

# 5. 强制全量走 fromText 协商生成通道（不改用例文件）
java @a2a-t-sample/target/eval.javaargs.txt --negotiation-channel fromText /path/to/.env

# 6. 指定报告输出路径（默认 ./eval-report.json）
java @a2a-t-sample/target/eval.javaargs.txt --out eval-report-my-model.json /path/to/.env
```

**换用其他模型测试**：只需在 env 文件里改 4 个 LLM 变量（`A2AT_LLM_PROVIDER` / `A2AT_LLM_MODEL` / `A2AT_LLM_API_KEY` / `A2AT_LLM_BASE_URL`），无需改任何代码或用例。报告的 `llm` 字段会记录当次使用的 provider / model / base_url，`negotiation_channel` 字段记录当次通道（`per-case` 或强制值），便于横向对比多个模型的报告。

## 专线投诉信息协商 Qwen 评测

入口 `NegotiationQwenEvaluationMain` 用于验证传输专线业务投诉诊断场景的信息协商闭环。每条用例按以下四个阶段执行：

1. 生成 propose 协商报文
2. 校验 propose 协商报文并提取参数
3. 根据用例结论生成 accept 或 reject 协商报文
4. 校验 accept 或 reject 协商报文并提取参数

全量用例会覆盖三组生成/校验接口：propose、accept、reject。评测的通过条件是本条用例的四个阶段都成功返回；自然语言提取结果允许存在等义表述差异。

### 运行 Qwen 评测

先复制 `src/main/resources/sample/private-line-complaint-negotiation/evaluation/qwen.env.example` 到本地文件（例如 `qwen.env`），填写目标网络中的模型地址和 API key。环境文件已被 Git 忽略，不要将真实凭据提交到仓库。

首次运行先打包，Maven 会生成 `target/negotiation-qwen-evaluation.javaargs.txt`：

```bash
mvn -pl a2a-t-sample -am -DskipTests package
```

评测入口参数依次为：环境文件、报告路径、过程日志路径、用例选择器。用例选择器支持 `smoke`（20 条）、`full`（100 条）或逗号分隔的用例 ID：

```bash
# 20 条 smoke 用例
java @a2a-t-sample/target/negotiation-qwen-evaluation.javaargs.txt \
  /path/to/qwen.env \
  a2a-t-sample/target/negotiation-qwen-smoke-report.json \
  a2a-t-sample/target/negotiation-qwen-smoke-process.jsonl \
  smoke

# 100 条全量用例
java @a2a-t-sample/target/negotiation-qwen-evaluation.javaargs.txt \
  /path/to/qwen.env \
  a2a-t-sample/target/negotiation-qwen-report.json \
  a2a-t-sample/target/negotiation-qwen-process.jsonl \
  full

# 只复现指定用例
java @a2a-t-sample/target/negotiation-qwen-evaluation.javaargs.txt \
  /path/to/qwen.env \
  a2a-t-sample/target/negotiation-qwen-repro-report.json \
  a2a-t-sample/target/negotiation-qwen-repro-process.jsonl \
  P02,P14,R27
```

不传报告和过程日志路径时，默认写入 `a2a-t-sample/target/negotiation-qwen-report.json`，过程日志使用同名报告加 `-process.jsonl` 后缀；不传用例选择器时运行全量用例。

### 评测产物

- 报告 JSON 的 `cases` 保存每条用例的输入、四阶段结果、通过状态和错误信息。
- 报告 JSON 的 `api_trace` 保存每次 SDK 接口调用的完整 request/response、接口名、阶段和耗时。
- 过程日志是 JSONL，每行对应一次阶段调用，包含 `timestamp`、`case_id`、`step`、`api`、`request`、`response`、`elapsed_ms` 和 `outcome`；调用失败时额外记录 `error`。

生成接口的 request 使用 `text`、`context`、`template_uri`，校验接口的 request 使用 `prompt`、`context`、`schema`、`template_uri`。生成接口的 response 包含生成的 prompt，校验接口的 response 包含 `filled_data`。这些字段可用于区分输入构造、模板渲染和校验提参环节的问题。

**env 配置**：复制以下内容为 `eval.env`，填入你的模型信息即可（完整变量说明见根目录 `env.example`）：

```bash
A2AT_LANGUAGE=zh-CN
A2AT_PROMPT_SOURCE_TYPE=classpath
A2AT_LLM_PROVIDER=openai                     # OpenAI 兼容端点均为 openai
A2AT_LLM_MODEL=glm-5.2                       # 换成你的模型名
A2AT_LLM_API_KEY=sk-xxxxxxxx                 # 你的 API key
A2AT_LLM_BASE_URL=https://your-endpoint/v1   # OpenAI 兼容 base URL
A2AT_NEGOTIATION_STATE_STORE_TYPE=in_memory
A2AT_LLM_TIMEOUT_SECONDS=180                 # 推理型模型建议调大（默认 60）
A2AT_LLM_MAX_TOKENS=8192                     # 推理型模型建议调大（默认 2000）
```

`*.env` 已被 .gitignore 忽略，携带真实 key 的 env 文件不会被提交。

用例集：`sample/negotiation/eval/eval-suite.json`（**20 条用例，fromData（PLC-D01~D10）与 fromText（PLC-T01~T10）两条轨各 10 条**，覆盖同一场景矩阵：完整输入不触发 / 必选字段缺失（生成期拦截或触发协商）/ 双必选子字段缺失 / 可选字段缺失不触发 / 值无效（对象形态、分类枚举、流水号格式）/ 字段错位归位 / 口语化与推断 / 负例补槽被拒）。报告中每个 case 输出逐步证据（`api_calls`、`llm_calls`、生成 prompt 原文、校验判定与抽取参数、耗时），`metrics` 汇总通过率。

## 协商 fromData 接口专项评测（Negotiation FromData API Eval）

`NegotiationFromDataApiEvalApp` 是**聚焦协商接口本身**的专项验证入口，验证 **6 个协商接口**（生成 3 个 + 校验提参 3 个），不涉及 Task-T 等非协商接口。每个用例是一次"生成 → 校验提参"闭环：

**生成接口**（确定性渲染，零 LLM）：
- `A2ATServer.generateNegotiationProposePromptFromData`（propose，server 角色）
- `A2ATClient.generateNegotiationAcceptPromptFromData`（accept，client 角色）
- `A2ATClient.generateNegotiationRejectPromptFromData`（reject，client 角色）

**校验提参接口**（语义 LLM 管线），由协议中的**接收方角色**调用：
- `A2ATClient.validateProposePromptAndDataFilling`（client 校验收到的 propose）
- `A2ATServer.validateAcceptPromptAndDataFilling`（server 校验收到的 accept）
- `A2ATServer.validateRejectPromptAndDataFilling`（server 校验收到的 reject）

**输入为传统结构化数据**：每个条目就是一个 key-value——key 是 Task-T 模板的槽位名称（必选：任务对象/投诉分类/OSS侧事件流水号；可选：问题发生时间/投诉详情），value 是原子数据值（如 `"投诉分类": "专线中断"`、`"OSS侧事件流水号": "event-id-20260602-08841"`），不含自然语言段落，不构造模板传错场景。断言分两段：

| 阶段 | 断言 |
|---|---|
| 生成 | 报文逐字包含每个 item 的名称与值、relationship 原文、`Accept`/`Reject` 结论标记、模板 URI 匹配 |
| 校验提参 | 校验通过，且**提取出的参数与输入的 key-value 完全一致**（提参保真度） |

**用例矩阵**（按 Task-T 字段模型的组合）：propose 覆盖全部必选缺失/单必选缺失×3/双必选缺失/必选+可选/仅可选/空边界共 8 条；accept 覆盖全必选补齐/单必选补齐/全 5 字段补齐/可选补齐共 4 条；reject 覆盖单字段/双字段无法提供（字段名+原因，模板字面形态）共 2 条。用例集：`sample/negotiation/eval/fromdata-api-suite.json`。

运行命令（生成段零 LLM；**校验提参段走语义 LLM，需真实 key**，两段证据均在报告 `llm_calls` 中）：

```bash
mvn -pl a2a-t-sample -am -DskipTests package
java @a2a-t-sample/target/fromdata-eval.javaargs.txt --out fromdata-eval-report.json /path/to/.env
```

报告含每个 case 两步的 `api_calls`（方法+完整 JSON 入参）、生成的报文原文、校验提取参数、逐条断言与 `llm_calls` 证据。

## 授权策略（Authorization-T）演示 Demo

授权策略 Demo 是单进程直调 SDK 示例：客户端生成 Authorization-T prompt → 服务端校验合规性并提取参数。题集按场景分组，覆盖新增/修改/删除/查询四种操作类型及各类校验失败路径：

| 题集 | 入口 | 说明 |
|---|---|---|
| 冒烟集 `scenarios.json` | 默认题集 | 15 例，负例代表 + 多条列表 + 变异 schema 组 |
| 全量集 `scenarios-100.json` | 全量评测 | 100 例，评分基线 |
| 留出集 `scenarios-holdout-100.json` | 防过拟合 | 100 例，与全量同构 |

入口类：`net.openan.a2at.sample.authz_policy.AuthzSampleMain`

捆绑环境模板：`sample/authz-policy/authz.env`（需配置真实 LLM key：`A2AT_LLM_PROVIDER` / `A2AT_LLM_MODEL` / `A2AT_LLM_API_KEY`）

场景清单：`sample/authz-policy/scenarios.json`（增删场景零代码）

启动命令：

```bash
java @a2a-t-sample/target/authz.javaargs.txt [env-path]
```

退出码约定：`0` 表示全部通过；非零表示存在 `FAIL` 或 `ERROR`。

## 客户端启动

1. 修改仓库根目录下的 `client.env`，补充可用的 `A2AT_LLM_API_KEY`
2. 如需修改默认请求内容，可编辑 `sample/subscribe_incident/client/scenario.json`
3. 启动客户端：

```bash
java @a2a-t-sample/target/client.javaargs.txt
```

如果不传参数，`ClientSampleMain` 会回退到包内的 `sample/subscribe_incident/client/client.env`。

## 服务端启动

1. 修改仓库根目录下的 `server.env`，补充可用的 `A2AT_LLM_API_KEY`
2. 启动服务端：

```bash
java @a2a-t-sample/target/server.javaargs.txt
```

如果不传参数，`ServerSampleMain` 会回退到包内的 `sample/subscribe_incident/server/server.env`。

## Git Bash 本地调试

先编译打包：

```bash
mvn "-Dmaven.repo.local=.mvn/repository" -pl a2a-t-sample -am -DskipTests package
```

启动服务端：

```bash
java @a2a-t-sample/target/service-recovery-server.javaargs.txt
```

另开一个窗口启动客户端：

```bash
java @a2a-t-sample/target/service-recovery-client.javaargs.txt
```

协商样例（无需启动服务端，单进程，需指定含 LLM key 的 .env）：

```bash
java @a2a-t-sample/target/negotiation.javaargs.txt /path/to/.env
```

 Task-T 样例（无需启动服务端，单进程，需指定含 LLM key 的 .env）

```bash
java @a2a-t-sample/target/taskt.javaargs.txt /path/to/.env
