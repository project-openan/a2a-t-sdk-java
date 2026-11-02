package net.openan.a2at.sdk.corpus.engine.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Non-live tests of the corpus {@code A2AT_LANGUAGE} configuration and case-file naming (port of the Python
 * {@code test_language_config.py}). These tests touch neither the real {@code .env} nor the LLM runtime.
 */
final class CorpusLanguageConfigTest {

    private String savedLanguageProperty;

    @BeforeEach
    void saveLanguageProperty() {
        savedLanguageProperty = System.getProperty("a2at.language");
        System.clearProperty("a2at.language");
    }

    @AfterEach
    void restoreLanguageProperty() {
        if (savedLanguageProperty == null) {
            System.clearProperty("a2at.language");
        } else {
            System.setProperty("a2at.language", savedLanguageProperty);
        }
    }

    @Test
    void resolveLanguageDefaultsToZhCn() {
        // An unset or blank language keeps the committed Chinese corpus as the default.
        assertEquals("zh-CN", CorpusEnvConfig.resolveLanguage(Map.of(), Map.of()));
        assertEquals("zh-CN", CorpusEnvConfig.resolveLanguage(Map.of("A2AT_LANGUAGE", ""), Map.of()));
    }

    @Test
    void resolveLanguagePrefersTheSystemPropertyOverTheDotenvFile() {
        // The system property wins over the corpus .env value.
        System.setProperty("a2at.language", "en-US");
        assertEquals("en-US", CorpusEnvConfig.resolveLanguage(Map.of("A2AT_LANGUAGE", "zh-CN"), Map.of()));
    }

    @Test
    void resolveLanguageRejectsUnsupportedValues() {
        // An unsupported language fails with the supported set in the message.
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> CorpusEnvConfig.resolveLanguage(Map.of("A2AT_LANGUAGE", "fr-FR"), Map.of()));
        assertTrue(error.getMessage().contains("en-US, zh-CN"), error.getMessage());
    }

    @Test
    void corpusConfigCarriesLanguageFromTheDotenvFile() {
        // The full corpus config carries the .env language into the runtime config.
        Map<String, String> envFileValues = Map.of(
                "A2AT_LANGUAGE", "en-US",
                "A2AT_LLM_PROVIDER", "openai",
                "A2AT_LLM_BASE_URL", "https://llm.example.test/v1",
                "A2AT_LLM_API_KEY", "test-key",
                "A2AT_LLM_MODEL", "test-model");
        assertEquals("en-US", CorpusEnvConfig.create(envFileValues).language());
    }

    @Test
    void caseFileNamesFollowTheLanguageSuffixConvention() {
        // zh-CN keeps the historical names; other languages append their locale tag.
        assertEquals(
                "input_case_from_text.json", CorpusLanguage.localizedCaseFile("input_case_from_text.json", "zh-CN"));
        assertEquals(
                "input_case_from_text_en-US.json",
                CorpusLanguage.localizedCaseFile("input_case_from_text.json", "en-US"));
        assertEquals(
                List.of("input_case_from_data.json", "input_case_from_data_en-US.json"),
                CorpusLanguage.caseFileVariants("input_case_from_data.json"));
    }
}
