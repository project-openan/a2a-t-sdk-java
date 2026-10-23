package net.openan.a2at.sdk.core.telemetry;

/**
 * Telemetry SPI consumed by SDK facades. Implementations live in optional observability modules; the default
 * {@link #NO_OP} keeps the core dependency-free.
 */
public interface TelemetryHook {

    /** Starts one facade-operation observation named {@code a2at.sdk.<role>.<method>}. */
    TelemetryOperation startSdkOperation(String role, String method);

    /** Reports one terminal negotiation round count (outcome: accept/reject). */
    void reportNegotiationRounds(String negotiationId, String outcome, long totalRounds);

    TelemetryHook NO_OP = new TelemetryHook() {
        @Override
        public TelemetryOperation startSdkOperation(String role, String method) {
            return TelemetryOperation.NO_OP;
        }

        @Override
        public void reportNegotiationRounds(String negotiationId, String outcome, long totalRounds) {}
    };
}
