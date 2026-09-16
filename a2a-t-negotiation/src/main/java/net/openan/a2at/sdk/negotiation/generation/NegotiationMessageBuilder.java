package net.openan.a2at.sdk.negotiation.generation;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Assembles the LLM message list of one negotiation LLM step.
 *
 * <p>The system prompt of the addressed category is used verbatim. The user prompt is loaded from the same category and
 * every literal bracket token it contains, such as {@code [phase]} or {@code [input]}, is replaced by the value
 * supplied for that token name; a supplied null value is replaced with an empty string and bracket tokens without a
 * supplied value are left unchanged.
 *
 * @since 2026-08
 */
final class NegotiationMessageBuilder {

    /** Token name receiving the negotiation phase of the step, such as {@code propose} or {@code accept}. */
    public static final String TOKEN_PHASE = "phase";

    /** Token name receiving the free-text input of the step. */
    public static final String TOKEN_INPUT = "input";

    /** Token name receiving the template URI declared by the caller. */
    public static final String TOKEN_TEMPLATE_URI = "template_uri";

    /** Token name receiving the negotiation type declared by the caller. */
    public static final String TOKEN_NEGOTIATION_TYPE = "negotiation_type";

    /** Token name receiving the JSON Schema of the step. */
    public static final String TOKEN_SCHEMA = "schema";

    private final NegotiationPromptResourceLoader resourceLoader;

    /** Creates a message builder using the default classpath prompt resource loader. */
    public NegotiationMessageBuilder() {
        this(new NegotiationPromptResourceLoader());
    }

    /**
     * Creates a message builder on one prompt resource loader.
     *
     * @param resourceLoader loader supplying the system and user prompts
     * @throws NullPointerException if the resource loader is null
     */
    public NegotiationMessageBuilder(NegotiationPromptResourceLoader resourceLoader) {
        Objects.requireNonNull(resourceLoader, "Negotiation message builder requires a prompt resource loader.");
        this.resourceLoader = resourceLoader;
    }

    /**
     * Builds the system and user messages of one LLM step.
     *
     * @param promptCategory prompt category directory such as {@code information_negotiation}
     * @param language locale identifier such as {@code zh-CN} or {@code en-US}
     * @param tokens token values keyed by token name such as {@code phase} or {@code input}; null or blank values are
     *     replaced with an empty string
     * @return ordered message list with one system message followed by one user message
     */
    public List<Map<String, String>> buildMessages(String promptCategory, String language, Map<String, String> tokens) {
        String systemPrompt = resourceLoader.loadSystem(promptCategory, language);
        String userPrompt = replaceTokens(resourceLoader.loadUser(promptCategory, language), tokens);
        return List.of(
                Map.of("role", "system", "content", systemPrompt), Map.of("role", "user", "content", userPrompt));
    }

    private static String replaceTokens(String userPrompt, Map<String, String> tokens) {
        String result = userPrompt;
        if (tokens != null) {
            for (Map.Entry<String, String> token : tokens.entrySet()) {
                String value = token.getValue() == null ? "" : token.getValue();
                result = result.replace("[" + token.getKey() + "]", value);
            }
        }
        return result;
    }
}
