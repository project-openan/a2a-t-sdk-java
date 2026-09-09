# a2a-t-corpus - A2A-T Accuracy Verification Corpus

Data-driven accuracy verification for the A2A-T SDK: workflow cases written as JSON run against a **real LLM**
through the production SDK assembly path, with per-step API/LLM transcripts, latency and token metrics, and 95%+ accuracy
target reporting for prompt tuning.

Phase 1 (current) implements **Task-T only**, as the benchmark pilot; Notification-T / Negotiation-T /
Authorization-T follow the same structure once Task-T stabilizes (suite class + `resources/` + registry entries,
zero fork).

- **Pure test module**: no `src/main`, not in the BOM, excluded from release artifacts
  (`maven.deploy.skip/source.skip/javadoc.skip=true`) and from CI (`mvn ... -pl '!a2a-t-corpus'`).
- **A real LLM is mandatory**: running without the three required `A2AT_LLM_*` values fails fast with guidance.
  The `.env` keys reuse the project-root `env.example` naming, so the corpus template is a tuned subset of the
  root one.

## Layout

```
a2a-t-corpus/
├── pom.xml / env.example / .env (secrets, gitignored) / README.md / README_zh.md
├── schemas/          input-case.schema.json, output-result.schema.json (first-hand format definitions)
├── tools/            inputCsvToJson(.py/.sh), outputJsonToCsv(.py/.sh), csv-templates/
└── src/test/java/net/openan/a2at/sdk/corpus/
    ├── engine/       framework core: config/loader/registry/engine/llm/assertion/report/discover + CorpusWorkFlowSuite
    ├── task/         TaskTFromTextWorkFlowTest, TaskTFromDataWorkFlowTest, resources/<scenario>/
    └── self/         corpus self-guard meta test (no LLM)
```

`task/resources/<scenario>/` holds `input_case_from_text.json` and `input_case_from_data.json` (case design,
hand-authored); `output_result_*.json` files are written back by the engine after each run.

## Quick start

```bash
cp a2a-t-corpus/env.example a2a-t-corpus/.env   # fill A2AT_LLM_BASE_URL / API_KEY / MODEL
mvn -pl a2a-t-corpus -am test -Dtest=TaskTFromTextWorkFlowTest
mvn -pl a2a-t-corpus test -Dtest=TaskTFromTextWorkFlowTest -Dcorpus.scenario=private-line-complaint
mvn -pl a2a-t-corpus test -Dtest=TaskTFromTextWorkFlowTest -Dcorpus.scenario='private-*' -Dcase.filter='TC0000000*'
```

- `-Dcorpus.scenario`: scenario name glob (`*` wildcard, comma-separated); `-Dcase.filter`: case id glob.
- `-Dtest` filters apply reactor-wide under `-am`; the parent POM configures surefire
  `failIfNoSpecifiedTests=false`, so upstream modules without a matching test class do not fail the build.
- Every executed case is printed to the console as it completes; failed/crashed cases print their full interaction
  trace there too. The transcript file reflects exactly this run (a filtered run overwrites it with the matching
  cases only).
- `-Dcorpus.output.dir=<dir>`: redirect outputs instead of writing back into the scenario directories
  (summaries default to `target/corpus/`).
- Structure-only gate without an LLM: `mvn -pl a2a-t-corpus test -Dtest=CorpusSelfGuardTest`.

## Case JSON contract (v1, first-hand definitions in `schemas/`)

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

- `id` uses the `TC` (TestCase) prefix, e.g. `TC00000001`; ids are unique within one case file.
- `input` / `expected` are equal-length arrays; order is the execution order; any step count is allowed and the same
  API may appear repeatedly.
- `expected.result`: `success` / `error`; `data` is a subset assertion (optional `dataExact` tightens to full
  equality); `error.errCode` must be a code from the SDK error catalog.
- `{"$fromStep": N, "$field": "promptText"}` copies a field from the response of an earlier step (1-based);
  literal values always win.
- `caseCategory` is an open enumeration, e.g. `正常` / `异常` / `边界` / `模糊语义` ... (aggregation key only,
  never coupled to assertions).
- Loading is strict: unknown keys, missing expectations, mismatched array lengths, unregistered api names and
  out-of-catalog error codes all fail at load time with aggregated diagnostics.
- Business payloads (input texts, schema descriptions, expected slot values) and the case metadata
  (`caseDimension` / `caseDesc` / `caseCategory`) stay in Chinese because they describe business cases for the
  zh-CN prompt resources; everything else (tooling, messages, code) is English.

## Outputs and metrics

- Per scenario: `output_result_from_text.json` / `output_result_from_data.json` with one record per case whose
  interactions interleave **API steps and their underlying LLM calls**, carrying complete requests/responses
  (error steps dump every exception property plus the cause chain), latency and tokens - complete output, never
  truncated.
- `a2a-t-corpus/target/corpus/summary_report_<flow>.json` + console table: accuracy overall / per scenario / per
  dimension / per category / per API with the **95% target line annotated**, latency p50/p95, token totals.

## Tooling loop

```bash
python a2a-t-corpus/tools/inputCsvToJson.py --template --out my-cases.csv   # design table (with an example row)
# fill the table, then convert with structural validation on by default
python a2a-t-corpus/tools/inputCsvToJson.py --csv my-cases.csv \
    --out a2a-t-corpus/src/test/java/net/openan/a2at/sdk/corpus/task/resources/<scenario>/input_case_from_text.json
# run a suite, then review (one row per case, full per-step request/response)
python a2a-t-corpus/tools/outputJsonToCsv.py \
    --json a2a-t-corpus/src/test/java/net/openan/a2at/sdk/corpus/task/resources/<scenario>/output_result_from_text.json \
    --out review.csv
# optional: fill input JSON back into the design table
python a2a-t-corpus/tools/inputCsvToJson.py --reverse --csv <input_case_from_text.json> --out back.csv
```

Run the Python scripts directly on any platform; `tools/*.sh` are thin bash wrappers that forward every argument 1:1,
so any command above works by swapping `python a2a-t-corpus/tools/<name>.py` for `tools/<name>.sh`:

```bash
tools/inputCsvToJson.sh --template --out my-cases.csv
tools/inputCsvToJson.sh --csv my-cases.csv --out <scenario>/input_case_from_text.json
tools/outputJsonToCsv.sh --json <scenario>/output_result_from_text.json --out review.csv
tools/inputCsvToJson.sh --reverse --csv <input_case_from_text.json> --out back.csv
```

The wrappers (Git Bash / WSL / Linux / macOS) are **not** meant to be double-clicked: double-clicking starts them
without arguments, so they only print the usage text (and the terminal may close right away). Run them from a terminal
with the arguments shown above, or run `python a2a-t-corpus/tools/...` directly when no bash shell is available. The
wrappers resolve `python3` (falling back to `python`) from the terminal PATH and fail with a clear message when no
Python interpreter is installed.

## Adding a scenario (zero Java changes)

Create `task/resources/<scenario>/` with the two input JSON files - the `@TestFactory` suites discover scenarios at
runtime. Run `CorpusSelfGuardTest` (no LLM) as the structural gate first.

## Later phases (after Task-T stabilizes)

For Notification-T / Negotiation-T / Authorization-T: add `corpus/<extension>/<XXWorkFlowTest>` suites plus their
`resources/`, register the extension facade methods in `ApiRegistry`, and reuse the framework and tools as-is.