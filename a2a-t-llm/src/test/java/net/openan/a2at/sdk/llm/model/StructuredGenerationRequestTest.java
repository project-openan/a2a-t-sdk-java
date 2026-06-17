package net.openan.a2at.sdk.llm.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.model.PromptMessage;
import net.openan.a2at.sdk.llm.model.StructuredGenerationRequest;
import org.junit.jupiter.api.Test;

class StructuredGenerationRequestTest {

    @Test
    void structuredGenerationRequestRetainsMessagesSchemaAndOptionalOverrides() {
        StructuredGenerationRequest request = new StructuredGenerationRequest(
                List.of(new PromptMessage("user", "extract slots")),
                Map.of("type", "object"),
                "deepseek",
                "deepseek-chat",
                0.2d,
                256,
                30.0d);

        assertEquals(1, request.messages().size());
        assertEquals("user", request.messages().get(0).role());
        assertEquals("extract slots", request.messages().get(0).content());
        assertEquals("object", request.jsonSchema().get("type"));
        assertEquals("deepseek", request.provider());
        assertEquals("deepseek-chat", request.model());
        assertEquals(0.2d, request.temperature());
        assertEquals(256, request.maxTokens());
        assertEquals(30.0d, request.timeoutSeconds());
    }
}
