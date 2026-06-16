package net.openan.a2at.sdk.core.exception;

/**
 * Raised when a referenced SDK resource cannot be resolved.
 *
 * @since 2026-05
 */
public final class ResourceNotFoundException extends SdkException {

    private final String resourcePath;

    /**
     * Creates a resource-not-found exception for one SDK classpath resource.
     *
     * @param message failure message
     * @param resourcePath missing classpath resource path
     */
    public ResourceNotFoundException(String message, String resourcePath) {
        super(message);
        this.resourcePath = resourcePath;
    }

    /**
     * Returns the missing classpath resource path.
     *
     * @return missing resource path
     */
    public String resourcePath() {
        return resourcePath;
    }
}
