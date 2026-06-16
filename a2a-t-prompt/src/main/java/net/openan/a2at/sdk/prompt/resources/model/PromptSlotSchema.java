package net.openan.a2at.sdk.prompt.resources.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Shared flat slot schema model.
 *
 * @param scenarioCode scenario code
 * @param slotDefinitions flattened slot definitions
 * @since 2026-06
 */
public record PromptSlotSchema(
        @JsonProperty("scenario_code") String scenarioCode,
        @JsonProperty("slot_definitions") List<PromptSlotDefinition> slotDefinitions) {}
