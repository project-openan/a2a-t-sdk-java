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

package net.openan.a2at.sdk.observability;

import java.util.function.UnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Shared payload preparation: redact first, then truncate (security order). */
public final class PayloadSanitizer {

    private static final Logger LOG = LoggerFactory.getLogger(PayloadSanitizer.class);

    private PayloadSanitizer() {}

    public static String prepare(String payload, A2ATObservabilityConfig config) {
        String value = payload;
        UnaryOperator<String> redactor = config.payloadRedactor();
        if (redactor != null) {
            try {
                value = redactor.apply(value);
            } catch (RuntimeException e) {
                LOG.warn("a2at: payload redactor failed; using unredacted value", e);
            }
        }
        int max = config.payloadLogMaxLength();
        if (value.length() > max) {
            return value.substring(0, max) + "[truncated]";
        }
        return value;
    }
}
