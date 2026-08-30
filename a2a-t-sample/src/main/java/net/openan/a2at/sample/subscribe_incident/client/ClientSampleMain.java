package net.openan.a2at.sample.subscribe_incident.client;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.openan.a2at.sample.subscribe_incident.client.flow.ClientSampleFlow;
import net.openan.a2at.sample.subscribe_incident.client.runtime.A2AJavaClientRuntime;
import net.openan.a2at.sample.subscribe_incident.client.runtime.DefaultSampleClientRuntime;
import net.openan.a2at.sample.subscribe_incident.client.runtime.SampleClientRuntime;
import net.openan.a2at.sample.subscribe_incident.client.runtime.SampleClientRuntimeFactory;
import net.openan.a2at.sample.subscribe_incident.shared.mock.SampleMockLlmInstaller;
import net.openan.a2at.sample.subscribe_incident.shared.scenario.SampleScenarioLoader;

/**
 * Main entry orchestration for the client sample.
 *
 * @since 2026-05
 */
public final class ClientSampleMain {

    private static final String MOCK_RESOURCE_ROOT = "sample/subscribe-incident/mock_responses";

    private ClientSampleMain() {}

    public static Path resolveEnvPath(String[] args) {
        return args.length > 0 ? Path.of(args[0]) : DefaultSampleClientRuntime.resolveDefaultEnvPath();
    }

    public static List<Map<String, Object>> runMain(Path envPath, SampleClientRuntimeFactory runtimeFactory) {
        return runMain(envPath, runtimeFactory, null);
    }

    public static List<Map<String, Object>> runMain(
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
            Map<String, Object> scenarioPayload =
                    SampleScenarioLoader.loadClasspathScenario("sample/subscribe-incident/client/scenario.json");
            return ClientSampleFlow.runClientFlow(
                    scenarioPayload,
                    runtime.registryClient(),
                    runtime.promptClient(),
                    a2aRuntime,
                    runtime.endpointCache(),
                    logSink,
                    resolveMaxArtifacts(),
                    resolveLanguage(resolvedEnvPath));
        } finally {
            runtime.close();
        }
    }

    public static void main(String[] args) {
        runMain(resolveEnvPath(args), DefaultSampleClientRuntime::new, System.out::println);
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

    private static String resolveLanguage(Path envPath) {
        if (envPath == null || !java.nio.file.Files.exists(envPath)) {
            return "zh-CN";
        }
        try {
            for (String rawLine : java.nio.file.Files.readAllLines(envPath)) {
                String line = rawLine.trim();
                if (line.startsWith("\uFEFF")) {
                    line = line.substring(1).trim();
                }
                if (line.startsWith("A2AT_LANGUAGE=") && line.length() > "A2AT_LANGUAGE=".length()) {
                    String value = line.substring("A2AT_LANGUAGE=".length()).trim();
                    if (!value.isEmpty()) {
                        return value;
                    }
                }
            }
        } catch (java.io.IOException exception) {
            // fall through to default
        }
        return "zh-CN";
    }

    private static void emit(Consumer<String> logSink, String message) {
        if (logSink != null) {
            logSink.accept(message);
        }
    }
}
