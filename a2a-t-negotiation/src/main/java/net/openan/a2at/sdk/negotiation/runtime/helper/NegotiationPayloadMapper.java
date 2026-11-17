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

package net.openan.a2at.sdk.negotiation.runtime.helper;

import java.util.LinkedHashMap;
import java.util.Map;
import net.openan.a2at.sdk.negotiation.runtime.NegotiationHandler;
import net.openan.a2at.sdk.negotiation.types.exception.NegotiationStateException;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationContext;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationStatus;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationType;

/**
 * Shared helpers for negotiation payload and context map conversion.
 *
 * @since 2026-06
 */
public final class NegotiationPayloadMapper {

    public static Map<String, Object> payload(
            NegotiationContext context, String contentText, Map<String, Object> facts) {
        Map<String, Object> negotiationData = new LinkedHashMap<>();
        negotiationData.put("message", contentText);
        negotiationData.putAll(contextPayload(context));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(NegotiationHandler.NEGOTIATION_T_URI_NL, negotiationData);
        if (!facts.isEmpty()) {
            payload.put("facts", facts);
        }
        return payload;
    }

    public static Map<String, Object> contextPayload(NegotiationContext context) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(
                "negotiationType",
                context.negotiationType().name().toLowerCase().replace('_', '-'));
        payload.put("negotiationId", context.negotiationId());
        payload.put("round", context.round());
        payload.put("status", context.status().name().toLowerCase().replace('_', '-'));
        payload.put("extra", Map.of());
        return payload;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> extractContextMap(Map<String, Object> payload) {
        Object primary = payload.get(NegotiationHandler.NEGOTIATION_T_URI_NL);
        if (primary instanceof Map<?, ?> data) {
            return (Map<String, Object>) data;
        }
        Object legacy = payload.get(NegotiationHandler.NEGOTIATION_T_URI);
        if (legacy instanceof Map<?, ?> data) {
            return (Map<String, Object>) data;
        }
        throw new NegotiationStateException("Negotiation-T extension key not found in payload.");
    }

    public static NegotiationContext contextFromMap(Map<String, Object> contextMap) {
        String rawType = stringValue(contextMap, "negotiationType");
        String normalizedType = rawType.replace('-', '_').toUpperCase();
        String rawStatus = stringValue(contextMap, "status");
        String normalizedStatus = rawStatus.replace('-', '_').toUpperCase();
        int round = numberValue(contextMap, "round").intValue();
        if (round <= 0) {
            throw new NegotiationStateException("Negotiation round must be a positive integer.");
        }
        return new NegotiationContext(
                NegotiationType.valueOf(normalizedType),
                stringValue(contextMap, "negotiationId"),
                round,
                NegotiationStatus.valueOf(normalizedStatus));
    }

    private static String stringValue(Map<String, Object> contextMap, String key) {
        Object value = contextMap.get(key);
        if (value instanceof String text) {
            return text;
        }
        throw new NegotiationStateException("Negotiation context field must be a string: " + key);
    }

    private static Number numberValue(Map<String, Object> contextMap, String key) {
        Object value = contextMap.get(key);
        if (value instanceof Number number) {
            return number;
        }
        throw new NegotiationStateException("Negotiation context field must be a number: " + key);
    }
}
