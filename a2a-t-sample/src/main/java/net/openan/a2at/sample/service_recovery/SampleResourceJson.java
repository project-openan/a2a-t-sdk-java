package net.openan.a2at.sample.service_recovery;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Loads JSON resources from the service-recovery sample resource tree.
 *
 * <p>Keeps locale-specific business data (zh-CN slot names, event field labels) in resource files so the Java sources
 * stay pure ASCII.
 *
 * @since 2026-08
 */
public final class SampleResourceJson {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private SampleResourceJson() {}

    /**
     * Loads one JSON object resource from the classpath.
     *
     * @param resourcePath classpath path of the JSON resource
     * @return parsed JSON object as a string-to-object map
     */
    public static Map<String, Object> load(String resourcePath) {
        try (InputStream inputStream = SampleResourceJson.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalStateException("Sample resource not found: " + resourcePath);
            }
            return OBJECT_MAPPER.readValue(inputStream, new TypeReference<Map<String, Object>>() {});
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read sample resource: " + resourcePath, exception);
        }
    }

    /**
     * Loads one text resource from the classpath.
     *
     * @param resourcePath classpath path of the text resource
     * @return resource content as UTF-8 text
     */
    static String loadText(String resourcePath) {
        try (InputStream inputStream = SampleResourceJson.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalStateException("Sample resource not found: " + resourcePath);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read sample resource: " + resourcePath, exception);
        }
    }
}
