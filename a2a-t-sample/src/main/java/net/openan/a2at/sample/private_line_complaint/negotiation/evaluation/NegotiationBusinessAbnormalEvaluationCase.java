package net.openan.a2at.sample.private_line_complaint.negotiation.evaluation;

import java.util.List;

/** One business-level malformed-content case executed against a real LLM. */
public record NegotiationBusinessAbnormalEvaluationCase(
        String id, String api, String input, String description, Boolean modelDependent, List<String> expectedCodes) {

    public NegotiationBusinessAbnormalEvaluationCase {
        modelDependent = Boolean.TRUE.equals(modelDependent);
    }
}
