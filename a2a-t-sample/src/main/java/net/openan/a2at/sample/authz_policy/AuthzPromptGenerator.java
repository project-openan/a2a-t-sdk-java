package net.openan.a2at.sample.authz_policy;

import net.openan.a2at.sdk.core.model.MetadataContent;

/**
 * Functional interface for generating an authorization prompt from a scenario.
 *
 * @since 2026-08
 */
@FunctionalInterface
public interface AuthzPromptGenerator {

    MetadataContent generate(AuthzScenario scenario);
}
