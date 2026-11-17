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

package net.openan.a2at.sdk.negotiation.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.openan.a2at.sdk.negotiation.types.model.NegotiationContext;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationContinueResult;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationReceiveResult;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationStatus;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationType;
import net.openan.a2at.sdk.negotiation.types.model.TaskPromptComplianceFailure;
import net.openan.a2at.sdk.negotiation.types.model.TaskPromptComplianceResult;
import net.openan.a2at.sdk.negotiation.types.checker.TaskPromptComplianceChecker;
import org.junit.jupiter.api.Test;

class InformationNegotiationTest {

    private final InformationNegotiation negotiationType = new InformationNegotiation();

    @Test
    void processReceivedMessageReturnsPromptAsResponseMessage() {
        NegotiationReceiveResult result = negotiationType.processReceivedMessage(
                "latest full task prompt",
                new NegotiationContext(NegotiationType.INFORMATION, "neg-info", 1, NegotiationStatus.IN_PROGRESS));

        assertTrue(result.needResponse());
        assertTrue(result.facts().isEmpty());
        assertEquals("latest full task prompt", result.message());
    }

    @Test
    void processReceivedMessageReturnsCompletionMessageWhenServerPromptCompliancePasses() {
        InformationNegotiation complianceCheckingNegotiation =
                new InformationNegotiation(processedPromptText -> TaskPromptComplianceResult.success());

        NegotiationReceiveResult result = complianceCheckingNegotiation.processReceivedMessage(
                "latest full task prompt",
                new NegotiationContext(NegotiationType.INFORMATION, "neg-info", 1, NegotiationStatus.IN_PROGRESS));

        assertTrue(result.needResponse());
        assertTrue(result.facts().isEmpty());
        assertEquals("Task prompt is complete.", result.message());
    }

    @Test
    void processReceivedMessageReturnsComplianceFailureMessageWhenServerPromptComplianceFails() {
        RecordingPromptComplianceOrchestrator complianceOrchestrator =
                new RecordingPromptComplianceOrchestrator(TaskPromptComplianceResult.failure(
                        new TaskPromptComplianceFailure("guardrail_rejected", "Guardrail rejected the task prompt.")));
        InformationNegotiation guardedNegotiation = new InformationNegotiation(complianceOrchestrator);

        NegotiationReceiveResult result = guardedNegotiation.processReceivedMessage(
                "latest full task prompt",
                new NegotiationContext(NegotiationType.INFORMATION, "neg-info", 1, NegotiationStatus.IN_PROGRESS));

        assertEquals("latest full task prompt", complianceOrchestrator.lastProcessedPromptText);
        assertTrue(result.needResponse());
        assertTrue(result.facts().isEmpty());
        assertEquals("Guardrail rejected the task prompt.", result.message());
    }

    @Test
    void renderContinueReturnsFinalTaskPromptWhenAgreed() {
        NegotiationContinueResult result = negotiationType.renderContinue(
                new NegotiationContext(NegotiationType.INFORMATION, "neg-info", 2, NegotiationStatus.AGREED),
                "final prompt");

        assertEquals("final prompt", result.promptText());
        assertEquals("final prompt", result.finalTaskPrompt());
    }

    private static final class RecordingPromptComplianceOrchestrator implements TaskPromptComplianceChecker {
        private final TaskPromptComplianceResult result;
        private String lastProcessedPromptText;

        private RecordingPromptComplianceOrchestrator(TaskPromptComplianceResult result) {
            this.result = result;
        }

        @Override
        public TaskPromptComplianceResult check(String processedPromptText) {
            this.lastProcessedPromptText = processedPromptText;
            return result;
        }
    }
}
