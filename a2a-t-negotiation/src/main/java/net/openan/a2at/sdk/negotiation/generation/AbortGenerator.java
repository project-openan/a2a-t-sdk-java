package net.openan.a2at.sdk.negotiation.generation;

import java.util.LinkedHashMap;
import java.util.Map;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.PromptTemplate;
import net.openan.a2at.sdk.negotiation.content.NegotiationAbortContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationContent;
import net.openan.a2at.sdk.negotiation.content.Vocabulary;

/**
 * Generator for the type-independent abort negotiation message.
 *
 * <p>The common abort template carries the fixed {@code Abort} conclusion section, so this generator fills only the
 * negotiation context and the termination reason slots.
 *
 * @since 2026-08
 */
final class AbortGenerator extends AbstractNegotiationGenerator {

    /**
     * Generates an abort negotiation message.
     *
     * @param context negotiation context of the message
     * @param content abort content carrying the termination reason
     * @param template common abort template to render
     * @param vocabulary vocabulary of the message language
     * @return rendered abort message text
     */
    @Override
    public String generate(
            NegotiationContext context, NegotiationContent content, PromptTemplate template, Vocabulary vocabulary) {
        NegotiationAbortContent abortContent = contentOf(content, NegotiationAbortContent.class, "Abort generator");
        Map<String, String> slots = new LinkedHashMap<>();
        slots.put(
                vocabulary.get("slot.termination_reason"),
                requiredText(
                        abortContent.terminationReason(),
                        "content.terminationReason",
                        "Termination reason of an abort negotiation message",
                        vocabulary));
        return render(template, slots);
    }
}
