package net.openan.a2at.sample.negotiation.shared;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Business-neutral, phase-specific caller schemas for information-negotiation validation.
 *
 * <p>The three validation APIs intentionally use independent parameter contracts. The contracts describe the shape of
 * the extracted result while the information-item names and values come from the negotiation message.
 *
 * <p>Shared by every information-negotiation sample path — fromText (natural-language generation) and fromData
 * (typed-record generation) — because the validate*AndDataFilling interfaces and the rendered wire format are identical
 * for both.
 */
public final class InformationNegotiationSchemas {

    private static final Map<String, Object> PROPOSE = createProposeSchema();

    private static final Map<String, Object> ACCEPT = createAcceptSchema();

    private static final Map<String, Object> REJECT = createRejectSchema();

    private InformationNegotiationSchemas() {}

    public static Map<String, Object> propose() {
        return PROPOSE;
    }

    public static Map<String, Object> accept() {
        return ACCEPT;
    }

    public static Map<String, Object> reject() {
        return REJECT;
    }

    private static Map<String, Object> createProposeSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(
                "items",
                itemArraySchema(
                        itemSchema(
                                "requirement",
                                "Meaning, format, constraint, or example requested for the information item"),
                        "Requested information items and their requirements"));
        properties.put(
                "relationship",
                nullableStringSchema("Relationship among requested information items, or null when unspecified"));
        return objectSchema(properties, List.of("items"));
    }

    private static Map<String, Object> createAcceptSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(
                "items",
                itemArraySchema(
                        itemSchema("value", "Value supplied for the information item"),
                        "Information items and values supplied by the accepting party"));
        return objectSchema(properties, List.of("items"));
    }

    private static Map<String, Object> createRejectSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(
                "items",
                itemArraySchema(
                        itemSchema("reason", "Reason why the information item cannot be supplied"),
                        "Information items that cannot be supplied and their reasons"));
        return objectSchema(properties, List.of("items"));
    }

    private static Map<String, Object> itemArraySchema(Map<String, Object> itemSchema, String description) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "array");
        schema.put("items", itemSchema);
        schema.put("minItems", 1);
        schema.put("description", description);
        return Collections.unmodifiableMap(schema);
    }

    private static Map<String, Object> itemSchema(String valueName, String valueDescription) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("name", stringSchema("Name of the information item"));
        properties.put(valueName, nullableStringSchema(valueDescription));
        return objectSchema(properties, List.of("name", valueName));
    }

    private static Map<String, Object> stringSchema(String description) {
        return Map.of("type", "string", "description", description);
    }

    private static Map<String, Object> nullableStringSchema(String description) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", List.of("string", "null"));
        schema.put("description", description);
        return Collections.unmodifiableMap(schema);
    }

    private static Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("properties", Collections.unmodifiableMap(properties));
        schema.put("required", required);
        return Collections.unmodifiableMap(schema);
    }
}
