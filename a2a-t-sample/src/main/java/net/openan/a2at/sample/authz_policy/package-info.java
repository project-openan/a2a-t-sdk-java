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
 * Authorization-T demo: configuration-driven prompt generation and compliance verification.
 *
 * <p>The demo loads scenarios from a JSON configuration file, generates authorization prompts through the
 * {@code A2ATClient} facade, validates them through the {@code A2ATServer} facade, and reports
 * {@code match}/{@code mismatch} results with a non-zero exit code when any scenario does not match its expected
 * outcome.
 *
 * <p>Layout:
 *
 * <ul>
 *   <li>{@code AuthzPromptGenerator} - functional interface for prompt generation;
 *   <li>{@code AuthzPromptValidator} - functional interface for prompt validation;
 *   <li>{@code AuthzScenario} - immutable scenario configuration record;
 *   <li>{@code AuthzScenarioLoader} - JSON scenario file parser and validator;
 *   <li>{@code AuthzScenarioRunner} - scenario execution engine with injectable generator/validator;
 *   <li>{@code AuthzScenarioExecutor} - concurrent scenario executor with progress callback;
 *   <li>{@code AuthzReasoningCapture} - LLM reasoning capture apparatus;
 *   <li>{@code AuthzSampleMain} - entry point that wires everything together.
 * </ul>
 *
 * @since 2026-08
 */
@org.jspecify.annotations.NullUnmarked
package net.openan.a2at.sample.authz_policy;
