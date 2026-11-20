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

package net.openan.a2at.sdk.negotiation.runtime;

import java.util.Map;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationContext;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationRole;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationStatus;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationType;

/**
 * Role-bound negotiation orchestrator shared by client and server facades.
 *
 * @since 2026-06
 */
public final class RoleBoundNegotiationOrchestrator {

    private final NegotiationHandler handler;

    private final NegotiationRole role;

    /**
     * Creates a role-bound negotiation orchestrator.
     *
     * @param handler shared negotiation handler facade
     * @param role local role that this orchestrator represents
     */
    public RoleBoundNegotiationOrchestrator(NegotiationHandler handler, NegotiationRole role) {
        this.handler = handler;
        this.role = role;
    }

    /**
     * Starts a negotiation for the orchestrator's bound role.
     *
     * @param type negotiation type to initiate
     * @param contentText human-readable negotiation message
     * @param facts structured facts attached to the payload
     * @return transport payload for the initial turn
     */
    public Map<String, Object> startNegotiation(NegotiationType type, String contentText, Map<String, Object> facts) {
        return handler.start(role, type, contentText, facts);
    }

    /**
     * Processes a received negotiation message.
     *
     * @param message received negotiation message
     * @param context transport context payload
     * @return normalized receive payload
     */
    public Map<String, Object> receiveNegotiation(String message, Map<String, Object> context) {
        return handler.receive(message, context);
    }

    /**
     * Continues a negotiation using one local context snapshot.
     *
     * @param context current negotiation context
     * @param status next status to emit
     * @param contentText message content for the next turn
     * @return transport payload for the next turn
     */
    public Map<String, Object> continueNegotiation(
            NegotiationContext context, NegotiationStatus status, String contentText) {
        return handler.continueNegotiation(context, status, contentText);
    }
}
