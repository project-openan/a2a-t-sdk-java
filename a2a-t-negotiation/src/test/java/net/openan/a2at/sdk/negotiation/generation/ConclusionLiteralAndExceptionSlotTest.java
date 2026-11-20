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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.negotiation.content.FeasibilityEndingContent;
import net.openan.a2at.sdk.negotiation.content.InformationEndingContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationConclusion;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.NegotiationPerformative;
import net.openan.a2at.sdk.negotiation.content.NegotiationEndingData;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;
import net.openan.a2at.sdk.negotiation.content.TargetEndingContent;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Verifies the conclusion literals and the feasibility summary exception slot of the terminal messages.
 *
 * <p>The conclusion slot of every terminal message renders the fixed literal {@code Accept} or {@code Reject}. The
 * feasibility terminal message renders its summary under the section title of the vocabulary while the underlying slot
 * name is the vocabulary exception: the section appears under its own title, never under the slot name, which proves
 * the exception mapping is wired correctly (a missing exception mapping would silently drop the whole section).
 */
class ConclusionLiteralAndExceptionSlotTest {

    private static final String UUID = "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3";

    static Stream<Arguments> languages() {
        return Stream.of(Arguments.of("zh-CN"), Arguments.of("en-US"));
    }

    @ParameterizedTest(name = "accept conclusion renders the Accept literal [{0}]")
    @MethodSource("languages")
    void acceptConclusionRendersTheAcceptLiteral(String language) {
        NegotiationGenerationOrchestrator orchestrator = orchestrator(language);
        NegotiationContext context = new NegotiationContext(UUID, 2, 5, NegotiationPerformative.ACCEPT);

        String information = orchestrator
                .generateAcceptFromData(
                        new NegotiationEndingData(
                                context,
                                new InformationEndingContent(
                                        NegotiationConclusion.ACCEPT,
                                        List.of(new NegotiationItem("area information", "Songshan Lake")))),
                        StandardTemplates.INFORMATION_NEGOTIATION_ACCEPT_REJECT)
                .promptText();
        String target = orchestrator
                .generateAcceptFromData(
                        new NegotiationEndingData(
                                context,
                                new TargetEndingContent(
                                        NegotiationConclusion.ACCEPT, "The confirmed intent is recorded.", null)),
                        StandardTemplates.TARGET_NEGOTIATION_ACCEPT_REJECT)
                .promptText();
        String feasibility = orchestrator
                .generateAcceptFromData(
                        new NegotiationEndingData(
                                context,
                                new FeasibilityEndingContent(
                                        NegotiationConclusion.ACCEPT, "The target is achievable.")),
                        StandardTemplates.FEASIBILITY_NEGOTIATION_ACCEPT_REJECT)
                .promptText();

        assertTrue(information.contains(conclusionSection(language, "information") + "\nAccept"));
        assertTrue(target.contains(conclusionSection(language, "target") + "\nAccept"));
        assertTrue(feasibility.contains(conclusionSection(language, "feasibility") + "\nAccept"));
        assertFalse(information.contains("\nReject"));
        assertFalse(target.contains("\nReject"));
        assertFalse(feasibility.contains("\nReject"));
    }

    @ParameterizedTest(name = "reject conclusion renders the Reject literal [{0}]")
    @MethodSource("languages")
    void rejectConclusionRendersTheRejectLiteral(String language) {
        NegotiationGenerationOrchestrator orchestrator = orchestrator(language);
        NegotiationContext context = new NegotiationContext(UUID, 2, 5, NegotiationPerformative.REJECT);

        String information = orchestrator
                .generateRejectFromData(
                        new NegotiationEndingData(
                                context,
                                new InformationEndingContent(
                                        NegotiationConclusion.REJECT,
                                        List.of(new NegotiationItem("area information", "not available")))),
                        StandardTemplates.INFORMATION_NEGOTIATION_ACCEPT_REJECT)
                .promptText();
        String target = orchestrator
                .generateRejectFromData(
                        new NegotiationEndingData(
                                context,
                                new TargetEndingContent(
                                        NegotiationConclusion.REJECT, null, "The intent cannot be clarified.")),
                        StandardTemplates.TARGET_NEGOTIATION_ACCEPT_REJECT)
                .promptText();
        String feasibility = orchestrator
                .generateRejectFromData(
                        new NegotiationEndingData(
                                context,
                                new FeasibilityEndingContent(
                                        NegotiationConclusion.REJECT, "The target is not achievable.")),
                        StandardTemplates.FEASIBILITY_NEGOTIATION_ACCEPT_REJECT)
                .promptText();

        assertTrue(information.contains(conclusionSection(language, "information") + "\nReject"));
        assertTrue(target.contains(conclusionSection(language, "target") + "\nReject"));
        assertTrue(feasibility.contains(conclusionSection(language, "feasibility") + "\nReject"));
    }

