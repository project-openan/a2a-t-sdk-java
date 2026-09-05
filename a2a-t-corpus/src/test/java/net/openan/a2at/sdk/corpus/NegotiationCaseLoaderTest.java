package net.openan.a2at.sdk.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Smoke test of {@link NegotiationCaseLoader}: a good corpus loads and expands correctly and every format violation
 * fails fast with an error naming the file, the record id and the defect.
 *
 * <p>All corpora are written into a temporary directory, so the test does not depend on any corpus file existing.
 */
class NegotiationCaseLoaderTest {

    private static final String SESSION_ID = "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3";

    private static final String SHARED_RESPONSES =
            """
            {
              "extract.information.accept.full": "{\\"conclusion\\": \\"Accept\\", \\"items\\": []}",
              "extract.information.propose.full": "{\\"items\\": [{\\"name\\": \\"节能区域信息\\", \\"value\\": \\"松山湖\\"}]}"
            }
            """;

    private static final String SHARED_SCHEMAS =
            """
            {
              "flat": {"type": "object", "properties": {"id": {"type": "string"}}}
            }
            """;

    // ------------------------------------------------------------------ happy path

    @Test
    void loadsAndExpandsAGoodCorpus(@TempDir Path root) throws IOException {
        writeGoodCorpus(root);

        LoadedCorpus corpus = NegotiationCaseLoader.load(root);

        assertEquals(3, corpus.cases().size(), "FT-HAPPY-01 expands to two languages, VAL-HAPPY-01 to one");
        assertEquals(1, corpus.scenarios().size());

        NegotiationCase acceptZh = caseById(corpus, "FT-HAPPY-01/zh-CN");
        NegotiationCase acceptEn = caseById(corpus, "FT-HAPPY-01/en-US");
        NegotiationCase validateZh = caseById(corpus, "VAL-HAPPY-01/zh-CN");

        assertEquals("FT-HAPPY-01", acceptZh.baseId());
        assertEquals("from-text/happy.json", acceptZh.sourceFile());
        assertEquals(NegotiationApi.GENERATE_ACCEPT_FROM_TEXT, acceptZh.api());
        assertEquals("zh-CN", acceptZh.language());
        assertEquals("P1", acceptZh.priority());
        assertEquals("我确认第一阶段的信息。", acceptZh.inputText());
        assertEquals("I confirm the first-stage information.", acceptEn.inputText());
        assertEquals(new ContextSpec(SESSION_ID, 2, 5), acceptZh.context());
        assertTrue(acceptZh.inputData().isObject(), "the differential typed input data travels as a JSON node");
        assertTrue(acceptZh.expect().success());
        assertTrue(acceptZh.expect().differential());
        assertEquals(2, acceptZh.expect().llmCalls());
        assertEquals("information_accept", acceptZh.expect().promptTextEqualsGolden());
        assertEquals(
                "Negotiation-T/information-negotiation/accept-reject/v1",
                acceptZh.expect().metadata().templateUriEcho());
        assertEquals(Boolean.TRUE, acceptZh.expect().metadata().contextEcho());

        LlmScriptStep firstStep = acceptZh.llm().steps().get(0);
        LlmScriptStep.Fail failStep = assertInstanceOf(LlmScriptStep.Fail.class, firstStep);
        assertEquals(LlmFailMarker.NON_JSON, failStep.marker());
        LlmScriptStep.Payload payloadStep = assertInstanceOf(
                LlmScriptStep.Payload.class, acceptZh.llm().steps().get(1));
        assertEquals("{\"conclusion\": \"Accept\", \"items\": []}", payloadStep.json());
        assertEquals(3, acceptZh.llm().maxAttempts());

        assertEquals(NegotiationApi.VALIDATE_PROPOSE_PROMPT_AND_DATA_FILLING, validateZh.api());
        PromptSource.Golden golden = assertInstanceOf(PromptSource.Golden.class, validateZh.prompt());
        assertEquals("information_propose", golden.golden());
        assertEquals(corpus.sharedSchemas().get("flat"), validateZh.schema());
        assertEquals(
                Map.of("id", SESSION_ID, "round", 1, "maxRounds", 5),
                validateZh.expect().params());

        ScenarioCase scenario = corpus.scenarios().get(0);
        assertEquals("SC-INFO-01/zh-CN", scenario.id());
        assertEquals("SC-INFO-01", scenario.baseId());
        assertEquals(2, scenario.steps().size());
        assertEquals(1, scenario.steps().get(0).step());
        assertEquals("A", scenario.steps().get(0).role());
        assertEquals(
                "SC-INFO-01/zh-CN#step-1", scenario.steps().get(0).caseData().id());
        assertEquals(
                NegotiationApi.GENERATE_PROPOSE_FROM_TEXT,
                scenario.steps().get(0).caseData().api());
        PromptSource.FromStep fromStep = assertInstanceOf(
                PromptSource.FromStep.class, scenario.steps().get(1).caseData().prompt());
        assertEquals(1, fromStep.step());
        assertEquals("reject", scenario.expectFlow().terminalCondition());
        assertEquals(1, scenario.expectFlow().roundsUsed());
        assertEquals(Boolean.TRUE, scenario.expectFlow().distinctMessages());

        NegotiationCase failedStep = scenario.steps().get(1).caseData();
        assertFalse(failedStep.expect().success());
        assertEquals("negotiation.semantic_rejected", failedStep.expect().code());
        assertEquals(1, failedStep.expect().llmCalls());
        LlmScriptStep.Fail llmErrorStep = assertInstanceOf(
                LlmScriptStep.Fail.class, failedStep.llm().steps().get(0));
        assertEquals(LlmFailMarker.LLM_ERROR, llmErrorStep.marker());
    }

