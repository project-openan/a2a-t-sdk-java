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

package net.openan.a2at.sdk.negotiation.validation;

/**
 * Internal failure of the negotiation semantic validation step.
 *
 * <p>This exception is internal to the validation pipeline: it signals an LLM infrastructure failure or a response that
 * violates the output contract (missing required keys or wrong shapes). It must never bubble out of the public APIs;
 * the orchestration layer converts it into a parameter-extraction failure carrying the retryable LLM infrastructure
 * error code.
 *
 * @since 2026-08
 */
public class NegotiationValidationException extends RuntimeException {

    /**
     * Creates an internal semantic validation failure.
     *
     * @param message failure message
     */
    public NegotiationValidationException(String message) {
        super(message);
    }

    /**
     * Creates an internal semantic validation failure with a cause.
     *
     * @param message failure message
     * @param cause underlying failure
     */
    public NegotiationValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
