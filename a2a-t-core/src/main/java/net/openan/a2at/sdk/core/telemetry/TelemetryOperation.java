package net.openan.a2at.sdk.core.telemetry;

/** One observed SDK operation; AutoCloseable for try-with-resources. */
public interface TelemetryOperation extends AutoCloseable {

    /** Attaches one attribute to the underlying observation. No-op values are ignored. */
    void setAttribute(String key, Object value);

    /** Marks the operation as business-successful before close. */
    void markSuccess();

    /** Marks the operation as failed; the Throwable is recorded verbatim. */
    void markError(Throwable error);

    /** Ends the operation. Idempotent. */
    @Override
    void close();

    TelemetryOperation NO_OP = new TelemetryOperation() {
        @Override
        public void setAttribute(String key, Object value) {}

        @Override
        public void markSuccess() {}

        @Override
        public void markError(Throwable error) {}

        @Override
        public void close() {}
    };
}
