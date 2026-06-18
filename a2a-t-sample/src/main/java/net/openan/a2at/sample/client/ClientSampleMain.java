package net.openan.a2at.sample.client;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.openan.a2at.sample.client.flow.ClientSampleFlow;
import net.openan.a2at.sample.client.runtime.A2AJavaClientRuntime;
import net.openan.a2at.sample.client.runtime.DefaultSampleClientRuntime;
import net.openan.a2at.sample.client.runtime.SampleClientRuntime;
import net.openan.a2at.sample.client.runtime.SampleClientRuntimeFactory;
import net.openan.a2at.sample.shared.scenario.SampleScenarioLoader;

/**
 * Main entry orchestration for the client sample.
 *
 * @since 2026-05
 */
public final class ClientSampleMain {

    private ClientSampleMain() {
    }

    public static Path resolveEnvPath(String[] args) {
        return args.length > 0 ? Path.of(args[0]) : DefaultSampleClientRuntime.resolveDefaultEnvPath();
    }

    public static List<Map<String, Object>> runMain(Path envPath, SampleClientRuntimeFactory runtimeFactory) {
        return runMain(envPath, runtimeFactory, null);
    }

    public static List<Map<String, Object>> runMain(
            Path envPath,
            SampleClientRuntimeFactory runtimeFactory,
            Consumer<String> logSink) {
        SampleClientRuntime runtime = runtimeFactory.create(envPath);
        try {
            if (!(runtime instanceof A2AJavaClientRuntime a2aRuntime)) {
                throw new IllegalStateException("Sample client runtime must implement A2AJavaClientRuntime");
            }
            Map<String, Object> scenarioPayload = SampleScenarioLoader.loadClasspathScenario("sample/client/scenario.json");
            return ClientSampleFlow.runClientFlow(
                    scenarioPayload,
                    runtime.registryClient(),
                    runtime.promptClient(),
                    a2aRuntime,
                    runtime.endpointCache(),
                    logSink);
        } finally {
            runtime.close();
        }
    }

    public static void main(String[] args) {
        runMain(resolveEnvPath(args), DefaultSampleClientRuntime::new, System.out::println);
    }
}

