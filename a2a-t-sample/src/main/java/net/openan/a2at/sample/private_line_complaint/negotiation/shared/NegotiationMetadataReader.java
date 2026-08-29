package net.openan.a2at.sample.private_line_complaint.negotiation.shared;

import java.util.Map;
import net.openan.a2at.sdk.core.model.ExtensionUriConstants;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.NegotiationPerformative;

/** Reads and validates Negotiation-T metadata received over A2A. */
public final class NegotiationMetadataReader {

    private NegotiationMetadataReader() {
    }

    public static String readPrompt(Map<String, ?> metadata, String expectedTemplateUri) {
        if (metadata == null) {
            throw new IllegalArgumentException("Negotiation metadata is required");
        }
        String templateUri = stringValue(metadata.get(MetadataContent.TEMPLATE_URI_METADATA_KEY));
        if (!expectedTemplateUri.equals(templateUri)) {
            throw new IllegalArgumentException(
                    "Unexpected negotiation template URI: expected=" + expectedTemplateUri + ", actual=" + templateUri);
        }
        String prompt = stringValue(metadata.get(ExtensionUriConstants.NEGOTIATION_T_EXTENSION_URI));
        if (prompt.isBlank()) {
            prompt = stringValue(metadata.get(ExtensionUriConstants.NEGOTIATION_T_EXTENSION_URI_NL));
        }
        if (prompt.isBlank()) {
            throw new IllegalArgumentException("Negotiation-T prompt metadata is required");
        }
        return prompt;
    }

    /**
     * Reads the negotiation context carried in the {@code negotiationContext} metadata entry.
     *
     * <p>The nested map must carry the four fields {@code id}, {@code round}, {@code maxRounds}, and
     * {@code performative}; the performative entry must hold a valid upper-case performative name.
     *
     * @param metadata A2A message metadata of a Negotiation-T message
     * @return the carried negotiation context, or {@code null} when the metadata carries no context
     * @throws IllegalArgumentException when the entry exists but is malformed
     */
    public static NegotiationContext readContext(Map<String, ?> metadata) {
        if (metadata == null) {
            return null;
        }
        Object value = metadata.get(MetadataContent.NEGOTIATION_CONTEXT_METADATA_KEY);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Map<?, ?> contextMap)) {
            throw new IllegalArgumentException("Negotiation-T context metadata must be an object");
        }
        Object id = contextMap.get("id");
        Object round = contextMap.get("round");
        Object maxRounds = contextMap.get("maxRounds");
        if (!(id instanceof String idText)
                || !(round instanceof Number roundNumber)
                || !(maxRounds instanceof Number maxRoundsNumber)) {
            throw new IllegalArgumentException("Negotiation-T context metadata is malformed");
        }
        NegotiationPerformative performative = readPerformative(contextMap.get("performative"));
        return new NegotiationContext(idText, roundNumber.intValue(), maxRoundsNumber.intValue(), performative);
    }

    /**
     * Reads the {@code performative} entry of the nested context map.
     *
     * <p>A missing entry is malformed: every negotiation context carries the performative of its message. A present
     * entry must hold one of the four upper-case performative names.
     *
     * @param value candidate wire value, may be {@code null}
     * @return the matching performative
     * @throws IllegalArgumentException when the entry is absent or is not a valid performative name
     */
    private static NegotiationPerformative readPerformative(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("Negotiation-T context metadata has no performative entry");
        }
        return NegotiationPerformative.tryParse(String.valueOf(value))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Negotiation-T context metadata has an unknown performative: " + value));
    }

    public static void requireExtension(String extensionHeader) {
        if (!ExtensionUriConstants.NEGOTIATION_T_EXTENSION_URI.equals(extensionHeader)) {
            throw new IllegalArgumentException("A2A-Extensions must contain the Negotiation-T extension URI");
        }
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
