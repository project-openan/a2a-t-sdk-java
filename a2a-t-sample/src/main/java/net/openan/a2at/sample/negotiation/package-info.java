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

/**
 * Negotiation end-to-end demo: SPN private-line-complaint diagnosis over real a2a-java HTTP A2A.
 *
 * <p>Implements the 4-message flow from the A2A-T protocol spec (§7.3/§7.4):
 *
 * <ol>
 *   <li>client -> Task-T (params missing);
 *   <li>server -> Negotiation-T information request (missing params) -> INPUT_REQUIRED;
 *   <li>client -> Task-T (params filled) + Negotiation-T accept;
 *   <li>server -> diagnosis result artifact -> COMPLETED.
 * </ol>
 *
 * Layout:
 *
 * <ul>
 *   <li>{@code client/} - 4-message client orchestration over a2a-java RestTransport;
 *   <li>{@code server/} - AgentExecutor (Task-T validation -> negotiation request -> diagnosis) + runtime;
 *   <li>{@code shared/} - A2A metadata bridge, scenario data, extension/template URI constants.
 * </ul>
 *
 * The core path uses deterministic {@code fromData} generation with the negotiation session context carried in the
 * message metadata (no LLM key needed); optional {@code validateAndFilling} runs when a real LLM key is configured.
 *
 * @since 2026-08
 */
package net.openan.a2at.sample.negotiation;
