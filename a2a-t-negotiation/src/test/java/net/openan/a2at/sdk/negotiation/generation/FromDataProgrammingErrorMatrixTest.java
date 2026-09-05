package net.openan.a2at.sdk.negotiation.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.openan.a2at.sdk.core.exception.A2ATError;
import net.openan.a2at.sdk.core.exception.ErrorCatalog;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.NegotiationPerformative;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.negotiation.content.FeasibilityEndingContent;
import net.openan.a2at.sdk.negotiation.content.FeasibilityProposeContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationAction;
import net.openan.a2at.sdk.negotiation.content.NegotiationConclusion;
import net.openan.a2at.sdk.negotiation.content.NegotiationEndingData;
import net.openan.a2at.sdk.negotiation.content.NegotiationGenerationException;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;
import net.openan.a2at.sdk.negotiation.content.NegotiationProposeData;
import net.openan.a2at.sdk.negotiation.content.TargetEndingContent;
import net.openan.a2at.sdk.negotiation.content.TargetProposeContent;
import org.junit.jupiter.api.Test;

/**
 * Verifies the corpus-inexpressible rows of the programming-error matrix of the from-data generation (design §6 Q8).
 *
 * <p>The remaining rows of the matrix — null context, phase-conclusion mismatch, blank conditional fields of the ending
 * family, empty evaluation/alternative lists and every template-URI phase violation — are absorbed by the
 * {@code FD-PROG} batch of the test corpus ({@code negotiation-cases/from-data/programming-errors.json}), which asserts
 * the same exception type, message fragment and zero-LLM guarantee per case. Only the rows the corpus cannot express
 * stay here: null propose/ending data (a corpus record always carries {@code input.data}), a null conclusion and a
 * missing feasibility action (the typed-input assembler rejects both as authoring defects), blank propose descriptions
 * and ending summaries (the assembler demands non-blank text), and a template URI whose type contradicts the content
 * type (the assembler derives the content type from the URI, so the contradiction cannot be authored). Every row fails
 * with a standard {@link NullPointerException} (pure null arguments) or {@link IllegalArgumentException} (URI-shape and
 * constructor-constraint violations) that is not part of the SDK business-failure hierarchy and carries an English
 * message pointing at the offending input. Blank content fields are coded business failures carrying
 * {@code negotiation.content_invalid} and are covered by a dedicated test below. No row ever reaches the LLM.
 * Structural URI malformation is impossible by construction of {@link TemplateUri} and is therefore not a row.
 */
class FromDataProgrammingErrorMatrixTest {

    private static final String UUID = "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3";

    private static final TemplateUri INFORMATION_PROPOSE_URI = StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE;

    private static final TemplateUri INFORMATION_ACCEPT_URI = StandardTemplates.INFORMATION_NEGOTIATION_ACCEPT_REJECT;

    private static final TemplateUri TARGET_PROPOSE_URI = StandardTemplates.TARGET_NEGOTIATION_PROPOSE;

    private static final TemplateUri FEASIBILITY_PROPOSE_URI = StandardTemplates.FEASIBILITY_NEGOTIATION_PROPOSE;

    private static final TemplateUri FEASIBILITY_ACCEPT_URI = StandardTemplates.FEASIBILITY_NEGOTIATION_ACCEPT_REJECT;

    private static final TemplateUri TARGET_ACCEPT_URI = StandardTemplates.TARGET_NEGOTIATION_ACCEPT_REJECT;

    private final CountingClient llm = new CountingClient();

    private final NegotiationGenerationOrchestrator orchestrator = NegotiationGenerationOrchestratorBuilder.builder()
            .language("zh-CN")
            .llmClient(llm)
            .build();

    @Test
    void everyMatrixRowFailsWithAStandardJavaException() {
        List<MatrixRow> matrix = matrix();

        // The propose-versus-ending family mismatch of UT-GEN-002 is deliberately absent here: the typed data records
        // make that mismatch a compile-time error at the facade, so it cannot occur at runtime.
        assertEquals(5, matrix.size(), "exactly the corpus-inexpressible rows must stay (FD-PROG absorbs the rest)");
        for (MatrixRow row : matrix) {
            RuntimeException failure =
                    assertThrows(row.expectedException(), () -> row.call().get(), "row must fail: " + row.label());
            assertFalse(
                    A2ATError.class.isInstance(failure),
                    "a programming error must not be part of the processing-error hierarchy: " + row.label());
            assertTrue(
                    failure.getMessage() != null && !failure.getMessage().isBlank(),
                    "failure message must be present: " + row.label());
            assertTrue(
                    isAsciiText(failure.getMessage()),
                    "failure message must be English (ASCII): " + failure.getMessage());
            assertTrue(
                    failure.getMessage().contains(row.expectedMessageFragment()),
                    "failure message must point at the problem of row " + row.label() + " but was: "
                            + failure.getMessage());
        }
        assertEquals(0, llm.calls, "no matrix row may call the LLM");
    }

