package net.openan.a2at.sample.private_line_complaint.negotiation.shared.mock;

import net.openan.a2at.sdk.llm.LLMClientFactory;

/** Installs the sample's schema-dispatching LLM implementation for offline runs. */
public final class NegotiationMockLlmInstaller {

    public static final String PROVIDER = "negotiation-sample-mock";

    private NegotiationMockLlmInstaller() {}

    public static synchronized void install() {
        if (!LLMClientFactory.availableProviders().contains(PROVIDER)) {
            LLMClientFactory.register(PROVIDER, NegotiationMockLLMClient.class);
        }
    }
}
