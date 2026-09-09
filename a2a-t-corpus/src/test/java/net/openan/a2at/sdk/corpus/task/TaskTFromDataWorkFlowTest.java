package net.openan.a2at.sdk.corpus.task;

import net.openan.a2at.sdk.corpus.engine.CorpusWorkFlowSuite;

/**
 * Task-T structured-input workflow suite: for every scenario directory under
 * {@code corpus/task/resources/}, runs the recorded SDK API flow of each case in
 * {@code input_case_from_data.json} against a real LLM.
 *
 * <p>Default API flow (also shown in the case files): {@code generateTaskPromptFromDataWithSchema} →
 * {@code validateTaskPromptAndDataFilling}.
 *
 * <p>Filters: {@code -Dcorpus.scenario} and {@code -Dcase.filter}, see {@link TaskTFromTextWorkFlowTest}.
 */
public final class TaskTFromDataWorkFlowTest extends CorpusWorkFlowSuite {

    @Override
    protected String extensionFolder() {
        return "task";
    }

    @Override
    protected String inputFileName() {
        return INPUT_CASE_FROM_DATA;
    }

    @Override
    protected String flowType() {
        return FLOW_FROM_DATA;
    }
}