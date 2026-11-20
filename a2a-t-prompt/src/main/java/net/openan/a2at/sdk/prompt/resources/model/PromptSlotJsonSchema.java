/*
 * Copyright (c) 2026 Huawei Technologies Co., Ltd.
 * All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 *    Licensed under the Apache License, Version 2.0 (the "License"); you may
 *    not use this file except in compliance with the License. You may obtain
 *    a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 */

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
        Map<String, PromptSlotJsonProperty> orderedProperties =
                properties == null ? Map.of() : new LinkedHashMap<>(properties);
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
                    property.examples(),
                    property.description(),
                    property.valueConstraint()));
        }
        return new PromptSlotSchema(scenarioCode, List.copyOf(slotDefinitions));
    }
}
