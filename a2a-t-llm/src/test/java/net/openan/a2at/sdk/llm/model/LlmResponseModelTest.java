package net.openan.a2at.sdk.llm.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import net.openan.a2at.sdk.llm.model.LLMResponse;
import net.openan.a2at.sdk.llm.model.LlmUsage;
import org.junit.jupiter.api.Test;

class LlmResponseModelTest {

    @Test
    void llmResponseCarriesStructuredContentModelUsageAndMetadata() {
        LlmUsage usage = new LlmUsage(11, 7, 18);
        LLMResponse response = new LLMResponse("{\"status\":\"ok\"}", "gpt-test", usage, Map.of("provider", "openai"));

        assertEquals("{\"status\":\"ok\"}", response.content());
        assertEquals("gpt-test", response.model());
        assertEquals(11, response.usage().promptTokens());
        assertEquals(7, response.usage().completionTokens());
        assertEquals(18, response.usage().totalTokens());
        assertEquals("openai", response.metadata().get("provider"));
        assertTrue(response.metadata().containsKey("provider"));
    }
}
