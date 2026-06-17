package net.openan.a2at.sdk.server.model;

import java.util.List;

/**
 * Template candidate used to parse processed task prompts on the server side.
 *
 * @param scenarioCode scenario code associated with the template
 * @param language language associated with the template
 * @param templateText canonical template text
 * @param slotDefinitions slot definitions expected by the template
 * @since 2026-06
 */
public record PromptTemplateDefinition(
        String scenarioCode,
        String language,
        String templateText,
        List<PromptTemplateSlotDefinition> slotDefinitions) {}
