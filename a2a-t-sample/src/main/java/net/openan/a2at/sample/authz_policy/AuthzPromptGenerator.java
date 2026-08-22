package net.openan.a2at.sample.authz_policy;

import net.openan.a2at.sdk.core.model.MetadataContent;

@FunctionalInterface
public interface AuthzPromptGenerator {

    MetadataContent generate(AuthzScenario scenario);
}