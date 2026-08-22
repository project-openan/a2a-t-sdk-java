package net.openan.a2at.sample.authz_policy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

public final class AuthzScenarioLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AuthzScenarioLoader() {}

    public static List<AuthzScenario> load(String resourcePath) {
        Map<String, Object> root;
        try (InputStream stream = AuthzScenarioLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalStateException("Scenario resource not found: " + resourcePath);
            }
            root = MAPPER.readValue(stream, new TypeReference<Map<String, Object>>() {});
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to parse scenario resource: " + resourcePath + " - " + e.getMessage(), e);
        }

        Object rawScenariosObj = root.get("scenarios");
        if (rawScenariosObj == null) {
            throw new IllegalStateException("Missing 'scenarios' key in: " + resourcePath);
        }
        if (!(rawScenariosObj instanceof List<?> rawList)) {
            throw new IllegalStateException("'scenarios' must be an array in: " + resourcePath);
        }

        List<AuthzScenario> scenarios = rawList.stream()
                .map(item -> {
                    if (!(item instanceof Map<?, ?> raw)) {
                        throw new IllegalStateException("Scenario entry must be an object in: " + resourcePath);
                    }
                    Object labelObj = raw.get("label");
                    if (!(labelObj instanceof String label)) {
                        throw new IllegalStateException("Scenario 'label' must be a string in: " + resourcePath);
                    }
                    Object entryObj = raw.get("entry");
                    if (!(entryObj instanceof String entry)) {
                        throw new IllegalStateException("Scenario 'entry' must be a string in: " + resourcePath);
                    }
                    Object inputObj = raw.get("input");
                    if (!(inputObj instanceof Map<?, ?> input)) {
                        throw new IllegalStateException("Scenario 'input' must be an object in: " + resourcePath);
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> inputMap = (Map<String, Object>) input;
                    Object expectedObj = raw.get("expected");
                    if (!(expectedObj instanceof String expected)) {
                        throw new IllegalStateException("Scenario 'expected' must be a string in: " + resourcePath);
                    }
                    return new AuthzScenario(label, entry, inputMap, expected);
                })
                .toList();

        for (AuthzScenario scenario : scenarios) {
            try {
                AuthzScenario.validate(scenario);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid scenario '" + scenario.label() + "': " + e.getMessage(), e);
            }
        }

        return scenarios;
    }
}
