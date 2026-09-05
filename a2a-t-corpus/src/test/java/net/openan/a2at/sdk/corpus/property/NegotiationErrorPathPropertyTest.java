package net.openan.a2at.sdk.corpus.property;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.openan.a2at.sdk.core.exception.ErrorCatalog;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.NegotiationPerformative;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.corpus.ScriptedNegotiationLlmClient;
import net.openan.a2at.sdk.negotiation.content.NegotiationGenerationException;
import net.openan.a2at.sdk.negotiation.content.NegotiationParamExtractionException;
import net.openan.a2at.sdk.negotiation.generation.NegotiationContentService;

/**
 * Error-path property layer (design §8.3): arbitraries specifically designed to trigger one error code each.
 *
 * <p>Every property samples ~100 inputs built to violate exactly one rule — blank free text, a conclusion that
 * contradicts the addressed phase, a payload missing one required field, or a context round above the budget — and
 * asserts the exact public error code plus the exact LLM call count (0 when the failure precedes the LLM step, 1 when
 * the mapped failure is non-retryable).
 *
 * @since 2026-08
 */
class NegotiationErrorPathPropertyTest {

    private static final Map<String, Object> FLAT_SCHEMA = PropertyHarness.objectSchema(Map.of());

    @Property(tries = 100, seed = "20260910")
    void blankTextFailsWithInvalidInput(
            @ForAll("languages") String language,
            @ForAll("contexts") NegotiationContext context,
            @ForAll("blankTexts") String text) {
        ScriptedNegotiationLlmClient llm = ScriptedNegotiationLlmClient.assertionOnly();
        NegotiationContentService service = PropertyHarness.service(language, llm);
        NegotiationGenerationException propose = assertThrows(
                NegotiationGenerationException.class,
                () -> service.generateProposeFromText(
                        text,
                        context,
                        PropertyHarness.templateUri("Negotiation-T/information-negotiation/propose/v1")));
        assertEquals(ErrorCatalog.NEGOTIATION_INVALID_INPUT.getCode(), propose.getCode());
        NegotiationGenerationException abort = assertThrows(
                NegotiationGenerationException.class,
                () -> service.generateAbortFromText(
                        text, context, PropertyHarness.templateUri("Negotiation-T/common/abort/v1")));
        assertEquals(ErrorCatalog.NEGOTIATION_INVALID_INPUT.getCode(), abort.getCode());
        assertEquals(0, llm.callCount());
    }

    @Property(tries = 100, seed = "20260911")
    void conclusionMismatchingThePhaseFailsWithConclusionMismatch(
            @ForAll("languages") String language,
            @ForAll("contexts") NegotiationContext context,
            @ForAll("negotiationTypes") String typeSegment,
            @ForAll("swappedConclusions") boolean swapped) {
        String rawUri = "Negotiation-T/" + typeSegment + "/accept-reject/v1";
        TemplateUri templateUri = PropertyHarness.templateUri(rawUri);
        // An accept call receives a Reject payload and vice versa; all other required fields are present.
        String conclusion = swapped ? "Reject" : "Accept";
        ScriptedNegotiationLlmClient llm = PropertyHarness.scripted(endingPayload(conclusion));
        NegotiationContentService service = PropertyHarness.service(language, llm);
        NegotiationGenerationException exception;
        if (swapped) {
            exception = assertThrows(
                    NegotiationGenerationException.class,
                    () -> service.generateAcceptFromText("I must refuse the current offer.", context, templateUri));
        } else {
            exception = assertThrows(
                    NegotiationGenerationException.class,
                    () -> service.generateRejectFromText(
                            "I cannot agree with the current offer.", context, templateUri));
        }
        assertEquals(ErrorCatalog.NEGOTIATION_CONCLUSION_MISMATCH.getCode(), exception.getCode());
        assertEquals(1, llm.callCount());
    }

    @Property(tries = 100, seed = "20260912")
    void payloadMissingOneRequiredFieldFailsWithSlotMissing(
            @ForAll("languages") String language,
            @ForAll("contexts") NegotiationContext context,
            @ForAll("missingRequiredFields") MissingRequiredField field) {
        ScriptedNegotiationLlmClient llm = PropertyHarness.scripted(PropertyHarness.json(field.payload()));
        NegotiationContentService service = PropertyHarness.service(language, llm);
        NegotiationGenerationException exception =
                assertThrows(NegotiationGenerationException.class, () -> field.run(service, context));
        assertEquals(ErrorCatalog.NEGOTIATION_FIELD_MISSING.getCode(), exception.getCode());
        assertEquals(1, llm.callCount());
    }

