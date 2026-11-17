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

/**
 * Content of a target negotiation terminal message.
 *
 * <p>Exactly one of {@code confirmedIntent} and {@code failureReason} is meaningful, selected by the conclusion: an
 * accept message must carry the confirmed intent, a reject message must carry the failure reason.
 *
 * @param conclusion terminal conclusion of the target negotiation
 * @param confirmedIntent confirmed intent for an accept conclusion; null otherwise
 * @param failureReason reason for a reject conclusion; null otherwise
 * @since 2026-08
 */
public record TargetEndingContent(NegotiationConclusion conclusion, String confirmedIntent, String failureReason)
        implements NegotiationEndingContent {}
