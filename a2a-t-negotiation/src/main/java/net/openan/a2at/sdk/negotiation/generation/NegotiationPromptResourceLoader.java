package net.openan.a2at.sdk.negotiation.generation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import net.openan.a2at.sdk.core.exception.A2ATError;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.resources.ClasspathResourceStreams;
import net.openan.a2at.sdk.core.resources.PathSegments;

/**
 * Loads negotiation LLM prompt resources from the classpath bundle packaged with the SDK.
 *
 * <p>Prompt resources are classpath-only: the categories are {@code information_negotiation},
 * {@code target_negotiation}, {@code feasibility_negotiation} and {@code negotiation_semantic_validation}, each with a
 * {@code system.md} and a {@code user.md} per language.
 *
 * @since 2026-08
 */
class NegotiationPromptResourceLoader {

    private static final String CLASSPATH_ROOT = "prompt_resources/prompts/";

    private static final String SYSTEM_FILE_NAME = "system.md";

    private static final String USER_FILE_NAME = "user.md";

    /**
     * Loads the system prompt of one prompt category.
     *
     * @param promptCategory prompt category directory such as {@code information_negotiation}
     * @param language locale identifier such as {@code zh-CN} or {@code en-US}
     * @return full system prompt text
     * @throws ResourceNotFoundException if the system prompt does not exist for the category and language
     */
    public String loadSystem(String promptCategory, String language) {
        return loadPrompt(SYSTEM_FILE_NAME, promptCategory, language);
    }

    /**
     * Loads the user prompt of one prompt category.
     *
     * @param promptCategory prompt category directory such as {@code information_negotiation}
     * @param language locale identifier such as {@code zh-CN} or {@code en-US}
     * @return full user prompt text
     * @throws ResourceNotFoundException if the user prompt does not exist for the category and language
     */
    public String loadUser(String promptCategory, String language) {
        return loadPrompt(USER_FILE_NAME, promptCategory, language);
    }

    private static String loadPrompt(String fileName, String promptCategory, String language) {
        if (!PathSegments.isSimpleSegment(promptCategory)) {
            throw new IllegalArgumentException(
                    "Prompt category must be a non-blank simple path segment but was " + promptCategory + ".");
        }
        if (!PathSegments.isSimpleSegment(language)) {
            throw new IllegalArgumentException(
                    "Prompt language must be a non-blank simple path segment but was " + language + ".");
        }
        String path = CLASSPATH_ROOT + promptCategory + "/" + language + "/" + fileName;
        return readResource(path);
    }

    private static String readResource(String path) {
        InputStream stream = ClasspathResourceStreams.open(path);
        if (stream == null) {
            throw new ResourceNotFoundException(
                    "Negotiation prompt resource does not exist on the classpath: " + path, path);
        }
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new A2ATError("Failed to read negotiation prompt resource: " + path, exception);
        }
    }
}
