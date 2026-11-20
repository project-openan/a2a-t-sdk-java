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

package net.openan.a2at.sdk.corpus.suites.task;

import java.util.Set;
import net.openan.a2at.sdk.corpus.engine.CorpusWorkFlowSuite;

/**
 * Task-T natural-language workflow suite: for every scenario directory under
 * {@code suites/task/resources/}, runs the recorded SDK API flow of each case in
 * {@code input_case_from_text.json} against a real LLM.
 *
 * <p>Default API flow (also shown in the case files): {@code generateTaskPromptFromText} →
 * {@code validateTaskPromptAndDataFilling}.
 *
 * <p>Filters: {@code -Dcorpus.scenario=private-line-complaint} (glob, comma-separated) and
 * {@code -Dcase.filter=TC00000001} (glob on case id).
 */
public final class TaskTFromTextWorkFlowTest extends CorpusWorkFlowSuite {

    @Override
    protected String extensionFolder() {
        return "task";
    }

    @Override
    protected String inputFileName() {
        return INPUT_CASE_FROM_TEXT;
    }

    @Override
    protected String flowType() {
        return FLOW_FROM_TEXT;
    }

    @Override
    protected Set<String> apiNames() {
        return taskApiNames();
    }
}