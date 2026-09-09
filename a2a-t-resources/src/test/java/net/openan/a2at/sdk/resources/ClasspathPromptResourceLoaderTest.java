package net.openan.a2at.sdk.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.model.TemplateUri;
import org.junit.jupiter.api.Test;

class ClasspathPromptResourceLoaderTest {

    private final ClasspathPromptResourceLoader loader = new ClasspathPromptResourceLoader();

    @Test
    void loadsTextResourceUsingPromptResourceKey() {
        PromptResourceKey key = PromptResourceKey.prompt("slot_extraction", "en-US", "system.md");

        String text = loader.loadText(key);

        assertEquals("system prompt", text.trim());
    }

    @Test
    void rejectsResourceTraversalSegments() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PromptResourceKey("prompts", List.of("../escape"), "en-US", "system.md"));
    }

    @Test
    void raisesTypedErrorWhenResourceIsMissing() {
        PromptResourceKey key = PromptResourceKey.template(
                TemplateUri.of("Task-T", "network-layer", "missing_scenario"), "en-US", "template.md");

        ResourceNotFoundException error = assertThrows(ResourceNotFoundException.class, () -> loader.loadText(key));

        assertEquals(
                "prompt_resources/templates/Task-T/network-layer/missing_scenario/v1/en-US/template.md",
                error.resourcePath().replace('\\', '/'));
    }

    @Test
    void loadsPackagedScenarioCatalogForZhCn() {
        String text = loader.loadText(PromptResourceKey.scenario("zh-CN", "scenarios.json"));

        assertTrue(text.contains("subscribe-incident"));
        assertTrue(text.contains("ran-energy-saving"));
    }

    @Test
    void loadsPackagedSubscribeIncidentSlotSchemaWithSemanticHint() {
        PromptResourceKey key = PromptResourceKey.slotSchema(
                TemplateUri.of("Notification-T", "network-layer", "subscribe-incident"), "zh-CN", "slot.json");

        String text = loader.loadText(key);

        assertTrue(text.contains("\"required\": []"));
        assertTrue(text.contains("x-a2at-value-constraint"));
        assertTrue(text.contains("critical"));
        assertTrue(text.contains("high"));
        assertTrue(text.contains("medium"));
        assertTrue(text.contains("low"));
    }

    @Test
    void servesTheCachedValueAfterTheClasspathResourceIsAlteredOnDisk() throws Exception {
        PromptResourceKey key = PromptResourceKey.prompt("slot_extraction", "en-US", "system.md");
        String first = loader.loadText(key);

        Path resourceFile = testResourceFile("prompt_resources/prompts/slot_extraction/en-US/system.md");
        String original = Files.readString(resourceFile, StandardCharsets.UTF_8);
        Files.writeString(resourceFile, "changed on disk", StandardCharsets.UTF_8);
        try {
            assertEquals(first, loader.loadText(key));
        } finally {
            Files.writeString(resourceFile, original, StandardCharsets.UTF_8);
        }
    }

    @Test
    void doesNotCacheMissingResourcesAndKeepsThrowing() {
        PromptResourceKey key = PromptResourceKey.template(
                TemplateUri.of("Task-T", "network-layer", "missing_scenario"), "en-US", "template.md");

        assertThrows(ResourceNotFoundException.class, () -> loader.loadText(key));
        assertThrows(ResourceNotFoundException.class, () -> loader.loadText(key));
    }

    private static Path testResourceFile(String classpathPath) throws Exception {
        URL resource = ClasspathPromptResourceLoaderTest.class.getClassLoader().getResource(classpathPath);
        if (resource == null || !"file".equals(resource.getProtocol())) {
            throw new IllegalStateException(
                    "test resource not reachable as a file URL: " + classpathPath + " -> " + resource);
        }
        return Path.of(resource.toURI());
    }
}
