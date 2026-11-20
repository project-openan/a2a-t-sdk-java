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

package net.openan.a2at.sdk.negotiation.validation;

import net.openan.a2at.sdk.core.model.NegotiationContext;

/**
 * Rule-level compliance checker for negotiation messages.
 *
 * <p>The checker is deterministic and never calls an LLM. It validates only the strong constraints of the negotiation
 * context carried alongside the message: the id must be a UUID in 8-4-4-4-12 hexadecimal form and the round must not
 * exceed the round budget. The positive-integer shape of the round fields is already guaranteed by the
 * {@link NegotiationContext} constructor. Type inference, conclusion values, ending-section presence and
 * conditional-section exclusivity are deliberately not checked here; they belong to the semantic validation step.
 *
 * @since 2026-08
 */
public interface NegotiationComplianceChecker {

    /**
     * Runs the rule-level compliance check of one negotiation context.
     *
     * @param context negotiation context carried alongside the message in the A2A-T metadata
     * @return rule check outcome carrying the pass flag and the structured errors
     * @throws NullPointerException if the context is null
     */
    NegotiationRuleCheckResult check(NegotiationContext context);
}
