package net.openan.a2at.sdk.negotiation.generation;

import net.openan.a2at.sdk.negotiation.content.NegotiationContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationGenerationException;
import net.openan.a2at.sdk.negotiation.resources.NegotiationReference;

/**
 * Extracts typed negotiation content from a free-text input.
 *
 * <p>Implementations decide how the text is understood; the returned content must already satisfy the generator input
 * rules so the deterministic rendering step can consume it directly.
 *
 * @since 2026-08
 */
public interface NegotiationContentExtractor {

    /**
     * Extracts the typed content of one negotiation message from free text.
     *
     * @param text free-text input describing the message content
     * @param reference reference identifying the negotiation type, phase and language to extract for
     * @return typed negotiation content matching the reference
     * @throws NegotiationGenerationException with code {@code llm.not_configured} when no LLM client is configured,
     *     {@code llm.invocation_failed} or {@code llm.response_invalid} when the LLM invocation fails,
     *     {@code negotiation.content_extract_failed} when the response cannot be parsed as the expected content,
     *     {@code negotiation.field_missing} when a required field is missing, {@code negotiation.conclusion_mismatch}
     *     when the extracted conclusion contradicts the addressed phase, or {@code negotiation.invalid_input} when the
     *     extracted content contradicts the addressed phase or action
     */
    NegotiationContent extract(String text, NegotiationReference reference);
}
