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

package net.openan.a2at.sdk.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TemplateUriTest {

    @Test
    void composesUriFromComponents() {
        TemplateUri uri = TemplateUri.of("Task-T", "network-layer", "ran-energy-saving");
        assertEquals("Task-T/network-layer/ran-energy-saving/v1", uri.uri());
        assertEquals("Task-T", uri.extensionName());
        assertEquals(List.of("network-layer", "ran-energy-saving"), uri.pathSegments());
        assertEquals("v1", uri.templateVersion());
    }

    @Test
    void parseRoundTripsNetworkLayerUri() {
        Optional<TemplateUri> parsed = TemplateUri.parse("Task-T/network-layer/ran-energy-saving/v1");
        assertEquals(
                Optional.of(TemplateUri.of("Task-T", "network-layer", "ran-energy-saving")), parsed);
        assertEquals("Task-T/network-layer/ran-energy-saving/v1", parsed.orElseThrow().uri());
    }

    @Test
    void parseRoundTripsAuthorizationUri() {
        Optional<TemplateUri> parsed = TemplateUri.parse("Authorization-T/authorization-policy-management/v1");
        assertEquals(
                Optional.of(TemplateUri.of("Authorization-T", "authorization-policy-management")), parsed);
    }

    @Test
    void parseRoundTripsNegotiationUri() {
        Optional<TemplateUri> parsed = TemplateUri.parse("Negotiation-T/information-negotiation/propose/v1");
        assertEquals(
                Optional.of(TemplateUri.of("Negotiation-T", "information-negotiation", "propose")), parsed);
    }

    @Test
    void parseRejectsMalformedUris() {
        assertEquals(Optional.empty(), TemplateUri.parse(null));
        assertEquals(Optional.empty(), TemplateUri.parse("  "));
        assertEquals(Optional.empty(), TemplateUri.parse("Task-T"));
        assertEquals(Optional.empty(), TemplateUri.parse("Task-T/v1"));
        assertEquals(Optional.empty(), TemplateUri.parse("Task-T/network-layer/ran-energy-saving/../etc"));
        assertEquals(Optional.empty(), TemplateUri.parse("Task-T/network-layer/energy\\saving/v1"));
        assertEquals(Optional.empty(), TemplateUri.parse("Task-T//ran-energy-saving/v1"));
    }

    @Test
    void ofRejectsInvalidComponents() {
        assertThrows(NullPointerException.class, () -> TemplateUri.of(null, "scenario"));
        assertThrows(NullPointerException.class, () -> TemplateUri.of("Task-T", (String[]) null));
        assertThrows(IllegalArgumentException.class, () -> TemplateUri.of("Task-T"));
        assertThrows(NullPointerException.class, () -> new TemplateUri("Task-T", (List<String>) null, "v1"));
        assertThrows(IllegalArgumentException.class, () -> TemplateUri.of("Task-T", "scen/ario"));
        assertThrows(IllegalArgumentException.class, () -> TemplateUri.of("Task-T", "scen..ario/x"));
        assertThrows(IllegalArgumentException.class, () -> TemplateUri.of("Task-T", "  "));
        assertThrows(NullPointerException.class, () -> TemplateUri.of("Task-T", List.of("scenario"), null));
        assertThrows(IllegalArgumentException.class, () -> TemplateUri.of("Task-T", List.of("scenario"), "  "));
    }

    @Test
    void pathSegmentsAreDefensivelyCopied() {
        ArrayList<String> segments = new ArrayList<>(List.of("network-layer", "ran-energy-saving"));
        TemplateUri uri = new TemplateUri("Task-T", segments, "v1");
        segments.add("tampered");
        assertEquals(List.of("network-layer", "ran-energy-saving"), uri.pathSegments());
    }
}
