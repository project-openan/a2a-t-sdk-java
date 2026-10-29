package net.openan.a2at.sdk.observability;

import io.opentelemetry.api.common.Attributes;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** a2at.negotiation.total_rounds histogram; terminal outcomes only; never raises. */
public final class NegotiationMetrics {

    public static final String METRIC_NAME = "a2at.negotiation.total_rounds";
    private static final Logger LOG = LoggerFactory.getLogger(NegotiationMetrics.class);
    private static final Set<String> OUTCOMES = Set.of("accept", "reject", "abort");

    private NegotiationMetrics() {}

    public static void reportRounds(@Nullable String negotiationId, @Nullable String outcome, int rounds) {
        try {
            if (!ObservabilityRuntime.isMetricEnabled() || rounds < 0) {
                return;
            }
            if (outcome == null || !OUTCOMES.contains(outcome.toLowerCase(java.util.Locale.ROOT))) {
                return;
            }
            var builder = Attributes.builder();
            builder.put(A2ATAttributes.ATTR_EXTENSION_NAME, A2ATAttributes.NEGOTIATION_EXTENSION);
            if (negotiationId != null && !negotiationId.isEmpty()) {
                builder.put(A2ATAttributes.ATTR_NEGOTIATION_ID, negotiationId);
            }
            builder.put("outcome", outcome.toLowerCase(java.util.Locale.ROOT));
            MetricHistograms.histogram(METRIC_NAME, "{round}", "Negotiation terminal message round count")
                    .record(rounds, builder.build());
        } catch (RuntimeException e) {
            LOG.warn("a2at: failed to report negotiation rounds", e);
        }
    }
}
