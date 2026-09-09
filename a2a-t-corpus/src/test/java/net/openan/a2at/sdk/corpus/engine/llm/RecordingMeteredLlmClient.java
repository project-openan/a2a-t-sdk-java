package net.openan.a2at.sdk.corpus.engine.llm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.corpus.engine.util.ThrowableSerializer;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMResponse;

/**
 * Decorator wrapping the real LLM client as the single test seam of the corpus: it records every invocation with the
 * complete request (messages, schema, parameters, model), the complete response (content, model, usage), the latency
 * and the token counters, then delegates transparently. The production pipeline is otherwise assembled unchanged.
 */
public final class RecordingMeteredLlmClient implements LLMClient {

    /** One captured LLM invocation. */
    public record RecordedLlmCall(
            Map<String, Object> request,
            Map<String, Object> response,
            String result,
            long durationMs,
            int inputToken,
            int outputToken) {}

    private final LLMClient delegate;

    private final String modelName;

    private final List<RecordedLlmCall> calls = new ArrayList<>();

    public RecordingMeteredLlmClient(LLMClient delegate, String modelName) {
        this.delegate = delegate;
        this.modelName = modelName;
    }

    @Override
    public LLMResponse structured(
            List<Map<String, String>> messages,
            Map<String, Object> jsonSchema,
            Double temperature,
            Integer maxTokens) {
        long startedAt = System.nanoTime();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("messages", new ArrayList<>(messages));
        request.put("jsonSchema", jsonSchema == null ? Map.of() : jsonSchema);
        if (temperature != null) {
            request.put("temperature", temperature);
        }
        if (maxTokens != null) {
            request.put("maxTokens", maxTokens);
        }
        request.put("model", modelName);
        try {
            LLMResponse response = delegate.structured(messages, jsonSchema, temperature, maxTokens);
            Map<String, Object> responseMap = new LinkedHashMap<>();
            responseMap.put("content", response.content());
            responseMap.put("model", response.model());
            responseMap.put("usage", response.usage() == null ? Map.of() : response.usage());
            record(new RecordedLlmCall(
                    request,
                    responseMap,
                    "success",
                    elapsedMillis(startedAt),
                    tokens(response.usage(), "prompt_tokens"),
                    tokens(response.usage(), "completion_tokens")));
            return response;
        } catch (Throwable error) {
            record(new RecordedLlmCall(
                    request, ThrowableSerializer.toMap(error), "error", elapsedMillis(startedAt), 0, 0));
            throw error;
        }
    }

    /** Returns the calls recorded since the previous drain and forgets them. */
    public synchronized List<RecordedLlmCall> drain() {
        List<RecordedLlmCall> snapshot = new ArrayList<>(calls);
        calls.clear();
        return snapshot;
    }

    private static long elapsedMillis(long startedAtNanos) {
        return Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L);
    }

    private static int tokens(Map<String, Integer> usage, String key) {
        if (usage == null) {
            return 0;
        }
        Integer value = usage.get(key);
        return value == null ? 0 : value;
    }

    private synchronized void record(RecordedLlmCall call) {
        calls.add(call);
    }
}