    @Test
    void skipsTheSchemaFileAndLoadsAnEmptyCorpus(@TempDir Path root) throws IOException {
        write(root, "corpus-schema.json", "{\"formatVersion\": 1}");
        write(root, "shared/llm-responses.json", SHARED_RESPONSES);

        LoadedCorpus corpus = NegotiationCaseLoader.load(root);

        assertTrue(corpus.cases().isEmpty());
        assertTrue(corpus.scenarios().isEmpty());
        assertEquals(2, corpus.sharedResponses().size(), "the shared files load even without any case file");
    }

    @Test
    void rejectsAMissingCorpusRoot(@TempDir Path root) {
        CorpusLoadException exception =
                assertThrows(CorpusLoadException.class, () -> NegotiationCaseLoader.load(root.resolve("nope")));

        assertTrue(exception.getMessage().contains("not an existing directory"));
    }

    // ------------------------------------------------------------------ fail-fast violations

    @Test
    void failsFastOnAnUnknownKey(@TempDir Path root) throws IOException {
        write(
                root,
                "from-text/bad.json",
                """
                [
                  {
                    "id": "FT-BAD-01",
                    "api": "generateAcceptFromText",
                    "languages": ["zh-CN"],
                    "expect": {"outcome": "success", "expection": "Oops"}
                  }
                ]
                """);

        CorpusLoadException exception = assertThrows(CorpusLoadException.class, () -> NegotiationCaseLoader.load(root));

        assertTrue(exception.getMessage().contains("from-text/bad.json"), exception.getMessage());
        assertTrue(exception.getMessage().contains("FT-BAD-01"), exception.getMessage());
        assertTrue(exception.getMessage().contains("expection"), exception.getMessage());
        assertTrue(exception.getMessage().contains("expect"), exception.getMessage());
    }

    @Test
    void failsFastOnADanglingRef(@TempDir Path root) throws IOException {
        write(
                root,
                "from-text/bad.json",
                """
                [
                  {
                    "id": "FT-BAD-01",
                    "api": "generateAcceptFromText",
                    "languages": ["zh-CN"],
                    "llm": {"script": [{"$ref": "responses/missing.payload"}]},
                    "expect": {"outcome": "success"}
                  }
                ]
                """);

        CorpusLoadException exception = assertThrows(CorpusLoadException.class, () -> NegotiationCaseLoader.load(root));

        assertTrue(exception.getMessage().contains("dangling"), exception.getMessage());
        assertTrue(exception.getMessage().contains("missing.payload"), exception.getMessage());
        assertTrue(exception.getMessage().contains("FT-BAD-01"), exception.getMessage());
    }

    @Test
    void failsFastOnAnOutOfScopeRef(@TempDir Path root) throws IOException {
        write(root, "shared/schemas.json", SHARED_SCHEMAS);
        write(
                root,
                "from-text/bad.json",
                """
                [
                  {
                    "id": "FT-BAD-01",
                    "api": "generateAcceptFromText",
                    "languages": ["zh-CN"],
                    "llm": {"script": [{"$ref": "schemas/flat"}]},
                    "expect": {"outcome": "success"}
                  }
                ]
                """);

        CorpusLoadException exception = assertThrows(CorpusLoadException.class, () -> NegotiationCaseLoader.load(root));

        assertTrue(exception.getMessage().contains("out of scope"), exception.getMessage());
        assertTrue(exception.getMessage().contains("responses/"), exception.getMessage());
    }

    @Test
    void failsFastOnANestedRef(@TempDir Path root) throws IOException {
        write(
                root,
                "shared/schemas.json",
                """
                {
                  "a": {"$ref": "schemas/b"},
                  "b": {"type": "object"}
                }
                """);
        write(
                root,
                "validate/bad.json",
                """
                [
                  {
                    "id": "VAL-BAD-01",
                    "api": "validateProposePromptAndDataFilling",
                    "languages": ["zh-CN"],
                    "templateUri": "Negotiation-T/information-negotiation/propose/v1",
                    "prompt": {"text": "## 提议"},
                    "schema": {"$ref": "schemas/a"},
                    "expect": {"outcome": "success"}
                  }
                ]
                """);

        CorpusLoadException exception = assertThrows(CorpusLoadException.class, () -> NegotiationCaseLoader.load(root));

        assertTrue(exception.getMessage().contains("nested $ref"), exception.getMessage());
        assertTrue(exception.getMessage().contains("VAL-BAD-01"), exception.getMessage());
    }

