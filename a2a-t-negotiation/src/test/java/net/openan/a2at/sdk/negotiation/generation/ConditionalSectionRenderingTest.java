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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.negotiation.content.FeasibilityProposeContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationAction;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.NegotiationPerformative;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;
import net.openan.a2at.sdk.negotiation.content.NegotiationProposeData;
import net.openan.a2at.sdk.negotiation.content.TargetProposeContent;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Verifies the conditional sections of the propose templates against direct (non-golden) assertions.
 *
 * <p>The feasibility propose template renders exactly one of its two conditional sections, selected by the action; the
 * target propose template renders the intent understanding section only on the first round, the alignment and
 * clarification section only on later rounds, and drops the clarification request section entirely when no
 * clarification items are present.
 */
class ConditionalSectionRenderingTest {

    private static final String UUID = "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3";

    private static final TemplateUri FEASIBILITY_PROPOSE_URI = StandardTemplates.FEASIBILITY_NEGOTIATION_PROPOSE;

    private static final TemplateUri TARGET_PROPOSE_URI = StandardTemplates.TARGET_NEGOTIATION_PROPOSE;

    static Stream<Arguments> languages() {
        return Stream.of(Arguments.of("zh-CN"), Arguments.of("en-US"));
    }

    @ParameterizedTest(name = "evaluation request renders only the evaluate section [{0}]")
    @MethodSource("languages")
    void evaluationRequestRendersOnlyTheContentsToEvaluateSection(String language) {
        NegotiationGenerationOrchestrator orchestrator = orchestrator(language);

        String promptText = orchestrator
                .generateProposeFromData(
                        new NegotiationProposeData(
                                new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE),
                                new FeasibilityProposeContent(
                                        "Please assess the adjusted rate target.",
                                        NegotiationAction.REQUEST_FEASIBILITY_EVALUATION,
                                        List.of(new NegotiationItem("adjusted target", "rate lowered to 2Mbps")),
                                        null,
                                        null)),
                        FEASIBILITY_PROPOSE_URI)
                .promptText();

