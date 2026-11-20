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

import io.opentelemetry.api.trace.Span;
import org.jspecify.annotations.Nullable;

/** Accessor for the active A2A-T span. */
public final class CurrentSpans {

    private CurrentSpans() {}

    public static @Nullable A2ATCurrentSpan current() {
        Span span = Span.current();
        if (!span.getSpanContext().isValid() || !span.isRecording()) {
            return null;
        }
        return new A2ATCurrentSpan(span);
    }
}
