package net.openan.a2at.sample.authz_policy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sample.authz_policy.AuthzScenario.AuthzExpected;
import net.openan.a2at.sample.authz_policy.AuthzScenario.ClientExpected;
import net.openan.a2at.sample.authz_policy.AuthzScenario.ServerExpected;

/**
 * Loads and validates demo scenarios from a JSON resource file.
 *
 * <p>The JSON file must contain a top-level {@code scenarios} array. Each scenario entry carries a staged
 * {@code expected} object: {@code expected.client} with {@code outcome} (generation failure) or {@code promptText}
 * (successful generation), and {@code expected.server} with {@code outcome}, {@code slot_errors} and {@code params}.
 *
 * @since 2026-08
 */
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

        List<AuthzScenario> scenarios =
                rawList.stream().map(item -> parseScenario(item, resourcePath)).toList();

        for (AuthzScenario scenario : scenarios) {
            try {
                AuthzScenario.validate(scenario);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid scenario '" + scenario.label() + "': " + e.getMessage(), e);
            }
        }

        return scenarios;
    }

    private static AuthzScenario parseScenario(Object item, String resourcePath) {
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
        if (!(expectedObj instanceof Map<?, ?> expectedRaw)) {
            throw new IllegalStateException("Scenario 'expected' must be an object in: " + resourcePath);
        }
        Object clientObj = expectedRaw.get("client");
        if (!(clientObj instanceof Map<?, ?> clientRaw)) {
            throw new IllegalStateException("Scenario 'expected.client' must be an object in: " + resourcePath);
        }
        ClientExpected client = new ClientExpected(
                asNullableString(clientRaw.get("outcome")),
                asNullableString(clientRaw.get("promptText")),
                parseSlotErrors(clientRaw.get("slot_errors")));
        ServerExpected server = null;
        Object serverObj = expectedRaw.get("server");
        if (serverObj instanceof Map<?, ?> serverRaw) {
            @SuppressWarnings("unchecked")
            Map<String, Object> params =
                    serverRaw.get("params") instanceof Map<?, ?> paramsRaw ? (Map<String, Object>) paramsRaw : null;
            server = new ServerExpected(
                    asNullableString(serverRaw.get("outcome")), parseSlotErrors(serverRaw.get("slot_errors")), params);
        }
        Object validateSchemaObj = raw.get("validate_schema");
        Map<String, Object> validateSchema = null;
        if (validateSchemaObj instanceof Map<?, ?> validateSchemaRaw) {
            if (validateSchemaRaw.isEmpty()) {
                throw new IllegalStateException(
                        "Scenario 'validate_schema' must be a non-empty object in: " + resourcePath);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> validateSchemaMap = (Map<String, Object>) validateSchemaRaw;
            validateSchema = validateSchemaMap;
        }
        AuthzExpected expected = new AuthzExpected(client, server);
        return new AuthzScenario(label, entry, inputMap, expected, validateSchema);
    }

    private static String asNullableString(Object raw) {
        return raw instanceof String text ? text : null;
    }

    private static List<AuthzScenario.SlotErrorExpectation> parseSlotErrors(Object raw) {
        if (!(raw instanceof List<?> rawList)) {
            return null;
        }
        List<AuthzScenario.SlotErrorExpectation> result = new ArrayList<>();
        for (Object item : rawList) {
            if (!(item instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Object slotNameObj = rawMap.get("slot_name");
            Object codeObj = rawMap.get("code");
            if (!(slotNameObj instanceof String slotName) || !(codeObj instanceof String code)) {
                continue;
            }
            result.add(new AuthzScenario.SlotErrorExpectation(slotName, code));
        }
        return result;
    }
}
