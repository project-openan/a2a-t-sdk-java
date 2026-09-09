package net.openan.a2at.sdk.server.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import net.openan.a2at.sdk.core.exception.ErrorCatalog;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.core.validation.ContentValidationException;
import net.openan.a2at.sdk.core.validation.ContentValidator;
import org.junit.jupiter.api.Test;

class InputLimitedContentValidatorTest {

    @Test
    void validateThrowsInputTooLongWithoutDelegationWhenPromptExceedsLimit() {
        RecordingContentValidator delegate = new RecordingContentValidator();
        InputLimitedContentValidator validator = new InputLimitedContentValidator(delegate, 5);
        String oversizedPrompt = "a".repeat(6);

        ContentValidationException error = assertThrows(
                ContentValidationException.class,
                () -> validator.validate(oversizedPrompt, Map.of(), StandardTemplates.ENERGY_SAVING));

        assertEquals(ErrorCatalog.INPUT_TEXT_TOO_LONG.getCode(), error.getCode());
        assertEquals("Input text length 6 exceeds the maximum of 5 (A2AT_INPUT_TEXT_MAX_CHARS)", error.getMessage());
        assertEquals(null, delegate.lastPrompt, "Delegate must not run for an oversized prompt");
    }

    @Test
    void validateDelegatesWhenPromptIsExactlyAtLimit() {
        RecordingContentValidator delegate = new RecordingContentValidator();
        InputLimitedContentValidator validator = new InputLimitedContentValidator(delegate, 5);
        String boundaryPrompt = "a".repeat(5);

        FilledParamData result = validator.validate(boundaryPrompt, Map.of(), StandardTemplates.ENERGY_SAVING);

        assertEquals(boundaryPrompt, delegate.lastPrompt);
        assertEquals(FilledParamData.class, result.getClass());
    }

    private static final class RecordingContentValidator implements ContentValidator {

        private String lastPrompt;

        @Override
        public FilledParamData validate(
                String prompt, Map<String, Object> schema, net.openan.a2at.sdk.core.model.TemplateUri templateUri) {
            this.lastPrompt = prompt;
            return new FilledParamData(Map.of());
        }
    }
}
