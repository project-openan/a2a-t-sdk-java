package net.openan.a2at.sdk.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import net.openan.a2at.sdk.core.exception.ErrorCatalog;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Meta test of the negotiation test corpus (design document §6 Q6 and §7): the corpus is checked against its own
 * contracts instead of the production code, so a hole in the corpus is caught before it silently narrows the suites.
 *
 * <p>Checked today, in full strictness: global id uniqueness, expectation-block completeness, every expected error code
 * being one of the content-layer error codes, the content-layer failure coverage, the validate-family code coverage,
 * the four operational definitions of the bilingual parity (§7), and the per-record uniform language expansion. The
 * business-alignment decisions add three more (design §10): the three closed-loop task APIs are exercised by scenario
 * steps only and are role-bound (A=workbench generates, B=OMC validates), and every VAL-DRIFT drift probe is a
 * compliant peer text expecting success. The Q19 business-review soft gate (§8.7) is opt-in: P0 cases must be 100%
 * approved in review-status.json when {@code -Dcorpus.review.gate=true} is set and the status file exists, and is
 * skipped otherwise.
 *
 * <p>The full corpus has landed (design §8.4): a missing error-code or mapCode dimension is a hard failure, not a TODO
 * line — a hole in the corpus silently narrows the suites, so it must block the build.
 *
 * @since 2026-08
 */
class CorpusContractTest {

    /**
     * Full strictness (design Q6/§8.4): every one of the content-layer error codes must be covered by at least one
     * failure case of the corpus; a missing code fails the build.
     */
    private static final boolean REQUIRE_FULL_ERROR_CODE_COVERAGE = true;

    /**
     * Full strictness (design Q6/§8.4): every code the validate family can fail with must be covered by at least one
     * validate-family failure case (the VAL-MAP/VAL-RULE/VAL-RETRY batches). The unknown-code fallback of the LLM
     * response parsing (negotiation.rule_violation / content.rule_violation) cannot be expressed through the public
     * exception surface and stays a hand-written-suite concern (ParamExtractorTest).
     */
    private static final boolean REQUIRE_FULL_VALIDATE_CODE_COVERAGE = true;

    /** The content-layer error codes of the production exception surface (ErrorCatalog codes). */
    private static final List<String> CONTENT_LAYER_ERROR_CODES = List.of(
            ErrorCatalog.TEMPLATE_NOT_FOUND.getCode(),
            ErrorCatalog.NEGOTIATION_INVALID_INPUT.getCode(),
            ErrorCatalog.NEGOTIATION_FIELD_MISSING.getCode(),
            ErrorCatalog.NEGOTIATION_CONTENT_EXTRACT_FAILED.getCode(),
            ErrorCatalog.NEGOTIATION_CONCLUSION_MISMATCH.getCode(),
            ErrorCatalog.NEGOTIATION_CONTENT_INVALID.getCode(),
            ErrorCatalog.NEGOTIATION_RULE_VIOLATION.getCode(),
            ErrorCatalog.NEGOTIATION_SEMANTIC_REJECTED.getCode(),
            ErrorCatalog.LLM_INVOCATION_FAILED.getCode(),
            ErrorCatalog.LLM_RESPONSE_INVALID.getCode());

    /**
     * The codes the validate family fails with: the pipeline surfaces these directly (there is no code mapping layer
     * since the ErrorCatalog migration), so a validate-family failure case must expect each of them.
     */
    private static final List<String> VALIDATE_FAMILY_ERROR_CODES = List.of(
            ErrorCatalog.TEMPLATE_NOT_FOUND.getCode(),
            ErrorCatalog.NEGOTIATION_INVALID_INPUT.getCode(),
            ErrorCatalog.NEGOTIATION_RULE_VIOLATION.getCode(),
            ErrorCatalog.NEGOTIATION_SEMANTIC_REJECTED.getCode(),
            ErrorCatalog.LLM_INVOCATION_FAILED.getCode(),
            ErrorCatalog.LLM_RESPONSE_INVALID.getCode());

    private static final LoadedCorpus CORPUS = CorpusSuites.loadCorpus();

