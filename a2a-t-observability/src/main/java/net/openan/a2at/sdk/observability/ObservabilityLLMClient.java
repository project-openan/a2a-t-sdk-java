package net.openan.a2at.sdk.observability;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMResponse;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** LLMClient decorator: usage stash + gen_ai.client.token.usage histogram; never breaks the call. */
public final class ObservabilityLLMClient implements LLMClient {

    public static final String METRIC_TOKEN_USAGE = "gen_ai.client.token.usage";
    private static final Logger LOG = LoggerFactory.getLogger(ObservabilityLLMClient.class);
    private final LLMClient delegate;

    public ObservabilityLLMClient(LLMClient delegate) {
        this.delegate = java.util.Objects.requireNonNull(delegate);
    }

    @Override
    public LLMResponse structured(
            List<Map<String, String>> messages,
            Map<String, Object> jsonSchema,
            @Nullable Double temperature,
            @Nullable Integer maxTokens) {
        LLMResponse response = delegate.structured(messages, jsonSchema, temperature, maxTokens);
        report(response);
        return response;
    }

    private void report(LLMResponse response) {
        try {
            Map<String, Integer> usage = response.usage() == null ? Map.of() : response.usage();
            Integer input = usage.get("prompt_tokens");
            Integer output = usage.get("completion_tokens");
            LlmUsageStashHolder.set(new LlmUsageStash(input, output, response.model()));
            if (ObservabilityRuntime.isMetricEnabled()) {
                recordTokenUsage(input, output);
            }
        } catch (RuntimeException e) {
            LOG.warn("a2at: failed to report llm token usage", e);
        }
    }

    private void recordTokenUsage(@Nullable Integer input, @Nullable Integer output) {
        if (input != null) {
            MetricHistograms.histogram(METRIC_TOKEN_USAGE, "{token}", "GenAI client token usage")
                    .record(
                            input,
                            Attributes.of(AttributeKey.stringKey(A2ATAttributes.ATTR_GEN_AI_TOKEN_TYPE), "input"));
        }
        if (output != null) {
            MetricHistograms.histogram(METRIC_TOKEN_USAGE, "{token}", "GenAI client token usage")
                    .record(
                            output,
                            Attributes.of(AttributeKey.stringKey(A2ATAttributes.ATTR_GEN_AI_TOKEN_TYPE), "output"));
        }
    }
}
