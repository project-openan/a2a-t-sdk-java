package net.openan.a2at.sdk.resources;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.exception.SdkException;

/**
 * Loads prompt resources from the classpath bundle packaged with the SDK.
 *
 * @since 2026-06
 */
public final class ClasspathPromptResourceLoader {

    /**
     * Loads one UTF-8 text resource from the packaged prompt resource tree.
     *
     * @param key resource key to resolve
     * @return loaded text payload
     */
    public String loadText(PromptResourceKey key) {
        String relativePath = key.relativePath();
        InputStream stream = loadResource(relativePath);
        if (stream == null) {
            throw new ResourceNotFoundException("Prompt resource file does not exist.", relativePath);
        }

        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new SdkException("Failed to read prompt resource: " + relativePath, error);
        }
    }

    private static InputStream loadResource(String relativePath) {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader != null) {
            InputStream stream = contextClassLoader.getResourceAsStream(relativePath);
            if (stream != null) {
                return stream;
            }
        }
        return ClasspathPromptResourceLoader.class.getClassLoader().getResourceAsStream(relativePath);
    }
}
