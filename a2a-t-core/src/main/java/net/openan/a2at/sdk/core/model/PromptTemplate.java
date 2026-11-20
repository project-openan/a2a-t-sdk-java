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

package net.openan.a2at.sdk.core.model;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * One loadable prompt template of any A2A-T extension.
 *
 * @param templateUri typed template URI such as {@code Task-T/network-layer/ran-energy-saving/v1} or
 *     {@code Negotiation-T/information-negotiation/propose/v1}
 * @param description template description taken from the leading HTML comment; empty string when the template has no
 *     comment
 * @param content full template file text; null when the template content is unavailable
 * @param source effective origin of the loaded template, either {@code classpath} or {@code local}
 * @since 2026-08
 */
public record PromptTemplate(
        @NonNull TemplateUri templateUri,
        @NonNull String description,
        @Nullable String content,
        @NonNull String source) {

    /** Source marker for templates loaded from the built-in classpath resources. */
    public static final String SOURCE_CLASSPATH = "classpath";

    /** Source marker for templates loaded from the configured local root. */
    public static final String SOURCE_LOCAL = "local";
}
