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

package net.openan.a2at.sdk.negotiation.generation;

import java.util.LinkedHashMap;
import java.util.Map;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.PromptTemplate;
import net.openan.a2at.sdk.negotiation.content.FeasibilityEndingContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationContent;
import net.openan.a2at.sdk.negotiation.content.Vocabulary;

/**
 * Generator for feasibility negotiation terminal messages.
 *
 * <p>The message carries the terminal conclusion literal and the required feasibility summary. The summary slot name
 * differs from its section title, so the vocabulary exception key is used.
 *
 * @since 2026-08
 */
final class FeasibilityEndingGenerator extends AbstractNegotiationGenerator {

    /**
     * Generates a feasibility negotiation accept or reject message.
     *
     * @param context negotiation context of the message
     * @param content feasibility ending content
     * @param template feasibility ending template to render
     * @param vocabulary vocabulary of the message language
     * @return rendered feasibility terminal message text
     */
    @Override
    public String generate(
            NegotiationContext context, NegotiationContent content, PromptTemplate template, Vocabulary vocabulary) {
        FeasibilityEndingContent endingContent =
                contentOf(content, FeasibilityEndingContent.class, "Feasibility ending generator");
        renderableConclusion(endingContent.conclusion());
        requiredText(
                endingContent.feasibilitySummary(),
                "content.feasibilitySummary",
                "Feasibility summary of a terminal feasibility negotiation message",
                vocabulary);
        Map<String, String> slots = new LinkedHashMap<>();
        slots.put(
                vocabulary.get("slot.feasibility_conclusion"),
                endingContent.conclusion().literal());
        slots.put(vocabulary.get("slot.feasibility_confirm"), endingContent.feasibilitySummary());
        return render(template, slots);
    }
}
