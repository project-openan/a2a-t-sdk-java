package net.openan.a2at.sdk.core.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import net.openan.a2at.sdk.core.exception.SdkException;
import org.junit.jupiter.api.Test;

class JacksonJsonValueParserTest {

    @Test
    void parseObjectReturnsJsonObjectFields() {
        JsonValueParser parser = new JacksonJsonValueParser();

        Map<String, Object> parsed = parser.parseObject("{\"matched\":true,\"scenario_code\":\"energy_saving\"}");

        assertEquals(true, parsed.get("matched"));
        assertEquals("energy_saving", parsed.get("scenario_code"));
    }

    @Test
    void parseObjectRejectsNonObjectPayload() {
        JsonValueParser parser = new JacksonJsonValueParser();

        SdkException error = assertThrows(SdkException.class, () -> parser.parseObject("[\"not-object\"]"));

        assertEquals("Structured JSON payload must be a JSON object.", error.getMessage());
    }
}
