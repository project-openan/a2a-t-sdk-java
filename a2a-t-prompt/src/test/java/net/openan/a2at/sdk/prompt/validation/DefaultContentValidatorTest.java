package net.openan.a2at.sdk.prompt.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.core.validation.ContentValidationException;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.llm.LLMRuntimeError;
import net.openan.a2at.sdk.prompt.resources.loader.PromptTemplateTextLoader;
import org.junit.jupiter.api.Test;

class DefaultContentValidatorTest {

    private static final TemplateUri TASK_URI = TemplateUri.of("Task-T", "network-layer", "ran-energy-saving");

    private static final PromptTemplateTextLoader STUB_LOADER = (scenarioCode, language) -> "dummy template content";

    @Test
    void validatesSuccessfullyOnFirstCall() {
        DefaultContentValidator validator =
                new DefaultContentValidator("Task-T", "zh-CN", 3, new StubClient(), STUB_LOADER);

        FilledParamData result =
                assertDoesNotThrow(() -> validator.validate("task prompt", Map.of("type", "object"), TASK_URI));

        assertNotNull(result);
        assertEquals(Map.of("site", "Site A"), result.data());
    }

    @Test
    void rejectsMismatchedExtensionName() {
        DefaultContentValidator validator =
                new DefaultContentValidator("Task-T", "zh-CN", 3, new StubClient(), STUB_LOADER);

        ContentValidationException exception = assertThrows(
                ContentValidationException.class,
                () -> validator.validate(
                        "task prompt",
                        Map.of("type", "object"),
                        TemplateUri.of("Notification-T", "network-layer", "service-recovery")));

        assertEquals("negotiation.invalid_input", exception.getCode());
    }

    @Test
    void templateVersionIsUnsupported() {
        DefaultContentValidator validator =
                new DefaultContentValidator("Task-T", "zh-CN", 3, new StubClient(), STUB_LOADER);

        TemplateUri unsupportedVersion = TemplateUri.of("Task-T", List.of("network-layer", "ran-energy-saving"), "v2");

        ContentValidationException exception = assertThrows(
                ContentValidationException.class,
                () -> validator.validate("task prompt", Map.of("type", "object"), unsupportedVersion));

        assertEquals("negotiation.invalid_input", exception.getCode());
    }

    @Test
    void templateUriIsNull() {
        DefaultContentValidator validator =
                new DefaultContentValidator("Task-T", "zh-CN", 3, new StubClient(), STUB_LOADER);

        ContentValidationException exception = assertThrows(
                ContentValidationException.class,
                () -> validator.validate("task prompt", Map.of("type", "object"), null));

        assertEquals("negotiation.invalid_input", exception.getCode());
    }

    @Test
    void unsupportedLanguageFailsAtConstructionWithTemplateNotFound() {
        ContentValidationException exception = assertThrows(
                ContentValidationException.class,
                () -> new DefaultContentValidator("Task-T", "fr-FR", 3, new StubClient(), STUB_LOADER));

        assertEquals("template.not_found", exception.getCode());
        assertInstanceOf(ResourceNotFoundException.class, exception.getCause());
        assertTrue(exception.getCause().getMessage().contains("zh-CN or en-US"));
    }

    @Test
    void constructionSucceedsWithEmptyLocalRootBecausePromptLoadsFromClasspath() {
        PromptTemplateTextLoader emptyLocalRootLoader = (scenarioCode, language) -> {
            throw new ResourceNotFoundException("No template in the local root for " + scenarioCode, scenarioCode);
        };

        assertDoesNotThrow(
                () -> new DefaultContentValidator("Task-T", "zh-CN", 3, new StubClient(), emptyLocalRootLoader));
    }

    @Test
    void retryableFailureAfterResourceLoadMapsLlmInvocationFailed() {
        DefaultContentValidator validator =
                new DefaultContentValidator("Task-T", "zh-CN", 1, new FailingClient(), STUB_LOADER);

        ContentValidationException exception = assertThrows(
                ContentValidationException.class,
                () -> validator.validate("task prompt", Map.of("type", "object"), TASK_URI));

        assertEquals("llm.invocation_failed", exception.getCode());
    }

    @Test
    void missingTemplateFailsWithTemplateNotFound() {
        PromptTemplateTextLoader failingLoader = (scenarioCode, language) -> {
            throw new ResourceNotFoundException("Template does not exist.", scenarioCode);
        };
        DefaultContentValidator validator =
                new DefaultContentValidator("Task-T", "zh-CN", 3, new StubClient(), failingLoader);

        ContentValidationException exception = assertThrows(
                ContentValidationException.class,
                () -> validator.validate("task prompt", Map.of("type", "object"), TASK_URI));

        assertEquals("template.not_found", exception.getCode());
        assertInstanceOf(ResourceNotFoundException.class, exception.getCause());
    }

    private static final class StubClient implements LLMClient {

        @Override
        public LLMResponse structured(
                List<Map<String, String>> messages,
                Map<String, Object> jsonSchema,
                Double temperature,
                Integer maxTokens) {
            return new LLMResponse(
                    """
                    {
                      "semantic_verdict": true,
                      "errors": [],
                      "params": {"site": "Site A"}
                    }
                    """,
                    "stub-llm",
                    Map.of(),
                    Map.of());
        }
    }

    private static final class FailingClient implements LLMClient {

        @Override
        public LLMResponse structured(
                List<Map<String, String>> messages,
                Map<String, Object> jsonSchema,
                Double temperature,
                Integer maxTokens) {
            throw new LLMRuntimeError("network timeout");
        }
    }
}