    // ------------------------------------------------------------------ ids and expectation blocks

    @Test
    void idsAreGloballyUniqueAcrossCasesAndScenarios() {
        Set<String> expandedIds = new LinkedHashSet<>();
        int expectedCount = 0;
        for (NegotiationCase testCase : allCaseRecords()) {
            assertTrue(expandedIds.add(testCase.id()), "duplicate expanded id " + testCase.id());
            expectedCount++;
        }
        for (ScenarioCase scenario : CORPUS.scenarios()) {
            assertTrue(expandedIds.add(scenario.id()), "duplicate expanded id " + scenario.id());
            expectedCount++;
        }
        for (LiveCase liveCase : CORPUS.liveCases()) {
            assertTrue(expandedIds.add(liveCase.id()), "duplicate expanded id " + liveCase.id());
            expectedCount++;
        }
        assertEquals(expectedCount, expandedIds.size(), "the id is the primary key of the whole corpus");

        // One base id belongs to exactly one record: all its language expansions come from the same file, and every
        // expansion carries a distinct language (the loader already fails fast on duplicates at parse time; this
        // re-asserts the invariant on the loaded corpus). Scenario step cases are derived records of a scenario and are
        // covered by the scenario check below.
        Map<String, List<NegotiationCase>> caseGroups = new LinkedHashMap<>();
        for (NegotiationCase testCase : allCaseRecords()) {
            if (testCase.id().contains("#step-")) {
                continue;
            }
            caseGroups
                    .computeIfAbsent(testCase.baseId(), key -> new ArrayList<>())
                    .add(testCase);
        }
        for (List<NegotiationCase> group : caseGroups.values()) {
            Set<String> languages = new LinkedHashSet<>();
            String sourceFile = group.get(0).sourceFile();
            for (NegotiationCase expansion : group) {
                assertEquals(
                        sourceFile,
                        expansion.sourceFile(),
                        "the base id " + expansion.baseId() + " must belong to exactly one corpus file");
                assertTrue(languages.add(expansion.language()), "duplicate language expansion " + expansion.id());
            }
        }
        Map<String, List<ScenarioCase>> scenarioGroups = new LinkedHashMap<>();
        for (ScenarioCase scenario : CORPUS.scenarios()) {
            scenarioGroups
                    .computeIfAbsent(scenario.baseId(), key -> new ArrayList<>())
                    .add(scenario);
        }
        for (List<ScenarioCase> group : scenarioGroups.values()) {
            Set<String> languages = new LinkedHashSet<>();
            String sourceFile = group.get(0).sourceFile();
            for (ScenarioCase expansion : group) {
                assertEquals(
                        sourceFile,
                        expansion.sourceFile(),
                        "the base id " + expansion.baseId() + " must belong to exactly one corpus file");
                assertTrue(languages.add(expansion.language()), "duplicate language expansion " + expansion.id());
            }
        }
        Map<String, List<LiveCase>> liveGroups = new LinkedHashMap<>();
        for (LiveCase liveCase : CORPUS.liveCases()) {
            liveGroups
                    .computeIfAbsent(liveCase.baseId(), key -> new ArrayList<>())
                    .add(liveCase);
        }
        for (List<LiveCase> group : liveGroups.values()) {
            Set<String> languages = new LinkedHashSet<>();
            String sourceFile = group.get(0).sourceFile();
            for (LiveCase expansion : group) {
                assertEquals(
                        sourceFile,
                        expansion.sourceFile(),
                        "the base id " + expansion.baseId() + " must belong to exactly one corpus file");
                assertTrue(languages.add(expansion.language()), "duplicate language expansion " + expansion.id());
            }
        }
    }

    @Test
    void expectationBlocksAreComplete() {
        for (NegotiationCase testCase : allCaseRecords()) {
            Expectation expect = testCase.expect();
            if (expect.success()) {
                assertTrue(
                        expect.exception() == null && expect.code() == null,
                        testCase.errorPrefix()
                                + ": a success expectation must not carry an exception or an error code");
            } else {
                assertTrue(
                        expect.exception() != null || expect.code() != null,
                        testCase.errorPrefix()
                                + ": a failure expectation must name the expected exception or the expected error code");
            }
        }
    }