    @Test
    void failsFastOnACircularRef(@TempDir Path root) throws IOException {
        write(
                root,
                "shared/schemas.json",
                """
                {
                  "a": {"$ref": "schemas/a"}
                }
                """);
        write(
                root,
                "validate/bad.json",
                """
                [
                  {
                    "id": "VAL-BAD-01",
                    "api": "validateProposePromptAndDataFilling",
                    "languages": ["zh-CN"],
                    "templateUri": "Negotiation-T/information-negotiation/propose/v1",
                    "prompt": {"text": "## 提议"},
                    "schema": {"$ref": "schemas/a"},
                    "expect": {"outcome": "success"}
                  }
                ]
                """);

        CorpusLoadException exception = assertThrows(CorpusLoadException.class, () -> NegotiationCaseLoader.load(root));

        assertTrue(exception.getMessage().contains("circular"), exception.getMessage());
    }

    @Test
    void failsFastOnADuplicateId(@TempDir Path root) throws IOException {
        write(root, "from-text/one.json", minimalCase("FT-DUP-01"));
        write(root, "from-text/two.json", minimalCase("FT-DUP-01"));

        CorpusLoadException exception = assertThrows(CorpusLoadException.class, () -> NegotiationCaseLoader.load(root));

        assertTrue(exception.getMessage().contains("duplicate id 'FT-DUP-01'"), exception.getMessage());
        assertTrue(exception.getMessage().contains("from-text/one.json"), exception.getMessage());
    }

    @Test
    void failsFastOnADuplicateExpandedId(@TempDir Path root) throws IOException {
        write(
                root,
                "from-text/bad.json",
                """
                [
                  {
                    "id": "FT-BAD-01",
                    "api": "generateAcceptFromText",
                    "languages": ["zh-CN", "zh-CN"],
                    "expect": {"outcome": "success"}
                  }
                ]
                """);

        CorpusLoadException exception = assertThrows(CorpusLoadException.class, () -> NegotiationCaseLoader.load(root));

        assertTrue(exception.getMessage().contains("duplicate language"), exception.getMessage());
    }

    @Test
    void failsFastOnAnIncompleteFailureExpectation(@TempDir Path root) throws IOException {
        write(
                root,
                "from-text/bad.json",
                """
                [
                  {
                    "id": "FT-BAD-01",
                    "api": "generateAcceptFromText",
                    "languages": ["zh-CN"],
                    "expect": {"outcome": "failure"}
                  }
                ]
                """);

        CorpusLoadException exception = assertThrows(CorpusLoadException.class, () -> NegotiationCaseLoader.load(root));

        assertTrue(exception.getMessage().contains("failure expectation must name"), exception.getMessage());
        assertTrue(exception.getMessage().contains("FT-BAD-01"), exception.getMessage());
    }

    @Test
    void failsFastOnFailureOnlyFieldsOnASuccessExpectation(@TempDir Path root) throws IOException {
        write(
                root,
                "from-text/bad.json",
                """
                [
                  {
                    "id": "FT-BAD-01",
                    "api": "generateAcceptFromText",
                    "languages": ["zh-CN"],
                    "expect": {"outcome": "success", "code": "negotiation.invalid_input"}
                  }
                ]
                """);

        CorpusLoadException exception = assertThrows(CorpusLoadException.class, () -> NegotiationCaseLoader.load(root));

        assertTrue(
                exception.getMessage().contains("success expectation must not carry failure-only fields"),
                exception.getMessage());
    }

    @Test
    void failsFastOnAnUnknownApi(@TempDir Path root) throws IOException {
        write(
                root,
                "from-text/bad.json",
                """
                [
                  {
                    "id": "FT-BAD-01",
                    "api": "generateWhateverFromText",
                    "languages": ["zh-CN"],
                    "expect": {"outcome": "success"}
                  }
                ]
                """);

        CorpusLoadException exception = assertThrows(CorpusLoadException.class, () -> NegotiationCaseLoader.load(root));

        assertTrue(exception.getMessage().contains("unknown api 'generateWhateverFromText'"), exception.getMessage());
        assertTrue(exception.getMessage().contains("generateAcceptFromText"), exception.getMessage());
    }

    @Test
    void failsFastOnAnUnknownFailMarker(@TempDir Path root) throws IOException {
        write(
                root,
                "from-text/bad.json",
                """
                [
                  {
                    "id": "FT-BAD-01",
                    "api": "generateAcceptFromText",
                    "languages": ["zh-CN"],
                    "llm": {"script": [{"$fail": "explodes"}]},
                    "expect": {"outcome": "success"}
                  }
                ]
                """);

        CorpusLoadException exception = assertThrows(CorpusLoadException.class, () -> NegotiationCaseLoader.load(root));

        assertTrue(exception.getMessage().contains("unknown $fail marker 'explodes'"), exception.getMessage());
        assertTrue(exception.getMessage().contains("non-json"), exception.getMessage());
    }

    @Test
    void failsFastOnAnUnsupportedLanguage(@TempDir Path root) throws IOException {
        write(
                root,
                "from-text/bad.json",
                """
                [
                  {
                    "id": "FT-BAD-01",
                    "api": "generateAcceptFromText",
                    "languages": ["zh_CN"],
                    "expect": {"outcome": "success"}
                  }
                ]
                """);

        CorpusLoadException exception = assertThrows(CorpusLoadException.class, () -> NegotiationCaseLoader.load(root));

        assertTrue(exception.getMessage().contains("unsupported language 'zh_CN'"), exception.getMessage());
    }

