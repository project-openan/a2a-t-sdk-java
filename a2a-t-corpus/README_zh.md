# a2a-t-corpus — A2A-T 准确性验证语料

A2A-T SDK 的数据驱动准确性验证：把 JSON 工作流用例经生产 SDK 装配链路对**真实 LLM** 执行，产出逐步 API/LLM 完整转录、时延与 Token 指标，并以 95%+ 准确率达标线支撑提示词调优。

阶段策略（当前）仅实现 **Task-T**，作为标杆迭代优化；Notification-T / Negotiation-T / Authorization-T 待 Task-T 稳定后按同结构扩展（套件类 + `resources/` + 注册表条目，零分叉）。

- **纯测试模块**：无 `src/main`，不进 BOM、不参与发布（`maven.deploy.skip/source.skip/javadoc.skip=true`），且从 CI 排除（`mvn ... -pl '!a2a-t-corpus'`）。
- **必须配置真实 LLM**：缺少三个必填 `A2AT_LLM_*` 配置时 fail-fast 并给出配置指引。`.env` 键名与项目根 `env.example` 保持一致，corpus 模板即根模板的精调子集。

## 目录

```
a2a-t-corpus/
├── pom.xml / env.example / .env（密钥，gitignore）/ README.md / README_zh.md
├── schemas/          input-case.schema.json、output-result.schema.json（格式一手定义）
├── tools/            inputCsvToJson(.py/.sh)、outputJsonToCsv(.py/.sh)、csv-templates/
└── src/test/java/net/openan/a2at/sdk/corpus/
    ├── engine/       框架核心：config/loader/registry/engine/llm/assertion/report/discover + CorpusWorkFlowSuite
    ├── task/         TaskTFromTextWorkFlowTest、TaskTFromDataWorkFlowTest、resources/<场景>/
    └── self/         语料自守卫元测试（无 LLM）
```

`task/resources/<场景>/` 下为 `input_case_from_text.json` 与 `input_case_from_data.json`（用例设计，人工构造）；`output_result_*.json` 由引擎在每次运行后写回。

## 快速开始

```bash
cp a2a-t-corpus/env.example a2a-t-corpus/.env   # 填写 A2AT_LLM_BASE_URL / API_KEY / MODEL
mvn -pl a2a-t-corpus -am test -Dtest=TaskTFromTextWorkFlowTest
mvn -pl a2a-t-corpus test -Dtest=TaskTFromTextWorkFlowTest -Dcorpus.scenario=private-line-complaint
mvn -pl a2a-t-corpus test -Dtest=TaskTFromTextWorkFlowTest -Dcorpus.scenario='private-*' -Dcase.filter='TC0000000*'
```

- `-Dcorpus.scenario`：场景名 glob（`*` 通配，逗号分隔）；`-Dcase.filter`：用例 id glob。
- `-Dtest` 过滤在 `-am` 下作用于整个反应堆；父 POM 已配置 surefire `failIfNoSpecifiedTests=false`，上游模块无同名测试类不会导致构建失败。
- 每条已执行用例完成即打印到控制台；失败/崩溃用例同时打印完整 interaction 轨迹；转录文件只反映本次执行（过滤运行会以命中的用例整体覆盖文件）。
- `-Dcorpus.output.dir=<dir>`：把输出重定向到指定目录（缺省写回场景目录；summary 默认落 `target/corpus/`）。
- 无 LLM 时先跑结构自守卫：`mvn -pl a2a-t-corpus test -Dtest=CorpusSelfGuardTest`。

## 用例 JSON 契约（v1，一手定义见 `schemas/`）

```json
{
  "id": "TC00000001", "caseDimension": "01-意图清晰参数完整", "caseDesc": "标准专线业务中断投诉",
  "caseCategory": "正常",
  "input": [
    { "api": "generateTaskPromptFromText",
      "args": { "text": "发生专线业务中断，接入端口名称为P781-……", "templateUri": "Task-T/network-layer/private-line-complaint/v1" } },
    { "api": "validateTaskPromptAndDataFilling",
      "args": { "schema": { "…": "JSON Schema" }, "templateUri": "…", "promptText": { "$fromStep": 1, "$field": "promptText" } } }
  ],
  "expected": [
    { "result": "success", "data": {}, "error": { "errCode": "", "errMessage": "" } },
    { "result": "success", "data": { "accessPort": "P781-……" }, "error": { "errCode": "", "errMessage": "" } }
  ]
}
```

