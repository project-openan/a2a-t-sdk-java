package net.openan.a2at.sample.negotiation.eval;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sample.subscribe_incident.shared.error.ValueErrorException;
import net.openan.a2at.sample.subscribe_incident.shared.mock.SampleLoggingLLMClient;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMClientConfig;
import net.openan.a2at.sdk.llm.LLMClientFactory;
import net.openan.a2at.sdk.llm.LLMResponse;

/**
 * LLM client wrapper for the evaluator: records every structured call — the full request (messages, JSON schema,
 * temperature, max tokens) and the response (content, model, usage) — into an in-memory buffer so the report can attach
 * the exact prompts fed to the model to each step.
 *
 * <p>When a case fails, the recorded prompts show precisely which system prompt, slot description or schema entry the
 * model saw, which is what drives reverse tuning of the prompt resources: a rejected or mis-extracted case can be
 * traced to the exact prompt text instead of being guessed at.
 *
 * <p>Installed into {@link LLMClientFactory} by replacing the {@code openai} entry — the same reflective mechanism
 * {@code SampleMockLlmInstaller.installLlmLogger} uses. Delegates to {@link SampleLoggingLLMClient} so the live console
 * logging keeps working unchanged.
 *
 * @since 2026-08
 */
public final class EvalLlmCaptureClient implements LLMClient {

    private static final List<Map<String, Object>> buffer = new ArrayList<>();

    private final LLMClient delegate;

    /**
     * Creates a capturing LLM client. The supplied config is passed through to the logging delegate.
     *
     * @param config resolved LLM provider config
     */
    public EvalLlmCaptureClient(LLMClientConfig config) {
        this.delegate = new SampleLoggingLLMClient(config);
    }

    /**
     * Installs this client as the {@code openai} provider and keeps the console logging configured.
     *
     * <p>Must be called once at evaluator startup, before any {@code A2ATClient} or {@code A2ATServer} is constructed,
     * so that every structured call of the run is captured.
     */
    public static synchronized void install() {
        try {
            Field clientsField = LLMClientFactory.class.getDeclaredField("CLIENTS");
            clientsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Class<? extends LLMClient>> clients =
                    (Map<String, Class<? extends LLMClient>>) clientsField.get(null);
            clients.put("openai", EvalLlmCaptureClient.class);
            SampleLoggingLLMClient.configure(false, "eval", System.out::println);
        } catch (ReflectiveOperationException exception) {
            throw new ValueErrorException("Failed to install the capturing LLM client", exception);
        }
    }

    /** Clears the capture buffer; called at the start of each case so calls never leak across cases. */
    public static void reset() {
        synchronized (buffer) {
            buffer.clear();
        }
    }

    /** Drains the calls recorded since the last reset or drain; empty when no LLM call happened. */
    public static List<Map<String, Object>> drain() {
        synchronized (buffer) {
            List<Map<String, Object>> calls = new ArrayList<>(buffer);
            buffer.clear();
            return calls;
        }
    }

    @Override
    public LLMResponse structured(
            List<Map<String, String>> messages, Map<String, Object> jsonSchema, Double temperature, Integer maxTokens) {
        long nanos = System.nanoTime();
        try {
            LLMResponse response = delegate.structured(messages, jsonSchema, temperature, maxTokens);
            record(messages, jsonSchema, temperature, maxTokens, response, null, nanos);
            return response;
        } catch (RuntimeException error) {
            // the request is recorded even when the call fails (rate limit, empty content, timeout) so the
            // failing prompt is still inspectable from the report
            record(messages, jsonSchema, temperature, maxTokens, null, error, nanos);
            throw error;
        }
    }

    private static void record(
            List<Map<String, String>> messages,
            Map<String, Object> jsonSchema,
            Double temperature,
            Integer maxTokens,
            LLMResponse response,
            RuntimeException error,
            long nanos) {
        Map<String, Object> call = new LinkedHashMap<>();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("messages", messages);
        request.put("json_schema", jsonSchema);
        request.put("temperature", temperature);
        request.put("max_tokens", maxTokens);
        call.put("request", request);
        if (response != null) {
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("content", response.content());
            resp.put("model", response.model());
            resp.put("usage", response.usage());
            call.put("response", resp);
        }
        if (error != null) {
            Map<String, Object> failure = new LinkedHashMap<>();
            failure.put("type", error.getClass().getSimpleName());
            failure.put("message", String.valueOf(error.getMessage()));
            call.put("error", failure);
        }
        call.put("duration_ms", (System.nanoTime() - nanos) / 1_000_000);
        synchronized (buffer) {
            buffer.add(call);
        }
    }
}
