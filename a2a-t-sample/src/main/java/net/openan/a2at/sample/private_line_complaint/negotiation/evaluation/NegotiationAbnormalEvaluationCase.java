package net.openan.a2at.sample.private_line_complaint.negotiation.evaluation;

import com.fasterxml.jackson.annotation.JsonProperty;

/** One deterministic abnormal-input case for the six Negotiation-T facade APIs. */
public record NegotiationAbnormalEvaluationCase(
        String id,
        String api,
        String input,
        String template,
        String performative,
        @JsonProperty("expected_exception") String expectedException,
        @JsonProperty("expected_code") String expectedCode,
        @JsonProperty("context_mode") String contextMode,
        @JsonProperty("schema_mode") String schemaMode,
        String description) {}