    @Test
    void failsFastOnAMissingLanguageTextEntry(@TempDir Path root) throws IOException {
        write(
                root,
                "from-text/bad.json",
                """
                [
                  {
                    "id": "FT-BAD-01",
                    "api": "generateAcceptFromText",
                    "languages": ["zh-CN", "en-US"],
                    "input": {"text": {"zh-CN": "我确认"}},
                    "expect": {"outcome": "success"}
                  }
                ]
                """);

        CorpusLoadException exception = assertThrows(CorpusLoadException.class, () -> NegotiationCaseLoader.load(root));

        assertTrue(exception.getMessage().contains("missing the 'en-US' text entry"), exception.getMessage());
    }

    @Test
    void failsFastOnNonConsecutiveScenarioSteps(@TempDir Path root) throws IOException {
        write(
                root,
                "scenarios/bad.json",
                """
                [
                  {
                    "id": "SC-BAD-01",
                    "languages": ["zh-CN"],
                    "steps": [
                      {
                        "step": 2,
                        "api": "generateProposeFromText",
                        "context": {"id": "%s", "round": 1, "maxRounds": 5},
                        "expect": {"outcome": "success"}
                      }
                    ]
                  }
                ]
                """
                        .formatted(SESSION_ID));

        CorpusLoadException exception = assertThrows(CorpusLoadException.class, () -> NegotiationCaseLoader.load(root));

        assertTrue(exception.getMessage().contains("consecutively from 1"), exception.getMessage());
        assertTrue(exception.getMessage().contains("SC-BAD-01"), exception.getMessage());
    }

    @Test
    void failsFastOnAnUnknownKeyInsideAScenarioStep(@TempDir Path root) throws IOException {
        write(
                root,
                "scenarios/bad.json",
                """
                [
                  {
                    "id": "SC-BAD-01",
                    "languages": ["zh-CN"],
                    "steps": [
                      {
                        "step": 1,
                        "api": "generateProposeFromText",
                        "context": {"id": "%s", "round": 1, "maxRounds": 5},
                        "rol": "A",
                        "expect": {"outcome": "success"}
                      }
                    ]
                  }
                ]
                """
                        .formatted(SESSION_ID));

        CorpusLoadException exception = assertThrows(CorpusLoadException.class, () -> NegotiationCaseLoader.load(root));

        assertTrue(exception.getMessage().contains("rol"), exception.getMessage());
        assertTrue(exception.getMessage().contains("SC-BAD-01"), exception.getMessage());
    }

    // ------------------------------------------------------------------ task family format (Q21–Q23)

