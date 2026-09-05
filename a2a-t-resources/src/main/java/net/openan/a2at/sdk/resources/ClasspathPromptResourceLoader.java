package net.openan.a2at.sdk.resources;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import net.openan.a2at.sdk.core.exception.A2ATError;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.resources.ClasspathResourceStreams;
import org.jspecify.annotations.Nullable;

/**
 * Loads prompt resources from the classpath bundle packaged with the SDK.
 *
 * <p>Classpath resources are frozen at assembly time: each resource path is resolved once and cached at the JVM level,
 * so every subsequent load returns the cached copy. Missing resources throw on every call and are never cached. The
 * loader is thread-safe.
 *
 * @since 2026-06
 */
public final class ClasspathPromptResourceLoader {

    private static final ConcurrentHashMap<CacheKey, String> CACHE = new ConcurrentHashMap<>();

    /**
     * Loads one UTF-8 text resource from the packaged prompt resource tree.
     *
     * @param key resource key to resolve
     * @return loaded text payload
     */
    public String loadText(PromptResourceKey key) {
        String relativePath = key.relativePath();
        CacheKey cacheKey = new CacheKey(relativePath, Thread.currentThread().getContextClassLoader());
        return CACHE.computeIfAbsent(cacheKey, ignored -> read(relativePath));
    }

    private static String read(String relativePath) {
        InputStream stream = ClasspathResourceStreams.open(relativePath);
        if (stream == null) {
            throw new ResourceNotFoundException("Prompt resource file does not exist.", relativePath);
        }

        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new A2ATError("Failed to read prompt resource: " + relativePath, error);
        }
    }

    private record CacheKey(String relativePath, @Nullable ClassLoader contextClassLoader) {}
}
