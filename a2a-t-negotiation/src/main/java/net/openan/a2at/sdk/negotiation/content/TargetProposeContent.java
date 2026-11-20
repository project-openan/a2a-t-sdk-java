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
 * Content of a target negotiation propose message.
 *
 * @param targetNegotiationDescription required summary of what the target negotiation is about
 * @param intentUnderstanding restatement of the counterpart's intent; null or empty omits the section (first round
 *     only)
 * @param alignmentAndClarification alignment statements and clarifications; null or empty omits the section (later
 *     rounds only)
 * @param requestForClarification open clarification requests; null or empty omits the section
 * @param targetConfirmRequest non-blank when this round's message category is "target clarified and requesting
 *     confirmation from the counterpart"; the three conditional lists above must then all be null or empty, because a
 *     confirm-request round carries only the summary and the confirm request (the fixed wording is not validated and
 *     passes through verbatim)
 * @since 2026-08
 */
public record TargetProposeContent(
        String targetNegotiationDescription,
        @Nullable List<NegotiationItem> intentUnderstanding,
        @Nullable List<NegotiationItem> alignmentAndClarification,
        @Nullable List<NegotiationItem> requestForClarification,
        @Nullable String targetConfirmRequest)
        implements NegotiationProposeContent {}