        assertTrue(promptText.contains(evaluateTitle(language)));
        assertFalse(promptText.contains(infeasibleTitle(language)));
    }

    @ParameterizedTest(name = "alternative proposal renders only the infeasibility section [{0}]")
    @MethodSource("languages")
    void alternativeProposalRendersOnlyTheInfeasibilitySection(String language) {
        NegotiationGenerationOrchestrator orchestrator = orchestrator(language);

        String promptText = orchestrator
                .generateProposeFromData(
                        new NegotiationProposeData(
                                new NegotiationContext(UUID, 2, 5, NegotiationPerformative.PROPOSE),
                                new FeasibilityProposeContent(
                                        "The rate target is infeasible; a proposal follows.",
                                        NegotiationAction.PROPOSE_ALTERNATIVE_ON_FAILURE,
                                        null,
                                        List.of(new NegotiationItem(
                                                "proposal", "lower the rate guarantee target to 2Mbps")),
                                        null)),
                        FEASIBILITY_PROPOSE_URI)
                .promptText();

        assertTrue(promptText.contains(infeasibleTitle(language)));
        assertFalse(promptText.contains(evaluateTitle(language)));
    }

    @ParameterizedTest(name = "round 1 renders the intent section without the alignment section [{0}]")
    @MethodSource("languages")
    void firstRoundRendersIntentWithoutAlignment(String language) {
        NegotiationGenerationOrchestrator orchestrator = orchestrator(language);

        String promptText = orchestrator
                .generateProposeFromData(
                        new NegotiationProposeData(
                                new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE),
                                new TargetProposeContent(
                                        "Clarify the intent of the ran-energy-saving task.",
                                        List.of(new NegotiationItem("task intent", "ran-energy-saving optimization")),
                                        null,
                                        null,
                                        null)),
                        TARGET_PROPOSE_URI)
                .promptText();

        assertTrue(promptText.contains(intentTitle(language)));
        assertFalse(promptText.contains(alignmentTitle(language)));
    }

    @ParameterizedTest(name = "later rounds render the alignment section without the intent section [{0}]")
    @MethodSource("languages")
    void laterRoundsRenderAlignmentWithoutIntent(String language) {
        NegotiationGenerationOrchestrator orchestrator = orchestrator(language);

        String promptText = orchestrator
                .generateProposeFromData(
                        new NegotiationProposeData(
                                new NegotiationContext(UUID, 3, 5, NegotiationPerformative.PROPOSE),
                                new TargetProposeContent(
                                        "Clarify the intent of the ran-energy-saving task.",
                                        null,
                                        List.of(new NegotiationItem("task intent", "confirmed as correct")),
                                        null,
                                        null)),
                        TARGET_PROPOSE_URI)
                .promptText();

        assertTrue(promptText.contains(alignmentTitle(language)));
        assertFalse(promptText.contains(intentTitle(language)));
    }

    @ParameterizedTest(name = "target confirm request renders only the confirm section [{0}]")
    @MethodSource("languages")
    void targetConfirmRequestRendersOnlyTheConfirmSection(String language) {
        NegotiationGenerationOrchestrator orchestrator = orchestrator(language);

        String promptText = orchestrator
                .generateProposeFromData(
                        new NegotiationProposeData(
                                new NegotiationContext(UUID, 2, 5, NegotiationPerformative.PROPOSE),
                                new TargetProposeContent(
                                        "The clarification of the task target has been completed. Please reply to"
                                                + " <Target Clarification Confirmation Request>.",
                                        null,
                                        null,
                                        null,
                                        targetConfirmWording(language))),
                        TARGET_PROPOSE_URI)
                .promptText();

        assertTrue(promptText.contains(targetConfirmTitle(language)));
        assertTrue(promptText.contains(targetConfirmWording(language)));
        assertFalse(promptText.contains(intentTitle(language)));
        assertFalse(promptText.contains(alignmentTitle(language)));
        assertFalse(promptText.contains(clarificationTitle(language)));
    }

    @ParameterizedTest(name = "feasibility confirm request renders only the confirm section [{0}]")
    @MethodSource("languages")
    void feasibilityConfirmRequestRendersOnlyTheConfirmSection(String language) {
        NegotiationGenerationOrchestrator orchestrator = orchestrator(language);

        String promptText = orchestrator
                .generateProposeFromData(
                        new NegotiationProposeData(
                                new NegotiationContext(UUID, 2, 5, NegotiationPerformative.PROPOSE),
                                new FeasibilityProposeContent(
                                        "Regarding the adjusted rate guarantee target, the feasibility assessment has"
                                                + " been completed and the conclusion is feasible. Please reply to"
                                                + " <Feasible Evaluation Confirmation Request>.",
                                        NegotiationAction.REQUEST_FEASIBILITY_EVALUATION,
                                        null,
                                        null,
                                        feasibilityConfirmWording(language))),
                        FEASIBILITY_PROPOSE_URI)
                .promptText();

        assertTrue(promptText.contains(feasibilityConfirmTitle(language)));
        assertTrue(promptText.contains(feasibilityConfirmWording(language)));
        assertFalse(promptText.contains(evaluateTitle(language)));
        assertFalse(promptText.contains(infeasibleTitle(language)));
    }

    @ParameterizedTest(name = "empty clarification list drops the clarification section [{0}]")
    @MethodSource("languages")
    void emptyClarificationListDropsTheClarificationSection(String language) {
        NegotiationGenerationOrchestrator orchestrator = orchestrator(language);

        String withoutClarification = orchestrator
                .generateProposeFromData(
                        new NegotiationProposeData(
                                new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE),
                                new TargetProposeContent(
                                        "Confirm the understood intent of the ran-energy-saving task.",
                                        List.of(new NegotiationItem("task intent", "ran-energy-saving optimization")),
                                        null,
                                        null,
                                        null)),
                        TARGET_PROPOSE_URI)
                .promptText();
        assertFalse(withoutClarification.contains(clarificationTitle(language)));

        String withClarification = orchestrator
                .generateProposeFromData(
                        new NegotiationProposeData(
                                new NegotiationContext(UUID, 1, 5, NegotiationPerformative.PROPOSE),
                                new TargetProposeContent(
                                        "Confirm the understood intent of the ran-energy-saving task.",
                                        List.of(new NegotiationItem("task intent", "ran-energy-saving optimization")),
                                        null,
                                        List.of(new NegotiationItem("area", "which site is covered")),
                                        null)),
                        TARGET_PROPOSE_URI)
                .promptText();
        assertTrue(withClarification.contains(clarificationTitle(language)));
    }

    private static NegotiationGenerationOrchestrator orchestrator(String language) {
        return NegotiationGenerationOrchestratorBuilder.builder()
                .language(language)
                .build();
    }

    private static String evaluateTitle(String language) {
        return "zh-CN".equals(language) ? "## 待评估内容说明" : "## Under Evaluation Description";
    }

    private static String infeasibleTitle(String language) {
        return "zh-CN".equals(language) ? "## 评估不可行时的详情和提案" : "## Infeasible Evaluation Details and Proposal";
    }

    private static String intentTitle(String language) {
        return "zh-CN".equals(language) ? "## 意图理解陈述" : "## Intent Understanding Statement";
    }

    private static String alignmentTitle(String language) {
        return "zh-CN".equals(language) ? "## 理解对齐与疑问澄清" : "## Understanding Alignment and Clarification";
    }

    private static String clarificationTitle(String language) {
        return "zh-CN".equals(language) ? "## 待澄清内容" : "## Content to Clarify";
    }

    private static String targetConfirmTitle(String language) {
        return "zh-CN".equals(language) ? "## 目标澄清后的确认请求" : "## Target Clarification Confirmation Request";
    }

    private static String targetConfirmWording(String language) {
        return "zh-CN".equals(language)
                ? "目标已经澄清，是否同意按照此目标继续执行？"
                : "The target has been clarified. Do you agree to proceed with this target?";
    }

    private static String feasibilityConfirmTitle(String language) {
        return "zh-CN".equals(language) ? "## 评估可行时的确认请求" : "## Feasible Evaluation Confirmation Request";
    }

    private static String feasibilityConfirmWording(String language) {
        return "zh-CN".equals(language)
                ? "评估目标可行，是否同意按照此目标继续执行？"
                : "The target is assessed as feasible. Do you agree to proceed with this target?";
    }
}
