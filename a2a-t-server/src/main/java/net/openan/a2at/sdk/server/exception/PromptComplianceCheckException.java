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

package net.openan.a2at.sdk.server.exception;

import java.util.Map;
import net.openan.a2at.sdk.core.exception.A2ATBusinessException;
import net.openan.a2at.sdk.core.exception.A2ATError;
import net.openan.a2at.sdk.core.exception.ErrorCatalog;
import net.openan.a2at.sdk.core.exception.ErrorMessages;
import org.jspecify.annotations.Nullable;

/**
 * Compliance-check failure carrying a stable error code and the compliance stage where the failure occurred.
 *
 * <p>Part of the {@link A2ATBusinessException} tree: callers can catch the {@link A2ATError} root to handle any A2A-T
 * processing failure and dispatch on its machine-readable error code. Messages are rendered from the code's message
 * template via {@link ErrorMessages}; the fact values that produced the message travel in {@link #getFacts()}.
 *
 * @since 2026-06
 */
public final class PromptComplianceCheckException extends A2ATBusinessException {

    private final String stage;

    /**
     * Creates a standardized compliance-check exception.
     *
     * @param code stable error code
     * @param message human-readable failure message
     * @param stage compliance stage where the failure occurred
     */
    public PromptComplianceCheckException(String code, String message, String stage) {
        super(code, message);
        this.stage = stage;
    }

    /**
     * Creates a standardized compliance-check exception whose message is rendered from the code's template.
     *
     * @param entry catalog entry identifying the failure
     * @param language message language, for example {@code zh-CN}; null or blank falls back to {@code en-US}
     * @param facts fact values keyed by fact parameter name, may be null
     * @param stage compliance stage where the failure occurred
     */
    public PromptComplianceCheckException(
            ErrorCatalog entry, @Nullable String language, @Nullable Map<String, String> facts, String stage) {
        super(entry, ErrorMessages.render(entry, language, facts), facts);
        this.stage = stage;
    }

    /**
     * Returns the compliance stage where the failure occurred.
     *
     * @return failure stage
     */
    public String getStage() {
        return stage;
    }
}
