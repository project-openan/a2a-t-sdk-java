package net.openan.a2at.sample.authz_policy;

import java.util.Map;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.TemplateUri;

@FunctionalInterface
public interface AuthzPromptValidator {

    FilledParamData validate(String prompt, Map<String, Object> schema, TemplateUri templateUri);
}