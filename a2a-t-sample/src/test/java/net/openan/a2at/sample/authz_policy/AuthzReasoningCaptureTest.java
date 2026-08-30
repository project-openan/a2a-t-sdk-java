package net.openan.a2at.sample.authz_policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import net.openan.a2at.sample.authz_policy.AuthzReasoningCapture.StageCapture;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AuthzReasoningCaptureTest {

    private static final String SLOT_EXTRACTION_SYSTEM = "/prompt_resources/prompts/slot_extraction/zh-CN/system.md";
    private static final String CONTENT_VALIDATION_SYSTEM =
            "/prompt_resources/prompts/content_validation/zh-CN/system.md";

    @AfterEach
    void clearProperty() {
        System.clearProperty("authz.reasoning");
    }

    private static String loadResource(String path) {
        try (InputStream in = AuthzReasoningCaptureTest.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Resource not found: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read resource: " + path, e);
        }
    }

    private static List<Map<String, String>> messagesWithSystem(String systemContent) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemContent));
        messages.add(Map.of("role", "user", "content", "dummy user content"));
        return messages;
    }

    private static Map<String, Object> contentValidationSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("semantic_verdict", Map.of("type", "boolean"));
        properties.put("errors", Map.of("type", "array"));
        properties.put("params", Map.of("type", "object"));
        schema.put("properties", properties);
        schema.put("required", List.of("semantic_verdict", "errors", "params"));
        schema.put("additionalProperties", false);
        return schema;
    }

    private static final class RecordingClient implements LLMClient {
        List<Map<String, String>> lastMessages;
        Map<String, Object> lastSchema;
        String responseContent;

        @Override
        public LLMResponse structured(
                List<Map<String, String>> messages,
                Map<String, Object> jsonSchema,
                Double temperature,
                Integer maxTokens) {
            this.lastMessages = messages;
            this.lastSchema = jsonSchema;
            return new LLMResponse(responseContent, "test-model", Map.of(), Map.of());
        }
    }

    @Test
    void should_identifyStage_AndAppendAppendix_WhenSlotExtractionMatches() {
        RecordingClient real = new RecordingClient();
        AuthzReasoningCapture capture = new AuthzReasoningCapture();
        LLMClient wrapped = capture.wrap(real);
        String system = loadResource(SLOT_EXTRACTION_SYSTEM);
        List<Map<String, String>> messages = messagesWithSystem(system);
        Map<String, Object> schema = Map.of("type", "object");

        wrapped.structured(messages, schema, null, null);

        assertTrue(real.lastMessages.get(0).get("content").contains("[reasoning_appendix]"));
        assertSame(schema, real.lastSchema);
        // original list unmutated
        assertTrue(messages.get(0).get("content").equals(system));
    }

    @Test
    void should_extendSchema_WhenContentValidationMatches() {
        RecordingClient real = new RecordingClient();
        AuthzReasoningCapture capture = new AuthzReasoningCapture();
        LLMClient wrapped = capture.wrap(real);
        String system = loadResource(CONTENT_VALIDATION_SYSTEM);
        Map<String, Object> schema = contentValidationSchema();

        wrapped.structured(messagesWithSystem(system), schema, null, null);

        String firstContent = real.lastMessages.get(0).get("content");
        assertTrue(firstContent.contains("[reasoning_appendix]"));
        Map<String, Object> received = real.lastSchema;
        assertTrue(received != schema, "schema should be a shallow copy, not the same instance");
        assertEquals("object", received.get("type"));
        assertEquals(List.of("semantic_verdict", "errors", "params"), received.get("required"));
        assertEquals(Boolean.FALSE, received.get("additionalProperties"));
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) received.get("properties");
        assertNotNull(props);
        assertEquals(Map.of("type", "string"), props.get("reasoning"));
        // original schema untouched
        @SuppressWarnings("unchecked")
        Map<String, Object> originalProps = (Map<String, Object>) schema.get("properties");
        assertFalse(originalProps.containsKey("reasoning"));
        assertEquals(Map.of("type", "boolean"), originalProps.get("semantic_verdict"));
    }

    @Test
    void should_notModifySchema_WhenSlotExtractionMatches() {
        RecordingClient real = new RecordingClient();
        AuthzReasoningCapture capture = new AuthzReasoningCapture();
        LLMClient wrapped = capture.wrap(real);
        String system = loadResource(SLOT_EXTRACTION_SYSTEM);
        Map<String, Object> schema = Map.of("type", "object", "required", List.of("slots"));

        wrapped.structured(messagesWithSystem(system), schema, null, null);

        assertSame(schema, real.lastSchema);
    }

    @Test
    void should_passThrough_WhenNoStageMatches() {
        RecordingClient real = new RecordingClient();
        real.responseContent = "{\"slots\":{}}";
        AuthzReasoningCapture capture = new AuthzReasoningCapture();
        LLMClient wrapped = capture.wrap(real);
        List<Map<String, String>> messages = messagesWithSystem("unrelated system prompt");
        Map<String, Object> schema = contentValidationSchema();

        wrapped.structured(messages, schema, null, null);

        assertSame(messages, real.lastMessages);
        assertSame(schema, real.lastSchema);
    }

    @Test
    void should_captureBothStages_ByLabel() {
        RecordingClient real = new RecordingClient();
        AuthzReasoningCapture capture = new AuthzReasoningCapture();
        LLMClient wrapped = capture.wrap(real);
        String slotSystem = loadResource(SLOT_EXTRACTION_SYSTEM);
        String contentSystem = loadResource(CONTENT_VALIDATION_SYSTEM);

        capture.beginScenario("x");
        real.responseContent = "{\"slots\":{},\"reasoning\":\"client-reason\"}";
        wrapped.structured(messagesWithSystem(slotSystem), Map.of(), null, null);
        real.responseContent =
                "{\"semantic_verdict\":true,\"errors\":[],\"params\":{},\"reasoning\":\"server-reason\"}";
        wrapped.structured(messagesWithSystem(contentSystem), contentValidationSchema(), null, null);
        capture.endScenario();

        Optional<StageCapture> captured = capture.capture("x");
        assertTrue(captured.isPresent());
        StageCapture s = captured.get();
        assertEquals("client-reason", s.clientReasoning());
        assertEquals("server-reason", s.serverReasoning());
        assertEquals("{\"slots\":{},\"reasoning\":\"client-reason\"}", s.clientRaw());
        assertEquals(
                "{\"semantic_verdict\":true,\"errors\":[],\"params\":{},\"reasoning\":\"server-reason\"}",
                s.serverRaw());
    }

    @Test
    void should_returnNullReasoning_WhenFieldMissingOrNonString() {
        RecordingClient real = new RecordingClient();
        AuthzReasoningCapture capture = new AuthzReasoningCapture();
        LLMClient wrapped = capture.wrap(real);
        String slotSystem = loadResource(SLOT_EXTRACTION_SYSTEM);

        capture.beginScenario("missing");
        real.responseContent = "{\"slots\":{}}";
        wrapped.structured(messagesWithSystem(slotSystem), Map.of(), null, null);
        capture.endScenario();

        StageCapture s = capture.capture("missing").orElseThrow();
        assertNull(s.clientReasoning());
        assertEquals("{\"slots\":{}}", s.clientRaw());

        capture.beginScenario("nonstring");
        real.responseContent = "{\"slots\":{},\"reasoning\":123}";
        wrapped.structured(messagesWithSystem(slotSystem), Map.of(), null, null);
        capture.endScenario();

        StageCapture ns = capture.capture("nonstring").orElseThrow();
        assertNull(ns.clientReasoning());
    }

    @Test
    void should_isolateCaptures_BetweenConcurrentThreads() throws Exception {
        AuthzReasoningCapture capture = new AuthzReasoningCapture();
        ThreadLocal<String> perThreadResponse = new ThreadLocal<>();
        LLMClient fake = (messages, jsonSchema, temperature, maxTokens) ->
                new LLMResponse(perThreadResponse.get(), "test-model", Map.of(), Map.of());
        LLMClient wrapped = capture.wrap(fake);
        String slotSystem = loadResource(SLOT_EXTRACTION_SYSTEM);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);

        Runnable taskA = () -> {
            perThreadResponse.set("{\"slots\":{},\"reasoning\":\"A\"}");
            capture.beginScenario("label-a");
            ready.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            wrapped.structured(messagesWithSystem(slotSystem), Map.of(), null, null);
            capture.endScenario();
        };
        Runnable taskB = () -> {
            perThreadResponse.set("{\"slots\":{},\"reasoning\":\"B\"}");
            capture.beginScenario("label-b");
            ready.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            wrapped.structured(messagesWithSystem(slotSystem), Map.of(), null, null);
            capture.endScenario();
        };

        Thread a = new Thread(taskA);
        Thread b = new Thread(taskB);
        a.start();
        b.start();
        ready.await();
        release.countDown();
        a.join();
        b.join();

        StageCapture ca = capture.capture("label-a").orElseThrow();
        StageCapture cb = capture.capture("label-b").orElseThrow();
        assertEquals("A", ca.clientReasoning());
        assertEquals("B", cb.clientReasoning());
        assertEquals("{\"slots\":{},\"reasoning\":\"A\"}", ca.clientRaw());
        assertEquals("{\"slots\":{},\"reasoning\":\"B\"}", cb.clientRaw());
    }

    @Test
    void should_resolveReasoningFlag_DefaultFalse() {
        System.clearProperty("authz.reasoning");
        assertFalse(AuthzReasoningCapture.resolveReasoningFlag());
    }

    @Test
    void should_resolveReasoningFlag_True() {
        System.setProperty("authz.reasoning", "true");
        assertTrue(AuthzReasoningCapture.resolveReasoningFlag());
        System.setProperty("authz.reasoning", "TRUE");
        assertTrue(AuthzReasoningCapture.resolveReasoningFlag());
        System.setProperty("authz.reasoning", "false");
        assertFalse(AuthzReasoningCapture.resolveReasoningFlag());
    }

    @Test
    void should_resolveReasoningFlag_ThrowOnInvalid() {
        for (String value : List.of("yes", "1", "on")) {
            System.setProperty("authz.reasoning", value);
            IllegalArgumentException ex =
                    assertThrows(IllegalArgumentException.class, AuthzReasoningCapture::resolveReasoningFlag);
            assertTrue(ex.getMessage().contains("authz.reasoning"));
        }
    }
}
