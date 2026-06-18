package net.openan.a2at.sdk.llm.impl.parsing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.exception.SdkException;
import net.openan.a2at.sdk.llm.internal.parsing.JsonObjectResponseParser;
import org.junit.jupiter.api.Test;

class JsonObjectResponseParserTest {

    private final JsonObjectResponseParser parser = new JsonObjectResponseParser();

    @Test
    void parsesFlatJsonObjectIntoMap() {
        Map<String, Object> payload = parser.parse("{\"matched\":true,\"scenario_code\":\"energy_saving\"}");

        assertEquals(Boolean.TRUE, payload.get("matched"));
        assertEquals("energy_saving", payload.get("scenario_code"));
    }

    @Test
    void parsesStructuredJsonObjectWithArraysAndNestedObjects() {
        Map<String, Object> payload = parser.parse("""
                {
                  "matched": true,
                  "scenario_code": "energy_saving",
                  "metadata": {
                    "confidence": 0.98
                  },
                  "candidates": ["energy_saving", "fault_diagnosis"]
                }
                """);

        assertEquals(Boolean.TRUE, payload.get("matched"));
        assertEquals("energy_saving", payload.get("scenario_code"));
        assertEquals(Map.of("confidence", 0.98d), payload.get("metadata"));
        assertEquals(List.of("energy_saving", "fault_diagnosis"), payload.get("candidates"));
    }

    @Test
    void rejectsNonObjectPayload() {
        assertThrows(SdkException.class, () -> parser.parse("[\"energy_saving\"]"));
    }
}