    @Test
    void loadsTaskFamilyRecordsWithTheClosedLoopExpectationFields(@TempDir Path root) throws IOException {
        write(root, "shared/llm-responses.json", SHARED_RESPONSES);
        write(
                root,
                "task/happy.json",
                """
                [
                  {
                    "id": "TASK-FT-01",
                    "api": "generateTaskPromptFromText",
                    "languages": ["zh-CN"],
                    "summary": "工作台从原始投诉文本生成缺参任务报文",
                    "templateUri": "Task-T/network-layer/private-line-complaint/v1",
                    "input": {
                      "text": {"zh-CN": "深圳访问广州的专线时延骤升，OSS侧事件流水号event-id-20260511-09013。"}
                    },
                    "llm": {"script": ["{\\"slots\\": {\\"任务对象\\": \\"\\", \\"任务上下文\\": \\"投诉分类：待补充\\"}, \\"slot_errors\\": []}"]},
                    "expect": {
                      "outcome": "success",
                      "llmCalls": 1,
                      "promptTextContains": ["## 任务类型", "## 任务对象"]
                    }
                  }
                ]
                """);
        write(
                root,
                "scenarios/task-closed-loop.json",
                """
                [
                  {
                    "id": "SC-TASK-01",
                    "languages": ["zh-CN"],
                    "summary": "任务缺参发现与补参提取的因果闭环",
                    "roles": ["A", "B"],
                    "rolesDesc": {
                      "A": "工作台（client，任务发起/补数方）",
                      "B": "OMC（server，执行/要数方，协商发起方）"
                    },
                    "steps": [
                      {
                        "step": 1,
                        "role": "A",
                        "api": "generateTaskPromptFromText",
                        "context": null,
                        "templateUri": "Task-T/network-layer/private-line-complaint/v1",
                        "input": {
                          "text": {"zh-CN": "深圳访问广州的专线时延骤升，OSS侧事件流水号event-id-20260511-09013。"}
                        },
                        "llm": {"script": ["{\\"slots\\": {\\"任务对象\\": \\"\\", \\"任务上下文\\": \\"投诉分类：待补充\\"}, \\"slot_errors\\": []}"]},
                        "expect": {"outcome": "success", "llmCalls": 1}
                      },
                      {
                        "step": 2,
                        "role": "B",
                        "api": "validateTaskPromptAndDataFilling",
                        "context": null,
                        "templateUri": "Task-T/network-layer/private-line-complaint/v1",
                        "prompt": {"fromStep": 1},
                        "schema": {"type": "object", "properties": {"accessPort": {"type": "string"}}},
                        "llm": {"script": ["{\\"semantic_verdict\\":true,\\"errors\\":[],\\"params\\":{\\"accessPort\\":null}}"]},
                        "expect": {
                          "outcome": "success",
                          "llmCalls": 1,
                          "missingParams": ["accessPort"],
                          "params": {"faultTime": "2026-05-11T08:21:46Z"}
                        }
                      },
                      {
                        "step": 3,
                        "role": "B",
                        "api": "validateAcceptPromptAndDataFilling",
                        "context": {"id": "%s", "round": 2, "maxRounds": 5},
                        "templateUri": "Negotiation-T/information-negotiation/accept-reject/v1",
                        "prompt": {"fromStep": 1},
                        "schema": {"type": "object"},
                        "llm": {"script": ["{\\"semantic_verdict\\":true,\\"errors\\":[],\\"params\\":{}}"]},
                        "expect": {"outcome": "success", "llmCalls": 1, "paramsFromStep": 2}
                      }
                    ],
                    "expectFlow": {"terminalCondition": "accept", "missingParamsFilled": 2}
                  }
                ]
                """
                        .formatted(SESSION_ID));

        LoadedCorpus corpus = NegotiationCaseLoader.load(root);

        NegotiationCase taskCase = caseById(corpus, "TASK-FT-01/zh-CN");
        assertEquals(NegotiationApi.GENERATE_TASK_PROMPT_FROM_TEXT, taskCase.api());
        assertEquals(List.of("## 任务类型", "## 任务对象"), taskCase.expect().promptTextContains());

        ScenarioCase scenario = corpus.scenarios().get(0);
        assertEquals(Map.of("A", "工作台（client，任务发起/补数方）", "B", "OMC（server，执行/要数方，协商发起方）"), scenario.rolesDesc());
        assertEquals("A=工作台（client，任务发起/补数方）", scenario.describeRole("A"));
        NegotiationCase validationStep = scenario.steps().get(1).caseData();
        assertEquals(NegotiationApi.VALIDATE_TASK_PROMPT_AND_DATA_FILLING, validationStep.api());
        assertEquals(List.of("accessPort"), validationStep.expect().missingParams());
        assertEquals(
                Map.of("faultTime", "2026-05-11T08:21:46Z"),
                validationStep.expect().params());
        assertEquals(2, scenario.steps().get(2).caseData().expect().paramsFromStep());
        assertEquals(2, scenario.expectFlow().missingParamsFilled());
    }

    @Test
    void failsFastOnTaskSuccessFieldsOnAFailureExpectation(@TempDir Path root) throws IOException {
        write(
                root,
                "task/bad.json",
                """
                [
                  {
                    "id": "TASK-BAD-01",
                    "api": "validateTaskPromptAndDataFilling",
                    "languages": ["zh-CN"],
                    "templateUri": "Task-T/network-layer/private-line-complaint/v1",
                    "prompt": {"text": "## 任务类型(Task Type)"},
                    "schema": {"type": "object"},
                    "llm": {"script": ["{}"]},
                    "expect": {
                      "outcome": "failure",
                      "code": "negotiation.semantic_rejected",
                      "missingParams": ["accessPort"]
                    }
                  }
                ]
                """);

        CorpusLoadException exception = assertThrows(CorpusLoadException.class, () -> NegotiationCaseLoader.load(root));

        assertTrue(exception.getMessage().contains("success-only fields"), exception.getMessage());
        assertTrue(exception.getMessage().contains("missingParams"), exception.getMessage());
    }

    @Test
    void failsFastOnAJsonNullInsideParams(@TempDir Path root) throws IOException {
        write(
                root,
                "task/bad.json",
                """
                [
                  {
                    "id": "TASK-BAD-02",
                    "api": "validateTaskPromptAndDataFilling",
                    "languages": ["zh-CN"],
                    "templateUri": "Task-T/network-layer/private-line-complaint/v1",
                    "prompt": {"text": "## 任务类型(Task Type)"},
                    "schema": {"type": "object"},
                    "llm": {"script": ["{}"]},
                    "expect": {
                      "outcome": "success",
                      "params": {"accessPort": null}
                    }
                  }
                ]
                """);

        CorpusLoadException exception = assertThrows(CorpusLoadException.class, () -> NegotiationCaseLoader.load(root));

        assertTrue(
                exception.getMessage().contains("accessPort")
                        && exception.getMessage().contains("missingParams"),
                "a JSON null param must point at missingParams but was: " + exception.getMessage());
    }

    @Test
    void failsFastOnParamsFromStepBelowOne(@TempDir Path root) throws IOException {
        write(
                root,
                "task/bad.json",
                """
                [
                  {
                    "id": "TASK-BAD-03",
                    "api": "validateTaskPromptAndDataFilling",
                    "languages": ["zh-CN"],
                    "templateUri": "Task-T/network-layer/private-line-complaint/v1",
                    "prompt": {"text": "## 任务类型(Task Type)"},
                    "schema": {"type": "object"},
                    "llm": {"script": ["{}"]},
                    "expect": {
                      "outcome": "success",
                      "paramsFromStep": 0
                    }
                  }
                ]
                """);

        CorpusLoadException exception = assertThrows(CorpusLoadException.class, () -> NegotiationCaseLoader.load(root));

        assertTrue(exception.getMessage().contains("paramsFromStep"), exception.getMessage());
    }