    @Property(tries = 100, seed = "20260913")
    void roundAboveTheBudgetFailsWithRuleViolation(
            @ForAll("languages") String language,
            @ForAll("maxRoundsValues") int maxRounds,
            @ForAll("budgetOvershoots") int overshoot) {
        // Each leg validates the message of one performative, so its incoming context carries that performative.
        NegotiationContext proposeContext = new NegotiationContext(
                PropertyArbitraries.anySessionId(), maxRounds + overshoot, maxRounds, NegotiationPerformative.PROPOSE);
        NegotiationContext abortContext = new NegotiationContext(
                PropertyArbitraries.anySessionId(), maxRounds + overshoot, maxRounds, NegotiationPerformative.ABORT);
        ScriptedNegotiationLlmClient llm = ScriptedNegotiationLlmClient.assertionOnly();
        NegotiationContentService service = PropertyHarness.service(language, llm);
        NegotiationParamExtractionException propose = assertThrows(
                NegotiationParamExtractionException.class,
                () -> service.validateProposePromptAndDataFilling(
                        "Rendered negotiation message text.",
                        proposeContext,
                        FLAT_SCHEMA,
                        PropertyHarness.templateUri("Negotiation-T/information-negotiation/propose/v1")));
        assertEquals(ErrorCatalog.NEGOTIATION_RULE_VIOLATION.getCode(), propose.getCode());
        NegotiationParamExtractionException abort = assertThrows(
                NegotiationParamExtractionException.class,
                () -> service.validateAbortPromptAndDataFilling(
                        "Rendered negotiation message text.",
                        abortContext,
                        FLAT_SCHEMA,
                        PropertyHarness.templateUri("Negotiation-T/common/abort/v1")));
        assertEquals(ErrorCatalog.NEGOTIATION_RULE_VIOLATION.getCode(), abort.getCode());
        assertEquals(0, llm.callCount());
    }

    // ------------------------------------------------------------------ missing-field matrix

    /**
     * One required extraction field dropped from an otherwise valid payload, paired with the API whose payload it is.
     */
    private enum MissingRequiredField {
        INFORMATION_PROPOSE_ITEMS {
            @Override
            Map<String, Object> payload() {
                return withoutKey(validInformationProposePayload(), "items");
            }

            @Override
            Object run(NegotiationContentService service, NegotiationContext context) {
                return service.generateProposeFromText(
                        "Please provide the missing information.",
                        context,
                        PropertyHarness.templateUri("Negotiation-T/information-negotiation/propose/v1"));
            }
        },
        TARGET_PROPOSE_DESCRIPTION {
            @Override
            Map<String, Object> payload() {
                return withoutKey(validTargetProposePayload(), "target_negotiation_description");
            }

            @Override
            Object run(NegotiationContentService service, NegotiationContext context) {
                return service.generateProposeFromText(
                        "I want to negotiate the coverage target.",
                        context,
                        PropertyHarness.templateUri("Negotiation-T/target-negotiation/propose/v1"));
            }
        },
        FEASIBILITY_PROPOSE_DESCRIPTION {
            @Override
            Map<String, Object> payload() {
                return withoutKey(validFeasibilityProposePayload(), "feasibility_negotiation_description");
            }

            @Override
            Object run(NegotiationContentService service, NegotiationContext context) {
                return service.generateProposeFromText(
                        "Please evaluate whether the upgrade is feasible.",
                        context,
                        PropertyHarness.templateUri("Negotiation-T/feasibility-negotiation/propose/v1"));
            }
        },
        ENDING_CONCLUSION {
            @Override
            Map<String, Object> payload() {
                return withoutKey(validInformationEndingPayload(), "conclusion");
            }

            @Override
            Object run(NegotiationContentService service, NegotiationContext context) {
                return service.generateAcceptFromText(
                        "I accept the proposed information set.",
                        context,
                        PropertyHarness.templateUri("Negotiation-T/information-negotiation/accept-reject/v1"));
            }
        },
        TARGET_ACCEPT_CONFIRMED_INTENT {
            @Override
            Map<String, Object> payload() {
                return withoutKey(validTargetEndingPayload(), "confirmed_intent");
            }

            @Override
            Object run(NegotiationContentService service, NegotiationContext context) {
                return service.generateAcceptFromText(
                        "I confirm the negotiated intent.",
                        context,
                        PropertyHarness.templateUri("Negotiation-T/target-negotiation/accept-reject/v1"));
            }
        },
        ABORT_TERMINATION_REASON {
            @Override
            Map<String, Object> payload() {
                return withoutKey(validAbortPayload(), "termination_reason");
            }

            @Override
            Object run(NegotiationContentService service, NegotiationContext context) {
                return service.generateAbortFromText(
                        "The round limit is reached.",
                        context,
                        PropertyHarness.templateUri("Negotiation-T/common/abort/v1"));
            }
        };

