package net.openan.a2at.sdk.prompt.resources.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Shared raw JSON-schema property view for one prompt slot.
 *
 * @param type json schema type
 * @param pattern optional regex constraint
 * @param minimum optional numeric minimum
 * @param maximum optional numeric maximum
 * @param allowedValues optional enum values
 * @param description slot description
 * @since 2026-06
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PromptSlotJsonProperty(
        @JsonProperty("type") String type,
        @JsonProperty("pattern") String pattern,
        @JsonProperty("minimum") Double minimum,
        @JsonProperty("maximum") Double maximum,
        @JsonProperty("enum") List<String> allowedValues,
        @JsonAlias({"description", "x-a2at-value-constraint"}) @JsonProperty("description") String description) {}
