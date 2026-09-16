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