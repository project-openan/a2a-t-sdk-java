package net.openan.a2at.sdk.corpus.engine.loader;

import java.util.Map;

/** One per-step expectation, aligned by index with the matching {@link InputStep}. */
public record ExpectedStep(
        String result,
        Map<String, Object> data,
        boolean dataExact,
        String errCode,
        String errMessage,
        String errMessageContains) {}