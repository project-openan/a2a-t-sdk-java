package net.openan.a2at.sdk.negotiation.resources;

import net.openan.a2at.sdk.core.model.PromptTemplate;

/**
 * Loads negotiation templates addressed by {@link NegotiationReference} keys.
 *
 * @since 2026-08
 */
public interface NegotiationTemplateLoader {

    /**
     * Loads one negotiation template.
     *
     * @param reference template addressing key, including the language to load
     * @return loaded template with its URI, description and full content
     * @throws net.openan.a2at.sdk.core.exception.ResourceNotFoundException if the template does not exist in the
     *     built-in classpath resources
     */
    PromptTemplate load(NegotiationReference reference);
}
