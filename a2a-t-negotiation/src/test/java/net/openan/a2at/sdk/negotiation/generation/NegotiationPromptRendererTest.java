package net.openan.a2at.sdk.negotiation.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NegotiationPromptRendererTest {

    private final NegotiationPromptRenderer renderer = new NegotiationPromptRenderer();

    private static final String ZH_TEMPLATE =
            """
            <!-- information negotiation propose template -->

            ## 协商上下文
            {{协商上下文}}（必填）
            要求：
            必须包含 id、round 与 maxRounds。

            ## 信息协商
            请根据<所需信息项>补充相关内容。

            ## 所需信息项
            {{所需信息项}}（必填）
            要求：
            提供每个缺失项的名称。
            """;

    private static final String EN_TEMPLATE =
            """
            <!-- information negotiation propose template -->

            ## Negotiation Context
            {{Negotiation Context}} (required)
            Requirements:
            Every negotiation must include id, round and maxRounds.

            ## Required Information Items
            {{Required Information Items}} (required)
            Requirements:
            Provide the name of each missing item.
            """;

    @Test
    void rendersSlotSectionsDroppingRequirementsAndStaticSections() {
        Map<String, String> slots = new LinkedHashMap<>();
        slots.put("协商上下文", "- id: 3dbc13b5-bd57-4c2b-b503-24e381b6c8d3\n- round: 1\n- maxRounds: 5");
        slots.put("所需信息项", "1. 节能区域信息：松山湖");

        String rendered = renderer.render(ZH_TEMPLATE, slots);

        assertEquals(
                "## 协商上下文\n"
                        + "- id: 3dbc13b5-bd57-4c2b-b503-24e381b6c8d3\n"
                        + "- round: 1\n"
                        + "- maxRounds: 5\n"
                        + "\n"
                        + "## 信息协商\n"
                        + "请根据<所需信息项>补充相关内容。\n"
                        + "\n"
                        + "## 所需信息项\n"
                        + "1. 节能区域信息：松山湖",
                rendered);
    }

    @Test
    void dropsWholeSlotSectionWhenValueIsMissingNullOrBlank() {
        Map<String, String> slots = new LinkedHashMap<>();
        slots.put("协商上下文", "- id: id-1\n- round: 1\n- maxRounds: 5");
        slots.put("所需信息项", null);

        String renderedWithNull = renderer.render(ZH_TEMPLATE, slots);

        assertEquals(
                "## 协商上下文\n- id: id-1\n- round: 1\n- maxRounds: 5\n\n## 信息协商\n请根据<所需信息项>补充相关内容。", renderedWithNull);

        slots.put("所需信息项", "   ");
        String renderedWithBlank = renderer.render(ZH_TEMPLATE, slots);

        assertEquals(renderedWithNull, renderedWithBlank);
    }

    @Test
    void dropsLeadingHtmlCommentAndPreambleBeforeFirstSection() {
        String rendered = renderer.render(ZH_TEMPLATE, Map.of("协商上下文", "- id: id-1", "所需信息项", "1. 名称"));

        assertFalse(rendered.contains("<!--"));
        assertFalse(rendered.contains("information negotiation propose template"));
        assertTrue(rendered.startsWith("## 协商上下文"));
    }

    @Test
    void recognizesEnglishRequiredAndOptionalMarkers() {
        Map<String, String> slots = Map.of(
                "Negotiation Context", "- id: id-1",
                "Required Information Items", "1. Energy-saving area information: Songshan Lake");

        String rendered = renderer.render(EN_TEMPLATE, slots);

        assertEquals(
                "## Negotiation Context\n"
                        + "- id: id-1\n"
                        + "\n"
                        + "## Required Information Items\n"
                        + "1. Energy-saving area information: Songshan Lake",
                rendered);
    }

    @Test
    void recognizesChineseOptionalMarker() {
        String template =
                """
                ## 待澄清内容
                {{待澄清内容}}（选填）
                要求：
                必须能定位到具体字段。
                """;

        String rendered = renderer.render(template, Map.of("待澄清内容", "1. 节能时间范围：需要澄清"));

        assertEquals("## 待澄清内容\n1. 节能时间范围：需要澄清", rendered);
    }

    @Test
    void joinsSectionsWithSingleBlankLineAndNoTrailingNewline() {
        String rendered = renderer.render(ZH_TEMPLATE, Map.of("协商上下文", "v1", "所需信息项", "v2"));

        assertTrue(rendered.endsWith("v2"));
        assertFalse(rendered.endsWith("\n"));
        // Three rendered sections are joined by exactly two single blank lines.
        assertEquals(2, countOccurrences(rendered, "\n\n"));
        assertFalse(rendered.contains("\n\n\n"));
    }

    @Test
    void returnsEmptyStringWhenEverySectionIsDropped() {
        String template =
                """
                ## 协商上下文
                {{协商上下文}}（必填）
                要求：
                上下文要求。

                ## 所需信息项
                {{所需信息项}}（必填）
                要求：
                信息项要求。
                """;

        assertEquals("", renderer.render(template, Map.of()));
    }

    @Test
    void keepsStaticSectionsWhenAllSlotsAreEmpty() {
        Map<String, String> slots = Map.of("协商上下文", "", "所需信息项", "");

        String rendered = renderer.render(ZH_TEMPLATE, slots);

        assertEquals("## 信息协商\n请根据<所需信息项>补充相关内容。", rendered);
    }

    @Test
    void passesMalformedBracesThroughInStaticSections() {
        String template =
                """
                ## 静态板块
                单花括号 { 不是槽位 }、未闭合 {{ 不是槽位、空名 {{}} 都保持原样。
                """;

        String rendered = renderer.render(template, Map.of("其他槽位", "值"));

        assertEquals("## 静态板块\n单花括号 { 不是槽位 }、未闭合 {{ 不是槽位、空名 {{}} 都保持原样。", rendered);
    }

    @Test
    void substitutesKnownSlotsInsideStaticSectionsAndLeavesUnknownOnes() {
        String template = """
                ## 静态板块
                已知槽位 {{已知}} 与未知槽位 {{未知}} 并存。
                """;

        String rendered = renderer.render(template, Map.of("已知", "替换值"));

        assertEquals("## 静态板块\n已知槽位 替换值 与未知槽位 {{未知}} 并存。", rendered);
    }

    @Test
    void rejectsNullTemplateText() {
        NegotiationRenderException exception =
                assertThrows(NegotiationRenderException.class, () -> renderer.render(null, Map.of()));

        assertEquals("Negotiation template text must not be null.", exception.getMessage());
    }

    @Test
    void ignoresNullSlotMap() {
        String rendered = renderer.render("## 板块\n静态内容。", null);

        assertEquals("## 板块\n静态内容。", rendered);
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