- `id` 使用 `TC`（TestCase）前缀，如 `TC00000001`；id 在单个用例文件内唯一。
- `input` / `expected` 为**等长数组**，顺序即执行序；步数不限，同一 API 可多次出现。
- `expected.result`：`success` / `error`；`data` 为子集断言（可选 `dataExact` 收紧为全量相等）；`error.errCode` 必须位于 SDK 错误码目录。
- `{"$fromStep": N, "$field": "promptText"}` 按步序（1-based）引用更早步骤响应中的字段；显式字面值优先。
- `caseCategory` 为开放枚举：`正常` / `异常` / `边界` / `模糊语义` …（仅作统计与组织维度，断言与之间无耦合）。
- 加载为严格解析：未知键、缺期望、数组不等长、api 未注册、errCode 不在目录 → 加载期汇总报错。
- 业务载荷（输入文本、schema 描述、期望槽位值）与用例元数据（`caseDimension` / `caseDesc` / `caseCategory`）保留中文，因为它们描述的是面向 zh-CN 提示词资源的业务用例；其余（工具、消息、代码）一律英文。

## 输出与指标

- 每场景：`output_result_from_text.json` / `output_result_from_data.json`，每个用例一条记录，其 interactions 交错记录 **API 步与其底层 LLM 调用**，携带完整请求/响应（异常步 dump 每个属性 + cause 链）、时延、Token——**完整输出，不截断**。
- `a2a-t-corpus/target/corpus/summary_report_<flow>.json` + 控制台表：总体/按场景/按维度/按分类/按 API 的准确率（**95% 达标线标注**）、时延 p50/p95、Token 合计。

## 工具闭环

```bash
python a2a-t-corpus/tools/inputCsvToJson.py --template --out my-cases.csv   # 用例设计表（含一行示例）
# 填表后转换（默认开启产物结构校验）
python a2a-t-corpus/tools/inputCsvToJson.py --csv my-cases.csv \
    --out a2a-t-corpus/src/test/java/net/openan/a2at/sdk/corpus/task/resources/<场景>/input_case_from_text.json
# 跑套件后审视（一行=一个用例，含每步完整请求/响应）
python a2a-t-corpus/tools/outputJsonToCsv.py \
    --json a2a-t-corpus/src/test/java/net/openan/a2at/sdk/corpus/task/resources/<场景>/output_result_from_text.json \
    --out review.csv
# 可选：把 input JSON 回填为设计表
python a2a-t-corpus/tools/inputCsvToJson.py --reverse --csv <input_case_from_text.json> --out back.csv
```

各平台直接运行 Python 脚本；`tools/*.sh` 为 bash 薄包装（Git Bash / WSL / Linux / macOS），参数 1:1 透传，把上面命令中的 `python a2a-t-corpus/tools/<名称>.py` 换成 `tools/<名称>.sh` 即可：

```bash
tools/inputCsvToJson.sh --template --out my-cases.csv
tools/inputCsvToJson.sh --csv my-cases.csv --out <场景>/input_case_from_text.json
tools/outputJsonToCsv.sh --json <场景>/output_result_from_text.json --out review.csv
tools/inputCsvToJson.sh --reverse --csv <input_case_from_text.json> --out back.csv
```

两个 sh 脚本**不是双击直接运行的**：双击启动时没有参数，只会打印用法说明（且终端可能立刻关闭）。请在终端中带参数运行；没有 bash 环境时直接 `python a2a-t-corpus/tools/...` 运行 Python 脚本。脚本会从终端 PATH 中查找 `python3`（找不到则回退 `python`），两者都缺失时给出明确报错。

## 新增场景（零 Java 改动）

新建 `task/resources/<场景>/` 并放入两个 input JSON 即可——`@TestFactory` 套件运行时扫描发现场景。结构门禁先用 `CorpusSelfGuardTest`（无 LLM）跑一遍。

## 后续阶段（Task-T 稳定后）

Notification-T / Negotiation-T / Authorization-T：新增 `corpus/<扩展>/<XXWorkFlowTest>` 套件与对应 `resources/`，在 `ApiRegistry` 登记该扩展 facade 方法，框架与工具链原样复用。