package net.openan.a2at.sample.client.request;

import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallContext;
import org.a2aproject.sdk.spec.MessageSendParams;

/**
 * Real a2a-java request bundle used by the sample client flow.
 *
 * @since 2026-05
 */
public record BuiltA2AJavaRequest(MessageSendParams request, ClientCallContext callContext) {
}


