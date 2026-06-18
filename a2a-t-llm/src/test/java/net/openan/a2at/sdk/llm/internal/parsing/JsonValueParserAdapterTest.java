package net.openan.a2at.sdk.llm.impl.parsing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import net.openan.a2at.sdk.core.json.JsonValueParser;
import net.openan.a2at.sdk.llm.internal.parsing.JsonObjectResponseParser;
import org.junit.jupiter.api.Test;

class JsonValueParserAdapterTest {

    @Test
    void jsonObjectResponseParserImplementsSharedJsonValueParserContract() {
        JsonValueParser parser = new JsonObjectResponseParser();

        Map<String, Object> payload = parser.parseObject("{\"matched\":true,\"scenario_code\":\"energy_saving\"}");

        assertEquals(Boolean.TRUE, payload.get("matched"));
        assertEquals("energy_saving", payload.get("scenario_code"));
    }
}
