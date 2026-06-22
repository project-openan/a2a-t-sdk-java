package net.openan.a2at.sample.server.runtime;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import net.openan.a2at.sample.server.agentcard.ServerSampleAgentCardBuilder;
import net.openan.a2at.sample.server.flow.PromptComplianceChecker;
import net.openan.a2at.sample.server.flow.ServerSampleAgentExecutor;
import net.openan.a2at.sample.server.registry.ServerRegistryClient;
import net.openan.a2at.sample.shared.env.SampleEnvironmentPathResolver;
import net.openan.a2at.sample.shared.error.ValueErrorException;
import net.openan.a2at.sample.shared.registry.RegistryAgentCardMapper;
import net.openan.a2at.sdk.server.A2ATServer;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.events.InMemoryQueueManager;
import org.a2aproject.sdk.server.events.MainEventBus;
import org.a2aproject.sdk.server.events.MainEventBusProcessor;
import org.a2aproject.sdk.server.requesthandlers.DefaultRequestHandler;
import org.a2aproject.sdk.server.requesthandlers.RequestHandler;
import org.a2aproject.sdk.server.tasks.BasePushNotificationSender;
import org.a2aproject.sdk.server.tasks.InMemoryPushNotificationConfigStore;
import org.a2aproject.sdk.server.tasks.InMemoryTaskStore;
import org.a2aproject.sdk.server.tasks.PushNotificationConfigStore;
import org.a2aproject.sdk.server.tasks.PushNotificationSender;
import org.a2aproject.sdk.server.tasks.TaskStore;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentExtension;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentProvider;
import org.a2aproject.sdk.spec.AgentSkill;

/**
 * Default runtime assembly for the server sample entrypoint.
 *
 * @since 2026-05
 */
public final class DefaultSampleServerRuntime implements SampleServerRuntime, A2AJavaServerRuntime {
    private static final String SAMPLE_THREAD_NAME = "a2a-t-sample-server";

    private final Path envPath;

    private final Consumer<String> logSink;

    private final Executor sampleExecutor;

    public DefaultSampleServerRuntime(Path envPath) {
        this(envPath, System.out::println);
    }

    public DefaultSampleServerRuntime(Path envPath, Consumer<String> logSink) {
        this.envPath = envPath;
        this.logSink = logSink;
        this.sampleExecutor = command -> {
            Thread thread = createSampleThread(command, this.logSink);
            thread.start();
        };
    }

    @Override
    public ServerBind resolveBind() {
        Map<String, String> envValues = ServerRegistryClient.parseEnvFile(envPath);
        String host = envValues.getOrDefault("A2AT_SAMPLE_HOST", "127.0.0.1");
        String portValue = envValues.getOrDefault("A2AT_SAMPLE_PORT", "8000");
        try {
            return new ServerBind(host, Integer.parseInt(portValue));
        } catch (NumberFormatException exception) {
            throw new ValueErrorException("Invalid A2AT_SAMPLE_PORT: " + portValue);
        }
    }

    @Override
    public Map<String, Object> buildAgentCard(String host, int port) {
        return ServerSampleAgentCardBuilder.buildAgentCard(host, port);
    }

    @Override
    public PromptComplianceChecker buildPromptChecker(Path envPath) {
        A2ATServer server = new A2ATServer(envPath);
        return server::checkTaskPrompt;
    }

    @Override
    public Object buildApp(Map<String, Object> agentCard, PromptComplianceChecker promptChecker) {
        TaskStore taskStore = new InMemoryTaskStore();
        MainEventBus mainEventBus = new MainEventBus();
        InMemoryQueueManager queueManager = new InMemoryQueueManager((InMemoryTaskStore) taskStore, mainEventBus);
        PushNotificationConfigStore pushNotificationConfigStore = new InMemoryPushNotificationConfigStore();
        PushNotificationSender pushNotificationSender = new BasePushNotificationSender(pushNotificationConfigStore);
        MainEventBusProcessor mainEventBusProcessor =
                new MainEventBusProcessor(mainEventBus, taskStore, pushNotificationSender, queueManager);
        startMainEventBusProcessor(mainEventBusProcessor);
        AgentExecutor agentExecutor = new ServerSampleAgentExecutor(
                promptChecker,
                delaySeconds -> {
                    try {
                        Thread.sleep(delaySeconds * 1000L);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(exception);
                    }
                },
                buildMockIncidentArtifactData(),
                logSink);
        RequestHandler requestHandler = DefaultRequestHandler.create(
                agentExecutor,
                taskStore,
                queueManager,
                pushNotificationConfigStore,
                mainEventBusProcessor,
                sampleExecutor,
                sampleExecutor);
        return requestHandler;
    }

