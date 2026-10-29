package net.openan.a2at.sdk.observability;

import org.jspecify.annotations.Nullable;

/** Token usage captured from one LLM call; drained onto the next entry span. */
public record LlmUsageStash(
        @Nullable Integer inputTokens, @Nullable Integer outputTokens, @Nullable String requestModel) {}
