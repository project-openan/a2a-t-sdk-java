package net.openan.a2at.sdk.core.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import net.openan.a2at.sdk.core.exception.A2ATError;
import org.junit.jupiter.api.Test;

class JacksonJsonValueParserTest {

    @Test
    void parseObjectReturnsJsonObjectFields() {
        JsonValueParser parser = new JacksonJsonValueParser();

        Map<String, Object> parsed = parser.parseObject("{\"matched\":true,\"scenario_code\":\"ran-energy-saving\"}");

        assertEquals(true, parsed.get("matched"));
        assertEquals("ran-energy-saving", parsed.get("scenario_code"));
    }

    @Test
    void parseObjectRejectsNonObjectPayload() {
        JsonValueParser parser = new JacksonJsonValueParser();

        A2ATError error = assertThrows(A2ATError.class, () -> parser.parseObject("[\"not-object\"]"));

        assertEquals("Structured JSON payload must be a JSON object.", error.getMessage());
    }
}
