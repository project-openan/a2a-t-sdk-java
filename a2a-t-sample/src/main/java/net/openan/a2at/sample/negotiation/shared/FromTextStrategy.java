package net.openan.a2at.sample.negotiation.shared;

import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.client.A2ATClient;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;
import net.openan.a2at.sdk.server.A2ATServer;

/**
 * LLM-based negotiation strategy: converts the items to natural-language text and calls the fromText API.
 *
 * <p>The SDK runs one LLM content-extraction step to parse the text into typed content, then renders deterministically.
 * The sentence framing of the assembled text comes from the scenario configuration ({@code from_text_propose_prefix},
 * {@code from_text_accept_prefix}, {@code from_text_accept_suffix}); the item lines follow one generic numbered-list
 * rule.
 *
 * @since 2026-08
 */
public final class FromTextStrategy implements NegotiationStrategy {

    @Override
    public MetadataContent generatePropose(
            A2ATServer server,
            NegotiationContext ctx,
            List<NegotiationItem> missingItems,
            String relationship,
            String templateUri) {
        String text = itemsToProposeText(missingItems, relationship);
        return server.generateNegotiationProposePromptFromText(text, ctx, templateUri);
    }

    @Override
    public MetadataContent generateAccept(
            A2ATClient facade, NegotiationContext ctx, List<NegotiationItem> filledItems, String templateUri) {
        String text = itemsToAcceptText(filledItems);
        return facade.generateNegotiationAcceptPromptFromText(text, ctx, templateUri);
    }

    @Override
    public MetadataContent generateAcceptServer(
            A2ATServer server, NegotiationContext ctx, List<NegotiationItem> filledItems, String templateUri) {
        String text = itemsToAcceptText(filledItems);
        return server.generateNegotiationAcceptPromptFromText(text, ctx, templateUri);
    }

    private static String itemsToProposeText(List<NegotiationItem> items, String relationship) {
        StringBuilder sb =
                new StringBuilder(ScenarioData.negotiationPhrasing().getOrDefault("from_text_propose_prefix", ""));
        appendNumberedItems(sb, items);
        if (relationship != null && !relationship.isBlank()) {
            sb.append(relationship);
        }
        return sb.toString();
    }

    private static String itemsToAcceptText(List<NegotiationItem> items) {
        Map<String, String> phrasing = ScenarioData.negotiationPhrasing();
        StringBuilder sb = new StringBuilder(phrasing.getOrDefault("from_text_accept_prefix", ""));
        appendNumberedItems(sb, items);
        sb.append(phrasing.getOrDefault("from_text_accept_suffix", ""));
        return sb.toString();
    }

    /** Generic numbered-list rule: {@code N. name：value；} per item, value omitted when blank. */
    private static void appendNumberedItems(StringBuilder sb, List<NegotiationItem> items) {
        for (int i = 0; i < items.size(); i++) {
            NegotiationItem item = items.get(i);
            sb.append(i + 1).append(". ").append(item.name());
            if (item.value() != null && !item.value().isBlank()) {
                sb.append("：").append(item.value());
            }
            sb.append("；");
        }
    }
}
