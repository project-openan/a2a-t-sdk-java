package net.openan.a2at.sdk.corpus.task;

import net.openan.a2at.sdk.corpus.engine.CorpusWorkFlowSuite;

/**
 * Task-T natural-language workflow suite: for every scenario directory under
 * {@code corpus/task/resources/}, runs the recorded SDK API flow of each case in
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
}