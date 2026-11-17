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
 * Marker for the typed content of any negotiation message, regardless of phase.
 *
 * <p>The concrete families are {@link NegotiationProposeContent} for propose-phase messages,
 * {@link NegotiationEndingContent} for terminal accept/reject messages and {@link NegotiationAbortContent} for the
 * type-independent abort message. Generators and content extractors accept this common supertype and dispatch on the
 * exact runtime type.
 *
 * @since 2026-08
 */
public sealed interface NegotiationContent permits NegotiationProposeContent, NegotiationEndingContent, NegotiationAbortContent {}
