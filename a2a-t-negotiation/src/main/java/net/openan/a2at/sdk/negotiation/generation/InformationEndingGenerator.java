package net.openan.a2at.sdk.negotiation.generation;

import java.util.LinkedHashMap;
import java.util.Map;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.PromptTemplate;
import net.openan.a2at.sdk.negotiation.content.InformationEndingContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationContent;
import net.openan.a2at.sdk.negotiation.content.Vocabulary;

/**
 * Generator for information negotiation terminal messages.
 *
 * <p>The message carries the terminal conclusion literal and the information items delivered with it.
 *
 * @since 2026-08
 */
final class InformationEndingGenerator extends AbstractNegotiationGenerator {

    /**
     * Generates an information negotiation accept or reject message.
     *
     * @param context negotiation context of the message
     * @param content information ending content
     * @param template information ending template to render
     * @param vocabulary vocabulary of the message language
     * @return rendered information terminal message text
     */
    @Override
    public String generate(
            NegotiationContext context, NegotiationContent content, PromptTemplate template, Vocabulary vocabulary) {
        InformationEndingContent endingContent =
                contentOf(content, InformationEndingContent.class, "Information ending generator");
        renderableConclusion(endingContent.conclusion());
        requiredItems(
                endingContent.items(), "items", "Information negotiation terminal message result content", vocabulary);
        Map<String, String> slots = new LinkedHashMap<>();
        slots.put(
                vocabulary.get("slot.info_conclusion"),
                endingContent.conclusion().literal());
        slots.put(vocabulary.get("slot.info_result_content"), formatItems(endingContent.items(), vocabulary));
        return render(template, slots);
    }
}
