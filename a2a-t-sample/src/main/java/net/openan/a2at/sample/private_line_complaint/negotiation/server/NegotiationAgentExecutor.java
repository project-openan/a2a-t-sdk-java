package net.openan.a2at.sample.private_line_complaint.negotiation.server;

import java.util.List;
import java.util.Map;
import net.openan.a2at.sample.negotiation.shared.InformationNegotiationSchemas;
import net.openan.a2at.sample.private_line_complaint.negotiation.shared.NegotiationDecision;
import net.openan.a2at.sample.private_line_complaint.negotiation.shared.NegotiationMetadataReader;
import net.openan.a2at.sample.private_line_complaint.negotiation.shared.NegotiationSampleFlow;
import net.openan.a2at.sample.private_line_complaint.negotiation.shared.NegotiationScenario;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.server.A2ATServer;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.TextPart;

/** Server-side A2A executor for one terminal information-negotiation reply. */
public final class NegotiationAgentExecutor implements AgentExecutor {

    private final A2ATServer server;

    private final NegotiationScenario scenario;

    private final NegotiationDecision decision;

    public NegotiationAgentExecutor(A2ATServer server, NegotiationScenario scenario, NegotiationDecision decision) {
        this.server = server;
        this.scenario = scenario;
        this.decision = decision;
    }

    @Override
    public void execute(RequestContext requestContext, AgentEmitter agentEmitter) throws A2AError {
        try {
            NegotiationMetadataReader.requireExtension(extensionHeader(requestContext));
            Message message = requestContext.getMessage();
            Map<String, ?> metadata = message == null ? null : message.metadata();
            String proposePrompt =
                    NegotiationMetadataReader.readPrompt(metadata, NegotiationSampleFlow.PROPOSE_TEMPLATE_URI);
            NegotiationContext context = NegotiationMetadataReader.readContext(metadata);
            server.validateProposePromptAndDataFilling(
                    proposePrompt,
                    context,
                    InformationNegotiationSchemas.propose(),
                    NegotiationSampleFlow.PROPOSE_TEMPLATE_URI);
            MetadataContent ending = decision == NegotiationDecision.ACCEPT
                    ? server.generateNegotiationAcceptPromptFromText(
                            scenario.acceptText(), context, NegotiationSampleFlow.ENDING_TEMPLATE_URI)
                    : server.generateNegotiationRejectPromptFromText(
                            scenario.rejectText(), context, NegotiationSampleFlow.ENDING_TEMPLATE_URI);
            agentEmitter.submit();
            agentEmitter.startWork();
            agentEmitter.addArtifact(
                    List.of(new TextPart(
                            "Information negotiation " + decision.name().toLowerCase())),
                    "information-negotiation",
                    "Private-line complaint negotiation result",
                    Map.copyOf(ending.buildMetadataContent()),
                    false,
                    true);
            agentEmitter.complete();
        } catch (RuntimeException exception) {
            agentEmitter.fail();
            throw exception;
        }
    }

    @Override
    public void cancel(RequestContext requestContext, AgentEmitter agentEmitter) throws A2AError {
        agentEmitter.cancel();
    }

    @SuppressWarnings("unchecked")
    private static String extensionHeader(RequestContext requestContext) {
        Object headers = requestContext.getCallContext().getState().get("headers");
        if (headers instanceof Map<?, ?> values) {
            return String.valueOf(((Map<String, String>) values).getOrDefault("A2A-Extensions", ""));
        }
        return "";
    }
}
