package net.openan.a2at.sdk.core.validation;

import java.util.function.Supplier;
import net.openan.a2at.sdk.core.exception.ErrorCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lightweight retry utility for the content validation pipeline.
 *
 * <p>Only retries on {@link ContentValidationException} failures carrying an {@code llm.*} catalog code, such as
 * {@link ErrorCatalog#LLM_INVOCATION_FAILED} and {@link ErrorCatalog#LLM_RESPONSE_INVALID}. All other failure codes and
 * unknown runtime exceptions are rethrown immediately.
 *
 * @since 2026-08
 */
final class RetryUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(RetryUtil.class);

    private static final String LLM_CODE_DOMAIN = "llm.";

    private RetryUtil() {}

    /**
     * Executes one action with retry on LLM failures.
     *
     * @param <T> result type
     * @param maxAttempts maximum number of attempts, must be at least 1
     * @param stepName name of the step for logging purposes
     * @param action the action to execute
     * @return the result of the action
     * @throws ContentValidationException if the action fails on every attempt with a retryable error, or if it fails
     *     with a non-retryable error
     */
    public static <T> T withRetry(int maxAttempts, String stepName, Supplier<T> action) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return action.get();
            } catch (ContentValidationException exception) {
                if (!isRetryableLlmFailure(exception)) {
                    throw exception;
                }
                if (attempt == maxAttempts) {
                    LOGGER.atWarn()
                            .log(
                                    "{}_retry_exhausted attempts={} last_error={}",
                                    stepName,
                                    attempt,
                                    exception.getMessage());
                    throw exception;
                }
                LOGGER.atWarn()
                        .log(
                                "{}_retry_attempt attempt={}/{} error={}",
                                stepName,
                                attempt,
                                maxAttempts,
                                exception.getMessage());
            } catch (RuntimeException exception) {
                throw exception;
            }
        }
        throw new IllegalStateException("unreachable");
    }

    private static boolean isRetryableLlmFailure(ContentValidationException exception) {
        return exception.getCode().startsWith(LLM_CODE_DOMAIN);
    }
}
