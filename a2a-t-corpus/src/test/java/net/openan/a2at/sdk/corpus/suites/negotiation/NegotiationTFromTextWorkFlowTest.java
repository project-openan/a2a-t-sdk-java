package net.openan.a2at.sdk.corpus.suites.negotiation;

import java.util.Set;
import net.openan.a2at.sdk.corpus.engine.CorpusWorkFlowSuite;

/**
 * Negotiation-T natural-language workflow suite: for every scenario directory under
 * {@code suites/negotiation/resources/}, runs the recorded SDK API flow of each case in
 * {@code input_case_from_text.json} against a real LLM.
 *
 * <p>Default API flow (also shown in the case files): one {@code generateNegotiation*PromptFromText} step followed
 * by its matching {@code validate*PromptAndDataFilling} step; the concrete performative (propose / accept / reject /
 * abort) is chosen per case.
 *
 * <p>Filters: {@code -Dcorpus.scenario=ran-energy-saving-target-negotiation} (glob, comma-separated) and
 * {@code -Dcase.filter=TC00000001} (glob on case id).
 */
public final class NegotiationTFromTextWorkFlowTest extends CorpusWorkFlowSuite {

    @Override
    protected String extensionFolder() {
        return "negotiation";
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
    protected String runtimeKind() {
        return "negotiation";
    }

    @Override
    protected Set<String> apiNames() {
        return negotiationApiNames();
    }
}