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

package net.openan.a2at.sdk.observability;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Classified view of one A2A stream event (protocol-agnostic). */
public record EventInfo(
        String kind,
        @Nullable String taskId,
        @Nullable String taskStatus,
        boolean finalEvent,
        boolean terminal,
        @Nullable Map<String, Object> messageMetadata,
        @Nullable String artifactName) {

    public static final EventInfo UNKNOWN = new EventInfo("unknown", null, null, false, false, null, null);

    public boolean isNegotiationCandidate() {
        return "message".equals(kind);
    }
}
