package net.openan.a2at.sample.private_line_complaint.negotiation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import net.openan.a2at.sample.private_line_complaint.negotiation.shared.NegotiationMetadataReader;
import net.openan.a2at.sample.private_line_complaint.negotiation.shared.NegotiationSampleFlow;
import net.openan.a2at.sdk.core.model.ExtensionUriConstants;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.NegotiationPerformative;
import org.junit.jupiter.api.Test;

class NegotiationMetadataReaderTest {

    @Test
    void readsCanonicalNegotiationMetadata() {
        Map<String, String> metadata = Map.of(
                ExtensionUriConstants.NEGOTIATION_T_EXTENSION_URI,
                "prompt",
                MetadataContent.TEMPLATE_URI_METADATA_KEY,
                NegotiationSampleFlow.PROPOSE_TEMPLATE_URI);

        assertEquals(
                "prompt",
                NegotiationMetadataReader.readPrompt(metadata, NegotiationSampleFlow.PROPOSE_TEMPLATE_URI));
    }

    @Test
    void readsTheNegotiationContextFromMetadata() {
        Map<String, Object> metadata = Map.of(
                ExtensionUriConstants.NEGOTIATION_T_EXTENSION_URI,
                "prompt",
                MetadataContent.TEMPLATE_URI_METADATA_KEY,
                NegotiationSampleFlow.PROPOSE_TEMPLATE_URI,
                MetadataContent.NEGOTIATION_CONTEXT_METADATA_KEY,
                Map.of("id", "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3", "round", 1, "maxRounds", 5, "performative", "PROPOSE"));

        assertEquals(
                new NegotiationContext("3dbc13b5-bd57-4c2b-b503-24e381b6c8d3", 1, 5, NegotiationPerformative.PROPOSE),
                NegotiationMetadataReader.readContext(metadata));
        assertEquals(null, NegotiationMetadataReader.readContext(Map.of("other", "value")));
        assertEquals(null, NegotiationMetadataReader.readContext(null));
        assertThrows(
                IllegalArgumentException.class,
                () -> NegotiationMetadataReader.readContext(
                        Map.of(MetadataContent.NEGOTIATION_CONTEXT_METADATA_KEY, Map.of("id", "x"))));
    }

    @Test
    void readsThePerformativeOfTheNegotiationContext() {
        Map<String, Object> metadata = Map.of(
                MetadataContent.NEGOTIATION_CONTEXT_METADATA_KEY,
                Map.of(
                        "id", "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3",
                        "round", 1,
                        "maxRounds", 5,
                        "performative", "PROPOSE"));

        assertEquals(
                new NegotiationContext("3dbc13b5-bd57-4c2b-b503-24e381b6c8d3", 1, 5, NegotiationPerformative.PROPOSE),
                NegotiationMetadataReader.readContext(metadata));
    }

    @Test
    void rejectsAContextWithoutAPerformative() {
        Map<String, Object> metadata = Map.of(
                MetadataContent.NEGOTIATION_CONTEXT_METADATA_KEY,
                Map.of("id", "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3", "round", 1, "maxRounds", 5));

        assertThrows(
                IllegalArgumentException.class, () -> NegotiationMetadataReader.readContext(metadata));
    }

    @Test
    void rejectsAnUnknownPerformativeOfTheNegotiationContext() {
        Map<String, Object> metadata = Map.of(
                MetadataContent.NEGOTIATION_CONTEXT_METADATA_KEY,
                Map.of("id", "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3", "round", 1, "maxRounds", 5, "performative", "propose"));

        assertThrows(
                IllegalArgumentException.class,
                () -> NegotiationMetadataReader.readContext(metadata));
    }

    @Test
    void rejectsMissingPromptAndWrongTemplateOrExtension() {
        assertThrows(
                IllegalArgumentException.class,
                () -> NegotiationMetadataReader.readPrompt(
                        Map.of(
                                MetadataContent.TEMPLATE_URI_METADATA_KEY,
                                NegotiationSampleFlow.PROPOSE_TEMPLATE_URI),
                        NegotiationSampleFlow.PROPOSE_TEMPLATE_URI));
        assertThrows(
                IllegalArgumentException.class,
                () -> NegotiationMetadataReader.readPrompt(
                        Map.of(
                                ExtensionUriConstants.NEGOTIATION_T_EXTENSION_URI,
                                "prompt",
                                MetadataContent.TEMPLATE_URI_METADATA_KEY,
                                NegotiationSampleFlow.ENDING_TEMPLATE_URI),
                        NegotiationSampleFlow.PROPOSE_TEMPLATE_URI));
        assertThrows(IllegalArgumentException.class, () -> NegotiationMetadataReader.requireExtension("Task-T/v1"));
    }
}
