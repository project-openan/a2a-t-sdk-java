package net.openan.a2at.sample.service_recovery.client;

import java.nio.file.Path;
import java.util.function.Consumer;
import net.openan.a2at.sample.service_recovery.VerificationCheck;
import net.openan.a2at.sample.service_recovery.client.flow.ClientFlowOutcome;
import net.openan.a2at.sample.service_recovery.client.flow.ClientSampleFlow;
import net.openan.a2at.sample.service_recovery.client.runtime.A2AJavaClientRuntime;
import net.openan.a2at.sample.service_recovery.client.runtime.DefaultSampleClientRuntime;
import net.openan.a2at.sample.service_recovery.client.runtime.SampleClientRuntime;
import net.openan.a2at.sample.service_recovery.client.runtime.SampleClientRuntimeFactory;
import net.openan.a2at.sample.service_recovery.shared.logging.SampleLoggingFormatter;
import net.openan.a2at.sample.service_recovery.shared.mock.SampleMockLlmInstaller;

/**
 * Main entry orchestration for the service-recovery client sample.
 *
 * <p>The process exits with code 0 when every verification check of the flow passes and 1 otherwise.
 *
 * @since 2026-08
 */
public final class ClientSampleMain {

    private static final String MOCK_RESOURCE_ROOT = "sample/service-recovery/mock_responses";

    private ClientSampleMain() {}

    /**
     * Resolves the sample environment file path, preferring an explicit argument.
     *
     * @param args command line arguments, only the first one is used
     * @return resolved environment file path
     */
    public static Path resolveEnvPath(String[] args) {
        return args.length > 0 ? Path.of(args[0]) : DefaultSampleClientRuntime.resolveDefaultEnvPath();
    }

    /**
     * Runs the client flow and returns its outcome.
     *
     * @param envPath sample environment file path
     * @param runtimeFactory client runtime factory
     * @param logSink log sink, may be null
     * @return client flow outcome with verification checks and received events
     */
    public static ClientFlowOutcome runMain(
            Path envPath, SampleClientRuntimeFactory runtimeFactory, Consumer<String> logSink) {
        boolean mockNeeded = SampleMockLlmInstaller.isMockNeeded(envPath);
        SampleMockLlmInstaller.installLlmLogger(mockNeeded, "client");
        Path resolvedEnvPath = SampleMockLlmInstaller.resolveEnvPath(envPath, MOCK_RESOURCE_ROOT);
        if (!resolvedEnvPath.equals(envPath)) {
            emit(logSink, "[client] no LLM API key found, using mock LLM responses for e2e");
        }
        SampleClientRuntime runtime = runtimeFactory.create(resolvedEnvPath);
        try {
            if (!(runtime instanceof A2AJavaClientRuntime a2aRuntime)) {
                throw new IllegalStateException("Sample client runtime must implement A2AJavaClientRuntime");
            }
            return ClientSampleFlow.runClientFlow(
                    runtime.registryClient(), runtime.promptClient(), a2aRuntime, logSink, resolveMaxArtifacts());
        } finally {
            runtime.close();
        }
    }

    /**
     * Sample entry point.
     *
     * @param args optional environment file path as the first argument
     */
    public static void main(String[] args) {
        ClientFlowOutcome outcome = runMain(resolveEnvPath(args), DefaultSampleClientRuntime::new, System.out::println);
        printSummary(outcome);
        System.exit(outcome.checks().stream().allMatch(VerificationCheck::passed) ? 0 : 1);
    }

    private static void printSummary(ClientFlowOutcome outcome) {
        long passedCount =
                outcome.checks().stream().filter(VerificationCheck::passed).count();
        long failedCount = outcome.checks().size() - passedCount;
        System.out.println("==== service-recovery client verification summary ====");
        for (VerificationCheck check : outcome.checks()) {
            System.out.println((check.passed() ? "PASS " : "FAIL ") + check.name() + " — " + check.detail());
        }
        System.out.println("passed=" + passedCount + " failed=" + failedCount);
        System.out.println(failedCount == 0 ? "VERIFICATION PASSED" : "VERIFICATION FAILED");
    }

    private static int resolveMaxArtifacts() {
        String raw = System.getenv("A2AT_SAMPLE_MAX_ARTIFACTS");
        if (raw != null) {
            String trimmed = raw.trim();
            if (!trimmed.isEmpty()) {
                try {
                    return Integer.parseInt(trimmed);
                } catch (NumberFormatException exception) {
                    // ignore and fall through to default
                }
            }
        }
        return 0;
    }

    private static void emit(Consumer<String> logSink, String message) {
        if (logSink != null) {
            logSink.accept(SampleLoggingFormatter.timestamped(message));
        }
    }
}
