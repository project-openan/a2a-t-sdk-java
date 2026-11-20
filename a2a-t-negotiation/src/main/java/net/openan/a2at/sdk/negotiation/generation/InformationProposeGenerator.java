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
import net.openan.a2at.sdk.negotiation.content.InformationProposeContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationContent;
import net.openan.a2at.sdk.negotiation.content.Vocabulary;

/**
 * Generator for information negotiation propose messages.
 *
 * <p>The message carries the requested information items and, when present, a free-form line describing how the missing
 * items relate to each other, appended after the item list.
 *
 * @since 2026-08
 */
final class InformationProposeGenerator extends AbstractNegotiationGenerator {

    /**
     * Generates an information negotiation propose message.
     *
     * @param context negotiation context of the message
     * @param content information propose content
     * @param template information propose template to render
     * @param vocabulary vocabulary of the message language
     * @return rendered information propose message text
     */
    @Override
    public String generate(
            NegotiationContext context, NegotiationContent content, PromptTemplate template, Vocabulary vocabulary) {
        InformationProposeContent proposeContent =
                contentOf(content, InformationProposeContent.class, "Information propose generator");
        requiredItems(
                proposeContent.items(), "items", "Information negotiation propose message requested items", vocabulary);
        Map<String, String> slots = new LinkedHashMap<>();
        slots.put(vocabulary.get("slot.info_items"), itemsSlotValue(proposeContent, vocabulary));
        return render(template, slots);
    }

    private String itemsSlotValue(InformationProposeContent content, Vocabulary vocabulary) {
        String items = formatItems(content.items(), vocabulary);
        if (content.relationship() == null || content.relationship().isBlank()) {
            return items;
        }
        String relationshipLine = vocabulary.get("label.relationship") + content.relationship();
        return items.isEmpty() ? relationshipLine : items + "\n" + relationshipLine;
    }
}
