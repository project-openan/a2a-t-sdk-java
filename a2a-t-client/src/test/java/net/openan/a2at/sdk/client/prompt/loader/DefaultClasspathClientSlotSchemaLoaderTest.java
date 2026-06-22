package net.openan.a2at.sdk.client.prompt.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotDefinition;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotSchema;
import net.openan.a2at.sdk.resources.ClasspathPromptResourceLoader;
import org.junit.jupiter.api.Test;

class DefaultClasspathClientSlotSchemaLoaderTest {

    @Test
    void loadSlotSchemaReadsJsonSchemaFromClasspathPromptResources() {
        DefaultClasspathClientSlotSchemaLoader loader =
                new DefaultClasspathClientSlotSchemaLoader(new ClasspathPromptResourceLoader());

        PromptSlotSchema schema = loader.loadSlotSchema("energy_saving", "zh-CN");

        assertEquals("energy_saving", schema.scenarioCode());
        assertEquals(4, schema.slotDefinitions().size());
        assertFalse(schema.slotDefinitions().get(0).name().isBlank());
        assertFalse(schema.slotDefinitions().get(0).required());
        assertEquals("string", schema.slotDefinitions().get(0).jsonType());
        assertFalse(schema.slotDefinitions().get(1).name().isBlank());
        assertFalse(schema.slotDefinitions().get(3).name().isBlank());
        assertTrue(schema.slotDefinitions().get(3).description().length() > 10);
    }

    @Test
    void loadSlotSchemaPropagatesTypedMissingResourceError() {
        DefaultClasspathClientSlotSchemaLoader loader =
                new DefaultClasspathClientSlotSchemaLoader(new ClasspathPromptResourceLoader());

        assertThrows(ResourceNotFoundException.class, () -> loader.loadSlotSchema("missing_scenario", "zh-CN"));
    }

    @Test
    void loadSlotSchemaKeepsSubscribeIncidentOptionalConditionConstraints() {
        DefaultClasspathClientSlotSchemaLoader loader =
                new DefaultClasspathClientSlotSchemaLoader(new ClasspathPromptResourceLoader());

        PromptSlotSchema schema = loader.loadSlotSchema("subscribe_incident", "zh-CN");

        assertEquals("subscribe_incident", schema.scenarioCode());
        assertEquals(3, schema.slotDefinitions().size());
        assertTrue(schema.slotDefinitions().stream().noneMatch(PromptSlotDefinition::required));
    }
}
