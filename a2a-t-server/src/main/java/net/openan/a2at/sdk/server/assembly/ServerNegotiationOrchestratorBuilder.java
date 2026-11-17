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

package net.openan.a2at.sdk.server.assembly;

import net.openan.a2at.sdk.negotiation.runtime.NegotiationHandler;
import net.openan.a2at.sdk.negotiation.runtime.RoleBoundNegotiationOrchestrator;
import net.openan.a2at.sdk.negotiation.store.impl.InMemoryNegotiationStore;
import net.openan.a2at.sdk.negotiation.store.NegotiationStore;
import net.openan.a2at.sdk.negotiation.handler.FeasibilityNegotiation;
import net.openan.a2at.sdk.negotiation.handler.InformationNegotiation;
import net.openan.a2at.sdk.negotiation.handler.TargetNegotiation;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationRole;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationType;
import net.openan.a2at.sdk.negotiation.types.model.TaskPromptComplianceFailure;
import net.openan.a2at.sdk.negotiation.types.model.TaskPromptComplianceResult;
import net.openan.a2at.sdk.server.model.PromptComplianceResult;
import net.openan.a2at.sdk.server.compliance.ServerPromptComplianceOrchestrator;

/**
 * Builder for server-side negotiation orchestration with compliance-aware information handling.
 *
 * @since 2026-06
 */
public final class ServerNegotiationOrchestratorBuilder {

    private ServerPromptComplianceOrchestrator promptComplianceOrchestrator;

    private NegotiationStore store;

    /**
     * Configures the prompt-compliance orchestrator used by information negotiation.
     *
     * @param promptComplianceOrchestrator compliance orchestrator
     * @return current builder
     */
    public ServerNegotiationOrchestratorBuilder promptComplianceOrchestrator(
            ServerPromptComplianceOrchestrator promptComplianceOrchestrator) {
        this.promptComplianceOrchestrator = promptComplianceOrchestrator;
        return this;
    }

    /**
     * Configures the negotiation persistence store.
     *
     * @param store negotiation store
     * @return current builder
     */
    public ServerNegotiationOrchestratorBuilder store(NegotiationStore store) {
        this.store = store;
        return this;
    }

    /**
     * Builds the server-side role-bound negotiation orchestrator.
     *
     * @return server-side role-bound negotiation orchestrator
     */
    public RoleBoundNegotiationOrchestrator build() {
        if (promptComplianceOrchestrator == null) {
            throw new IllegalStateException("Server prompt compliance orchestrator must be configured.");
        }
        NegotiationStore effectiveStore = store == null ? new InMemoryNegotiationStore() : store;
        NegotiationHandler handler = NegotiationHandler.builder()
                .store(effectiveStore)
                .register(NegotiationType.INFORMATION, new InformationNegotiation(processedPromptText -> {
                    PromptComplianceResult result = promptComplianceOrchestrator.checkTaskPrompt(processedPromptText);
                    return result.success()
                            ? TaskPromptComplianceResult.success()
                            : TaskPromptComplianceResult.failure(new TaskPromptComplianceFailure(
                                    result.failure().code(), result.failure().message()));
                }))
                .register(NegotiationType.TARGET, new TargetNegotiation())
                .register(NegotiationType.FEASIBILITY, new FeasibilityNegotiation())
                .build();
        return new RoleBoundNegotiationOrchestrator(handler, NegotiationRole.SERVER);
    }
}
