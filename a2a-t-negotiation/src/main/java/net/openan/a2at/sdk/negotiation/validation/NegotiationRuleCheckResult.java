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

import java.util.List;
import net.openan.a2at.sdk.core.model.SlotValidationError;

/**
 * Outcome of the rule-level compliance check of a negotiation message.
 *
 * <p>The checker only validates the negotiation context carried alongside the message, so this result carries exactly
 * two components: the overall pass flag and the structured rule errors. Nothing else is inferred or checked here.
 *
 * @param passed {@code true} only when the negotiation context satisfies every context rule
 * @param errors structured rule errors of the negotiation context; empty when every rule passes
 * @since 2026-08
 */
public record NegotiationRuleCheckResult(boolean passed, List<SlotValidationError> errors) {

    /**
     * Normalizes the error list.
     *
     * @throws NullPointerException if the errors list is null
     */
    public NegotiationRuleCheckResult {
        errors = List.copyOf(errors);
    }
}
