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

import io.opentelemetry.api.metrics.LongHistogram;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Lazy histogram cache keyed by metric name (shared meter scope a2at-observability/1.0.0). */
final class MetricHistograms {

    private static final ConcurrentMap<String, LongHistogram> INSTRUMENTS = new ConcurrentHashMap<>();

    private MetricHistograms() {}

    static LongHistogram histogram(String name, String unit, String description) {
        return INSTRUMENTS.computeIfAbsent(name, metricName -> ObservabilityRuntime.meter()
                .histogramBuilder(metricName)
                .setUnit(unit)
                .setDescription(description)
                .ofLongs()
                .build());
    }
}