    @Override
    public Map<String, Object> registerAgentCard(Map<String, Object> registrationPayload, Path envPath) {
        return ServerRegistryClient.registerAgentCard(
                registrationPayload,
                ServerRegistryClient.resolveRegistryBaseUrl(envPath),
                ServerRegistryClient.httpTransport(),
                null);
    }

    public static Path resolveDefaultEnvPath() {
        Path sampleEnvDir = Path.of("src", "main", "resources", "sample", "server");
        return SampleEnvironmentPathResolver.resolve(sampleEnvDir, "server.env", "server.env");
    }

    static Map<String, Object> buildMockIncidentArtifactData() {
        return Map.of("faultManagement.Incident", Map.ofEntries(
                Map.entry("csn", 1673735459373056L),
                Map.entry("name", "LASER_MOD_ERR"),
                Map.entry("domain", "PTN"),
                Map.entry("priority", "high"),
                Map.entry("occurTime", "2026-04-28T07:21:00Z"),
                Map.entry("createTime", "2026-04-28T07:29:19Z"),
                Map.entry("updateTime", "2026-04-28T12:35:15Z"),
                Map.entry("status", "unacknowledged-and-uncleared"),
                Map.entry("category", "Line"),
                Map.entry(
                        "sourceObjects",
                        List.of(Map.of(
                                "id", "9fc7ee3b-e4fb-450e-87d3-e03f027a4f64",
                                "type", "network-element",
                                "location", "Level1",
                                "name", "HUAWEI40-SPE",
                                "subObjList", List.of(Map.of(
                                        "id", "36f6f0e4-9fc3-4508-bc50-fa1831a7c179",
                                        "type", "ltp",
                                        "name", "1-TPA1EG24-15(M)"))))),
                Map.entry(
                        "rootCauses",
                        List.of(Map.of(
                                "name", "The connected peer network element on HUAWEI40-SPE 1-TPA1EG24-15(M)-MAC:1 is down.",
                                "repairAdvice", "Check the network element power connection state and restore power supply.",
                                "detailInformation",
                                "The connected peer network element on HUAWEI40-SPE 1-TPA1EG24-15(M)-MAC:1 is down.",
                                "rootCauseObj", Map.of(
                                        "id", "9fc7ee3b-e4fb-450e-87d3-e03f027a4f64",
                                        "type", "network-element",
                                        "name", "HUAWEI40-SPE",
                                        "location", "Level1",
                                        "subObjList", List.of(Map.of(
                                                "id", "36f6f0e4-9fc3-4508-bc50-fa1831a7c179",
                                                "type", "FixedNetworkLTP",
                                                "name", "1-TPA1EG24-15(M)")))))),
                Map.entry(
                        "detail",
                        "Intelligent diagnosis result:\n"
                                + "The connected peer network element on HUAWEI40-SPE 1-TPA1EG24-15(M)-MAC:1 is down.\n"
                                + "Fault detail:\n"
                                + "HUAWEI40-SPE optical module fault, location info: 1-TPA1EG24-15(M)-LASER:1.\n"
                                + "The user-side port 1-TPA1EG24-15(M)-MAC:1 on HUAWEI40-SPE is abnormal."),
                Map.entry("repairAdvice", "Check the network element power connection state and restore power supply."),
                Map.entry("messageType", "update"),
                Map.entry("rootEventCsns", List.of(Map.of("csn", "524261", "type", "0")))));
    }

    static Thread createSampleThread(Runnable command, Consumer<String> logSink) {
        Thread thread = new Thread(command, SAMPLE_THREAD_NAME);
        thread.setDaemon(true);
        thread.setUncaughtExceptionHandler((failedThread, throwable) -> {
            if (logSink != null) {
                logSink.accept("[server] sample-executor-uncaught-exception: "
                        + failedThread.getName()
                        + ": "
                        + throwable.getClass().getName()
                        + ": "
                        + throwable.getMessage());
            }
        });
        return thread;
    }

    private void startMainEventBusProcessor(MainEventBusProcessor mainEventBusProcessor) {
        try {
            java.lang.reflect.Method startMethod = MainEventBusProcessor.class.getDeclaredMethod("start");
            startMethod.setAccessible(true);
            startMethod.invoke(mainEventBusProcessor);
            if (logSink != null) {
                logSink.accept("[server] event-bus-processor: started");
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to start MainEventBusProcessor", exception);
        }
    }

    @Override
    public Object createRestApplication(String host, int port) {
        return RegistryAgentCardMapper.toA2AJavaAgentCard(ServerSampleAgentCardBuilder.buildAgentCard(host, port));
    }
}


