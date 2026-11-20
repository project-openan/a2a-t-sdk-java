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

import java.util.Map;
import net.openan.a2at.sdk.core.exception.ErrorCatalog;
import org.jspecify.annotations.Nullable;

/**
 * Raised when generating a negotiation message fails at runtime.
 *
 * @since 2026-08
 */
public class NegotiationGenerationException extends NegotiationProcessingException {

    /**
     * Creates a negotiation generation failure with one error code.
     *
     * @param code machine-readable error code for the failure
     * @param message failure message
     */
    public NegotiationGenerationException(String code, String message) {
        super(code, message);
    }

    /**
     * Creates a negotiation generation failure with one error code and a root cause.
     *
     * @param code machine-readable error code for the failure
     * @param message failure message
     * @param cause root cause
     */
    public NegotiationGenerationException(String code, String message, @Nullable Throwable cause) {
        super(code, message, cause);
    }

    /**
     * Creates a negotiation generation failure whose message is rendered from the code's template in one language.
     *
     * @param entry catalog entry identifying the failure
     * @param language message language, for example {@code zh-CN}; null or blank falls back to {@code en-US}
     * @param facts fact values keyed by fact parameter name, may be null
     */
    public NegotiationGenerationException(
            ErrorCatalog entry, @Nullable String language, @Nullable Map<String, String> facts) {
        super(entry, language, facts);
    }

    /**
     * Creates a negotiation generation failure with a rendered message and a root cause.
     *
     * @param entry catalog entry identifying the failure
     * @param language message language, for example {@code zh-CN}; null or blank falls back to {@code en-US}
     * @param facts fact values keyed by fact parameter name, may be null
     * @param cause root cause
     */
    public NegotiationGenerationException(
            ErrorCatalog entry,
            @Nullable String language,
            @Nullable Map<String, String> facts,
            @Nullable Throwable cause) {
        super(entry, language, facts, cause);
    }
}
