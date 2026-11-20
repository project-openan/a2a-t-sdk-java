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