    @Test
    void everyExpectedErrorCodeIsAKnownNegotiationCode() {
        for (NegotiationCase testCase : allCaseRecords()) {
            String code = testCase.expect().code();
            if (code != null) {
                assertTrue(
                        CONTENT_LAYER_ERROR_CODES.contains(code),
                        testCase.errorPrefix() + ": the expected code '" + code
                                + "' is not one of the content-layer error codes");
            }
        }
    }

    // ------------------------------------------------------------------ error-code coverage

    @Test
    void everyNegotiationErrorCodeIsCoveredByAFailureCase() {
        Set<String> covered = new LinkedHashSet<>();
        for (NegotiationCase testCase : allCaseRecords()) {
            if (!testCase.expect().success() && testCase.expect().code() != null) {
                covered.add(testCase.expect().code());
            }
        }
        List<String> missing = new ArrayList<>();
        for (String code : CONTENT_LAYER_ERROR_CODES) {
            if (covered.contains(code)) {
                continue;
            }
            if (REQUIRE_FULL_ERROR_CODE_COVERAGE) {
                fail("no failure case of the corpus expects the error code '" + code + "' yet");
            }
            missing.add(code);
        }
        if (!missing.isEmpty()) {
            System.out.println("error codes not yet covered by any failure case: " + missing);
        }
    }

    // ------------------------------------------------------------------ validate-family code coverage

    @Test
    void everyValidateFamilyCodeIsCoveredByAValidateFailureCase() {
        Set<String> covered = new LinkedHashSet<>();
        for (NegotiationCase testCase : allCaseRecords()) {
            if (testCase.api().family() == NegotiationApi.Family.VALIDATE
                    && !testCase.expect().success()
                    && testCase.expect().code() != null) {
                covered.add(testCase.expect().code());
            }
        }
        List<String> missing = new ArrayList<>();
        for (String code : VALIDATE_FAMILY_ERROR_CODES) {
            if (covered.contains(code)) {
                continue;
            }
            if (REQUIRE_FULL_VALIDATE_CODE_COVERAGE) {
                fail("no validate-family failure case expects the error code '" + code + "' yet");
            }
            missing.add(code);
        }
        if (!missing.isEmpty()) {
            System.out.println("validate-family codes not yet covered by any validate failure case: " + missing);
        }
    }

    // ------------------------------------------------------------------ closed-loop task APIs (Q20-Q23)

    /**
     * Q21 full strictness: every one of the three closed-loop task APIs must be exercised by at least one scenario
     * step, and the task APIs must never appear as standalone case-file records — the closed loop (task prompt
     * generation, peer validation, then negotiation) only has a business meaning inside a scenario, so a standalone
     * task case would bypass the 缺参 → 协商 → 补参 → 提取 causal chain the corpus exists to model.
     */
    @Test
    void everyTaskApiIsExercisedByScenarioStepsOnly() {
        List<NegotiationApi> taskApis = new ArrayList<>();
        for (ScenarioCase scenario : CORPUS.scenarios()) {
            for (ScenarioCase.ScenarioStep step : scenario.steps()) {
                NegotiationApi api = step.caseData().api();
                if (api.family() == NegotiationApi.Family.TASK) {
                    taskApis.add(api);
                }
            }
        }
        for (NegotiationApi api : NegotiationApi.values()) {
            if (api.family() != NegotiationApi.Family.TASK) {
                continue;
            }
            assertTrue(
                    taskApis.contains(api),
                    "no scenario step exercises the closed-loop task API '" + api.jsonName() + "' yet");
        }
        for (NegotiationCase testCase : CORPUS.cases()) {
            assertTrue(
                    testCase.api().family() != NegotiationApi.Family.TASK,
                    testCase.errorPrefix()
                            + ": a task API must not appear as a standalone case record, only as a scenario step"
                            + " (the closed-loop causal chain lives in scenarios)");
        }
    }

