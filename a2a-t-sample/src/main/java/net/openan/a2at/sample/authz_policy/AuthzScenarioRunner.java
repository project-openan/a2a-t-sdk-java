package net.openan.a2at.sample.authz_policy;

import java.util.Map;
import net.openan.a2at.sdk.core.exception.A2ATError;
import net.openan.a2at.sdk.core.exception.A2ATErrorCodes;
import net.openan.a2at.sdk.core.exception.PromptGenerationException;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.core.validation.ContentValidationException;

public final class AuthzScenarioRunner {

    static final String VERDICT_PASS = "PASS";
    static final String VERDICT_FAIL = "FAIL";
    static final String VERDICT_ERROR = "ERROR";

    private final AuthzPromptGenerator generator;
    private final AuthzPromptValidator validator;

    public AuthzScenarioRunner(AuthzPromptGenerator generator, AuthzPromptValidator validator) {
        this.generator = generator;
        this.validator = validator;
    }

    public ScenarioOutcome run(AuthzScenario scenario, Map<String, Object> slotSchemaMap, TemplateUri templateUri) {
        MetadataContent metadata;
        try {
            metadata = generator.generate(scenario);
        } catch (PromptGenerationException e) {
            return new ScenarioOutcome(new ScenarioResult(VERDICT_FAIL, e), null, null);
        }

        FilledParamData filled;
        try {
            filled = validator.validate(metadata.promptText(), slotSchemaMap, templateUri);
        } catch (ContentValidationException e) {
            String code = e.getCode();
            if (A2ATErrorCodes.VALIDATION_SEMANTIC_REJECTED.equals(code)) {
                if (AuthzScenario.EXPECTED_REJECT.equals(scenario.expected())) {
                    return new ScenarioOutcome(new ScenarioResult(VERDICT_PASS, null), metadata, null);
                }
                return new ScenarioOutcome(new ScenarioResult(VERDICT_FAIL, e), metadata, null);
            }
            return new ScenarioOutcome(new ScenarioResult(VERDICT_ERROR, e), metadata, null);
        }

        if (AuthzScenario.EXPECTED_PASS.equals(scenario.expected())) {
            return new ScenarioOutcome(new ScenarioResult(VERDICT_PASS, null), metadata, filled);
        }
        return new ScenarioOutcome(new ScenarioResult(VERDICT_FAIL, null), metadata, filled);
    }

    public record ScenarioResult(String name, A2ATError error) {}

    public record ScenarioOutcome(ScenarioResult result, MetadataContent metadata, FilledParamData filled) {}
}