    @Test
    void failsFastOnARolesDescEntryOutsideTheDeclaredRoles(@TempDir Path root) throws IOException {
        write(
                root,
                "scenarios/bad.json",
                """
                [
                  {
                    "id": "SC-BAD-02",
                    "languages": ["zh-CN"],
                    "roles": ["A", "B"],
                    "rolesDesc": {"C": "OSS（第三方）"},
                    "steps": [
                      {
                        "step": 1,
                        "api": "generateProposeFromText",
                        "context": {"id": "%s", "round": 1, "maxRounds": 5},
                        "expect": {"outcome": "success"}
                      }
                    ]
                  }
                ]
                """
                        .formatted(SESSION_ID));

        CorpusLoadException exception = assertThrows(CorpusLoadException.class, () -> NegotiationCaseLoader.load(root));

        assertTrue(
                exception.getMessage().contains("rolesDesc")
                        && exception.getMessage().contains("'C'"),
                exception.getMessage());
    }

    // ------------------------------------------------------------------ live family (live LLM phase 1)

    @Test
    void loadsLiveRecordsIntoTheDedicatedLiveCasesList(@TempDir Path root) throws IOException {
        write(
                root,
                "live/generate.json",
                """
                [
                  {
                    "id": "LIVE-GEN-01",
                    "api": "generateTaskPromptFromText",
                    "languages": ["zh-CN"],
                    "priority": "P0",
                    "tags": ["live", "scenario-recognition"],
                    "summary": "明确场景的典型投诉文本",
                    "templateUri": "Task-T/network-layer/private-line-complaint/v1",
                    "input": {"text": {"zh-CN": "深圳访问广州的专线时延骤升，OSS侧事件流水号event-id-20260511-09013。"}},
                    "expect": {
                      "success": true,
                      "scenarioCode": "private-line-complaint",
                      "paramsContains": {"accessPort": "P533-01"},
                      "paramsAbsent": ["faultTime"],
                      "promptTextContains": ["## instruction", "event-id-20260511-09013"],
                      "maxLlmCalls": 4
                    }
                  }
                ]
                """);

        LoadedCorpus corpus = NegotiationCaseLoader.load(root);

        assertEquals(1, corpus.liveCases().size(), "the live record expands once and lands in liveCases");
        assertTrue(corpus.cases().isEmpty(), "a live record never mixes into the offline cases list");
        LiveCase live = corpus.liveCases().get(0);
        assertEquals("LIVE-GEN-01/zh-CN", live.id());
        assertEquals("LIVE-GEN-01", live.baseId());
        assertEquals("live/generate.json", live.sourceFile());
        assertEquals(NegotiationApi.GENERATE_TASK_PROMPT_FROM_TEXT, live.api());
        assertEquals("zh-CN", live.language());
        assertEquals("P0", live.priority());
        assertEquals(List.of("live", "scenario-recognition"), live.tags());
        assertEquals("深圳访问广州的专线时延骤升，OSS侧事件流水号event-id-20260511-09013。", live.inputText());
        assertTrue(live.liveExpect().success());
        assertEquals("private-line-complaint", live.liveExpect().scenarioCode());
        assertEquals(Map.of("accessPort", "P533-01"), live.liveExpect().paramsContains());
        assertEquals(List.of("faultTime"), live.liveExpect().paramsAbsent());
        assertEquals(
                List.of("## instruction", "event-id-20260511-09013"),
                live.liveExpect().promptTextContains());
        assertEquals(4, live.liveExpect().maxLlmCalls());
        assertEquals("live/generate.json [LIVE-GEN-01/zh-CN]", live.errorPrefix());
    }

    @Test
    void failsFastOnALiveRecordWithoutTheLiveIdPrefix(@TempDir Path root) throws IOException {
        write(
                root,
                "live/bad.json",
                """
                [
                  {
                    "id": "TASK-LIVE-01",
                    "api": "generateTaskPromptFromText",
                    "languages": ["zh-CN"],
                    "expect": {"success": true}
                  }
                ]
                """);

        CorpusLoadException exception = assertThrows(CorpusLoadException.class, () -> NegotiationCaseLoader.load(root));

        assertTrue(exception.getMessage().contains("LIVE-"), exception.getMessage());
        assertTrue(exception.getMessage().contains("TASK-LIVE-01"), exception.getMessage());
    }

    @Test
    void failsFastOnALiveRecordOutsideThePhaseOneLanguages(@TempDir Path root) throws IOException {
        write(
                root,
                "live/bad.json",
                """
                [
                  {
                    "id": "LIVE-BAD-01",
                    "api": "generateTaskPromptFromText",
                    "languages": ["zh-CN", "en-US"],
                    "expect": {"success": true}
                  }
                ]
                """);

        CorpusLoadException exception = assertThrows(CorpusLoadException.class, () -> NegotiationCaseLoader.load(root));

        assertTrue(exception.getMessage().contains("exactly the languages [zh-CN]"), exception.getMessage());
    }

