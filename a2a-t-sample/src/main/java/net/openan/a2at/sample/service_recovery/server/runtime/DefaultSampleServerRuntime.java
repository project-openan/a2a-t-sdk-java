package net.openan.a2at.sample.service_recovery.server.runtime;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import net.openan.a2at.sample.service_recovery.ServiceRecoverySampleInputs;
import net.openan.a2at.sample.service_recovery.server.agentcard.ServerSampleAgentCardBuilder;
import net.openan.a2at.sample.service_recovery.server.flow.NotificationPromptValidator;
import net.openan.a2at.sample.service_recovery.server.flow.ServerFlowInterruptedException;
import net.openan.a2at.sample.service_recovery.server.flow.ServerSampleAgentExecutor;
import net.openan.a2at.sample.service_recovery.server.registry.ServerRegistryClient;
import net.openan.a2at.sample.service_recovery.shared.env.SampleEnvironmentPathResolver;
import net.openan.a2at.sample.service_recovery.shared.error.ValueErrorException;
import net.openan.a2at.sample.service_recovery.shared.registry.RegistryAgentCardMapper;
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

/**
 * Default runtime assembly for the service-recovery server sample entrypoint.
 *
 * @since 2026-08
 */
public final class DefaultSampleServerRuntime implements SampleServerRuntime, A2AJavaServerRuntime {
    private static final String SAMPLE_THREAD_NAME = "a2a-t-sample-server";
    private static final int SAMPLE_THREAD_COUNT = 4;

    private final Path envPath;

    private final Consumer<String> logSink;

    private final ExecutorService sampleExecutor;

    /**
     * Creates the runtime with the default log sink.
     *
     * @param envPath resolved environment file path
     */
    public DefaultSampleServerRuntime(Path envPath) {
        this(envPath, System.out::println);
    }

    /**
     * Creates the runtime with an explicit log sink.
     *
     * @param envPath resolved environment file path
     * @param logSink log sink
     */
    public DefaultSampleServerRuntime(Path envPath, Consumer<String> logSink) {
        this.envPath = envPath;
        this.logSink = logSink;
        this.sampleExecutor = new ThreadPoolExecutor(
                SAMPLE_THREAD_COUNT,
                SAMPLE_THREAD_COUNT,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                createSampleThreadFactory(this.logSink));
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
    public NotificationPromptValidator buildNotificationValidator(Path envPath) {
        A2ATServer server = new A2ATServer(envPath);
        String templateUri = ServiceRecoverySampleInputs.TEMPLATE_URI;
        return promptText -> server.validateNotificationPromptAndDataFilling(
                promptText,
                ServiceRecoverySampleInputs.validationParamSchema(),
                templateUri);
    }

    @Override
    public Object buildApp(Map<String, Object> agentCard, NotificationPromptValidator notificationValidator) {
        InMemoryTaskStore taskStore = new InMemoryTaskStore();
        MainEventBus mainEventBus = new MainEventBus();
        InMemoryQueueManager queueManager = new InMemoryQueueManager(taskStore, mainEventBus);
        PushNotificationConfigStore pushNotificationConfigStore = new InMemoryPushNotificationConfigStore();
        PushNotificationSender pushNotificationSender = new BasePushNotificationSender(pushNotificationConfigStore);
        MainEventBusProcessor mainEventBusProcessor =
                new MainEventBusProcessor(mainEventBus, taskStore, pushNotificationSender, queueManager);
        startMainEventBusProcessor(mainEventBusProcessor);
        AgentExecutor agentExecutor = new ServerSampleAgentExecutor(
                notificationValidator,
                delaySeconds -> {
                    try {
                        Thread.sleep(delaySeconds * 1000L);
                    } catch (InterruptedException exception) {
                        throw new ServerFlowInterruptedException("Sample server sleep was interrupted.");
                    }
                },
                buildMockServiceRecoveryEventData(),
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

    /**
     * Resolves the default sample environment file path relative to the repository root.
     *
     * @return resolved environment file path
     */
    public static Path resolveDefaultEnvPath() {
        Path sampleEnvDir = Path.of("a2a-t-sample", "src", "main", "resources", "sample", "service-recovery", "server");
        return SampleEnvironmentPathResolver.resolve(sampleEnvDir, "server.env", "server.env");
    }

    /**
     * Builds the mock service recovery event artifact payload. The field structure and values are
     * loaded from the {@code mock-event-data.json} resource so the zh-CN business fields stay out
     * of the Java source.
     *
     * @return mock service recovery event data
     */
    static Map<String, Object> buildMockServiceRecoveryEventData() {
        return net.openan.a2at.sample.service_recovery.SampleResourceJson
                .load("sample/service-recovery/server/mock-event-data.json");
    }

    /**
     * Creates the sample thread factory with daemon threads and an uncaught-exception logger.
     *
     * @param logSink log sink
     * @return thread factory
     */
    static ThreadFactory createSampleThreadFactory(Consumer<String> logSink) {
        AtomicLong sequence = new AtomicLong(0L);
        return command -> {
            Thread thread = Executors.defaultThreadFactory().newThread(command);
            thread.setName(SAMPLE_THREAD_NAME + "-" + sequence.incrementAndGet());
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
        };
    }

    private void startMainEventBusProcessor(MainEventBusProcessor mainEventBusProcessor) {
        try {
            java.lang.reflect.Method startMethod = MainEventBusProcessor.class.getDeclaredMethod("start");
            startMethod.setAccessible(true);
            startMethod.invoke(mainEventBusProcessor);
        } catch (ReflectiveOperationException exception) {
            throw new ValueErrorException("Failed to start MainEventBusProcessor", exception);
        }
    }

    @Override
    public Object createRestApplication(String host, int port) {
        return RegistryAgentCardMapper.toA2AJavaAgentCard(ServerSampleAgentCardBuilder.buildAgentCard(host, port));
    }
}
