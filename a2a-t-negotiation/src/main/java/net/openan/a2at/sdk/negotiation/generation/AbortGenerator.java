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
import net.openan.a2at.sdk.negotiation.content.NegotiationAbortContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationContent;
import net.openan.a2at.sdk.negotiation.content.Vocabulary;

/**
 * Generator for the type-independent abort negotiation message.
 *
 * <p>The common abort template carries the fixed {@code Abort} conclusion section, so this generator fills only the
 * negotiation context and the termination reason slots.
 *
 * @since 2026-08
 */
final class AbortGenerator extends AbstractNegotiationGenerator {

    /**
     * Generates an abort negotiation message.
     *
     * @param context negotiation context of the message
     * @param content abort content carrying the termination reason
     * @param template common abort template to render
     * @param vocabulary vocabulary of the message language
     * @return rendered abort message text
     */
    @Override
    public String generate(
            NegotiationContext context, NegotiationContent content, PromptTemplate template, Vocabulary vocabulary) {
        NegotiationAbortContent abortContent = contentOf(content, NegotiationAbortContent.class, "Abort generator");
        Map<String, String> slots = new LinkedHashMap<>();
        slots.put(
                vocabulary.get("slot.termination_reason"),
                requiredText(
                        abortContent.terminationReason(),
                        "content.terminationReason",
                        "Termination reason of an abort negotiation message",
                        vocabulary));
        return render(template, slots);
    }
}
