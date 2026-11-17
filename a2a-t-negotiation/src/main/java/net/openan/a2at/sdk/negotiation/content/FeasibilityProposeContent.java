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

package net.openan.a2at.sdk.negotiation.content;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Content of a feasibility negotiation propose message.
 *
 * @param feasibilityNegotiationDescription required summary describing the nature of the message
 * @param action action this propose message performs; selects which conditional section is rendered
 * @param contentsToEvaluate contents the counterpart should evaluate; null or empty omits the section (only for
 *     {@link NegotiationAction#REQUEST_FEASIBILITY_EVALUATION})
 * @param infeasibilityDetailsAndProposal infeasibility details and an alternative proposal; null or empty omits the
 *     section (only for {@link NegotiationAction#PROPOSE_ALTERNATIVE_ON_FAILURE})
 * @param feasibilityConfirmRequest non-blank when this round's message category is "assessed as feasible and
 *     requesting confirmation" — the third category is derived and the action stays at two values: it requires the
 *     {@link NegotiationAction#REQUEST_FEASIBILITY_EVALUATION} action with both lists above null or empty, so a
 *     confirm-request round carries only the summary and the confirm request (the fixed wording is not validated and
 *     passes through verbatim)
 * @since 2026-08
 */
public record FeasibilityProposeContent(
        String feasibilityNegotiationDescription,
        NegotiationAction action,
        @Nullable List<NegotiationItem> contentsToEvaluate,
        @Nullable List<NegotiationItem> infeasibilityDetailsAndProposal,
        @Nullable String feasibilityConfirmRequest)
        implements NegotiationProposeContent {}
