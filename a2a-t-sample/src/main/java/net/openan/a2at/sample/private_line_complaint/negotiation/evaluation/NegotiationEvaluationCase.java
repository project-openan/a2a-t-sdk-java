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

package net.openan.a2at.sample.private_line_complaint.negotiation.evaluation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;

/**
 * One manually labelled evaluation case.
 *
 * <p>Each case carries two parallel generation inputs for the same negotiation content: {@code text} drives the
 * fromText channel (natural language, LLM extraction) and {@code data} drives the fromData channel (typed records,
 * deterministic rendering). Both channels must produce the same {@code completedPrompt}, and the validation APIs
 * extract back the {@code expected} values in both cases.
 */
public record NegotiationEvaluationCase(
        String id,
        String phase,
        String category,
        String text,
        Map<String, Object> data,
        String completedPrompt,
        Map<String, Object> expected) {

    /** Structured items for the fromData generation channel, in prompt-rendering order. */
    public List<NegotiationItem> dataItems() {
        List<NegotiationItem> items = new ArrayList<>();
        Object raw = data == null ? null : data.get("items");
        if (raw instanceof List<?> entries) {
            for (Object entry : entries) {
                if (entry instanceof Map<?, ?> item) {
                    items.add(new NegotiationItem(String.valueOf(item.get("name")), String.valueOf(item.get("value"))));
                }
            }
        }
        return items;
    }

    /** Relationship among the data items, or null when the message carries none. */
    public String dataRelationship() {
        Object relationship = data == null ? null : data.get("relationship");
        return relationship == null ? null : String.valueOf(relationship);
    }
}
