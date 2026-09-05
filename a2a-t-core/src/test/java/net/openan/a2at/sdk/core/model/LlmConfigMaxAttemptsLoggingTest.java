package net.openan.a2at.sdk.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * Unit tests for the warning logs emitted while parsing {@code A2AT_LLM_MAX_ATTEMPTS}.
 *
 * <p>The value semantics themselves (in-range pass-through, clamping to 1 and 10, fallback to the default of 3 for
 * unparseable or blank values) are covered by {@link LlmConfigTest}; this suite locks the warning side of the contract:
 * every clamp and every fallback emits one warning carrying the raw configured value and the resolved value, and
 * well-formed values emit no warning at all.
 */
class LlmConfigMaxAttemptsLoggingTest {

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private final Logger logger = (Logger) LoggerFactory.getLogger(LlmConfig.class);

    @BeforeEach
    void attachAppender() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    /**
     * Verifies that a value below the allowed range clamps to 1 with one warning carrying the raw value and the clamped
     * value.
     */
    @Test
    void should_warnWithRawValue_When_maxAttemptsIsBelowTheLowerBound() {
        LlmConfig config = LlmConfig.fromMap(Map.of("A2AT_LLM_MAX_ATTEMPTS", "0"));

        assertEquals(1, config.maxAttempts());
        List<String> warnings = warningMessages();
        assertEquals(1, warnings.size());
        assertContains(warnings.get(0), "key=A2AT_LLM_MAX_ATTEMPTS", "raw_value=0", "clamped_value=1");
    }

    /**
     * Verifies that a value above the allowed range clamps to 10 with one warning carrying the raw value and the
     * clamped value.
     */
    @Test
    void should_warnWithRawValue_When_maxAttemptsIsAboveTheUpperBound() {
        LlmConfig config = LlmConfig.fromMap(Map.of("A2AT_LLM_MAX_ATTEMPTS", "99"));

        assertEquals(10, config.maxAttempts());
        List<String> warnings = warningMessages();
        assertEquals(1, warnings.size());
        assertContains(warnings.get(0), "key=A2AT_LLM_MAX_ATTEMPTS", "raw_value=99", "clamped_value=10");
    }

    /**
     * Verifies that an unparseable value falls back to the default of 3 with one warning carrying the raw value and the
     * default value.
     */
    @Test
    void should_warnWithRawValue_When_maxAttemptsIsUnparseable() {
        LlmConfig config = LlmConfig.fromMap(Map.of("A2AT_LLM_MAX_ATTEMPTS", "garbage"));

        assertEquals(3, config.maxAttempts());
        List<String> warnings = warningMessages();
        assertEquals(1, warnings.size());
        assertContains(warnings.get(0), "key=A2AT_LLM_MAX_ATTEMPTS", "raw_value=garbage", "default_value=3");
    }

    /** Verifies that in-range values and missing or blank values resolve without any warning. */
    @Test
    void should_notWarn_When_maxAttemptsIsInRangeAbsentOrBlank() {
        assertEquals(5, LlmConfig.fromMap(Map.of("A2AT_LLM_MAX_ATTEMPTS", "5")).maxAttempts());
        assertEquals(1, LlmConfig.fromMap(Map.of("A2AT_LLM_MAX_ATTEMPTS", "1")).maxAttempts());
        assertEquals(
                10, LlmConfig.fromMap(Map.of("A2AT_LLM_MAX_ATTEMPTS", "10")).maxAttempts());
        assertEquals(3, LlmConfig.fromMap(Map.of()).maxAttempts());
        assertEquals(3, LlmConfig.fromMap(Map.of("A2AT_LLM_MAX_ATTEMPTS", " ")).maxAttempts());

        assertTrue(warningMessages().isEmpty(), "well-formed or absent values must not emit a warning");
    }

    private List<String> warningMessages() {
        return appender.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private static void assertContains(String message, String... fragments) {
        for (String fragment : fragments) {
            assertTrue(message.contains(fragment), "expected [" + message + "] to contain [" + fragment + "]");
        }
    }
}