    /**
     * Q21/Q23 role semantics: the workbench (A) generates the task prompt and the OMC (B) validates the peer message,
     * so every task-generation step must be acted by role A and every task-validation step by role B (the dual-session
     * scenario numbers its roles A1/A2/B1/B2 — the letter prefix decides).
     */
    @Test
    void taskStepsAreBoundToTheirClosedLoopRoles() {
        for (ScenarioCase scenario : CORPUS.scenarios()) {
            for (ScenarioCase.ScenarioStep step : scenario.steps()) {
                NegotiationApi api = step.caseData().api();
                if (api.family() != NegotiationApi.Family.TASK) {
                    continue;
                }
                String role = step.role() == null ? "" : step.role();
                String expectedSide = api.jsonName().startsWith("generate") ? "A" : "B";
                assertTrue(
                        role.startsWith(expectedSide),
                        scenario.id() + " step " + step.step() + " (" + api.jsonName()
                                + ") must be acted by the " + (expectedSide.equals("A") ? "workbench (A)" : "OMC (B)")
                                + " side, but the role is '" + step.role() + "'");
            }
        }
    }

    // ------------------------------------------------------------------ drift probes (Q22)

    /**
     * Q22 full strictness: a drift probe is a compliant-but-reworded peer message, so it must (a) live in the validate
     * family, (b) expect success — a drift probe that fails would reward exact-wording coupling instead of semantic
     * compliance — and (c) take the peer message as inline text, never a golden fixture (golden is the regression
     * equality anchor, not the validate mainline input).
     */
    @Test
    void driftProbesAreCompliantPeerTextExpectingSuccess() {
        boolean seen = false;
        for (NegotiationCase testCase : CORPUS.cases()) {
            if (!testCase.baseId().startsWith("VAL-DRIFT-")) {
                continue;
            }
            seen = true;
            assertTrue(
                    testCase.api().family() == NegotiationApi.Family.VALIDATE,
                    testCase.errorPrefix() + ": a drift probe must be a validate-family case");
            assertTrue(
                    testCase.expect().success(),
                    testCase.errorPrefix() + ": a compliant-but-reworded peer message must pass validation");
            assertTrue(
                    testCase.prompt() instanceof PromptSource.Text,
                    testCase.errorPrefix()
                            + ": a drift probe must carry the peer message as inline text (prompt.text),"
                            + " not a golden fixture or a fromStep reference");
        }
        assertTrue(seen, "the corpus must carry at least one VAL-DRIFT drift probe");
    }

    // ------------------------------------------------------------------ live family (live design §2.2)

    /**
     * The live records live in their own {@code liveCases} list and are invisible to the six offline contracts above;
     * this contract pins their phase-1 scope (Q5/Q6): the {@code LIVE-} id prefix, zh-CN only, and the two task APIs —
     * a record outside the scope could never run through the live engine's dispatch.
     */
    @Test
    void liveRecordsStayInThePhase1Scope() {
        assertFalse(CORPUS.liveCases().isEmpty(), "the corpus must carry at least one live record");
        for (LiveCase liveCase : CORPUS.liveCases()) {
            assertTrue(
                    liveCase.baseId().startsWith("LIVE-"),
                    liveCase.errorPrefix() + ": the live family ids carry the 'LIVE-' prefix");
            assertEquals(
                    "zh-CN", liveCase.language(), liveCase.errorPrefix() + ": live phase 1 covers zh-CN only (Q6)");
            assertTrue(
                    liveCase.api() == NegotiationApi.GENERATE_TASK_PROMPT_FROM_TEXT
                            || liveCase.api() == NegotiationApi.VALIDATE_TASK_PROMPT_AND_DATA_FILLING,
                    liveCase.errorPrefix() + ": live phase 1 covers the two task APIs (Q5) but the record declares "
                            + liveCase.api().jsonName());
        }
    }

