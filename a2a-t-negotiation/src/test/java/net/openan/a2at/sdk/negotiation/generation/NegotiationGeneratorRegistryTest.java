package net.openan.a2at.sdk.negotiation.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.openan.a2at.sdk.core.exception.ErrorCatalog;
import net.openan.a2at.sdk.core.model.NegotiationPerformative;
import net.openan.a2at.sdk.negotiation.content.FeasibilityEndingContent;
import net.openan.a2at.sdk.negotiation.content.FeasibilityProposeContent;
import net.openan.a2at.sdk.negotiation.content.InformationEndingContent;
import net.openan.a2at.sdk.negotiation.content.InformationProposeContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationAction;
import net.openan.a2at.sdk.negotiation.content.NegotiationConclusion;
import net.openan.a2at.sdk.negotiation.content.NegotiationContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationGenerationException;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;
import net.openan.a2at.sdk.negotiation.content.NegotiationType;
import net.openan.a2at.sdk.negotiation.content.TargetEndingContent;
import net.openan.a2at.sdk.negotiation.content.TargetProposeContent;
import org.junit.jupiter.api.Test;

class NegotiationGeneratorRegistryTest {

    private final NegotiationGeneratorRegistry registry = new NegotiationGeneratorRegistry();

    private NegotiationGenerator resolve(
            NegotiationType type, NegotiationPerformative phase, NegotiationContent content) {
        return registry.resolve(type, phase, content, "zh-CN");
    }

    @Test
    void dispatchesEveryTypeAndPhaseCombination() {
        assertInstanceOf(
                InformationProposeGenerator.class,
                resolve(
                        NegotiationType.INFORMATION,
                        NegotiationPerformative.PROPOSE,
                        new InformationProposeContent(List.of(), null)));
        assertInstanceOf(
                TargetProposeGenerator.class,
                resolve(
                        NegotiationType.TARGET,
                        NegotiationPerformative.PROPOSE,
                        new TargetProposeContent("描述", null, null, null, null)));
        assertInstanceOf(
                FeasibilityProposeGenerator.class,
                resolve(
                        NegotiationType.FEASIBILITY,
                        NegotiationPerformative.PROPOSE,
                        new FeasibilityProposeContent(
                                "描述",
                                NegotiationAction.REQUEST_FEASIBILITY_EVALUATION,
                                List.of(new NegotiationItem("名称", "值")),
                                null,
                                null)));
        assertInstanceOf(
                InformationEndingGenerator.class,
                resolve(
                        NegotiationType.INFORMATION,
                        NegotiationPerformative.ACCEPT,
                        new InformationEndingContent(NegotiationConclusion.ACCEPT, List.of())));
        assertInstanceOf(
                InformationEndingGenerator.class,
                resolve(
                        NegotiationType.INFORMATION,
                        NegotiationPerformative.REJECT,
                        new InformationEndingContent(NegotiationConclusion.REJECT, List.of())));
        assertInstanceOf(
                TargetEndingGenerator.class,
                resolve(
                        NegotiationType.TARGET,
                        NegotiationPerformative.ACCEPT,
                        new TargetEndingContent(NegotiationConclusion.ACCEPT, "确认的意图", null)));
        assertInstanceOf(
                TargetEndingGenerator.class,
                resolve(
                        NegotiationType.TARGET,
                        NegotiationPerformative.REJECT,
                        new TargetEndingContent(NegotiationConclusion.REJECT, null, "失败原因")));
        assertInstanceOf(
                FeasibilityEndingGenerator.class,
                resolve(
                        NegotiationType.FEASIBILITY,
                        NegotiationPerformative.ACCEPT,
                        new FeasibilityEndingContent(NegotiationConclusion.ACCEPT, "评估结论")));
        assertInstanceOf(
                FeasibilityEndingGenerator.class,
                resolve(
                        NegotiationType.FEASIBILITY,
                        NegotiationPerformative.REJECT,
                        new FeasibilityEndingContent(NegotiationConclusion.REJECT, "评估结论")));
    }

    @Test
    void rejectsProposeContentInTerminalPhaseAndEndingContentInProposePhase() {
        IllegalArgumentException proposeInEnding = assertThrows(
                IllegalArgumentException.class,
                () -> resolve(
                        NegotiationType.INFORMATION,
                        NegotiationPerformative.ACCEPT,
                        new InformationProposeContent(List.of(), null)));
        assertTrue(proposeInEnding
                .getMessage()
                .contains("ACCEPT phase requires ending content but received propose content of type"
                        + " InformationProposeContent"));

        IllegalArgumentException endingInPropose = assertThrows(
                IllegalArgumentException.class,
                () -> resolve(
                        NegotiationType.INFORMATION,
                        NegotiationPerformative.PROPOSE,
                        new InformationEndingContent(NegotiationConclusion.ACCEPT, List.of())));
        assertTrue(endingInPropose
                .getMessage()
                .contains("PROPOSE phase requires propose content but received ending content of type"
                        + " InformationEndingContent"));
    }

    @Test
    void rejectsContentRuntimeTypeNotMatchingTheNegotiationType() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> resolve(
                        NegotiationType.INFORMATION,
                        NegotiationPerformative.PROPOSE,
                        new TargetProposeContent("描述", null, null, null, null)));

        assertTrue(exception
                .getMessage()
                .contains("Negotiation type INFORMATION requires content of type InformationProposeContent but received"
                        + " TargetProposeContent"));

        assertThrows(
                IllegalArgumentException.class,
                () -> resolve(
                        NegotiationType.TARGET,
                        NegotiationPerformative.ACCEPT,
                        new InformationEndingContent(NegotiationConclusion.ACCEPT, List.of())));
    }

    @Test
    void rejectsEndingConclusionNotMatchingThePhase() {
        NegotiationGenerationException exception = assertThrows(
                NegotiationGenerationException.class,
                () -> resolve(
                        NegotiationType.INFORMATION,
                        NegotiationPerformative.ACCEPT,
                        new InformationEndingContent(NegotiationConclusion.REJECT, List.of())));

        assertEquals(ErrorCatalog.NEGOTIATION_CONCLUSION_MISMATCH.getCode(), exception.getCode());
        assertEquals("Accept", exception.getFacts().get("expected"));
        assertEquals("Reject", exception.getFacts().get("actual"));

        assertThrows(
                NegotiationGenerationException.class,
                () -> resolve(
                        NegotiationType.TARGET,
                        NegotiationPerformative.REJECT,
                        new TargetEndingContent(NegotiationConclusion.ACCEPT, "确认的意图", null)));
    }

    @Test
    void rejectsEndingContentWithoutConclusion() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> resolve(
                        NegotiationType.INFORMATION,
                        NegotiationPerformative.ACCEPT,
                        new InformationEndingContent(null, List.of())));

        assertTrue(exception.getMessage().contains("conclusion must not be null"));
    }

    @Test
    void rejectsNullArguments() {
        NegotiationContent content = new InformationProposeContent(List.of(), null);

        assertEquals(
                "Negotiation type must not be null for the PROPOSE phase.",
                assertThrows(NullPointerException.class, () -> resolve(null, NegotiationPerformative.PROPOSE, content))
                        .getMessage());
        assertEquals(
                "Negotiation phase must not be null.",
                assertThrows(NullPointerException.class, () -> resolve(NegotiationType.INFORMATION, null, content))
                        .getMessage());
        assertEquals(
                "Negotiation content must not be null.",
                assertThrows(
                                NullPointerException.class,
                                () -> resolve(NegotiationType.INFORMATION, NegotiationPerformative.PROPOSE, null))
                        .getMessage());
    }
}
