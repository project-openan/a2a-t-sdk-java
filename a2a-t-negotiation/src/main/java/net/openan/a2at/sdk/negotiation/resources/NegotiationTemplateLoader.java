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

package net.openan.a2at.sdk.negotiation.resources;

import net.openan.a2at.sdk.core.model.PromptTemplate;

/**
 * Loads negotiation templates addressed by {@link NegotiationReference} keys.
 *
 * @since 2026-08
 */
public interface NegotiationTemplateLoader {

    /**
     * Loads one negotiation template.
     *
     * @param reference template addressing key, including the language to load
     * @return loaded template with its URI, description and full content
     * @throws net.openan.a2at.sdk.core.exception.ResourceNotFoundException if the template does not exist in the
     *     built-in classpath resources
     */
    PromptTemplate load(NegotiationReference reference);
}
