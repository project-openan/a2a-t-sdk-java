package net.openan.a2at.sdk.core.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link A2ATError}.
 *
 * <p>Tests cover the following scenarios:
 *
 * <ul>
 *   <li>Default error code when no explicit code is provided
 *   <li>Explicit error code forwarding
 *   <li>Null safety for the explicit code
 * </ul>
 *
 * @since 2026-08
 */
class A2ATErrorTest {

    /**
     * Verifies that {@link A2ATError#A2ATError(String)} and {@link A2ATError#A2ATError(String, Throwable)} fall back to
     * {@link ErrorCatalog#INFRA_INTERNAL_ERROR}.
     *
     * <p>Scenario: Create A2A-T errors without an explicit code. Expected result: getCode() returns
     * {@code infra.internal_error} and is never null.
     */
    @Test
    void should_defaultToInfraInternalErrorCode_When_createdWithoutExplicitCode() {
        assertEquals(ErrorCatalog.INFRA_INTERNAL_ERROR.getCode(), new A2ATError("boom").getCode());
        assertEquals(
                ErrorCatalog.INFRA_INTERNAL_ERROR.getCode(),
                new A2ATError("boom", new IllegalStateException("root")).getCode());
    }

    /**
     * Verifies that {@link A2ATError#A2ATError(String, String)} and {@link A2ATError#A2ATError(String, String,
     * Throwable)} carry the explicitly provided code.
     *
     * <p>Scenario: Create A2A-T errors with an explicit code. Expected result: getCode() returns the explicit code,
     * getMessage() returns the message and getCause() returns the cause when provided.
     */
    @Test
    void should_carryExplicitCode_When_createdWithExplicitCode() {
        String templateNotFound = ErrorCatalog.TEMPLATE_NOT_FOUND.getCode();
        assertEquals(templateNotFound, new A2ATError(templateNotFound, "missing").getCode());
        assertEquals("missing", new A2ATError(templateNotFound, "missing").getMessage());
        assertNull(new A2ATError(templateNotFound, "missing").getCause());

        IllegalStateException cause = new IllegalStateException("root");
        A2ATError withCause = new A2ATError(templateNotFound, "missing", cause);
        assertEquals(templateNotFound, withCause.getCode());
        assertEquals(cause, withCause.getCause());
    }

    /**
     * Verifies that the explicit-code constructors reject null codes.
     *
     * <p>Scenario: Attempt to create A2A-T errors with a null code. Expected result: NullPointerException is thrown.
     */
    @Test
    void should_throwNullPointerException_When_createdWithNullCode() {
        assertThrows(NullPointerException.class, () -> new A2ATError(null, "missing"));
        assertThrows(NullPointerException.class, () -> new A2ATError(null, "missing", new IllegalStateException()));
    }
}
