package net.openan.a2at.sdk.negotiation.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;

class NegotiationPromptResourceLoaderTest {

    private final NegotiationPromptResourceLoader loader = new NegotiationPromptResourceLoader();

    @Test
    void loadsSystemAndUserPromptsOfEveryCategoryAndLanguage() {
        for (String category : List.of(
                "information_negotiation",
                "target_negotiation",
                "feasibility_negotiation",
                "negotiation_semantic_validation")) {
            for (String language : List.of("zh-CN", "en-US")) {
                assertFalse(loader.loadSystem(category, language).isBlank());
                assertFalse(loader.loadUser(category, language).isBlank());
            }
        }
    }

    @Test
    void throwsResourceNotFoundForMissingPrompts() {
        ResourceNotFoundException missingCategory =
                assertThrows(ResourceNotFoundException.class, () -> loader.loadSystem("unknown_negotiation", "zh-CN"));

        assertEquals("prompt_resources/prompts/unknown_negotiation/zh-CN/system.md", missingCategory.resourcePath());

        assertThrows(ResourceNotFoundException.class, () -> loader.loadUser("information_negotiation", "fr-FR"));
    }

    @Test
    void rejectsPathLikeArguments() {
        assertTrue(assertThrows(
                        IllegalArgumentException.class, () -> loader.loadSystem("../information_negotiation", "zh-CN"))
                .getMessage()
                .contains("Prompt category must be a non-blank simple path segment"));
        assertTrue(
                assertThrows(IllegalArgumentException.class, () -> loader.loadUser("information_negotiation", "../zh-CN"))
                        .getMessage()
                        .contains("Prompt language must be a non-blank simple path segment"));
    }

    @Test
    void messageBuilderUsesSystemPromptVerbatimAndReplacesUserPromptTokens() {
        NegotiationMessageBuilder builder = new NegotiationMessageBuilder(loader);

        List<Map<String, String>> messages = builder.buildMessages(
                "information_negotiation",
                "zh-CN",
                Map.of(
                        NegotiationMessageBuilder.TOKEN_PHASE,
                        "propose",
                        NegotiationMessageBuilder.TOKEN_INPUT,
                        "请提供故障发生时间。"));

        assertEquals(2, messages.size());
        assertEquals("system", messages.get(0).get("role"));
        assertEquals(messages.get(0).get("content"), loader.loadSystem("information_negotiation", "zh-CN"));
        assertEquals("user", messages.get(1).get("role"));
        assertTrue(messages.get(1).get("content").contains("协商阶段：propose"));
        assertTrue(messages.get(1).get("content").contains("请提供故障发生时间。"));
        assertFalse(messages.get(1).get("content").contains("[phase]"));
        assertFalse(messages.get(1).get("content").contains("[input]"));
    }

    @Test
    void messageBuilderReplacesSemanticValidationTokens() {
        NegotiationMessageBuilder builder = new NegotiationMessageBuilder();

        List<Map<String, String>> messages = builder.buildMessages(
                "negotiation_semantic_validation",
                "en-US",
                Map.of(
                        NegotiationMessageBuilder.TOKEN_NEGOTIATION_TYPE,
                        "information",
                        NegotiationMessageBuilder.TOKEN_TEMPLATE_URI,
                        StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE.uri(),
                        NegotiationMessageBuilder.TOKEN_SCHEMA,
                        "{\"type\":\"object\"}",
                        NegotiationMessageBuilder.TOKEN_INPUT,
                        "the message text"));

        String userPrompt = messages.get(1).get("content");
        assertTrue(userPrompt.contains("Declared negotiation type: information"));
        assertTrue(userPrompt.contains(
                "Declared template identifier: " + StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE.uri()));
        assertTrue(userPrompt.contains("Parameter schema:"));
        assertTrue(userPrompt.contains("{\"type\":\"object\"}"));
        assertTrue(userPrompt.contains("Negotiation message to validate:"));
        assertTrue(userPrompt.contains("the message text"));
        assertFalse(userPrompt.contains("[negotiation_type]"));
        assertFalse(userPrompt.contains("[template_uri]"));
        assertFalse(userPrompt.contains("[schema]"));
        assertFalse(userPrompt.contains("[input]"));
    }

    @Test
    void messageBuilderRejectsNullResourceLoader() {
        assertEquals(
                "Negotiation message builder requires a prompt resource loader.",
                assertThrows(NullPointerException.class, () -> new NegotiationMessageBuilder(null))
                        .getMessage());
    }
}
