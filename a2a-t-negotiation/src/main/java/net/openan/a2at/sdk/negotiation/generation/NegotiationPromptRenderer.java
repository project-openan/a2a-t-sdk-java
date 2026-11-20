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

import java.util.Map;
import net.openan.a2at.sdk.prompt.taskrendering.DropBlankSlotSectionRenderer;
import net.openan.a2at.sdk.prompt.taskrendering.SectionedTemplateRenderer;

/**
 * Renders a negotiation template by filling slot sections and dropping empty ones.
 *
 * <p>A template is split into sections on {@code ## } title lines; any content before the first title, including a
 * leading HTML description comment, is discarded. A section whose first non-empty body line is a slot placeholder line
 * such as {@code {{required_information_items}} (required)} — or the same shape with the full-width required/optional
 * markers used by zh-CN templates — is a slot section: it is rendered as the title followed by the slot value, or
 * dropped entirely when the slot value is null or blank. Every other section is static and passes through with
 * placeholder substitution applied. Rendered sections are joined with a single blank line and the result carries no
 * trailing newline.
 *
 * <p>The rendering itself delegates to the shared {@link DropBlankSlotSectionRenderer} of the prompt kernel, which
 * owns the drop policy of the sectioned template grammar. Unlike the interface default, this adapter rejects a
 * null template text with the internal {@link NegotiationRenderException} instead of a {@code NullPointerException},
 * so the orchestration layer wraps the failure into a typed negotiation generation failure rather than letting it leak.
 *
 * @since 2026-08
 */
class NegotiationPromptRenderer implements SectionedTemplateRenderer {

    private final DropBlankSlotSectionRenderer delegate = new DropBlankSlotSectionRenderer();

    /**
     * Renders one template text with the given slot values.
     *
     * @param templateText full template text whose slot sections are filled or dropped
     * @param slots slot values keyed by the language-specific slot name; a null or blank value drops the slot section
     * @return rendered message text with sections joined by one blank line and no trailing newline; empty string when
     *     no section remains
     * @throws NegotiationRenderException if the template text is null; the orchestration layer wraps this
     *     internal failure into a typed negotiation generation failure instead of letting it leak
     */
    @Override
    public String render(String templateText, Map<String, String> slots) {
        if (templateText == null) {
            throw new NegotiationRenderException("Negotiation template text must not be null.");
        }
        return delegate.render(templateText, slots);
    }
}
