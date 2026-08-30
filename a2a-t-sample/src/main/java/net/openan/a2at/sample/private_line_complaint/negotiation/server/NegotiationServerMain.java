package net.openan.a2at.sample.private_line_complaint.negotiation.server;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.openan.a2at.sample.private_line_complaint.negotiation.shared.NegotiationAgentCard;
import net.openan.a2at.sample.private_line_complaint.negotiation.shared.NegotiationDecision;
import net.openan.a2at.sample.private_line_complaint.negotiation.shared.NegotiationSampleEnvironment;
import net.openan.a2at.sample.private_line_complaint.negotiation.shared.NegotiationScenarioLoader;
import net.openan.a2at.sample.private_line_complaint.negotiation.shared.mock.NegotiationMockLlmInstaller;
import net.openan.a2at.sample.subscribe_incident.server.http.EmbeddedA2AHttpServer;
import net.openan.a2at.sample.subscribe_incident.shared.registry.RegistryAgentCardMapper;
import net.openan.a2at.sdk.server.A2ATServer;
import org.a2aproject.sdk.server.events.InMemoryQueueManager;
import org.a2aproject.sdk.server.events.MainEventBus;
import org.a2aproject.sdk.server.events.MainEventBusProcessor;
import org.a2aproject.sdk.server.requesthandlers.DefaultRequestHandler;
import org.a2aproject.sdk.server.requesthandlers.RequestHandler;
import org.a2aproject.sdk.server.tasks.BasePushNotificationSender;
import org.a2aproject.sdk.server.tasks.InMemoryPushNotificationConfigStore;
import org.a2aproject.sdk.server.tasks.InMemoryTaskStore;

/** Starts the A2A HTTP server for the private-line complaint negotiation sample. */
public final class NegotiationServerMain {

    private NegotiationServerMain() {}

    public static void main(String[] args) throws InterruptedException {
        Path envPath = args.length == 0 ? NegotiationSampleEnvironment.defaultEnvPath("server") : Path.of(args[0]);
        Map<String, String> env = NegotiationSampleEnvironment.read(envPath);
        if (NegotiationMockLlmInstaller.PROVIDER.equals(env.get("A2AT_LLM_PROVIDER"))) {
            NegotiationMockLlmInstaller.install();
        }
        String host = NegotiationSampleEnvironment.host(env);
        int port = NegotiationSampleEnvironment.port(env);
        A2ATServer a2atServer = new A2ATServer(envPath);
        InMemoryTaskStore taskStore = new InMemoryTaskStore();
        MainEventBus eventBus = new MainEventBus();
        InMemoryQueueManager queueManager = new InMemoryQueueManager(taskStore, eventBus);
        InMemoryPushNotificationConfigStore notifications = new InMemoryPushNotificationConfigStore();
        MainEventBusProcessor processor = new MainEventBusProcessor(
                eventBus, taskStore, new BasePushNotificationSender(notifications), queueManager);
        startProcessor(processor);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        RequestHandler handler = DefaultRequestHandler.create(
                new NegotiationAgentExecutor(
                        a2atServer,
                        NegotiationScenarioLoader.load(),
                        NegotiationDecision.fromEnvironment(System.getenv("A2AT_SAMPLE_NEGOTIATION_DECISION"))),
                taskStore,
                queueManager,
                notifications,
                processor,
                executor,
                executor);
        try (EmbeddedA2AHttpServer ignored = EmbeddedA2AHttpServer.start(
                host,
                port,
                RegistryAgentCardMapper.toA2AJavaAgentCard(NegotiationAgentCard.build(host, port)),
                handler)) {
            System.out.println("[negotiation-server] listening on http://" + host + ":" + port);
            Thread.currentThread().join();
        }
    }

    private static void startProcessor(MainEventBusProcessor processor) {
        try {
            var method = MainEventBusProcessor.class.getDeclaredMethod("start");
            method.setAccessible(true);
            method.invoke(processor);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to start A2A event-bus processor", exception);
        }
    }
}