    /**
     * Live expectation completeness: the loose block still has to pin something checkable — {@code paramsContains}
     * values non-null (a null expected value would never match, the engine asserts non-null extraction), non-blank
     * {@code paramsAbsent} slots and {@code promptTextContains} fragments, and a positive {@code maxLlmCalls} bound.
     */
    @Test
    void liveExpectationBlocksAreComplete() {
        for (LiveCase liveCase : CORPUS.liveCases()) {
            LiveExpectation expect = liveCase.liveExpect();
            for (Map.Entry<String, Object> entry : expect.paramsContains().entrySet()) {
                assertTrue(
                        entry.getValue() != null,
                        liveCase.errorPrefix() + ": paramsContains." + entry.getKey() + " must pin a non-null value");
            }
            for (String slot : expect.paramsAbsent()) {
                assertFalse(slot.isBlank(), liveCase.errorPrefix() + ": paramsAbsent entries must not be blank");
            }
            for (String fragment : expect.promptTextContains()) {
                assertFalse(
                        fragment.isBlank(),
                        liveCase.errorPrefix() + ": promptTextContains fragments must not be blank");
            }
            if (expect.maxLlmCalls() != null) {
                assertTrue(
                        expect.maxLlmCalls() > 0,
                        liveCase.errorPrefix() + ": maxLlmCalls must be a positive upper bound");
            }
        }
    }

    // ------------------------------------------------------------------ bilingual parity (§7, four definitions)

    /**
     * Parity ①: every happy case of the case files declares both languages, so both expansions run and must succeed.
     */
    @Test
    void happyCasesDeclareBothLanguages() {
        for (List<NegotiationCase> group : caseFileGroupsByBaseId().values()) {
            if (!group.get(0).expect().success()) {
                continue;
            }
            Set<String> languages = languagesOf(group);
            assertEquals(
                    Set.of("zh-CN", "en-US"),
                    languages,
                    group.get(0).errorPrefix()
                            + ": a happy case must run in both languages (the suite executes every expansion)");
        }
    }

    /**
     * Parity ②: all language expansions of one record share the identical expectation block, so a failure has one code.
     */
    @Test
    void failureCasesShareTheIdenticalExpectationAcrossLanguages() {
        for (List<NegotiationCase> group : caseFileGroupsByBaseId().values()) {
            Expectation reference = group.get(0).expect();
            for (NegotiationCase expansion : group) {
                assertEquals(
                        reference,
                        expansion.expect(),
                        expansion.errorPrefix()
                                + ": every language expansion of one record must carry the identical expectation"
                                + " block, so a failure case fails with the same error code in both languages");
            }
        }
    }

    /** Parity ③: the golden fixture directories of the two languages carry the same number of fixtures. */
    @Test
    void goldenFixtureCountsMatchAcrossLanguages() {
        assertEquals(
                countGoldenFixtures("zh-CN"),
                countGoldenFixtures("en-US"),
                "the golden fixtures must exist in equal numbers for zh-CN and en-US");
    }

    /**
     * Parity ④: every record expands exactly once per declared language — no language is silently dropped or doubled.
     */
    @Test
    void recordsExpandExactlyOncePerDeclaredLanguage() {
        for (List<NegotiationCase> group : caseFileGroupsByBaseId().values()) {
            Set<String> languages = languagesOf(group);
            assertEquals(
                    languages.size(),
                    group.size(),
                    group.get(0).errorPrefix() + ": every declared language must expand into exactly one case");
        }
        Map<String, List<ScenarioCase>> scenarioGroups = new LinkedHashMap<>();
        for (ScenarioCase scenario : CORPUS.scenarios()) {
            scenarioGroups
                    .computeIfAbsent(scenario.baseId(), key -> new ArrayList<>())
                    .add(scenario);
        }
        for (List<ScenarioCase> group : scenarioGroups.values()) {
            Set<String> languages = new LinkedHashSet<>();
            for (ScenarioCase scenario : group) {
                assertTrue(languages.add(scenario.language()), "duplicate language expansion of " + scenario.id());
            }
            assertEquals(
                    languages.size(), group.size(), "every declared language must expand into exactly one scenario");
        }
    }

    // ------------------------------------------------------------------ business review soft gate (Q19, §8.7)

