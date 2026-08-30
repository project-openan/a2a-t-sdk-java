package net.openan.a2at.sample.service_recovery.shared.mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import net.openan.a2at.sample.service_recovery.shared.error.ValueErrorException;
import org.junit.jupiter.api.Test;

class SampleMockLLMClientTest {

    @Test
    void responseResourceReturnsSlotExtractionWhenSchemaHasSlotNames() {
        Map<String, Object> schema = Map.of("slotNames", List.of("topic"));

        String resource = SampleMockLLMClient.responseResource(schema);

        assertEquals("slot_extraction.json", resource);
    }

    @Test
    void responseResourceReturnsContentValidationWhenSchemaHasSemanticVerdict() {
        Map<String, Object> schema = Map.of("required", List.of("semantic_verdict"));

        String resource = SampleMockLLMClient.responseResource(schema);

        assertEquals("content_validation.json", resource);
    }

    @Test
    void responseResourceRaisesForUnknownSchema() {
        Map<String, Object> schema = Map.of("type", "object");

        assertThrows(ValueErrorException.class, () -> SampleMockLLMClient.responseResource(schema));
    }

    @Test
    void responseResourceRaisesForNullSchema() {
        assertThrows(ValueErrorException.class, () -> SampleMockLLMClient.responseResource(null));
    }
}
