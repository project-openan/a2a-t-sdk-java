package net.openan.a2at.sample.client.request;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallContext;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.TextPart;

/**
 * Builds real a2a-java message requests for the sample client flow.
 *
 * @since 2026-05
 */
public final class A2AJavaRequestBuilder {

    private A2AJavaRequestBuilder() {
    }

    public static BuiltA2AJavaRequest buildStreamRequest(
            String promptText, String extensionUri, String metadataPayload) {
        Message message = Message.builder()
                .messageId(UUID.randomUUID().toString())
                .role(Message.Role.ROLE_USER)
                .parts(new TextPart(metadataPayload))
                .metadata(Map.of(extensionUri, promptText))
                .build();
        MessageSendParams request = MessageSendParams.builder().message(message).build();
        ClientCallContext callContext = new ClientCallContext(Map.of(), Map.of("A2A-Extensions", extensionUri));
        return new BuiltA2AJavaRequest(request, callContext);
    }
}


