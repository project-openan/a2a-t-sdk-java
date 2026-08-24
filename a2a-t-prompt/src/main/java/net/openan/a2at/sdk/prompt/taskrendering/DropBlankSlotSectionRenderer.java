package net.openan.a2at.sdk.prompt.taskrendering;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sectioned template renderer with the <b>drop</b> blank-slot policy.
 *
 * <p>A template is split into sections on {@code ## } title lines; any content before the first title, including a
 * leading HTML description comment, is discarded. A section whose first non-empty body line begins with a
 * double-braced placeholder {@code {{name}}} is a slot section: it is rendered as the title followed by the slot
 * value, or dropped entirely when the slot value is null or blank. Whatever follows the placeholder (marker text,
 * parenthesized prose, nothing) is ignored for identification. Every other section is static and passes through
 * with placeholder substitution applied. Rendered sections are joined with a single blank line and the result
 * carries no trailing newline.
 *
 * @since 2026-08
 */
public final class DropBlankSlotSectionRenderer implements SectionedTemplateRenderer {

    private static final String SECTION_TITLE_PREFIX = "## ";

    private static final Pattern SLOT_LINE_PATTERN =
            Pattern.compile("^\\{\\{([^{}]+)\\}\\}.*$");

    private static final Pattern SLOT_TOKEN_PATTERN = Pattern.compile("\\{\\{([^{}]+)\\}\\}");

    /**
     * Renders one template text with the given slot values under the drop policy.
     *
     * @param templateText full template text whose slot sections are filled or dropped
     * @param slots slot values keyed by the language-specific slot name; a null or blank value drops the slot section
     * @return rendered message text with sections joined by one blank line and no trailing newline; empty string when
     *     no section remains
     * @throws NullPointerException if the template text is null
     */
    @Override
    public String render(String templateText, Map<String, String> slots) {
        if (templateText == null) {
            throw new NullPointerException("Template text must not be null.");
        }
        Map<String, String> safeSlots = slots == null ? Map.of() : slots;
        List<Section> sections = splitSections(templateText);
        List<String> renderedSections = new ArrayList<>();
        for (Section section : sections) {
            String slotName = slotNameOf(section);
            if (slotName != null) {
                String value = safeSlots.get(slotName);
                if (value == null || value.isBlank()) {
                    // An unfilled slot drops the whole section, title included.
                    continue;
                }
                renderedSections.add(SECTION_TITLE_PREFIX + section.title() + "\n" + value.strip());
            } else {
                renderedSections.add(SECTION_TITLE_PREFIX
                        + section.title()
                        + "\n"
                        + substitute(section.body(), safeSlots).stripTrailing());
            }
        }
        return String.join("\n\n", renderedSections);
    }

    private static List<Section> splitSections(String templateText) {
        List<Section> sections = new ArrayList<>();
        String currentTitle = null;
        List<String> currentBody = new ArrayList<>();
        for (String line : templateText.split("\n", -1)) {
            if (line.startsWith(SECTION_TITLE_PREFIX)) {
                if (currentTitle != null) {
                    sections.add(new Section(currentTitle, String.join("\n", currentBody)));
                }
                currentTitle = line.substring(SECTION_TITLE_PREFIX.length()).strip();
                currentBody = new ArrayList<>();
            } else if (currentTitle != null) {
                currentBody.add(line);
            }
            // Lines before the first title, such as the leading HTML comment, are discarded.
        }
        if (currentTitle != null) {
            sections.add(new Section(currentTitle, String.join("\n", currentBody)));
        }
        return sections;
    }

    private static String slotNameOf(Section section) {
        for (String line : section.body().split("\n", -1)) {
            if (line.isBlank()) {
                continue;
            }
            Matcher matcher = SLOT_LINE_PATTERN.matcher(line.strip());
            if (matcher.matches()) {
                return matcher.group(1);
            }
            return null;
        }
        return null;
    }

    private static String substitute(String body, Map<String, String> slots) {
        Matcher matcher = SLOT_TOKEN_PATTERN.matcher(body);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String value = slots.get(matcher.group(1));
            if (value != null && !value.isBlank()) {
                matcher.appendReplacement(result, Matcher.quoteReplacement(value));
            }
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private record Section(String title, String body) {}
}
