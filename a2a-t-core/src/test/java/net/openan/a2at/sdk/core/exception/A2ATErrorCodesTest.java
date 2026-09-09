package net.openan.a2at.sdk.core.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the deprecated {@link A2ATErrorCodes} forwarding constants.
 *
 * <p>Tests cover the following scenarios:
 *
 * <ul>
 *   <li>Every deprecated constant forwards to the layered {@code domain.semantic} code of its replacement
 *       {@link ErrorCatalog} entry
 * </ul>
 *
 * @since 2026-06
 */
@SuppressWarnings("removal")
class A2ATErrorCodesTest {

    /**
     * Verifies that {@link A2ATErrorCodes} forwards exactly the twenty-one legacy constants to their catalog
     * replacements.
     *
     * <p>Scenario: Reflection inspects all declared static final String fields of the deprecated registry. Expected
     * result: Each field value equals the code of the catalog entry that replaced it.
     *
     * @throws IllegalAccessException if a declared field cannot be read
     */
    @Test
    void should_forwardEveryConstantToItsCatalogReplacement_When_reflectingOverDeclaredFields()
            throws IllegalAccessException {
        Map<String, String> constants = new TreeMap<>();
        for (Field field : A2ATErrorCodes.class.getDeclaredFields()) {
            if (field.getType() == String.class
                    && Modifier.isStatic(field.getModifiers())
                    && Modifier.isFinal(field.getModifiers())) {
                field.setAccessible(true);
                constants.put(field.getName(), (String) field.get(null));
            }
        }

        assertEquals(
                Map.ofEntries(
                        Map.entry("SDK_INTERNAL_ERROR", ErrorCatalog.INFRA_INTERNAL_ERROR.getCode()),
                        Map.entry("PARAM_EXTRACTION_FAILED", ErrorCatalog.SLOT_NOT_PROVIDED.getCode()),
                        Map.entry("INPUT_TEXT_TOO_LONG", ErrorCatalog.INPUT_TEXT_TOO_LONG.getCode()),
                        Map.entry("TEMPLATE_NOT_FOUND", ErrorCatalog.TEMPLATE_NOT_FOUND.getCode()),
                        Map.entry(
                                "NEGOTIATION_CONTENT_EXTRACT_FAILED",
                                ErrorCatalog.NEGOTIATION_CONTENT_EXTRACT_FAILED.getCode()),
                        Map.entry(
                                "NEGOTIATION_SEMANTIC_REJECTED", ErrorCatalog.NEGOTIATION_SEMANTIC_REJECTED.getCode()),
                        Map.entry("NEGOTIATION_RULE_VIOLATION", ErrorCatalog.NEGOTIATION_RULE_VIOLATION.getCode()),
                        Map.entry("NEGOTIATION_SLOT_MISSING", ErrorCatalog.NEGOTIATION_FIELD_MISSING.getCode()),
                        Map.entry("NEGOTIATION_INVALID_INPUT", ErrorCatalog.NEGOTIATION_INVALID_INPUT.getCode()),
                        Map.entry("NEGOTIATION_LLM_INFRASTRUCTURE_ERROR", ErrorCatalog.LLM_INVOCATION_FAILED.getCode()),
                        Map.entry("VALIDATION_INVALID_INPUT", ErrorCatalog.NEGOTIATION_INVALID_INPUT.getCode()),
                        Map.entry("VALIDATION_RULE_VIOLATION", ErrorCatalog.NEGOTIATION_RULE_VIOLATION.getCode()),
                        Map.entry("VALIDATION_SEMANTIC_REJECTED", ErrorCatalog.NEGOTIATION_SEMANTIC_REJECTED.getCode()),
                        Map.entry("VALIDATION_LLM_INFRASTRUCTURE_ERROR", ErrorCatalog.LLM_INVOCATION_FAILED.getCode()),
                        Map.entry("VALIDATION_PROMPT_RESOURCE_NOT_FOUND", ErrorCatalog.TEMPLATE_NOT_FOUND.getCode()),
                        Map.entry("PROMPT_RESOURCE_LOAD_ERROR", ErrorCatalog.TEMPLATE_LOAD_FAILED.getCode()),
                        Map.entry("SLOT_SCHEMA_NOT_FOUND", ErrorCatalog.SLOT_SCHEMA_NOT_FOUND.getCode()),
                        Map.entry("LLM_INVOCATION_FAILED", ErrorCatalog.LLM_INVOCATION_FAILED.getCode()),
                        Map.entry("RENDER_FAILED", ErrorCatalog.TEMPLATE_RENDER_FAILED.getCode()),
                        Map.entry("SLOT_VALIDATION_ERROR", ErrorCatalog.SLOT_RULE_VIOLATION.getCode()),
                        Map.entry("PROCESSED_PROMPT_PARSE_ERROR", ErrorCatalog.SCENARIO_NOT_MATCHED.getCode())),
                constants);
    }
}
