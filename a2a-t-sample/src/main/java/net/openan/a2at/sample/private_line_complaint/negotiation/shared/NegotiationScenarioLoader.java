package net.openan.a2at.sample.private_line_complaint.negotiation.shared;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/** Loads the bundled private-line complaint negotiation input. */
public final class NegotiationScenarioLoader {

    public static final String RESOURCE_PATH = "sample/private-line-complaint-negotiation/scenario.json";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private NegotiationScenarioLoader() {}

    public static NegotiationScenario load() {
        try (InputStream input =
                NegotiationScenarioLoader.class.getClassLoader().getResourceAsStream(RESOURCE_PATH)) {
            if (input == null) {
                throw new IllegalStateException("Negotiation scenario resource not found: " + RESOURCE_PATH);
            }
            Map<String, String> values = OBJECT_MAPPER.readValue(input, new TypeReference<>() {});
            return new NegotiationScenario(
                    require(values, "scenario"),
                    require(values, "propose_text"),
                    require(values, "accept_text"),
                    require(values, "reject_text"));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read negotiation scenario", exception);
        }
    }

    private static String require(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Negotiation scenario field must not be blank: " + key);
        }
        return value;
    }
}
