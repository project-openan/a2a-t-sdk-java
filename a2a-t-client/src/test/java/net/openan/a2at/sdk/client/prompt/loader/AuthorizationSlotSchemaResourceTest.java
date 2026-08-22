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

class AuthorizationSlotSchemaResourceTest {

    @Test
    void should_loadAuthorizationSlotSchemaFromClasspath_ForZhCN() {
        DefaultClasspathClientSlotSchemaLoader loader =
                new DefaultClasspathClientSlotSchemaLoader(new ClasspathPromptResourceLoader());

        PromptSlotSchema schema = loader.loadSlotSchema("authorization-policy-management", "zh-CN");

        assertEquals("authorization-policy-management", schema.scenarioCode());
        assertEquals(2, schema.slotDefinitions().size());
        assertTrue(schema.slotDefinitions().get(0).required());
        assertEquals("授权策略的操作类型", schema.slotDefinitions().get(0).name());
    }

    @Test
    void should_loadAuthorizationSlotSchema_ForEnUS() {
        DefaultClasspathClientSlotSchemaLoader loader =
                new DefaultClasspathClientSlotSchemaLoader(new ClasspathPromptResourceLoader());

        PromptSlotSchema schema = loader.loadSlotSchema("authorization-policy-management", "en-US");

        assertEquals("authorization-policy-management", schema.scenarioCode());
        assertEquals(2, schema.slotDefinitions().size());
        assertTrue(schema.slotDefinitions().get(0).required());
        assertEquals("authorization_policy_operation_type", schema.slotDefinitions().get(0).name());
    }

    @Test
    void should_haveOperationTypeOnlyInRequired() {
        DefaultClasspathClientSlotSchemaLoader loader =
                new DefaultClasspathClientSlotSchemaLoader(new ClasspathPromptResourceLoader());

        PromptSlotSchema schema = loader.loadSlotSchema("authorization-policy-management", "zh-CN");

        long requiredCount = schema.slotDefinitions().stream()
                .filter(PromptSlotDefinition::required)
                .count();
        assertEquals(1, requiredCount);
        assertEquals("授权策略的操作类型", schema.slotDefinitions().get(0).name());
        assertFalse(schema.slotDefinitions().get(1).required());
    }

    @Test
    void should_havePropertyNamesMatchingTemplatePlaceholders() {
        DefaultClasspathClientSlotSchemaLoader loader =
                new DefaultClasspathClientSlotSchemaLoader(new ClasspathPromptResourceLoader());

        PromptSlotSchema zhSchema = loader.loadSlotSchema("authorization-policy-management", "zh-CN");
        PromptSlotSchema enSchema = loader.loadSlotSchema("authorization-policy-management", "en-US");

        assertEquals("授权策略的操作类型", zhSchema.slotDefinitions().get(0).name());
        assertEquals("动网操作的授权策略列表", zhSchema.slotDefinitions().get(1).name());
        assertEquals("authorization_policy_operation_type", enSchema.slotDefinitions().get(0).name());
        assertEquals("network_operation_authorization_policy_list", enSchema.slotDefinitions().get(1).name());
    }

    @Test
    void should_haveSchemaStructureParity() {
        DefaultClasspathClientSlotSchemaLoader loader =
                new DefaultClasspathClientSlotSchemaLoader(new ClasspathPromptResourceLoader());

        PromptSlotSchema authSchema = loader.loadSlotSchema("authorization-policy-management", "zh-CN");
        PromptSlotSchema energySchema = loader.loadSlotSchema("energy-saving", "zh-CN");

        assertFalse(authSchema.slotDefinitions().isEmpty());
        assertFalse(energySchema.slotDefinitions().isEmpty());

        for (PromptSlotDefinition def : authSchema.slotDefinitions()) {
            assertEquals("string", def.jsonType());
            assertFalse(def.description().isBlank());
            assertFalse(def.valueConstraint().isBlank());
        }
    }

    @Test
    void should_throwResourceNotFoundException_WhenSlotSchemaMissing() {
        DefaultClasspathClientSlotSchemaLoader loader =
                new DefaultClasspathClientSlotSchemaLoader(new ClasspathPromptResourceLoader());

        assertThrows(
                ResourceNotFoundException.class,
                () -> loader.loadSlotSchema("nonexistent-scenario", "zh-CN"));
    }
}