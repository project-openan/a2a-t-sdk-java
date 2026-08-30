package net.openan.a2at.sample.private_line_complaint.negotiation.client;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.openan.a2at.sample.negotiation.shared.InformationNegotiationSchemas;
import net.openan.a2at.sample.private_line_complaint.negotiation.shared.NegotiationAgentCard;
import net.openan.a2at.sample.private_line_complaint.negotiation.shared.NegotiationDecision;
import net.openan.a2at.sample.private_line_complaint.negotiation.shared.NegotiationMetadataReader;
import net.openan.a2at.sample.private_line_complaint.negotiation.shared.NegotiationSampleEnvironment;
import net.openan.a2at.sample.private_line_complaint.negotiation.shared.NegotiationSampleFlow;
import net.openan.a2at.sample.private_line_complaint.negotiation.shared.NegotiationScenarioLoader;
import net.openan.a2at.sample.private_line_complaint.negotiation.shared.mock.NegotiationMockLlmInstaller;
import net.openan.a2at.sample.subscribe_incident.client.flow.A2AJavaClientEventMapper;
import net.openan.a2at.sample.subscribe_incident.client.flow.SampleStreamTerminalStateDecider;
import net.openan.a2at.sample.subscribe_incident.client.runtime.DefaultSampleClientRuntime;
import net.openan.a2at.sdk.client.A2ATClient;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.NegotiationPerformative;
import org.a2aproject.sdk.client.ClientEvent;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallContext;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.TextPart;

/** Sends one A2A information-negotiation request and validates its terminal Artifact. */
public final class NegotiationClientMain {

    private NegotiationClientMain() {}

    public static void main(String[] args) {
        Path envPath = args.length == 0 ? NegotiationSampleEnvironment.defaultEnvPath("client") : Path.of(args[0]);
        Map<String, String> env = NegotiationSampleEnvironment.read(envPath);
        if (NegotiationMockLlmInstaller.PROVIDER.equals(env.get("A2AT_LLM_PROVIDER"))) {
            NegotiationMockLlmInstaller.install();
        }
        A2ATClient client = new A2ATClient(envPath);
        NegotiationContext context =
                new NegotiationContext(UUID.randomUUID().toString(), 1, 3, NegotiationPerformative.PROPOSE);
        MetadataContent propose = client.generateNegotiationProposePromptFromText(
                NegotiationScenarioLoader.load().proposeText(), context, NegotiationSampleFlow.PROPOSE_TEMPLATE_URI);
        Message message = Message.builder()
                .messageId(UUID.randomUUID().toString())
                .role(Message.Role.ROLE_USER)
                .parts(new TextPart("Private-line complaint information negotiation"))
                .metadata(new LinkedHashMap<>(propose.buildMetadataContent()))
                .build();
        MessageSendParams request = MessageSendParams.builder().message(message).build();
        ClientCallContext callContext =
                new ClientCallContext(Map.of(), Map.of("A2A-Extensions", propose.extensionUri()));
        try (DefaultSampleClientRuntime runtime = new DefaultSampleClientRuntime(envPath)) {
            Map<String, Object> agentCard = NegotiationAgentCard.build(
                    NegotiationSampleEnvironment.host(env), NegotiationSampleEnvironment.port(env));
            for (ClientEvent event : runtime.sendMessage(agentCard, request, callContext, System.out::println)) {
                Map<String, Object> payload = A2AJavaClientEventMapper.toPayload(event);
                Object artifact = payload.get("artifact");
                if (artifact instanceof Map<?, ?> artifactMap) {
                    Object metadata = artifactMap.get("metadata");
                    if (metadata instanceof Map<?, ?> rawMetadata) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> endingMetadata = (Map<String, Object>) rawMetadata;
                        String prompt = NegotiationMetadataReader.readPrompt(
                                endingMetadata, NegotiationSampleFlow.ENDING_TEMPLATE_URI);
                        NegotiationDecision decision = prompt.matches("(?s).*## 信息协商结果\\R\\s*Reject.*")
                                ? NegotiationDecision.REJECT
                                : NegotiationDecision.ACCEPT;
                        NegotiationContext endingContext = NegotiationMetadataReader.readContext(endingMetadata);
                        Map<String, Object> result = decision == NegotiationDecision.ACCEPT
                                ? client.validateAcceptPromptAndDataFilling(
                                                prompt,
                                                endingContext,
                                                InformationNegotiationSchemas.accept(),
                                                NegotiationSampleFlow.ENDING_TEMPLATE_URI)
                                        .data()
                                : client.validateRejectPromptAndDataFilling(
                                                prompt,
                                                endingContext,
                                                InformationNegotiationSchemas.reject(),
                                                NegotiationSampleFlow.ENDING_TEMPLATE_URI)
                                        .data();
                        System.out.println("[negotiation-client] result=" + result);
                    }
                }
                if (SampleStreamTerminalStateDecider.isTerminal(event)) {
                    break;
                }
            }
        }
    }
}
