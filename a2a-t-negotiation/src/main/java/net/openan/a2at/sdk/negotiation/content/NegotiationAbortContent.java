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
 * Content of an abort negotiation message.
 *
 * <p>Abort messages are type-independent: the single common abort template carries the fixed {@code Abort} conclusion
 * section and this content only fills the termination reason.
 *
 * @param terminationReason human-readable reason for terminating the negotiation, such as reaching the round limit,
 *     a timeout or a token budget exhaustion
 * @since 2026-08
 */
public record NegotiationAbortContent(String terminationReason) implements NegotiationContent {}
