package net.openan.a2at.sdk.prompt.resources.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Shared slot schema entry used by both client-side generation and server-side compliance checks.
 *
 * @param name slot name
 * @param required whether the slot is required
 * @param jsonType raw JSON schema type
 * @param pattern optional regex constraint
 * @param minimum optional numeric minimum
 * @param maximum optional numeric maximum
 * @param allowedValues optional enum constraint
 * @param description slot description
 * @since 2026-06
 */
public record PromptSlotDefinition(
        @JsonProperty("name") String name,
        @JsonProperty("required") boolean required,
        @JsonProperty("type") String jsonType,
        @JsonProperty("pattern") String pattern,
        @JsonProperty("minimum") Double minimum,
        @JsonProperty("maximum") Double maximum,
        @JsonProperty("enum") List<String> allowedValues,
        @JsonProperty("description") String description) {}
