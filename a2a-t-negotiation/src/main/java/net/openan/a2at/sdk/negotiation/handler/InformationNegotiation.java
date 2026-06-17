package net.openan.a2at.sdk.negotiation.handler;

import net.openan.a2at.sdk.negotiation.types.model.NegotiationContext;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationContinueResult;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationReceiveResult;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationStatus;
import net.openan.a2at.sdk.negotiation.types.model.TaskPromptComplianceResult;
import net.openan.a2at.sdk.negotiation.types.checker.TaskPromptComplianceChecker;

import java.util.Map;

/**
 * Minimal information negotiation behavior.
 *
 * @since 2026-06
 */
public final class InformationNegotiation implements Negotiation {

    private final TaskPromptComplianceChecker complianceChecker;

    /** Creates an information negotiation type without prompt-compliance checks. */
    public InformationNegotiation() {
        this.complianceChecker = null;
    }

    /**
     * Creates an information negotiation type with server prompt-compliance checks.
     *
     * @param complianceChecker compliance checker invoked on received prompts
     */
    public InformationNegotiation(TaskPromptComplianceChecker complianceChecker) {
        this.complianceChecker = complianceChecker;
    }

    @Override
    public NegotiationReceiveResult processReceivedMessage(String message, NegotiationContext context) {
        if (complianceChecker != null) {
            TaskPromptComplianceResult result = complianceChecker.check(message);
            String responseMessage = result.passed()
                    ? "Task prompt is complete."
                    : result.failure().message();
            return new NegotiationReceiveResult(true, Map.of(), responseMessage);
        }
        return new NegotiationReceiveResult(true, Map.of(), message);
    }

    /**
     * Renders the continue payload for one information negotiation context.
     *
     * @param context current negotiation context
     * @param contentText prompt text to send back
     * @return continue result including the final task prompt when agreed
     */
    public NegotiationContinueResult renderContinue(NegotiationContext context, String contentText) {
        String finalTaskPrompt = context.status() == NegotiationStatus.AGREED ? contentText : null;
        return new NegotiationContinueResult(contentText, finalTaskPrompt);
    }
}
