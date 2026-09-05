package net.openan.a2at.sdk.negotiation.generation;

import java.util.LinkedHashMap;
import java.util.Map;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.PromptTemplate;
import net.openan.a2at.sdk.negotiation.content.NegotiationConclusion;
import net.openan.a2at.sdk.negotiation.content.NegotiationContent;
import net.openan.a2at.sdk.negotiation.content.TargetEndingContent;
import net.openan.a2at.sdk.negotiation.content.Vocabulary;

/**
 * Generator for target negotiation terminal messages.
 *
 * <p>The result content slot carries the confirmed intent for an accept conclusion or the failure reason for a reject
 * conclusion; the other field is ignored.
 *
 * @since 2026-08
 */
final class TargetEndingGenerator extends AbstractNegotiationGenerator {

    /**
     * Generates a target negotiation accept or reject message.
     *
     * @param context negotiation context of the message
     * @param content target ending content
     * @param template target ending template to render
     * @param vocabulary vocabulary of the message language
     * @return rendered target terminal message text
     */
    @Override
    public String generate(
            NegotiationContext context, NegotiationContent content, PromptTemplate template, Vocabulary vocabulary) {
        TargetEndingContent endingContent = contentOf(content, TargetEndingContent.class, "Target ending generator");
        NegotiationConclusion conclusion = renderableConclusion(endingContent.conclusion());
        Map<String, String> slots = new LinkedHashMap<>();
        slots.put(vocabulary.get("slot.target_conclusion"), conclusion.literal());
        slots.put(
                vocabulary.get("slot.target_result_content"),
                resultContentSlotValue(endingContent, conclusion, vocabulary));
        return render(template, slots);
    }

    private static String resultContentSlotValue(
            TargetEndingContent content, NegotiationConclusion conclusion, Vocabulary vocabulary) {
        if (conclusion == NegotiationConclusion.ACCEPT) {
            return requiredText(
                    content.confirmedIntent(),
                    "content.confirmedIntent",
                    "Confirmed intent of an accepting target negotiation message",
                    vocabulary);
        }
        return requiredText(
                content.failureReason(),
                "content.failureReason",
                "Failure reason of a rejecting target negotiation" + " message",
                vocabulary);
    }
}
