package net.openan.a2at.sdk.negotiation.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class VocabularyTest {

    private static final Set<String> CANONICAL_KEYS = Set.of(
            "section.termination_reason",
            "section.info_items",
            "section.info_static",
            "section.info_conclusion",
            "section.info_result_content",
            "section.target",
            "section.target_intent",
            "section.target_alignment",
            "section.target_clarification",
            "section.target_confirm_request",
            "section.target_conclusion",
            "section.target_result_content",
            "section.feasibility",
            "section.feasibility_evaluate",
            "section.feasibility_infeasible",
            "section.feasibility_confirm_request",
            "section.feasibility_conclusion",
            "section.feasibility_confirm",
            "slot.termination_reason",
            "slot.info_items",
            "slot.info_conclusion",
            "slot.info_result_content",
            "slot.feasibility",
            "slot.target",
            "slot.target_intent",
            "slot.target_alignment",
            "slot.target_clarification",
            "slot.target_confirm_request",
            "slot.target_conclusion",
            "slot.target_result_content",
            "slot.feasibility_evaluate",
            "slot.feasibility_infeasible",
            "slot.feasibility_confirm_request",
            "slot.feasibility_conclusion",
            "slot.feasibility_confirm",
            "label.relationship",
            "punct.list_colon");

    @Test
    void bothLanguagesExposeExactlyTheCanonicalKeys() {
        Vocabulary zhCn = Vocabulary.forLanguage("zh-CN");
        Vocabulary enUs = Vocabulary.forLanguage("en-US");

        assertEquals(CANONICAL_KEYS, zhCn.canonicalKeys());
        assertEquals(CANONICAL_KEYS, enUs.canonicalKeys());
        assertEquals(Set.copyOf(Vocabulary.CANONICAL_KEYS), zhCn.canonicalKeys());
        assertEquals(new TreeSet<>(zhCn.canonicalKeys()), new TreeSet<>(enUs.canonicalKeys()));
        assertEquals(37, zhCn.canonicalKeys().size());
        assertEquals(37, Vocabulary.CANONICAL_KEYS.size());
    }

    @Test
    void terminationReasonSlotMatchesTheCommonAbortTemplatePlaceholder() {
        Vocabulary zhCn = Vocabulary.forLanguage("zh-CN");
        Vocabulary enUs = Vocabulary.forLanguage("en-US");

        assertEquals("协商终止原因", zhCn.get("slot.termination_reason"));
        assertEquals("negotiation_termination_reason", enUs.get("slot.termination_reason"));
        assertEquals("协商终止原因", zhCn.get("section.termination_reason"));
        assertEquals("Negotiation Termination Reason", enUs.get("section.termination_reason"));
    }

    @Test
    void slotPlaceholdersAreSnakeCaseForEnglishAndCjkForChinese() {
        Vocabulary zhCn = Vocabulary.forLanguage("zh-CN");
        Vocabulary enUs = Vocabulary.forLanguage("en-US");

        // The placeholders are snake_case for en-US and the matching CJK section title for zh-CN.
        assertEquals("意图理解陈述", zhCn.get("slot.target_intent"));
        assertEquals("intent_understanding_statement", enUs.get("slot.target_intent"));
    }

    @Test
    void summaryAndConfirmSlotsDifferFromTheirSectionTitle() {
        Vocabulary zhCn = Vocabulary.forLanguage("zh-CN");
        Vocabulary enUs = Vocabulary.forLanguage("en-US");

        assertEquals("可行性协商概述", zhCn.get("slot.feasibility"));
        assertEquals("feasibility_negotiation_summary", enUs.get("slot.feasibility"));
        assertEquals("目标协商概述", zhCn.get("slot.target"));
        assertEquals("target_negotiation_summary", enUs.get("slot.target"));
        assertEquals("评估结果确认", zhCn.get("slot.feasibility_confirm"));
        assertEquals("evaluation_result_confirmation", enUs.get("slot.feasibility_confirm"));
    }

    @Test
    void sectionValuesMatchTemplateSectionTitles() {
        Vocabulary zhCn = Vocabulary.forLanguage("zh-CN");
        Vocabulary enUs = Vocabulary.forLanguage("en-US");

        assertEquals("目标协商结果内容", zhCn.get("section.target_result_content"));
        assertEquals("Target Negotiation Result Content", enUs.get("section.target_result_content"));
        assertEquals("可行性评估结果确认", zhCn.get("section.feasibility_confirm"));
        assertEquals("Feasibility Assessment Result Confirmation", enUs.get("section.feasibility_confirm"));
    }

    @Test
    void confirmRequestSectionAndSlotKeysMatchTheNewTemplates() {
        Vocabulary zhCn = Vocabulary.forLanguage("zh-CN");
        Vocabulary enUs = Vocabulary.forLanguage("en-US");

        assertEquals("目标澄清后的确认请求", zhCn.get("section.target_confirm_request"));
        assertEquals("Target Clarification Confirmation Request", enUs.get("section.target_confirm_request"));
        assertEquals("目标澄清后的确认请求", zhCn.get("slot.target_confirm_request"));
        assertEquals("target_confirm_request", enUs.get("slot.target_confirm_request"));
        assertEquals("评估可行时的确认请求", zhCn.get("section.feasibility_confirm_request"));
        assertEquals("Feasible Evaluation Confirmation Request", enUs.get("section.feasibility_confirm_request"));
        assertEquals("评估可行时的确认请求", zhCn.get("slot.feasibility_confirm_request"));
        assertEquals("feasibility_confirm_request", enUs.get("slot.feasibility_confirm_request"));
    }

    @Test
    void englishRelationshipLabelCarriesOneTrailingSpace() {
        String englishLabel = Vocabulary.forLanguage("en-US").get("label.relationship");
        String chineseLabel = Vocabulary.forLanguage("zh-CN").get("label.relationship");

        assertEquals("Relationship between missing items: ", englishLabel);
        assertTrue(englishLabel.endsWith(" "));
        assertEquals("缺失项之间的关系：", chineseLabel);
    }

    @Test
    void listColonPunctuationDiffersPerLanguage() {
        assertEquals("：", Vocabulary.forLanguage("zh-CN").get("punct.list_colon"));
        assertEquals(": ", Vocabulary.forLanguage("en-US").get("punct.list_colon"));
    }

    @Test
    void unsupportedLanguageThrows() {
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> Vocabulary.forLanguage("fr-FR"));

        assertTrue(exception.getMessage().contains("fr-FR"));
        assertTrue(exception.getMessage().contains("A2AT_LANGUAGE"));
    }

    @Test
    void unknownKeyThrows() {
        Vocabulary vocabulary = Vocabulary.forLanguage("zh-CN");

        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> vocabulary.get("section.unknown"));

        assertTrue(exception.getMessage().contains("section.unknown"));
    }

    @Test
    void languageThatIsNotASimplePathSegmentThrows() {
        assertThrows(IllegalArgumentException.class, () -> Vocabulary.forLanguage("../escape"));
    }
}