    /**
     * Q19 soft gate (design §8.7): when enabled, every P0 case of the corpus must carry the review status 通过 in the
     * {@code docs-local/review/review-status.json} produced by {@code tools/corpus_review.py collect} — the P0
     * review-approval rate must be 100% before a release.
     *
     * <p>The gate is strictly opt-in so it never blocks daily development: it runs only when the system property
     * {@code corpus.review.gate} is set to {@code true} AND the status file exists; otherwise the test is skipped via
     * {@link Assumptions#assumeTrue(boolean, String)}. Scenario records carry no priority and are therefore out of the
     * P0 gate's scope (they stay tracked through the review dashboard itself).
     */
    @Test
    void p0CasesAreFullyApprovedByTheBusinessReview() throws IOException {
        Assumptions.assumeTrue(
                Boolean.getBoolean("corpus.review.gate"),
                "the business review gate is opt-in (enable with -Dcorpus.review.gate=true)");
        Path statusFile = findReviewStatusFile();
        Assumptions.assumeTrue(
                statusFile != null,
                "docs-local/review/review-status.json not found (run tools/corpus_review.py collect to produce it)");

        JsonNode reviewedCases =
                new ObjectMapper().readTree(Files.readString(statusFile)).path("cases");
        List<String> notApproved = new ArrayList<>();
        for (List<NegotiationCase> group : caseFileGroupsByBaseId().values()) {
            if (!"P0".equals(group.get(0).priority())) {
                continue;
            }
            String baseId = group.get(0).baseId();
            String status = reviewedCases.path(baseId).path("status").asText("");
            if (!APPROVED_STATUS.equals(status)) {
                notApproved.add(baseId + " (status: '" + (status.isEmpty() ? "unreviewed" : status) + "')");
            }
        }
        assertTrue(
                notApproved.isEmpty(),
                "the P0 review-approval rate must be 100% before a release, not approved yet: " + notApproved);
    }

    /** The single review status that counts as approved ({@code tools/corpus_review.py}: 通过 / 有疑问 / 否决). */
    private static final String APPROVED_STATUS = "通过";

    /**
     * Locates {@code docs-local/review/review-status.json} by walking up from the working directory to the repo root.
     */
    private static @Nullable Path findReviewStatusFile() {
        Path directory = Path.of(".").toAbsolutePath();
        while (directory != null) {
            Path candidate = directory.resolve("docs-local").resolve("review").resolve("review-status.json");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            directory = directory.getParent();
        }
        return null;
    }

    // ------------------------------------------------------------------ helpers

    /** All case records of the corpus: the case-file records plus the scenario step cases. */
    private static List<NegotiationCase> allCaseRecords() {
        List<NegotiationCase> records = new ArrayList<>(CORPUS.cases());
        for (ScenarioCase scenario : CORPUS.scenarios()) {
            for (ScenarioCase.ScenarioStep step : scenario.steps()) {
                records.add(step.caseData());
            }
        }
        return records;
    }

    /** Case-file records grouped by base id (the loader puts scenario step cases into the scenarios, not the cases). */
    private static Map<String, List<NegotiationCase>> caseFileGroupsByBaseId() {
        Map<String, List<NegotiationCase>> groups = new LinkedHashMap<>();
        for (NegotiationCase testCase : CORPUS.cases()) {
            groups.computeIfAbsent(testCase.baseId(), key -> new ArrayList<>()).add(testCase);
        }
        assertFalse(groups.isEmpty(), "the corpus must carry at least one case-file record");
        return groups;
    }

    private static Set<String> languagesOf(List<NegotiationCase> group) {
        Set<String> languages = new LinkedHashSet<>();
        for (NegotiationCase expansion : group) {
            assertTrue(
                    languages.add(expansion.language()), "duplicate language expansion of " + expansion.errorPrefix());
        }
        return languages;
    }

    private static int countGoldenFixtures(String language) {
        URL url = CorpusContractTest.class.getClassLoader().getResource("golden/" + language);
        if (url == null) {
            return 0;
        }
        try {
            Path directory = Path.of(url.toURI());
            try (Stream<Path> files = Files.list(directory)) {
                return (int) files.filter(file -> file.getFileName().toString().endsWith(".md"))
                        .count();
            }
        } catch (URISyntaxException | IOException exception) {
            throw new IllegalStateException("Failed to list the golden fixtures of " + language, exception);
        }
    }
}
