package net.openan.a2at.sdk.corpus.engine.loader;

import java.util.List;

/** One workflow case record as declared by {@code input_case_*.json}. */
public record InputCase(
        String id,
        String caseDimension,
        String caseDesc,
        String caseCategory,
        List<InputStep> input,
        List<ExpectedStep> expected) {}