    @Test
    void failsFastOnALiveRecordOutsideTheTaskFamily(@TempDir Path root) throws IOException {
        write(
                root,
                "live/bad.json",
                """
                [
                  {
                    "id": "LIVE-BAD-01",
                    "api": "generateAcceptFromText",
                    "languages": ["zh-CN"],
                    "expect": {"success": true}
                  }
                ]
                """);

        CorpusLoadException exception = assertThrows(CorpusLoadException.class, () -> NegotiationCaseLoader.load(root));

        assertTrue(exception.getMessage().contains("live phase 1 supports only"), exception.getMessage());
        assertTrue(exception.getMessage().contains("generateTaskPromptFromText"), exception.getMessage());
        assertTrue(exception.getMessage().contains("validateTaskPromptAndDataFilling"), exception.getMessage());
    }

    @Test
    void failsFastOnALiveExpectationWithoutSuccess(@TempDir Path root) throws IOException {
        write(
                root,
                "live/bad.json",
                """
                [
                  {
                    "id": "LIVE-BAD-01",
                    "api": "generateTaskPromptFromText",
                    "languages": ["zh-CN"],
                    "input": {"text": {"zh-CN": "深圳访问广州的专线时延骤升。"}},
                    "expect": {"scenarioCode": "private-line-complaint"}
                  }
                ]
                """);

        CorpusLoadException exception = assertThrows(CorpusLoadException.class, () -> NegotiationCaseLoader.load(root));

        assertTrue(exception.getMessage().contains("missing required field 'success'"), exception.getMessage());
    }

    @Test
    void failsFastOnALiveGenerateRecordWithoutInputText(@TempDir Path root) throws IOException {
        write(
                root,
                "live/bad.json",
                """
                [
                  {
                    "id": "LIVE-BAD-01",
                    "api": "generateTaskPromptFromText",
                    "languages": ["zh-CN"],
                    "expect": {"success": true}
                  }
                ]
                """);

        CorpusLoadException exception = assertThrows(CorpusLoadException.class, () -> NegotiationCaseLoader.load(root));

        assertTrue(exception.getMessage().contains("input.text"), exception.getMessage());
        assertTrue(exception.getMessage().contains("generateTaskPromptFromText"), exception.getMessage());
    }

    @Test
    void failsFastOnALiveRecordWithAGoldenOrFromStepPrompt(@TempDir Path root) throws IOException {
        write(
                root,
                "live/bad.json",
                """
                [
                  {
                    "id": "LIVE-BAD-01",
                    "api": "validateTaskPromptAndDataFilling",
                    "languages": ["zh-CN"],
                    "prompt": {"golden": "task-happy-zh-CN"},
                    "expect": {"success": true}
                  }
                ]
                """);

        CorpusLoadException exception = assertThrows(CorpusLoadException.class, () -> NegotiationCaseLoader.load(root));

        assertTrue(exception.getMessage().contains("only the inline prompt.text"), exception.getMessage());
        assertTrue(exception.getMessage().contains("golden"), exception.getMessage());
    }

    @Test
    void failsFastOnALiveValidateRecordDeclaringPromptTextContains(@TempDir Path root) throws IOException {
        write(
                root,
                "live/bad.json",
                """
                [
                  {
                    "id": "LIVE-BAD-01",
                    "api": "validateTaskPromptAndDataFilling",
                    "languages": ["zh-CN"],
                    "prompt": {"text": "## 任务类型(Task Type)"},
                    "expect": {"success": true, "promptTextContains": ["## 任务类型"]}
                  }
                ]
                """);

        CorpusLoadException exception = assertThrows(CorpusLoadException.class, () -> NegotiationCaseLoader.load(root));

        assertTrue(exception.getMessage().contains("promptTextContains"), exception.getMessage());
        assertTrue(exception.getMessage().contains("no generated prompt"), exception.getMessage());
    }

    @Test
    void defaultsTheLiveMaxLlmCallsToFourWhenTheRecordOmitsIt(@TempDir Path root) throws IOException {
        write(
                root,
                "live/generate.json",
                """
                [
                  {
                    "id": "LIVE-GEN-01",
                    "api": "generateTaskPromptFromText",
                    "languages": ["zh-CN"],
                    "input": {"text": {"zh-CN": "深圳访问广州的专线时延骤升。"}},
                    "expect": {"success": true}
                  }
                ]
                """);

        LoadedCorpus corpus = NegotiationCaseLoader.load(root);

        assertEquals(4, corpus.liveCases().get(0).liveExpect().maxLlmCalls(), "maxLlmCalls defaults to 4");
    }

    @Test
    void failsFastOnALiveIdCollidingWithAnOfflineCase(@TempDir Path root) throws IOException {
        write(root, "from-text/dup.json", minimalCase("LIVE-DUP-01"));
        write(
                root,
                "live/dup.json",
                """
                [
                  {
                    "id": "LIVE-DUP-01",
                    "api": "generateTaskPromptFromText",
                    "languages": ["zh-CN"],
                    "expect": {"success": true}
                  }
                ]
                """);

        CorpusLoadException exception = assertThrows(CorpusLoadException.class, () -> NegotiationCaseLoader.load(root));

        assertTrue(exception.getMessage().contains("duplicate id 'LIVE-DUP-01'"), exception.getMessage());
        assertTrue(exception.getMessage().contains("from-text/dup.json"), exception.getMessage());
    }

