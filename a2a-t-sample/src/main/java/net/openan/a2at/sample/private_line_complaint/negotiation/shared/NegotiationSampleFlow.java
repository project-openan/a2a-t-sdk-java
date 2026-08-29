package net.openan.a2at.sample.private_line_complaint.negotiation.shared;

import java.util.Map;
import java.util.UUID;
import net.openan.a2at.sdk.client.A2ATClient;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.NegotiationPerformative;
import net.openan.a2at.sdk.server.A2ATServer;
import net.openan.a2at.sample.negotiation.shared.InformationNegotiationSchemas;

/** Runs the six Negotiation-T APIs used by the private-line complaint sample. */
public final class NegotiationSampleFlow {

    public static final String PROPOSE_TEMPLATE_URI = StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE_URI;

    public static final String ENDING_TEMPLATE_URI = StandardTemplates.INFORMATION_NEGOTIATION_ACCEPT_REJECT_URI;

    private NegotiationSampleFlow() {
    }

    public static NegotiationFlowResult run(
            A2ATClient client,
            A2ATServer server,
            NegotiationScenario scenario,
            NegotiationDecision decision) {
        NegotiationContext requestContext =
                new NegotiationContext(UUID.randomUUID().toString(), 1, 3, NegotiationPerformative.PROPOSE);
        MetadataContent propose =
                client.generateNegotiationProposePromptFromText(scenario.proposeText(), requestContext, PROPOSE_TEMPLATE_URI);
        Map<String, Object> proposeMetadata = propose.buildMetadataContent();
        String proposePrompt = NegotiationMetadataReader.readPrompt(proposeMetadata, PROPOSE_TEMPLATE_URI);
        NegotiationContext proposeContext = NegotiationMetadataReader.readContext(proposeMetadata);
        FilledParamData proposeData = server.validateProposePromptAndDataFilling(
                proposePrompt, proposeContext, InformationNegotiationSchemas.propose(), PROPOSE_TEMPLATE_URI);

        NegotiationContext responseContext = proposeContext;
        MetadataContent ending = decision == NegotiationDecision.ACCEPT
                ? server.generateNegotiationAcceptPromptFromText(scenario.acceptText(), responseContext, ENDING_TEMPLATE_URI)
                : server.generateNegotiationRejectPromptFromText(scenario.rejectText(), responseContext, ENDING_TEMPLATE_URI);
        Map<String, Object> endingMetadata = ending.buildMetadataContent();
        String endingPrompt = NegotiationMetadataReader.readPrompt(endingMetadata, ENDING_TEMPLATE_URI);
        NegotiationContext endingContext = NegotiationMetadataReader.readContext(endingMetadata);
        FilledParamData endingData = decision == NegotiationDecision.ACCEPT
                ? client.validateAcceptPromptAndDataFilling(
                        endingPrompt, endingContext, InformationNegotiationSchemas.accept(), ENDING_TEMPLATE_URI)
                : client.validateRejectPromptAndDataFilling(
                        endingPrompt, endingContext, InformationNegotiationSchemas.reject(), ENDING_TEMPLATE_URI);
        return new NegotiationFlowResult(requestContext, propose, proposeData, ending, endingData, decision);
    }

    /**
     * Builds a context from the filled negotiation data.
     *
     * <p>The merged data carries the three session fields, so the performative of the message the context travels with
     * is passed in explicitly. The performative of an outbound message is stamped by the generation API
     * ({@code completeGeneration}), overriding whatever this context carries.
     *
     * @param data filled negotiation data holding the {@code id}, {@code round}, and {@code maxRounds} fields
     * @param performative communicative intent of the message the returned context travels with
     * @return the negotiation context
     * @throws IllegalArgumentException when the data does not contain a valid context
     */
    public static NegotiationContext contextFrom(Map<String, Object> data, NegotiationPerformative performative) {
        Object id = data.get("id");
        Object round = data.get("round");
        Object maxRounds = data.get("maxRounds");
        if (!(id instanceof String text) || !(round instanceof Number roundNumber) || !(maxRounds instanceof Number maxRoundsNumber)) {
            throw new IllegalArgumentException("Filled negotiation data does not contain a valid context");
        }
        return new NegotiationContext(text, roundNumber.intValue(), maxRoundsNumber.intValue(), performative);
    }

    public record NegotiationFlowResult(
            NegotiationContext requestContext,
            MetadataContent propose,
            FilledParamData proposeData,
            MetadataContent ending,
            FilledParamData endingData,
            NegotiationDecision decision) {
    }
}
