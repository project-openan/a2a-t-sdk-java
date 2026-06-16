package net.openan.a2at.sdk.prompt.resources.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Minimal scenario definition used by prompt analysis flows.
 *
 * @param scenarioCode unique scenario code
 * @param scenarioName display name
 * @param description scenario description
 * @param example example utterance
 * @since 2026-06
 */
public record ScenarioDefinition(
        @JsonProperty("scenario_code") String scenarioCode,
        @JsonProperty("scenario_name") String scenarioName,
        @JsonProperty("description") String description,
        @JsonProperty("example") String example) {}
