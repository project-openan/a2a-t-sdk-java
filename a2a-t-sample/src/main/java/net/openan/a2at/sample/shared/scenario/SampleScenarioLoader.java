package net.openan.a2at.sample.shared.scenario;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import net.openan.a2at.sample.shared.error.ValueErrorException;

/**
 * Loads static sample scenarios from classpath resources.
 *
 * @since 2026-05
 */
public final class SampleScenarioLoader {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private SampleScenarioLoader() {
    }

    public static Map<String, Object> loadClasspathScenario(String resourcePath) {
        try (InputStream inputStream = SampleScenarioLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new ValueErrorException("Scenario resource not found: " + resourcePath);
            }
            return OBJECT_MAPPER.readValue(inputStream, new TypeReference<>() {
            });
        } catch (IOException exception) {
            throw new ValueErrorException("Unable to load scenario resource: " + resourcePath);
        }
    }
}