        abstract Map<String, Object> payload();

        abstract Object run(NegotiationContentService service, NegotiationContext context);

        private static Map<String, Object> withoutKey(Map<String, Object> payload, String key) {
            Map<String, Object> reduced = new LinkedHashMap<>(payload);
            reduced.remove(key);
            return reduced;
        }
    }

    // ------------------------------------------------------------------ payload builders

    private static String endingPayload(String conclusion) {
        Map<String, Object> payload = new LinkedHashMap<>(validTargetEndingPayload());
        payload.put("conclusion", conclusion);
        return PropertyHarness.json(payload);
    }

    private static Map<String, Object> validInformationProposePayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(
                "items",
                List.of(Map.of("name", "access_port", "value", "P533-Zhujiang Old Town-PTN3900-23-TPA1EG24-1")));
        payload.put("relationship", null);
        return payload;
    }

    private static Map<String, Object> validTargetProposePayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("target_negotiation_description", "Latency repair target of the quality degradation complaint");
        payload.put("intent_understanding", List.of());
        payload.put("alignment_and_clarification", List.of());
        payload.put("request_for_clarification", List.of());
        return payload;
    }

    private static Map<String, Object> validFeasibilityProposePayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(
                "feasibility_negotiation_description", "Access port expansion feasibility within the cutover window");
        payload.put("action", "REQUEST_FEASIBILITY_EVALUATION");
        payload.put("contents_to_evaluate", List.of(Map.of("name", "latency_target", "value", "within 20ms")));
        payload.put("infeasibility_details_and_proposal", List.of());
        return payload;
    }

    private static Map<String, Object> validInformationEndingPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("conclusion", "Accept");
        payload.put(
                "items",
                List.of(Map.of("name", "access_port", "value", "P533-Zhujiang Old Town-PTN3900-23-TPA1EG24-1")));
        return payload;
    }

    private static Map<String, Object> validTargetEndingPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("conclusion", "Accept");
        payload.put("confirmed_intent", "Confirmed latency repair intent within 20ms");
        payload.put("failure_reason", null);
        return payload;
    }

    private static Map<String, Object> validAbortPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("termination_reason", "The negotiation round limit is reached.");
        return payload;
    }

    // ------------------------------------------------------------------ providers

    @Provide
    Arbitrary<String> languages() {
        return PropertyArbitraries.languages();
    }

    @Provide
    Arbitrary<NegotiationContext> contexts() {
        return PropertyArbitraries.contexts();
    }

    @Provide
    Arbitrary<String> blankTexts() {
        return Arbitraries.strings().withChars(" \t\r\n").ofMinLength(0).ofMaxLength(12);
    }

    @Provide
    Arbitrary<String> negotiationTypes() {
        return Arbitraries.of("information-negotiation", "target-negotiation", "feasibility-negotiation");
    }

    @Provide
    Arbitrary<Boolean> swappedConclusions() {
        return Arbitraries.of(true, false);
    }

    @Provide
    Arbitrary<MissingRequiredField> missingRequiredFields() {
        return Arbitraries.of(MissingRequiredField.values());
    }

    @Provide
    Arbitrary<Integer> maxRoundsValues() {
        return Arbitraries.integers().between(1, 10);
    }

    @Provide
    Arbitrary<Integer> budgetOvershoots() {
        return Arbitraries.integers().between(1, 20);
    }
}
