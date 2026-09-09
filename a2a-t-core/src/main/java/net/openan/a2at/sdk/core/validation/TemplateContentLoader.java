package net.openan.a2at.sdk.core.validation;

import org.jspecify.annotations.NonNull;

/**
 * Loads the template text for a template reference during validation.
 *
 * <p>Injected into {@link ValidationPipeline} as an optional gate that runs after the rule gate and before semantic
 * validation. Loaders resolve the template the content is validated against, so the semantic validator can compare the
 * content against the authoritative template body.
 *
 * @param <T> template addressing type the loader resolves
 * @since 2026-08
 */
@FunctionalInterface
public interface TemplateContentLoader<T> {

    /**
     * Loads the template text for the given template reference.
     *
     * @param reference template addressing value to resolve
     * @return loaded template text
     * @throws net.openan.a2at.sdk.core.exception.ResourceNotFoundException if the template cannot be resolved
     */
    @NonNull
    String load(@NonNull T reference);
}
