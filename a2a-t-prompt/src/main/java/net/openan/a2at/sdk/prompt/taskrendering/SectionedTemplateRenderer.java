package net.openan.a2at.sdk.prompt.taskrendering;

import java.util.Map;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Renders a sectioned prompt template by filling slot values under one blank-slot policy.
 *
 * <p>A sectioned template is split into sections on {@code ## } title lines; preamble handling is policy-specific —
 * the drop policy discards content before the first title, while the collapse policy may retain it. A section whose first non-empty body line is a standalone slot placeholder line is a
 * slot section driven by that slot; every other section is static and passes through with placeholder substitution.
 *
 * <p>Implementations differ in how they treat a slot section whose value is null or blank:
 *
 * <ul>
 *   <li><b>collapse policy</b> — {@link TaskPromptRenderer}: the section is kept and its standalone slot line collapses
 *       to the bare slot placeholder, preserving the section scaffolding of the template;
 *   <li><b>drop policy</b> — {@link DropBlankSlotSectionRenderer}: the whole section including its title is removed
 *       from the rendered output.
 * </ul>
 *
 * <p>The two policies are deliberately not merged: both are load-bearing for their extension families, so the grammar
 * of section splitting lives here while each policy keeps its own slot-line and substitution rules.
 *
 * @since 2026-08
 */
public interface SectionedTemplateRenderer {

    /**
     * Renders one sectioned template text with the given slot values.
     *
     * @param templateText full template text whose sections are filled according to the implementation's policy
     * @param slots slot values keyed by slot name; how null values or a null map are handled depends on the policy
     * @return rendered prompt text
     * @throws NullPointerException if the template text is null; an adapter that sits behind an internal
     *     error-wrapping boundary may instead raise its internal failure type
     */
    @NonNull String render(@NonNull String templateText, @Nullable Map<String, String> slots);
}
