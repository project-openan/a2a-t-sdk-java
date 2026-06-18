package net.openan.a2at.sample.client.runtime;

import java.util.Map;
import java.util.function.Consumer;
import org.a2aproject.sdk.client.ClientEvent;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallContext;
import org.a2aproject.sdk.spec.MessageSendParams;

/**
 * Transitional seam for assembling the real a2a-java client runtime.
 *
 * @since 2026-05
 */
public interface A2AJavaClientRuntime {

    Object createStreamingClient(String agentBaseUrl);

    Iterable<ClientEvent> sendMessage(
            Map<String, Object> agentCard,
            MessageSendParams request,
            ClientCallContext callContext,
            Consumer<String> logSink);
}


