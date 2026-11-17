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

import net.openan.a2at.sdk.negotiation.types.checker.TaskPromptComplianceChecker;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationContext;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationContinueResult;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationReceiveResult;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationStatus;
import net.openan.a2at.sdk.negotiation.types.model.TaskPromptComplianceResult;

import java.util.Map;

/**
 * Minimal information negotiation behavior.
 *
 * @since 2026-06
 */
public final class InformationNegotiation implements Negotiation {

    private final TaskPromptComplianceChecker complianceChecker;

    /** Creates an information negotiation type without prompt-compliance checks. */
    public InformationNegotiation() {
        this(null);
    }

    /**
     * Creates an information negotiation type with server prompt-compliance checks.
     *
     * @param complianceChecker compliance checker invoked on received prompts
     */
    public InformationNegotiation(TaskPromptComplianceChecker complianceChecker) {
        this.complianceChecker = complianceChecker;
    }

    @Override
    public NegotiationReceiveResult processReceivedMessage(String message, NegotiationContext context) {
        if (complianceChecker != null) {
            TaskPromptComplianceResult result = complianceChecker.check(message);
            String responseMessage = result.passed()
                    ? "Task prompt is complete."
                    : result.failure().message();
            return new NegotiationReceiveResult(true, Map.of(), responseMessage);
        }
        return new NegotiationReceiveResult(true, Map.of(), message);
    }

    /**
     * Renders the continue payload for one information negotiation context.
     *
     * @param context current negotiation context
     * @param contentText prompt text to send back
     * @return continue result including the final task prompt when agreed
     */
    public NegotiationContinueResult renderContinue(NegotiationContext context, String contentText) {
        String finalTaskPrompt = context.status() == NegotiationStatus.AGREED ? contentText : null;
        return new NegotiationContinueResult(contentText, finalTaskPrompt);
    }
}
