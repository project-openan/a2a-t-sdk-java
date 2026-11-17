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
 * @param examples optional example values
 * @param description slot description
 * @param valueConstraint human-readable value constraint description
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
        @JsonProperty("examples") List<Object> examples,
        @JsonProperty("description") String description,
        @JsonProperty("x-a2at-value-constraint") String valueConstraint) {}
