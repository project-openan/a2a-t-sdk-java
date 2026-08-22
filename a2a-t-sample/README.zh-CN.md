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
- 结构化数据协商样例（fromData 3x3）：`net.openan.a2at.sample.negotiation.fromdata.FromDataNegotiationSample`
- 自然语言协商样例（fromText 3x3）：`net.openan.a2at.sample.negotiation.fromtext.FromTextNegotiationSample`
- 授权策略样例（Authorization-T）：`net.openan.a2at.sample.authz_policy.AuthzSampleMain`

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
| `negotiation/shared/` | 策略层（`NegotiationStrategy` + `FromDataStrategy`/`FromTextStrategy`）、A2A metadata 桥接（`NegotiationMessage`）、扩展/模板 URI 常量（`DemoConstants`）、场景数据加载器（`ScenarioData`，数据在 `scenario.json`）、样例公共辅助（`NegotiationSampleSupport`） |
| `negotiation/fromdata/`、`negotiation/fromtext/` | 9 用例 API 验证样例（见下文两节） |

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

## 服务恢复（Service Recovery）样例

验证 `generateNotificationPromptFromText` / `generateNotificationPromptFromDataWithSchema` / `validateAndFillingNotificationData` 三个 API。客户端在一个进程内跑两轮订阅：轮① 自然语言输入，轮② 结构化输入 + 数据 schema，经服务端校验并建立真实订阅。每个订阅任务上报 5 个 notification 后自动完结。

支持 mock LLM 降级：未填 `A2AT_LLM_API_KEY` 时自动使用确定性 mock 响应，无需外部依赖即可完整跑通。

### 服务恢复样例启动

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

## 结构化数据协商样例（fromData）

验证 `generateNegotiationProposePromptFromData` / `generateNegotiationAcceptPromptFromData` / `generateNegotiationRejectPromptFromData` 三个 API（结构化输入，规则渲染）。覆盖 3 种协商类型 × 3 种阶段 = 9 个用例：

| 类型 | propose | accept | reject |
|---|---|---|---|
| 信息协商 | 请求补充接入端口名称/投诉分类 | 交付补充信息 | 站点清单不可用，无法提供 |
| 目标协商 | 意图理解 + 待澄清 | 确认节能目标 | 区域信息无法澄清 |
| 可行性协商 | 请求评估节能可行性 | 确认可行 | 供电约束下不可行 |

```bash
java @a2a-t-sample/target/fromdata.javaargs.txt /path/to/.env
```

9 个用例的协商报文生成均为确定性规则渲染，不调 LLM；但 SDK 配置仍需有效的 `A2AT_LLM_API_KEY`（`A2ATClient` 构造即校验）。

## 自然语言协商样例（fromText）

验证 `generateNegotiationProposePromptFromText` / `generateNegotiationAcceptPromptFromText` / `generateNegotiationRejectPromptFromText` 三个 API（自然语言输入，LLM 结构化抽取 + 渲染）。同样覆盖 3 种类型 × 3 种阶段 = 9 个用例。与 fromData 的差异：输入是自然语言文本，SDK 用 LLM 抽取为类型化内容后渲染。**需要真实 LLM API key**。

```bash
java @a2a-t-sample/target/fromtext.javaargs.txt /path/to/.env
```

复用 `shared/NegotiationSampleSupport` 公共辅助（SessionId、模板 URI 常量、summary），fromData 和 fromText 差异仅在输入构造（record vs 自然语言文本）。

## 授权策略（Authorization-T）演示 Demo

授权策略 Demo 是单进程直调 SDK 示例：客户端生成 Authorization-T prompt → 服务端校验合规性并提取参数。覆盖 3 个预置场景：

| 场景 | 入口 | 说明 |
|---|---|---|
| `add-from-text` | 自然语言 | 新增两条动网操作授权策略 |
| `add-from-data` | 结构化数据 | 结构化输入新增授权策略 |
| `invalid-request` | 自然语言 | 不在四种操作类型之内，预期拒绝 |

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