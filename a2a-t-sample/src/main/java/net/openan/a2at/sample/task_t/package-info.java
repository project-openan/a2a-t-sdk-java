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
 * Self-contained {@code Task-T} accuracy demonstration sample for the A2A-T Java SDK.
 *
 * <p>The demo closes the evaluation loop between the client facade ({@code A2ATClient}) and the server facade
 * ({@code A2ATServer}) over the {@code Task-T/network-layer/private-line-complaint/v1} template: each sample generates
 * a structured prompt with one of the two client APIs, the server validates the prompt and re-extracts the parameters,
 * and the extracted values are compared against the sample ground truth to compute field-level accuracy and
 * sample-level pass rates.
 *
 * @since 2026-08
 */
package net.openan.a2at.sample.task_t;
