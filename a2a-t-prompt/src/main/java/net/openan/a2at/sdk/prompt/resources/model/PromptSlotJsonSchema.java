package net.openan.a2at.sdk.prompt.resources.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Raw JSON schema view for a prompt slot file.
 *
 * @param required required slot names
 * @param properties slot property schemas
 * @since 2026-06
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PromptSlotJsonSchema(
        @JsonProperty("required") List<String> required,
        @JsonProperty("properties") Map<String, PromptSlotJsonProperty> properties) {

    /**
     * Converts one JSON schema document into the SDK's shared flat slot schema model.
     *
     * @param scenarioCode scenario code
     * @return flattened slot schema
     */
    public PromptSlotSchema toPromptSlotSchema(String scenarioCode) {
        Map<String, PromptSlotJsonProperty> orderedProperties = properties == null
                ? Map.of()
                : new LinkedHashMap<>(properties);
        List<String> requiredSlots = required == null ? List.of() : List.copyOf(required);
        List<PromptSlotDefinition> slotDefinitions = new ArrayList<>();
        for (Map.Entry<String, PromptSlotJsonProperty> entry : orderedProperties.entrySet()) {
            PromptSlotJsonProperty property = entry.getValue();
            if (property == null) {
                slotDefinitions.add(new PromptSlotDefinition(
                        entry.getKey(),
                        requiredSlots.contains(entry.getKey()),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null));
                continue;
            }
            slotDefinitions.add(new PromptSlotDefinition(
                    entry.getKey(),
                    requiredSlots.contains(entry.getKey()),
                    property.type(),
                    property.pattern(),
                    property.minimum(),
                    property.maximum(),
                    property.allowedValues(),
                    property.description()));
        }
        return new PromptSlotSchema(scenarioCode, List.copyOf(slotDefinitions));
    }
}
