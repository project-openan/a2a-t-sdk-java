package net.openan.a2at.sdk.prompt.taskrendering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class DropBlankSlotSectionRendererTest {

    private final DropBlankSlotSectionRenderer renderer = new DropBlankSlotSectionRenderer();

    @Test
    void rendersSlotSectionWithLowercaseEnglishMarker() {
        String template =
                """
                ## Slot
                {{slot}} (required)
                Requirements:
                Some requirements.
                """;

        String rendered = renderer.render(template, Map.of("slot", "value"));

        assertEquals("## Slot\nvalue", rendered);
    }

    @Test
    void rendersSlotSectionWithFullWidthChineseMarker() {
        String template =
                """
                ## 槽位
                {{槽位}}（必填）
                要求：
                一些要求。
                """;

        String rendered = renderer.render(template, Map.of("槽位", "值"));

        assertEquals("## 槽位\n值", rendered);
    }

    @Test
    void rendersSlotSectionWithChineseOptionalVariant() {
        String template =
                """
                ## 订阅条件
                {{订阅条件}}（可选）
                要求：
                提供订阅条件。
                """;

        String rendered = renderer.render(template, Map.of("订阅条件", "critical"));

        assertEquals("## 订阅条件\ncritical", rendered);
    }

    @Test
    void rendersSlotSectionWithChineseRequiredVariant() {
        String template =
                """
                ## 通知主题
                {{通知主题}}（必选）
                要求：
                提供通知主题。
                """;

        String rendered = renderer.render(template, Map.of("通知主题", "Incident"));

        assertEquals("## 通知主题\nIncident", rendered);
    }

    @Test
    void rendersSlotSectionWithBarePlaceholderNoMarker() {
        String template =
                """
                ## Slot
                {{slot}}
                Requirements:
                Some requirements.
                """;

        String rendered = renderer.render(template, Map.of("slot", "value"));

        assertEquals("## Slot\nvalue", rendered);
    }

    @Test
    void rendersSlotSectionWithLongConditionalSuffix() {
        String template =
                """
                ## Expected Output
                {{expected_output}} (optional when creating, not required when modifying)
                Requirement:
                Describe the output format.
                """;

        String rendered = renderer.render(template, Map.of("expected_output", "Report."));

        assertEquals("## Expected Output\nReport.", rendered);
    }

    @Test
    void dropsSlotSectionWhenValueIsNull() {
        String template =
                """
                ## Slot
                {{slot}} (required)
                Requirements:
                Some requirements.
                """;

        String rendered = renderer.render(template, Map.of());

        assertEquals("", rendered);
    }

    @Test
    void dropsSlotSectionWhenValueIsBlank() {
        String template =
                """
                ## Slot
                {{slot}} (required)
                Requirements:
                Some requirements.
                """;

        String rendered = renderer.render(template, Map.of("slot", "   "));

        assertEquals("", rendered);
    }

    @Test
    void keepsStaticSectionPassesThrough() {
        String template =
                """
                ## Static
                Some static content.
                """;

        String rendered = renderer.render(template, Map.of());

        assertEquals("## Static\nSome static content.", rendered);
    }

    @Test
    void joinsSectionsWithSingleBlankLine() {
        String template =
                """
                ## Section A
                Content A.

                ## Section B
                Content B.
                """;

        String rendered = renderer.render(template, Map.of());

        assertEquals("## Section A\nContent A.\n\n## Section B\nContent B.", rendered);
    }

    @Test
    void returnsEmptyStringWhenAllSectionsDropped() {
        String template =
                """
                ## Slot A
                {{slot_a}} (required)
                Requirements.

                ## Slot B
                {{slot_b}} (required)
                Requirements.
                """;

        String rendered = renderer.render(template, Map.of());

        assertEquals("", rendered);
    }

    @Test
    void rejectsNullTemplate() {
        assertThrows(NullPointerException.class, () -> renderer.render(null, Map.of()));
    }

    @Test
    void doesNotIdentifySlotSectionWhenFirstLineIsSingleBraceExample() {
        String template =
                """
                ## Section
                {00:00~06:00,2Mbps}

                {{slot}} (optional)
                """;

        String rendered = renderer.render(template, Map.of("slot", "value"));

        assertTrue(rendered.contains("{00:00~06:00,2Mbps}"));
        assertTrue(rendered.contains("value"));
        assertTrue(rendered.contains("(optional)"));
    }
}