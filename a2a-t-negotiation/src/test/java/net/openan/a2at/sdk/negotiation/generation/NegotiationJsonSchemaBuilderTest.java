package net.openan.a2at.sdk.negotiation.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.model.NegotiationPerformative;
import net.openan.a2at.sdk.negotiation.content.NegotiationType;
import org.junit.jupiter.api.Test;

class NegotiationJsonSchemaBuilderTest {

    private final NegotiationJsonSchemaBuilder builder = new NegotiationJsonSchemaBuilder();

    @Test
    void informationProposeSchemaUsesSnakeCasePropertiesAndNullableRelationship() {
        Map<String, Object> schema =
                builder.buildExtractionSchema(NegotiationType.INFORMATION, NegotiationPerformative.PROPOSE);

        assertEquals("object", schema.get("type"));
        Map<?, ?> properties = (Map<?, ?>) schema.get("properties");
        assertEquals(List.of("items", "relationship"), List.copyOf(properties.keySet()));
        assertEquals(List.of("items"), schema.get("required"));

        Map<?, ?> relationship = (Map<?, ?>) properties.get("relationship");
        assertEquals(List.of("string", "null"), relationship.get("type"));
        assertFalse(relationship.containsKey("enum"));

        Map<?, ?> items = (Map<?, ?>) properties.get("items");
        assertEquals("array", items.get("type"));
        Map<?, ?> item = (Map<?, ?>) items.get("items");
        assertEquals(List.of("name", "value"), List.copyOf(((Map<?, ?>) item.get("properties")).keySet()));
        assertEquals(List.of("name", "value"), item.get("required"));
    }

    @Test
    void informationEndingSchemaRequiresConclusionAndItems() {
        Map<String, Object> schema =
                builder.buildExtractionSchema(NegotiationType.INFORMATION, NegotiationPerformative.ACCEPT);

        Map<?, ?> properties = (Map<?, ?>) schema.get("properties");
        assertEquals(List.of("conclusion", "items"), List.copyOf(properties.keySet()));
        assertEquals(List.of("conclusion", "items"), schema.get("required"));
        assertEquals(List.of("Accept", "Reject"), ((Map<?, ?>) properties.get("conclusion")).get("enum"));
        assertEquals(1, ((Map<?, ?>) properties.get("items")).get("minItems"));
    }

    @Test
    void acceptAndRejectShareTheTerminalSchema() {
        assertEquals(
                builder.buildExtractionSchema(NegotiationType.TARGET, NegotiationPerformative.ACCEPT),
                builder.buildExtractionSchema(NegotiationType.TARGET, NegotiationPerformative.REJECT));
    }

    @Test
    void targetProposeSchemaUsesSnakeCaseProperties() {
        Map<String, Object> schema = builder.buildExtractionSchema(NegotiationType.TARGET, NegotiationPerformative.PROPOSE);

        Map<?, ?> properties = (Map<?, ?>) schema.get("properties");
        assertEquals(
                List.of(
                        "target_negotiation_description",
                        "intent_understanding",
                        "alignment_and_clarification",
                        "request_for_clarification",
                        "target_confirm_request"),
                List.copyOf(properties.keySet()));
        assertEquals(List.of("target_negotiation_description"), schema.get("required"));
        assertEquals(List.of("array", "null"), ((Map<?, ?>) properties.get("intent_understanding")).get("type"));
        assertEquals(List.of("string", "null"), ((Map<?, ?>) properties.get("target_confirm_request")).get("type"));
    }

    @Test
    void targetEndingSchemaCarriesNullableResultFields() {
        Map<String, Object> schema = builder.buildExtractionSchema(NegotiationType.TARGET, NegotiationPerformative.REJECT);

        Map<?, ?> properties = (Map<?, ?>) schema.get("properties");
        assertEquals(List.of("conclusion", "confirmed_intent", "failure_reason"), List.copyOf(properties.keySet()));
        assertEquals(List.of("conclusion"), schema.get("required"));
        assertEquals(List.of("string", "null"), ((Map<?, ?>) properties.get("confirmed_intent")).get("type"));
        assertEquals(List.of("string", "null"), ((Map<?, ?>) properties.get("failure_reason")).get("type"));
    }

    @Test
    void feasibilityProposeSchemaRequiresTheActionEnum() {
        Map<String, Object> schema =
                builder.buildExtractionSchema(NegotiationType.FEASIBILITY, NegotiationPerformative.PROPOSE);

        Map<?, ?> properties = (Map<?, ?>) schema.get("properties");
        assertEquals(
                List.of(
                        "feasibility_negotiation_description",
                        "action",
                        "contents_to_evaluate",
                        "infeasibility_details_and_proposal",
                        "feasibility_confirm_request"),
                List.copyOf(properties.keySet()));
        assertEquals(List.of("feasibility_negotiation_description", "action"), schema.get("required"));
        Map<?, ?> action = (Map<?, ?>) properties.get("action");
        assertEquals(List.of("REQUEST_FEASIBILITY_EVALUATION", "PROPOSE_ALTERNATIVE_ON_FAILURE"), action.get("enum"));
        assertEquals(List.of("string", "null"), ((Map<?, ?>) properties.get("feasibility_confirm_request")).get("type"));
    }

    @Test
    void feasibilityEndingSchemaRequiresTheSummary() {
        Map<String, Object> schema =
                builder.buildExtractionSchema(NegotiationType.FEASIBILITY, NegotiationPerformative.ACCEPT);

        Map<?, ?> properties = (Map<?, ?>) schema.get("properties");
        assertEquals(List.of("conclusion", "feasibility_summary"), List.copyOf(properties.keySet()));
        assertEquals(List.of("conclusion", "feasibility_summary"), schema.get("required"));
    }

    @Test
    void rejectsNullTypeAndPhase() {
        assertEquals(
                "Negotiation type must not be null for the PROPOSE phase.",
                assertThrows(
                                NullPointerException.class,
                                () -> builder.buildExtractionSchema(null, NegotiationPerformative.PROPOSE))
                        .getMessage());
        assertEquals(
                "Negotiation phase must not be null.",
                assertThrows(
                                NullPointerException.class,
                                () -> builder.buildExtractionSchema(NegotiationType.INFORMATION, null))
                        .getMessage());
    }

}
