package net.openan.a2at.sdk.negotiation.validation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.openan.a2at.sdk.core.exception.ErrorCatalog;
import net.openan.a2at.sdk.core.exception.ErrorMessages;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.validation.ContentValidationException;
import net.openan.a2at.sdk.core.validation.RuleChecker;
import org.jspecify.annotations.Nullable;

/**
 * Adapter that bridges {@link NegotiationComplianceChecker} to the {@link RuleChecker} contract for one validation
 * call.
 *
 * <p>The adapter holds a {@link NegotiationComplianceChecker} and the negotiation context carried alongside the message
 * in the A2A-T metadata, delegating the {@link RuleChecker#check(String)} call to the context-aware
 * {@link NegotiationComplianceChecker#check(NegotiationContext)} method. A null context is reported as not being a
 * negotiation message. The resulting {@link NegotiationRuleCheckResult} is converted into either a map of context
 * parameters ({@code id}, {@code round}, {@code maxRounds}) or a {@link ContentValidationException} carrying the
 * catalog codes {@code negotiation.invalid_input} (no negotiation context) or {@code negotiation.rule_violation}
 * (context rule violation) with messages rendered in the configured language.
 *
 * @since 2026-08
 */
public final class NegotiationRuleCheckerAdapter implements RuleChecker {

    private static final String MISSING_CONTEXT_REASON_EN =
            "the negotiation context is missing (the message is not a negotiation message)";

    private static final String MISSING_CONTEXT_REASON_ZH = "缺少协商上下文(该报文不是协商报文)";

    private final NegotiationComplianceChecker checker;

    private final NegotiationContext context;

    private final String language;

    /**
     * Creates an adapter for one compliance checker and negotiation context rendering messages in {@code en-US}.
     *
     * @param checker compliance checker validating the negotiation context
     * @param context negotiation context carried alongside the message; null is reported as not being a negotiation
     *     message
     */
    public NegotiationRuleCheckerAdapter(NegotiationComplianceChecker checker, @Nullable NegotiationContext context) {
        this(checker, context, null);
    }

    /**
     * Creates an adapter for one compliance checker, negotiation context and message language.
     *
     * @param checker compliance checker validating the negotiation context
     * @param context negotiation context carried alongside the message; null is reported as not being a negotiation
     *     message
     * @param language message language, for example {@code zh-CN}; null or blank falls back to {@code en-US}
     */
    public NegotiationRuleCheckerAdapter(
            NegotiationComplianceChecker checker, @Nullable NegotiationContext context, @Nullable String language) {
        this.checker = Objects.requireNonNull(checker, "checker");
        this.context = context;
        this.language = language;
    }

    @Override
    public Map<String, Object> check(String prompt) {
        if (context == null) {
            Map<String, String> facts = Map.of("reason", missingContextReason());
            throw new ContentValidationException(
                    ErrorCatalog.NEGOTIATION_INVALID_INPUT.getCode(),
                    ErrorMessages.render(ErrorCatalog.NEGOTIATION_INVALID_INPUT, language, facts),
                    List.of(),
                    Map.of(),
                    null);
        }
        NegotiationRuleCheckResult result = checker.check(context);
        if (!result.passed()) {
            String message = result.errors().isEmpty()
                    ? ErrorMessages.render(ErrorCatalog.NEGOTIATION_RULE_VIOLATION, language, null)
                    : result.errors().get(0).message();
            throw new ContentValidationException(
                    ErrorCatalog.NEGOTIATION_RULE_VIOLATION.getCode(), message, result.errors());
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", context.id());
        params.put("round", context.round());
        params.put("maxRounds", context.maxRounds());
        return Collections.unmodifiableMap(params);
    }

    private String missingContextReason() {
        return language != null && language.startsWith("zh") ? MISSING_CONTEXT_REASON_ZH : MISSING_CONTEXT_REASON_EN;
    }
}