    /**
     * Locks the T2 exception slot: the summary appears as the body of the section titled with the section vocabulary
     * key, while a section titled with the raw slot name must never appear.
     */
    @ParameterizedTest(name = "feasibility summary uses the exception slot under its own section title [{0}]")
    @MethodSource("languages")
    void feasibilitySummaryUsesTheExceptionSlotUnderItsOwnSectionTitle(String language) {
        NegotiationGenerationOrchestrator orchestrator = orchestrator(language);
        String summary = "The adjusted target is achievable; this negotiation is confirmed as concluded.";

        String acceptText = orchestrator
                .generateAcceptFromData(
                        new NegotiationEndingData(
                                new NegotiationContext(UUID, 2, 5, NegotiationPerformative.ACCEPT),
                                new FeasibilityEndingContent(NegotiationConclusion.ACCEPT, summary)),
                        StandardTemplates.FEASIBILITY_NEGOTIATION_ACCEPT_REJECT)
                .promptText();
        String rejectText = orchestrator
                .generateRejectFromData(
                        new NegotiationEndingData(
                                new NegotiationContext(UUID, 2, 5, NegotiationPerformative.REJECT),
                                new FeasibilityEndingContent(NegotiationConclusion.REJECT, summary)),
                        StandardTemplates.FEASIBILITY_NEGOTIATION_ACCEPT_REJECT)
                .promptText();

        String sectionTitle =
                "zh-CN".equals(language) ? "## 可行性评估结果确认" : "## Feasibility Assessment Result Confirmation";
        String rawSlotNameLine = "zh-CN".equals(language) ? "## 评估结果确认\n" : "## evaluation_result_confirmation\n";
        for (String promptText : List.of(acceptText, rejectText)) {
            assertTrue(promptText.contains(sectionTitle + "\n" + summary));
            assertFalse(promptText.contains(rawSlotNameLine), "the raw slot name must not surface as a section title");
        }
    }

    /** The summary itself is never mangled: the section carries it verbatim as its only body line. */
    @ParameterizedTest(name = "feasibility summary is carried verbatim [{0}]")
    @MethodSource("languages")
    void feasibilitySummaryIsCarriedVerbatim(String language) {
        NegotiationGenerationOrchestrator orchestrator = orchestrator(language);
        String summary = "The adjusted target is achievable; this negotiation is confirmed as concluded.";

        String promptText = orchestrator
                .generateAcceptFromData(
                        new NegotiationEndingData(
                                new NegotiationContext(UUID, 2, 5, NegotiationPerformative.ACCEPT),
                                new FeasibilityEndingContent(NegotiationConclusion.ACCEPT, summary)),
                        StandardTemplates.FEASIBILITY_NEGOTIATION_ACCEPT_REJECT)
                .promptText();

        assertEquals(1, countOccurrences(promptText, summary));
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static NegotiationGenerationOrchestrator orchestrator(String language) {
        return NegotiationGenerationOrchestratorBuilder.builder()
                .language(language)
                .build();
    }

    private static String conclusionSection(String language, String type) {
        if ("zh-CN".equals(language)) {
            return switch (type) {
                case "information" -> "## 信息协商结果";
                case "target" -> "## 目标协商结果";
                default -> "## 可行性协商结果";
            };
        }
        return switch (type) {
            case "information" -> "## Information Negotiation Result";
            case "target" -> "## Target Negotiation Result";
            default -> "## Feasibility Negotiation Result";
        };
    }
}