    /**
     * Returns the corpus-inexpressible rows of the programming-error matrix of the from-data variants (design §6 Q8:
     * FD-PROG-01..14 absorb the other fourteen rows). See the class javadoc for why each row below cannot be expressed
     * as a corpus record.
     */
    private List<MatrixRow> matrix() {
        NegotiationContext context = new NegotiationContext(UUID, 2, 5, NegotiationPerformative.PROPOSE);
        NegotiationContext acceptContext = new NegotiationContext(UUID, 2, 5, NegotiationPerformative.ACCEPT);
        return List.of(
                new MatrixRow(
                        "null propose data",
                        () -> orchestrator.generateProposeFromData(null, INFORMATION_PROPOSE_URI),
                        NullPointerException.class,
                        "Negotiation propose data must not be null."),
                new MatrixRow(
                        "null ending data",
                        () -> orchestrator.generateAcceptFromData(null, INFORMATION_ACCEPT_URI),
                        NullPointerException.class,
                        "Negotiation ending data must not be null."),
                new MatrixRow(
                        "accept method with null conclusion",
                        () -> orchestrator.generateAcceptFromData(
                                new NegotiationEndingData(acceptContext, new TargetEndingContent(null, "intent", null)),
                                TARGET_ACCEPT_URI),
                        NullPointerException.class,
                        "conclusion must not be null"),
                new MatrixRow(
                        "feasibility propose without action",
                        () -> orchestrator.generateProposeFromData(
                                new NegotiationProposeData(
                                        context,
                                        new FeasibilityProposeContent(
                                                "请评估。", null, List.of(new NegotiationItem("目标", "2Mbps")), null, null)),
                                FEASIBILITY_PROPOSE_URI),
                        NullPointerException.class,
                        "Feasibility negotiation action must not be null"),
                new MatrixRow(
                        "template URI type contradicts the content type",
                        () -> orchestrator.generateProposeFromData(
                                new NegotiationProposeData(
                                        context, new TargetProposeContent("目标协商概述。", null, null, null, null)),
                                INFORMATION_PROPOSE_URI),
                        IllegalArgumentException.class,
                        "Negotiation type INFORMATION requires content of type InformationProposeContent"));
    }

    @Test
    void blankRequiredContentFieldsFailWithTheCodedContentInvalidFailure() {
        NegotiationContext context = new NegotiationContext(UUID, 2, 5, NegotiationPerformative.PROPOSE);
        NegotiationContext acceptContext = new NegotiationContext(UUID, 2, 5, NegotiationPerformative.ACCEPT);
        List<Runnable> blankFieldCalls = List.of(
                () -> orchestrator.generateProposeFromData(
                        new NegotiationProposeData(context, new TargetProposeContent(" ", null, null, null, null)),
                        TARGET_PROPOSE_URI),
                () -> orchestrator.generateProposeFromData(
                        new NegotiationProposeData(
                                context,
                                new FeasibilityProposeContent(
                                        " ",
                                        NegotiationAction.REQUEST_FEASIBILITY_EVALUATION,
                                        List.of(new NegotiationItem("目标", "2Mbps")),
                                        null,
                                        null)),
                        FEASIBILITY_PROPOSE_URI),
                () -> orchestrator.generateAcceptFromData(
                        new NegotiationEndingData(
                                acceptContext, new FeasibilityEndingContent(NegotiationConclusion.ACCEPT, " ")),
                        FEASIBILITY_ACCEPT_URI));
        List<String> expectedFields = List.of(
                "content.targetNegotiationDescription",
                "content.feasibilityNegotiationDescription",
                "content.feasibilitySummary");

        for (int index = 0; index < blankFieldCalls.size(); index++) {
            NegotiationGenerationException failure = assertThrows(
                    NegotiationGenerationException.class,
                    blankFieldCalls.get(index)::run,
                    "blank content field " + expectedFields.get(index) + " must fail");
            assertEquals(
                    ErrorCatalog.NEGOTIATION_CONTENT_INVALID.getCode(), failure.getCode(), expectedFields.get(index));
            assertEquals(expectedFields.get(index), failure.getFacts().get("field"));
            assertTrue(failure.getMessage().contains(expectedFields.get(index)), failure.getMessage());
        }
        assertEquals(0, llm.calls, "no blank-content failure may call the LLM");
    }

    private static boolean isAsciiText(String message) {
        return message.chars().allMatch(codePoint -> codePoint < 128);
    }

    /** One row of the programming-error matrix: a failing call, the expected exception type and message fragment. */
    private record MatrixRow(
            String label,
            Supplier<Object> call,
            Class<? extends RuntimeException> expectedException,
            String expectedMessageFragment) {}

    private static final class CountingClient implements LLMClient {

        private int calls;

        @Override
        public LLMResponse structured(
                List<Map<String, String>> messages,
                Map<String, Object> jsonSchema,
                Double temperature,
                Integer maxTokens) {
            calls++;
            throw new AssertionError("A from-data programming error must fail before any LLM call");
        }
    }
}
