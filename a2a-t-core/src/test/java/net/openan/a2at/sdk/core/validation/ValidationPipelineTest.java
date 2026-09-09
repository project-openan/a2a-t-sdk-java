package net.openan.a2at.sdk.core.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.exception.ErrorCatalog;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;

/**
 * Guards the optional template loading gate of {@link ValidationPipeline}: the gate must run after the rule gate and
 * before semantic validation, a loader failure must map to {@code template.not_found}, and the loader-less default
 * {@code validate} variant must reject callers that did not provide the template content.
 */
class ValidationPipelineTest {

    @Test
    void should_loadTemplateBetweenRuleGateAndSemanticValidation() {
        List<String> events = new ArrayList<>();
        RuleChecker ruleChecker = prompt -> {
            events.add("rule");
            return Map.of();
        };
        SemanticValidator<String> semanticValidator = (prompt, schema, reference, templateContent) -> {
            events.add("semantic");
            return new ValidationResult(true, List.of(), Map.of("param", "value"));
        };
        TemplateContentLoader<String> loader = reference -> {
            events.add("load");
            return "template-content";
        };

        ValidationPipeline<String> pipeline = new ValidationPipeline<>(ruleChecker, semanticValidator, 1, null, loader);

        pipeline.validate("prompt", Map.of(), "ref");

        assertEquals(List.of("rule", "load", "semantic"), events);
    }

    @Test
    void should_mapLoaderFailureToPromptResourceNotFound() {
        RuleChecker ruleChecker = prompt -> Map.of();
        SemanticValidator<String> semanticValidator =
                (prompt, schema, reference, templateContent) -> new ValidationResult(true, List.of(), Map.of());
        TemplateContentLoader<String> loader = reference -> {
            throw new ResourceNotFoundException("missing template", reference);
        };

        ValidationPipeline<String> pipeline = new ValidationPipeline<>(ruleChecker, semanticValidator, 1, null, loader);

        ContentValidationException exception =
                assertThrows(ContentValidationException.class, () -> pipeline.validate("prompt", Map.of(), "ref"));
        assertEquals(ErrorCatalog.TEMPLATE_NOT_FOUND.getCode(), exception.getCode());
    }

    @Test
    void should_throwValidationInvalidInput_When_schemaIsNull() {
        RuleChecker ruleChecker = prompt -> Map.of();
        SemanticValidator<String> semanticValidator =
                (prompt, schema, reference, templateContent) -> new ValidationResult(true, List.of(), Map.of());

        ValidationPipeline<String> pipeline = new ValidationPipeline<>(ruleChecker, semanticValidator, 1);

        ContentValidationException exception = assertThrows(
                ContentValidationException.class, () -> pipeline.validate("prompt", null, "ref", "content"));
        assertEquals(ErrorCatalog.NEGOTIATION_INVALID_INPUT.getCode(), exception.getCode());
    }

    @Test
    void should_throwValidationInvalidInput_When_promptIsNullOrBlank() {
        RuleChecker ruleChecker = prompt -> Map.of();
        SemanticValidator<String> semanticValidator =
                (prompt, schema, reference, templateContent) -> new ValidationResult(true, List.of(), Map.of());

        ValidationPipeline<String> pipeline = new ValidationPipeline<>(ruleChecker, semanticValidator, 1);

        ContentValidationException nullException = assertThrows(
                ContentValidationException.class, () -> pipeline.validate(null, Map.of(), "ref", "content"));
        assertEquals(ErrorCatalog.NEGOTIATION_INVALID_INPUT.getCode(), nullException.getCode());

        ContentValidationException blankException = assertThrows(
                ContentValidationException.class, () -> pipeline.validate("   ", Map.of(), "ref", "content"));
        assertEquals(ErrorCatalog.NEGOTIATION_INVALID_INPUT.getCode(), blankException.getCode());
    }

    @Test
    void should_throwValidationInvalidInput_When_referenceIsNull() {
        RuleChecker ruleChecker = prompt -> Map.of();
        SemanticValidator<String> semanticValidator =
                (prompt, schema, reference, templateContent) -> new ValidationResult(true, List.of(), Map.of());

        ValidationPipeline<String> pipeline = new ValidationPipeline<>(ruleChecker, semanticValidator, 1);

        ContentValidationException exception = assertThrows(
                ContentValidationException.class, () -> pipeline.validate("prompt", Map.of(), null, "content"));
        assertEquals(ErrorCatalog.NEGOTIATION_INVALID_INPUT.getCode(), exception.getCode());
    }

    @Test
    void should_throwIllegalStateException_When_noLoaderInjected() {
        RuleChecker ruleChecker = prompt -> Map.of();
        SemanticValidator<String> semanticValidator =
                (prompt, schema, reference, templateContent) -> new ValidationResult(true, List.of(), Map.of());

        ValidationPipeline<String> pipeline = new ValidationPipeline<>(ruleChecker, semanticValidator, 1);

        assertThrows(IllegalStateException.class, () -> pipeline.validate("prompt", Map.of(), "ref"));
    }

    @Test
    void should_notInvokeLoader_When_templateContentProvidedExplicitly() {
        List<String> events = new ArrayList<>();
        RuleChecker ruleChecker = prompt -> {
            events.add("rule");
            return Map.of();
        };
        SemanticValidator<String> semanticValidator = (prompt, schema, reference, templateContent) -> {
            events.add("semantic");
            return new ValidationResult(true, List.of(), Map.of());
        };
        TemplateContentLoader<String> loader = reference -> {
            events.add("load");
            return "template-content";
        };

        ValidationPipeline<String> pipeline = new ValidationPipeline<>(ruleChecker, semanticValidator, 1, null, loader);

        pipeline.validate("prompt", Map.of(), "ref", "explicit-content");

        assertEquals(List.of("rule", "semantic"), events);
    }
}
