package net.openan.a2at.sdk.client.prompt.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotDefinition;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotSchema;
import org.junit.jupiter.api.Test;

class LocalFileClientSlotSchemaLoaderTest {

    @Test
    void loadSlotSchemaDeserializesJsonSchemaModelFromLocalFile() {
        LocalFileClientSlotSchemaLoader loader = new LocalFileClientSlotSchemaLoader(
                Path.of("..", "a2a-t-resources", "src", "main", "resources", "prompt_resources"));

        PromptSlotSchema schema = loader.loadSlotSchema("energy_saving", "zh-CN");

        assertEquals("energy_saving", schema.scenarioCode());
        assertEquals(4, schema.slotDefinitions().size());

        PromptSlotDefinition taskObject = schema.slotDefinitions().get(1);
        assertEquals("任务对象", taskObject.name());
        assertEquals("string", taskObject.jsonType());
        assertTrue(taskObject.description().contains("明确指定节能区域信息"));

        PromptSlotDefinition constraints = schema.slotDefinitions().get(3);
        assertEquals("约束条件", constraints.name());
        assertTrue(constraints.description().contains("限制和边界条件"));
    }
}
