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

package net.openan.a2at.sample.private_line_complaint.negotiation.shared.mock;

import net.openan.a2at.sdk.llm.LLMClientFactory;

/** Installs the sample's schema-dispatching LLM implementation for offline runs. */
public final class NegotiationMockLlmInstaller {

    public static final String PROVIDER = "negotiation-sample-mock";

    private NegotiationMockLlmInstaller() {}

    public static synchronized void install() {
        if (!LLMClientFactory.availableProviders().contains(PROVIDER)) {
            LLMClientFactory.register(PROVIDER, NegotiationMockLLMClient.class);
        }
    }
}
