package net.openan.a2at.sdk.negotiation.generation;

import java.util.LinkedHashMap;
import java.util.Map;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.PromptTemplate;
import net.openan.a2at.sdk.negotiation.content.FeasibilityEndingContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationContent;
import net.openan.a2at.sdk.negotiation.content.Vocabulary;

/**
 * Generator for feasibility negotiation terminal messages.
 *
 * <p>The message carries the terminal conclusion literal and the required feasibility summary. The summary slot name
 * differs from its section title, so the vocabulary exception key is used.
 *
 * @since 2026-08
 */
final class FeasibilityEndingGenerator extends AbstractNegotiationGenerator {

    /**
     * Generates a feasibility negotiation accept or reject message.
     *
     * @param context negotiation context of the message
     * @param content feasibility ending content
     * @param template feasibility ending template to render
     * @param vocabulary vocabulary of the message language
     * @return rendered feasibility terminal message text
     */
    @Override
    public String generate(
            NegotiationContext context, NegotiationContent content, PromptTemplate template, Vocabulary vocabulary) {
        FeasibilityEndingContent endingContent =
                contentOf(content, FeasibilityEndingContent.class, "Feasibility ending generator");
        renderableConclusion(endingContent.conclusion());
        requiredText(
                endingContent.feasibilitySummary(),
                "content.feasibilitySummary",
                "Feasibility summary of a terminal feasibility negotiation message",
                vocabulary);
        Map<String, String> slots = new LinkedHashMap<>();
        slots.put(
                vocabulary.get("slot.feasibility_conclusion"),
                endingContent.conclusion().literal());
        slots.put(vocabulary.get("slot.feasibility_confirm"), endingContent.feasibilitySummary());
        return render(template, slots);
    }
}
