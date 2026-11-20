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

package net.openan.a2at.sdk.corpus.suites.negotiation;

import java.util.Set;
import net.openan.a2at.sdk.corpus.engine.CorpusWorkFlowSuite;

/**
 * Negotiation-T structured-input workflow suite: for every scenario directory under
 * {@code suites/negotiation/resources/}, runs the recorded SDK API flow of each case in
 * {@code input_case_from_data.json} against a real LLM (the from-data generation leg is
 * deterministic; the validation leg uses the real semantic pipeline).
 *
 * <p>Default API flow (also shown in the case files): one {@code generateNegotiation*PromptFromData} step followed
 * by its matching {@code validate*PromptAndDataFilling} step.
 *
 * <p>Filters: {@code -Dcorpus.scenario} and {@code -Dcase.filter}, see
 * {@link NegotiationTFromTextWorkFlowTest}.
 */
public final class NegotiationTFromDataWorkFlowTest extends CorpusWorkFlowSuite {

    @Override
    protected String extensionFolder() {
        return "negotiation";
    }

    @Override
    protected String inputFileName() {
        return INPUT_CASE_FROM_DATA;
    }

    @Override
    protected String flowType() {
        return FLOW_FROM_DATA;
    }

    @Override
    protected String runtimeKind() {
        return "negotiation";
    }

    @Override
    protected Set<String> apiNames() {
        return negotiationApiNames();
    }
}