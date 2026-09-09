package net.openan.a2at.sdk.negotiation.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import net.openan.a2at.sdk.core.exception.A2ATBusinessException;
import net.openan.a2at.sdk.core.exception.A2ATError;
import net.openan.a2at.sdk.core.exception.ErrorCatalog;
import org.junit.jupiter.api.Test;

class NegotiationExceptionHierarchyTest {

    private static final Class<?>[] NEGOTIATION_EXCEPTION_TYPES = {
        NegotiationProcessingException.class,
        NegotiationGenerationException.class,
        NegotiationParamExtractionException.class
    };

    @Test
    void processingExceptionIsABusinessFailureWithCode() {
        NegotiationProcessingException exception =
                new NegotiationProcessingException(ErrorCatalog.NEGOTIATION_INVALID_INPUT.getCode(), "invalid input");

        assertTrue(exception instanceof A2ATBusinessException);
        assertTrue(exception instanceof A2ATError);
        assertEquals(ErrorCatalog.NEGOTIATION_INVALID_INPUT.getCode(), exception.getCode());
    }

    @Test
    void processingExceptionRendersItsMessageFromTheCatalogTemplate() {
        NegotiationProcessingException exception = new NegotiationProcessingException(
                ErrorCatalog.NEGOTIATION_CONTENT_INVALID,
                "zh-CN",
                java.util.Map.of("field", "content.terminationReason", "reason", "blank"));

        assertEquals(ErrorCatalog.NEGOTIATION_CONTENT_INVALID.getCode(), exception.getCode());
        assertEquals("协商内容字段「content.terminationReason」无效:blank", exception.getMessage());
        assertEquals("content.terminationReason", exception.getFacts().get("field"));
    }

    @Test
    void processingExceptionInheritsGetCodeFromTheA2ATErrorRoot() {
        NegotiationProcessingException exception =
                new NegotiationProcessingException(ErrorCatalog.NEGOTIATION_INVALID_INPUT.getCode(), "invalid input");

        assertEquals(
                A2ATError.class,
                findGetMethod(NegotiationProcessingException.class).getDeclaringClass(),
                "the code accessor must come from the A2ATError root so every negotiation failure shares one code"
                        + " contract");
        assertEquals(ErrorCatalog.NEGOTIATION_INVALID_INPUT.getCode(), exception.getCode());
    }

    @Test
    void generationExceptionExtendsProcessingException() {
        NegotiationGenerationException exception =
                new NegotiationGenerationException(ErrorCatalog.NEGOTIATION_FIELD_MISSING.getCode(), "missing field");

        assertTrue(exception instanceof NegotiationProcessingException);
        assertTrue(exception instanceof A2ATBusinessException);
        assertTrue(exception instanceof A2ATError);
        assertEquals(ErrorCatalog.NEGOTIATION_FIELD_MISSING.getCode(), exception.getCode());
    }

    @Test
    void paramExtractionExceptionIsABusinessFailureCarryingSlotDetails() {
        NegotiationParamExtractionException codedException = new NegotiationParamExtractionException(
                ErrorCatalog.NEGOTIATION_RULE_VIOLATION.getCode(), "rule violated", List.of());

        assertTrue(codedException instanceof A2ATBusinessException);
        assertTrue(codedException instanceof A2ATError);
        assertEquals(ErrorCatalog.NEGOTIATION_RULE_VIOLATION.getCode(), codedException.getCode());
        assertTrue(codedException.getErrors().isEmpty());
    }

    @Test
    void paramExtractionExceptionRendersItsMessageAndKeepsTheCause() {
        IllegalStateException cause = new IllegalStateException("endpoint unavailable");
        NegotiationParamExtractionException exception = new NegotiationParamExtractionException(
                ErrorCatalog.LLM_INVOCATION_FAILED,
                "zh-CN",
                java.util.Map.of("provider", "OpenAIClient", "reason", "endpoint unavailable"),
                List.of(),
                cause);

        assertEquals(ErrorCatalog.LLM_INVOCATION_FAILED.getCode(), exception.getCode());
        assertEquals("LLM 调用失败(提供方 OpenAIClient):endpoint unavailable", exception.getMessage());
        assertEquals("endpoint unavailable", exception.getFacts().get("reason"));
        assertEquals(cause, exception.getCause());
    }

    @Test
    void noNegotiationExceptionExposesAStageProperty() {
        for (Class<?> exceptionType : NEGOTIATION_EXCEPTION_TYPES) {
            boolean exposesStage = Arrays.stream(exceptionType.getMethods())
                    .anyMatch(method -> "getStage".equals(method.getName()) || "stage".equals(method.getName()));
            assertFalse(exposesStage, exceptionType.getSimpleName() + " must not expose a stage property");
        }
    }

    private static java.lang.reflect.Method findGetMethod(Class<?> exceptionType) {
        try {
            return exceptionType.getMethod("getCode");
        } catch (NoSuchMethodException exception) {
            throw new AssertionError("getCode must be inherited from A2ATError", exception);
        }
    }
}
