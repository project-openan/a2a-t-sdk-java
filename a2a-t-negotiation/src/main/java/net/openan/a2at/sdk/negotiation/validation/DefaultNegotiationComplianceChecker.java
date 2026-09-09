package net.openan.a2at.sdk.negotiation.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import net.openan.a2at.sdk.core.exception.ErrorCatalog;
import net.openan.a2at.sdk.core.exception.ErrorMessages;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.SlotValidationError;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default rule-level compliance checker for negotiation messages.
 *
 * <p>The checker validates the negotiation context carried alongside the message in the A2A-T metadata: the id must be
 * a UUID of 36 characters in 8-4-4-4-12 hexadecimal form and the round must not exceed the round budget. Error messages
 * are rendered from the {@link ErrorCatalog} message templates in the configured language. The positive-integer shape
 * of the round fields is already guaranteed by the {@link NegotiationContext} constructor. No other rules are applied:
 * the checker does not infer the negotiation type, does not validate conclusion values, does not require ending result
 * sections and does not check conditional-section exclusivity.
 *
 * @since 2026-08
 */
public final class DefaultNegotiationComplianceChecker implements NegotiationComplianceChecker {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultNegotiationComplianceChecker.class);

    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private static final String ID_SLOT = "id";

    private static final String ROUND_SLOT = "round";

    private final String language;

    /** Creates the default checker rendering messages in {@code en-US}. */
    public DefaultNegotiationComplianceChecker() {
        this(null);
    }

    /**
     * Creates the default checker rendering messages in one language.
     *
     * @param language message language, for example {@code zh-CN}; null or blank falls back to {@code en-US}
     */
    public DefaultNegotiationComplianceChecker(@Nullable String language) {
        this.language = language;
    }

    /**
     * Runs the rule-level compliance check of one negotiation context.
     *
     * @param context negotiation context carried alongside the message in the A2A-T metadata
     * @return rule check outcome with no errors when every context rule holds, otherwise the context rule errors
     * @throws NullPointerException if the context is null
     */
    @Override
    public NegotiationRuleCheckResult check(NegotiationContext context) {
        Objects.requireNonNull(context, "context");
        List<SlotValidationError> errors = new ArrayList<>();
        collectIdErrors(context.id(), errors);
        if (context.round() > context.maxRounds()) {
            errors.add(new SlotValidationError(
                    ROUND_SLOT,
                    ErrorCatalog.NEGOTIATION_ROUND_EXCEEDED.getCode(),
                    ErrorMessages.render(ErrorCatalog.NEGOTIATION_ROUND_EXCEEDED, language, roundFacts(context)),
                    roundFacts(context)));
        }
        return logResult(new NegotiationRuleCheckResult(errors.isEmpty(), List.copyOf(errors)));
    }

    private static Map<String, String> roundFacts(NegotiationContext context) {
        return Map.of(
                "round", String.valueOf(context.round()),
                "max_rounds", String.valueOf(context.maxRounds()));
    }

    private void collectIdErrors(String id, List<SlotValidationError> errors) {
        if (!UUID_PATTERN.matcher(id).matches()) {
            Map<String, String> facts = Map.of("actual", id);
            errors.add(new SlotValidationError(
                    ID_SLOT,
                    ErrorCatalog.NEGOTIATION_INVALID_CONTEXT_ID.getCode(),
                    ErrorMessages.render(ErrorCatalog.NEGOTIATION_INVALID_CONTEXT_ID, language, facts),
                    facts));
        }
    }

    /**
     * Emits the rule-check completion event of one check outcome.
     *
     * @param result rule check outcome to log
     * @return the unchanged outcome
     */
    private static NegotiationRuleCheckResult logResult(NegotiationRuleCheckResult result) {
        if (result.errors().isEmpty()) {
            LOGGER.atDebug().log("negotiation_rule_checks_completed passed={} error_count=0", result.passed());
        } else {
            LOGGER.atWarn()
                    .log(
                            "negotiation_rule_checks_completed passed={} error_count={}",
                            result.passed(),
                            result.errors().size());
        }
        return result;
    }
}
