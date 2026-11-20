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

package net.openan.a2at.sample.task_t;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Guards the packaged sample file {@code private-line-complaint-samples.json}: sample counts per case, the contract
 * {@code enum} on the scenario fields of both schemas, and the optional-slot observation samples (which must not expect
 * the optional time/serial fields).
 */
class TaskTPrivateLineComplaintSamplesTest {

    @Test
    void should_Load14AccuracySamples_7TextAnd7Data() {
        assertEquals(7, TaskTPrivateLineComplaintSamples.textSamples().size());
        assertEquals(7, TaskTPrivateLineComplaintSamples.dataWithSchemaSamples().size());
    }

    @Test
    void should_Load8RejectionSamples() {
        assertEquals(8, TaskTPrivateLineComplaintSamples.rejectionSamples().size());
    }

    @Test
    void should_ContractScenarioEnum_OnBothSchemas() {
        Map<String, Object> semantics =
                TaskTPrivateLineComplaintSamples.dataWithSchemaSamples().get(0).semanticsSchema();
        Map<String, Object> validation =
                TaskTPrivateLineComplaintSamples.dataWithSchemaSamples().get(0).validationSchema();

        assertEquals(List.of("专线中断", "专线质差"), enumOf(semantics, "complaintScenario"));
        assertEquals(List.of("专线中断", "专线质差"), enumOf(validation, "bizScenario"));
    }

    @Test
    void should_OptionalSlotObservationSamples_NotExpectTimeOrSerial() {
        TaskTSample text = sampleByName(TaskTPrivateLineComplaintSamples.textSamples(), "text-optional-slots-missing");
        TaskTSample data =
                sampleByName(TaskTPrivateLineComplaintSamples.dataWithSchemaSamples(), "data-optional-slots-missing");

        assertFalse(text.expectedParams().containsKey(TaskTPrivateLineComplaintSamples.SERVER_TIME));
        assertFalse(text.expectedParams().containsKey(TaskTPrivateLineComplaintSamples.SERVER_TICKET));
        assertFalse(data.expectedParams().containsKey(TaskTPrivateLineComplaintSamples.SERVER_TIME));
        assertFalse(data.expectedParams().containsKey(TaskTPrivateLineComplaintSamples.SERVER_TICKET));
        assertTrue(text.expectedParams().containsKey(TaskTPrivateLineComplaintSamples.SERVER_PORT));
        assertTrue(data.expectedParams().containsKey(TaskTPrivateLineComplaintSamples.SERVER_PORT));
    }

    private static List<?> enumOf(Map<String, Object> schema, String property) {
        Map<?, ?> properties = (Map<?, ?>) schema.get("properties");
        Map<?, ?> field = (Map<?, ?>) properties.get(property);
        return (List<?>) field.get("enum");
    }

    private static TaskTSample sampleByName(List<TaskTSample> samples, String name) {
        return samples.stream()
                .filter(sample -> sample.name().equals(name))
                .findFirst()
                .orElse(null);
    }
}
