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

import net.openan.a2at.sdk.negotiation.content.NegotiationContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationGenerationException;
import net.openan.a2at.sdk.negotiation.resources.NegotiationReference;

/**
 * Extracts typed negotiation content from a free-text input.
 *
 * <p>Implementations decide how the text is understood; the returned content must already satisfy the generator input
 * rules so the deterministic rendering step can consume it directly.
 *
 * @since 2026-08
 */
public interface NegotiationContentExtractor {

    /**
     * Extracts the typed content of one negotiation message from free text.
     *
     * @param text free-text input describing the message content
     * @param reference reference identifying the negotiation type, phase and language to extract for
     * @return typed negotiation content matching the reference
     * @throws NegotiationGenerationException with code {@code llm.not_configured} when no LLM client is configured,
     *     {@code llm.invocation_failed} or {@code llm.response_invalid} when the LLM invocation fails,
     *     {@code negotiation.content_extract_failed} when the response cannot be parsed as the expected content,
     *     {@code negotiation.field_missing} when a required field is missing, {@code negotiation.conclusion_mismatch}
     *     when the extracted conclusion contradicts the addressed phase, or {@code negotiation.invalid_input} when the
     *     extracted content contradicts the addressed phase or action
     */
    NegotiationContent extract(String text, NegotiationReference reference);
}
