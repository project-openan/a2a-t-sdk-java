package net.openan.a2at.sdk.negotiation.generation;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.openan.a2at.sdk.core.model.NegotiationPerformative;
import net.openan.a2at.sdk.negotiation.content.NegotiationType;
import org.jspecify.annotations.Nullable;

/**
 * Builds the JSON Schemas of the negotiation LLM steps.
 *
 * <p>Extraction schemas are keyed by the snake_case property names of the extracted content, one schema per
 * (negotiation type, phase) pair with accept and reject sharing the terminal schema. The semantic validation schema
 * merges a caller-provided parameter schema into the fixed four-key output contract of the semantic validation step.
 *
 * @since 2026-08
 */
final class NegotiationJsonSchemaBuilder {

    private static final List<String> CONCLUSION_ENUM = List.of("Accept", "Reject");

    private static final List<String> ACTION_ENUM =
            List.of("REQUEST_FEASIBILITY_EVALUATION", "PROPOSE_ALTERNATIVE_ON_FAILURE");

    /**
     * Builds the content extraction schema of one (negotiation type, phase) pair.
     *
     * @param type negotiation type whose content is extracted; null only for the type-independent abort phase
     * @param phase API-level phase of the extraction; accept and reject share the terminal schema
     * @return JSON Schema describing the snake_case extraction output of the pair
     * @throws NullPointerException if the phase is null, or the type is null on a typed phase
     */
    public Map<String, Object> buildExtractionSchema(
            @Nullable NegotiationType type, NegotiationPerformative phase) {
        Objects.requireNonNull(phase, "Negotiation phase must not be null.");
        if (phase == NegotiationPerformative.ABORT) {
            if (type != null) {
                throw new IllegalArgumentException(
                        "The ABORT phase is type-independent and must not carry a type but carried " + type + ".");
            }
            return abortSchema();
        }
        Objects.requireNonNull(type, "Negotiation type must not be null for the " + phase + " phase.");
        return switch (type) {
            case INFORMATION -> phase == NegotiationPerformative.PROPOSE
                    ? informationProposeSchema()
                    : informationEndingSchema();
            case TARGET -> phase == NegotiationPerformative.PROPOSE ? targetProposeSchema() : targetEndingSchema();
            case FEASIBILITY -> phase == NegotiationPerformative.PROPOSE
                    ? feasibilityProposeSchema()
                    : feasibilityEndingSchema();
        };
    }

    private static Map<String, Object> abortSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("termination_reason", Map.of("type", "string"));
        return objectSchema(properties, List.of("termination_reason"));
    }

    private static Map<String, Object> informationProposeSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("items", nonEmptyItemArraySchema());
        properties.put("relationship", nullableStringSchema());
        return objectSchema(properties, List.of("items"));
    }

    private static Map<String, Object> informationEndingSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("conclusion", conclusionSchema());
        properties.put("items", nonEmptyItemArraySchema());
        return objectSchema(properties, List.of("conclusion", "items"));
    }

    private static Map<String, Object> targetProposeSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("target_negotiation_description", Map.of("type", "string"));
        properties.put("intent_understanding", nullableItemArraySchema());
        properties.put("alignment_and_clarification", nullableItemArraySchema());
        properties.put("request_for_clarification", nullableItemArraySchema());
        properties.put("target_confirm_request", nullableStringSchema());
        return objectSchema(properties, List.of("target_negotiation_description"));
    }

    private static Map<String, Object> targetEndingSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("conclusion", conclusionSchema());
        properties.put("confirmed_intent", nullableStringSchema());
        properties.put("failure_reason", nullableStringSchema());
        return objectSchema(properties, List.of("conclusion"));
    }

    private static Map<String, Object> feasibilityProposeSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("feasibility_negotiation_description", Map.of("type", "string"));
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("type", "string");
        action.put("enum", ACTION_ENUM);
        properties.put("action", action);
        properties.put("contents_to_evaluate", nullableItemArraySchema());
        properties.put("infeasibility_details_and_proposal", nullableItemArraySchema());
        properties.put("feasibility_confirm_request", nullableStringSchema());
        return objectSchema(properties, List.of("feasibility_negotiation_description", "action"));
    }

    private static Map<String, Object> feasibilityEndingSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("conclusion", conclusionSchema());
        properties.put("feasibility_summary", Map.of("type", "string"));
        return objectSchema(properties, List.of("conclusion", "feasibility_summary"));
    }

    private static Map<String, Object> itemArraySchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "array");
        schema.put("items", itemSchema());
        return schema;
    }

    private static Map<String, Object> nonEmptyItemArraySchema() {
        Map<String, Object> schema = new LinkedHashMap<>(itemArraySchema());
        schema.put("minItems", 1);
        return schema;
    }

    private static Map<String, Object> nullableItemArraySchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", List.of("array", "null"));
        schema.put("items", itemSchema());
        return schema;
    }

    private static Map<String, Object> itemSchema() {
        Map<String, Object> itemProperties = new LinkedHashMap<>();
        itemProperties.put("name", Map.of("type", "string"));
        itemProperties.put("value", nullableStringSchema());
        return objectSchema(itemProperties, List.of("name", "value"));
    }

    private static Map<String, Object> conclusionSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "string");
        schema.put("enum", CONCLUSION_ENUM);
        return schema;
    }

    private static Map<String, Object> nullableStringSchema() {
        return Map.of("type", List.of("string", "null"));
    }

    private static Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        return schema;
    }
}
