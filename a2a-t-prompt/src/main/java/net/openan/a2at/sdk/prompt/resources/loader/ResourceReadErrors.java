package net.openan.a2at.sdk.prompt.resources.loader;

import java.util.Map;
import net.openan.a2at.sdk.core.exception.A2ATError;
import net.openan.a2at.sdk.core.exception.ErrorCatalog;
import net.openan.a2at.sdk.core.exception.ErrorMessages;
import org.jspecify.annotations.Nullable;

/**
 * Creates infra failures for prompt resources that cannot be read from their configured source.
 *
 * @since 2026-08
 */
final class ResourceReadErrors {

    private ResourceReadErrors() {}

    /**
     * Creates an {@code infra.resource_read_failed} failure for one resource path.
     *
     * @param resourcePath path of the resource that could not be read
     * @param language language used to render the failure message, for example {@code zh-CN}; null falls back to
     *     {@code en-US}
     * @param cause root cause, may be null
     * @return infra failure carrying the {@code infra.resource_read_failed} code
     */
    static A2ATError readFailed(String resourcePath, @Nullable String language, @Nullable Throwable cause) {
        return new A2ATError(
                ErrorCatalog.INFRA_RESOURCE_READ_FAILED.getCode(),
                ErrorMessages.render(
                        ErrorCatalog.INFRA_RESOURCE_READ_FAILED, language, Map.of("resource_path", resourcePath)),
                cause);
    }
}
