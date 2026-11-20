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

import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Successful result of an A2A-T prompt generation call.
 *
 * @param templateUri URI of the template the message was generated from
 * @param promptText rendered prompt text
 * @param extensionUri TMF extension URI under which the message travels in A2A-T metadata
 * @param negotiationContext negotiation session context; {@code null} for non-negotiation messages
 * @since 2026-08
 */
public record MetadataContent(
        @Nullable String templateUri,
        @Nullable String promptText,
        @NonNull String extensionUri,
        @Nullable NegotiationContext negotiationContext) {

    /** Metadata key carrying the template URI alongside the message itself. */
    public static final String TEMPLATE_URI_METADATA_KEY = "templateUri";

    /** Metadata key carrying the negotiation session context alongside the message itself. */
    public static final String NEGOTIATION_CONTEXT_METADATA_KEY = "negotiationContext";

    /**
     * Builds a metadata content without a negotiation context.
     *
     * @param templateUri URI of the template the message was generated from
     * @param promptText rendered prompt text
     * @param extensionUri TMF extension URI under which the message travels in A2A-T metadata
     */
    public MetadataContent(@Nullable String templateUri, @Nullable String promptText, @NonNull String extensionUri) {
        this(templateUri, promptText, extensionUri, null);
    }

    /**
     * Builds the A2A-T metadata map for this generated message.
     *
     * <p>The returned map always contains the extension URI mapping to the rendered message and {@code templateUri}
     * mapping to the template URI, in that order. When a negotiation context is present it additionally carries
     * {@code negotiationContext} mapping to a nested map with the {@code id}, {@code round}, {@code maxRounds}, and
     * {@code performative} fields, in that order, the performative holding its upper-case name. Non-negotiation
     * messages omit the {@code negotiationContext} key entirely. Repeated calls return equal maps.
     *
     * @return newly built metadata map with the extension URI, {@code templateUri}, and, for negotiation messages,
     *     {@code negotiationContext} keys
     */
    public @NonNull Map<String, Object> buildMetadataContent() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(extensionUri, promptText);
        metadata.put(TEMPLATE_URI_METADATA_KEY, templateUri);
        if (negotiationContext != null) {
            Map<String, Object> context = new LinkedHashMap<>();
            context.put("id", negotiationContext.id());
            context.put("round", negotiationContext.round());
            context.put("maxRounds", negotiationContext.maxRounds());
            context.put("performative", negotiationContext.performative().name());
            metadata.put(NEGOTIATION_CONTEXT_METADATA_KEY, context);
        }
        return metadata;
    }
}
