package net.openan.a2at.sdk.negotiation.generation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.exception.ErrorCatalog;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.PromptTemplate;
import net.openan.a2at.sdk.negotiation.content.NegotiationContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationGenerationException;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;
import net.openan.a2at.sdk.negotiation.content.TargetProposeContent;
import net.openan.a2at.sdk.negotiation.content.Vocabulary;
import org.jspecify.annotations.Nullable;

/**
 * Generator for target negotiation propose messages.
 *
 * <p>Besides the required target negotiation summary, the message renders round-driven conditional sections: the intent
 * understanding section appears only on the first round, the alignment and clarification section only on later rounds,
 * and the clarification request section only when clarification items are present. A non-blank confirm request marks
 * the "target clarified and requesting confirmation" category instead; the three conditional lists must then be empty,
 * so only the summary and the confirm request section remain.
 *
 * @since 2026-08
 */
final class TargetProposeGenerator extends AbstractNegotiationGenerator {

    /**
     * Generates a target negotiation propose message.
     *
     * @param context negotiation context of the message
     * @param content target propose content
     * @param template target propose template to render
     * @param vocabulary vocabulary of the message language
     * @return rendered target propose message text
     * @throws IllegalArgumentException if the content is of another runtime type
     * @throws NegotiationGenerationException with the code {@code negotiation.content_invalid} if the summary is blank,
     *     or {@code negotiation.invalid_input} if the confirm request is combined with any of the three conditional
     *     sections
     */
    @Override
    public String generate(
            NegotiationContext context, NegotiationContent content, PromptTemplate template, Vocabulary vocabulary) {
        TargetProposeContent proposeContent =
                contentOf(content, TargetProposeContent.class, "Target propose generator");
        requiredText(
                proposeContent.targetNegotiationDescription(),
                "content.targetNegotiationDescription",
                "Target negotiation description",
                vocabulary);
        String confirmRequest = presentText(proposeContent.targetConfirmRequest());
        if (confirmRequest != null
                && (hasItems(proposeContent.intentUnderstanding())
                        || hasItems(proposeContent.alignmentAndClarification())
                        || hasItems(proposeContent.requestForClarification()))) {
            throw invalidInput(
                    "Target confirm request must not be combined with the intent understanding, alignment and"
                            + " clarification or clarification request sections; a confirm-request round carries only"
                            + " the summary and the confirm request.",
                    vocabulary);
        }
        Map<String, String> slots = new LinkedHashMap<>();
        slots.put(vocabulary.get("slot.target"), proposeContent.targetNegotiationDescription());
        if (context.round() == 1) {
            slots.put(
                    vocabulary.get("slot.target_intent"),
                    formatItems(proposeContent.intentUnderstanding(), vocabulary));
        } else {
            slots.put(
                    vocabulary.get("slot.target_alignment"),
                    formatItems(proposeContent.alignmentAndClarification(), vocabulary));
        }
        slots.put(
                vocabulary.get("slot.target_clarification"),
                formatItems(proposeContent.requestForClarification(), vocabulary));
        if (confirmRequest != null) {
            slots.put(vocabulary.get("slot.target_confirm_request"), confirmRequest);
        }
        return render(template, slots);
    }

    private static @Nullable String presentText(@Nullable String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static NegotiationGenerationException invalidInput(String reason, Vocabulary vocabulary) {
        return new NegotiationGenerationException(
                ErrorCatalog.NEGOTIATION_INVALID_INPUT, vocabulary.language(), Map.of("reason", reason));
    }

    private static boolean hasItems(@Nullable List<NegotiationItem> items) {
        return items != null && !items.isEmpty();
    }
}
