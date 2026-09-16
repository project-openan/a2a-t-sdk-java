package net.openan.a2at.sdk.negotiation.generation;

import java.util.List;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;

/**
 * Formats negotiation item lists as numbered markdown lines.
 *
 * <p>Each item becomes one line {@code N. name<colon>value}; an item without a value becomes {@code N. name}. The
 * caller supplies the list punctuation from the negotiation vocabulary so the output matches the message language.
 *
 * @since 2026-08
 */
public final class NegotiationItemFormatter {

    /**
     * Formats one item list as numbered lines.
     *
     * @param items items to format; null or empty produces an empty string
     * @param listColon colon punctuation appended between item name and value, such as {@code ：} or {@code : }
     * @return numbered lines joined by single newlines, or an empty string when there is no item
     */
    public String format(List<NegotiationItem> items, String listColon) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        String colon = listColon == null ? "" : listColon;
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < items.size(); index++) {
            NegotiationItem item = items.get(index);
            if (index > 0) {
                text.append('\n');
            }
            text.append(index + 1).append(". ").append(item.name());
            if (item.value() != null && !item.value().isBlank()) {
                text.append(colon).append(item.value());
            }
        }
        return text.toString();
    }
}