    // ------------------------------------------------------------------ helpers

    private static void write(Path root, String relative, String content) throws IOException {
        Path file = root.resolve(relative);
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private static void writeGoodCorpus(Path root) throws IOException {
        write(root, "shared/llm-responses.json", SHARED_RESPONSES);
        write(root, "shared/schemas.json", SHARED_SCHEMAS);
        write(
                root,
                "from-text/happy.json",
                """
                [
                  {
                    "id": "FT-HAPPY-01",
                    "api": "generateAcceptFromText",
                    "languages": ["zh-CN", "en-US"],
                    "priority": "P1",
                    "tags": ["happy", "accept-reject"],
                    "summary": "信息确认后接受，成功",
                    "context": {"id": "%s", "round": 2, "maxRounds": 5},
                    "templateUri": "Negotiation-T/information-negotiation/accept-reject/v1",
                    "input": {
                      "text": {"zh-CN": "我确认第一阶段的信息。", "en-US": "I confirm the first-stage information."},
                      "data": {"items": [], "relationship": null}
                    },
                    "llm": {
                      "maxAttempts": 3,
                      "script": [{"$fail": "non-json"}, {"$ref": "responses/extract.information.accept.full"}]
                    },
                    "expect": {
                      "outcome": "success",
                      "llmCalls": 2,
                      "promptTextEqualsGolden": "information_accept",
                      "metadata": {
                        "templateUriEcho": "Negotiation-T/information-negotiation/accept-reject/v1",
                        "contextEcho": true
                      },
                      "contracts": ["conclusionLiteralPresent"],
                      "differential": true
                    }
                  }
                ]
                """
                        .formatted(SESSION_ID));
        write(
                root,
                "validate/happy.json",
                """
                [
                  {
                    "id": "VAL-HAPPY-01",
                    "api": "validateProposePromptAndDataFilling",
                    "languages": ["zh-CN"],
                    "context": {"id": "%s", "round": 1, "maxRounds": 5},
                    "templateUri": "Negotiation-T/information-negotiation/propose/v1",
                    "prompt": {"golden": "information_propose"},
                    "schema": {"$ref": "schemas/flat"},
                    "llm": {"script": ["{\\"verdict\\": true}"]},
                    "expect": {
                      "outcome": "success",
                      "llmCalls": 1,
                      "params": {"id": "%s", "round": 1, "maxRounds": 5}
                    }
                  }
                ]
                """
                        .formatted(SESSION_ID, SESSION_ID));
        write(
                root,
                "scenarios/flows.json",
                """
                [
                  {
                    "id": "SC-INFO-01",
                    "summary": "提议后语义拒绝",
                    "languages": ["zh-CN"],
                    "roles": ["A", "B"],
                    "steps": [
                      {
                        "step": 1,
                        "role": "A",
                        "api": "generateProposeFromText",
                        "context": {"id": "%s", "round": 1, "maxRounds": 5},
                        "templateUri": "Negotiation-T/information-negotiation/propose/v1",
                        "input": {"text": {"zh-CN": "请提供节能区域信息"}},
                        "llm": {"script": [{"$ref": "responses/extract.information.propose.full"}]},
                        "expect": {"outcome": "success", "llmCalls": 1}
                      },
                      {
                        "step": 2,
                        "role": "B",
                        "api": "validateProposePromptAndDataFilling",
                        "prompt": {"fromStep": 1},
                        "context": {"id": "%s", "round": 1, "maxRounds": 5},
                        "templateUri": "Negotiation-T/information-negotiation/propose/v1",
                        "schema": {"$ref": "schemas/flat"},
                        "llm": {"script": [{"$fail": "llm-error"}]},
                        "expect": {"outcome": "failure", "code": "negotiation.semantic_rejected", "llmCalls": 1}
                      }
                    ],
                    "expectFlow": {"terminalCondition": "reject", "roundsUsed": 1, "distinctMessages": true}
                  }
                ]
                """
                        .formatted(SESSION_ID, SESSION_ID));
    }

    private static String minimalCase(String id) {
        return """
                [
                  {
                    "id": "%s",
                    "api": "generateAcceptFromText",
                    "languages": ["zh-CN"],
                    "context": {"id": "%s", "round": 2, "maxRounds": 5},
                    "templateUri": "Negotiation-T/information-negotiation/accept-reject/v1",
                    "input": {"text": {"zh-CN": "我确认"}},
                    "llm": {"script": ["{}"]},
                    "expect": {"outcome": "success"}
                  }
                ]
                """
                .formatted(id, SESSION_ID);
    }

    private static NegotiationCase caseById(LoadedCorpus corpus, String id) {
        NegotiationCase found = null;
        for (NegotiationCase candidate : corpus.cases()) {
            if (candidate.id().equals(id)) {
                found = candidate;
            }
        }
        assertNotNull(found, "the corpus must expand the case " + id);
        return found;
    }
}
