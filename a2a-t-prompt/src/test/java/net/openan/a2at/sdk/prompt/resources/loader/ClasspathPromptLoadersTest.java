package net.openan.a2at.sdk.prompt.resources.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.exception.SdkException;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotSchema;
import net.openan.a2at.sdk.prompt.resources.model.ScenarioDefinition;
import net.openan.a2at.sdk.resources.ClasspathPromptResourceLoader;
import org.junit.jupiter.api.Test;

class ClasspathPromptLoadersTest {

    private final ClasspathPromptResourceLoader resourceLoader = new ClasspathPromptResourceLoader();

    private static String normalizeLineEndings(String payload) {
        return payload.replace("\r\n", "\n").replace("\r", "\n");
    }

    @Test
    void loadScenarioCatalogMapsJacksonAnnotatedRecords() {
        List<ScenarioDefinition> scenarios = new ClasspathPromptScenarioCatalogLoader(resourceLoader).load("en");

        assertEquals(3, scenarios.size());
        assertEquals("handoff_planning", scenarios.get(0).scenarioCode());
        assertEquals("Cross-Team Handoff Planning", scenarios.get(0).scenarioName());
    }

    @Test
    void loadTemplateReturnsExactMarkdownPayload() {
        String template = new ClasspathPromptTemplateLoader(resourceLoader).loadTemplate("handoff_planning", "en");
        template = normalizeLineEndings(template);
        assertEquals(
                """
                # Cross-Team Handoff Plan

                ## Context

                Scenario: {{scenario_code}}
                Primary owner: {{owner}}
                Target system: {{target_system}}
                Deadline: {{deadline}}

                ## Required Output

                1. Summarize the current state in two concise paragraphs.
                2. List the owners for engineering, security, product, and operations.
                3. Identify blocking dependencies and the expected unblock date.
                4. Provide a rollback path that can be executed by the on-call team.

                ## Acceptance Criteria

                - Every required slot is represented in the plan.
                - Risks are grouped by severity and owner.
                - Follow-up tasks include a due date and escalation path.
                """,
                template);
    }

    @Test
    void loadSlotSchemaMapsJacksonAnnotatedRecords() {
        PromptSlotSchema schema =
                new ClasspathPromptSlotSchemaLoader(resourceLoader).loadSlotSchema("handoff_planning", "en");

        assertEquals("handoff_planning", schema.scenarioCode());
        assertEquals(7, schema.slotDefinitions().size());
        assertEquals("task_id", schema.slotDefinitions().get(0).name());
        assertEquals(true, schema.slotDefinitions().get(0).required());
        assertEquals(
                List.of("1", "2", "3", "4", "5"),
                schema.slotDefinitions().get(1).allowedValues());
        assertEquals(
                "Business priority from 1 (highest urgency) to 5 (lowest urgency)",
                schema.slotDefinitions().get(1).description());
    }

    @Test
    void missingClasspathResourceReportsResolvedResourcePath() {
        ResourceNotFoundException exception =
                assertThrows(ResourceNotFoundException.class, () -> new ClasspathPromptTemplateLoader(resourceLoader)
                        .loadTemplate("missing_scenario", "en"));

        assertEquals("prompt_resources/templates/missing_scenario/en/template.md", exception.resourcePath());
    }

    @Test
    void invalidClasspathScenarioCatalogIsWrappedAsSdkException() {
        SdkException exception = assertThrows(
                SdkException.class, () -> new ClasspathPromptScenarioCatalogLoader(resourceLoader).load("broken"));

        assertEquals("Failed to parse scenario catalog for language: broken", exception.getMessage());
    }
}
