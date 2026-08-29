package net.openan.a2at.sample.service_recovery.client.prompt;

import java.util.Map;
import net.openan.a2at.sdk.client.A2ATClient;
import net.openan.a2at.sdk.core.model.MetadataContent;

/**
 * {@link SamplePromptClient} adapter delegating to the real {@link A2ATClient} facade.
 *
 * <p>Both generation entry points share one facade instance, proving the two Notification-T
 * generation APIs can run sequentially over the same client configuration.
 *
 * @since 2026-08
 */
public final class A2ATNotificationPromptClient implements SamplePromptClient {

    private final A2ATClient delegate;

    /**
     * Creates the adapter over one client facade.
     *
     * @param delegate SDK client facade used for the Notification-T generation calls
     */
    public A2ATNotificationPromptClient(A2ATClient delegate) {
        this.delegate = delegate;
    }

    @Override
    public MetadataContent generateNotificationPromptFromText(String text, String templateUri) {
        return delegate.generateNotificationPromptFromText(text, templateUri);
    }

    @Override
    public MetadataContent generateNotificationPromptFromDataWithSchema(
            Map<String, Object> data, Map<String, Object> schema, String templateUri) {
        return delegate.generateNotificationPromptFromDataWithSchema(data, schema, templateUri);
    }
}
