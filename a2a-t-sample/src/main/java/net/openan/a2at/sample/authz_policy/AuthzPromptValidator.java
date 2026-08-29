package net.openan.a2at.sample.authz_policy;

import java.util.Map;
import net.openan.a2at.sdk.core.model.FilledParamData;

/**
 * Functional interface for validating an authorization prompt and extracting filled slot data.
 *
 * @since 2026-08
 */
@FunctionalInterface
public interface AuthzPromptValidator {

    FilledParamData validate(String prompt, Map<String, Object> schema, String templateUri);
}