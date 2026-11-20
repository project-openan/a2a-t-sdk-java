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
import net.openan.a2at.sdk.negotiation.content.NegotiationConclusion;
import net.openan.a2at.sdk.negotiation.content.NegotiationContent;
import net.openan.a2at.sdk.negotiation.content.TargetEndingContent;
import net.openan.a2at.sdk.negotiation.content.Vocabulary;

/**
 * Generator for target negotiation terminal messages.
 *
 * <p>The result content slot carries the confirmed intent for an accept conclusion or the failure reason for a reject
 * conclusion; the other field is ignored.
 *
 * @since 2026-08
 */
final class TargetEndingGenerator extends AbstractNegotiationGenerator {

    /**
     * Generates a target negotiation accept or reject message.
     *
     * @param context negotiation context of the message
     * @param content target ending content
     * @param template target ending template to render
     * @param vocabulary vocabulary of the message language
     * @return rendered target terminal message text
     */
    @Override
    public String generate(
            NegotiationContext context, NegotiationContent content, PromptTemplate template, Vocabulary vocabulary) {
        TargetEndingContent endingContent = contentOf(content, TargetEndingContent.class, "Target ending generator");
        NegotiationConclusion conclusion = renderableConclusion(endingContent.conclusion());
        Map<String, String> slots = new LinkedHashMap<>();
        slots.put(vocabulary.get("slot.target_conclusion"), conclusion.literal());
        slots.put(
                vocabulary.get("slot.target_result_content"),
                resultContentSlotValue(endingContent, conclusion, vocabulary));
        return render(template, slots);
    }

    private static String resultContentSlotValue(
            TargetEndingContent content, NegotiationConclusion conclusion, Vocabulary vocabulary) {
        if (conclusion == NegotiationConclusion.ACCEPT) {
            return requiredText(
                    content.confirmedIntent(),
                    "content.confirmedIntent",
                    "Confirmed intent of an accepting target negotiation message",
                    vocabulary);
        }
        return requiredText(
                content.failureReason(),
                "content.failureReason",
                "Failure reason of a rejecting target negotiation" + " message",
                vocabulary);
    }
}
