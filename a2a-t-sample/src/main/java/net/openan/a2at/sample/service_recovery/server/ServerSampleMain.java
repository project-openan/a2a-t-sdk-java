package net.openan.a2at.sample.service_recovery.server;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;
import net.openan.a2at.sample.service_recovery.server.agentcard.ServerSampleAgentCardBuilder;
import net.openan.a2at.sample.service_recovery.server.flow.NotificationPromptValidator;
import net.openan.a2at.sample.service_recovery.server.http.EmbeddedA2AHttpServer;
import net.openan.a2at.sample.service_recovery.server.runtime.A2AJavaServerRuntime;
import net.openan.a2at.sample.service_recovery.server.runtime.DefaultSampleServerRuntime;
import net.openan.a2at.sample.service_recovery.server.runtime.SampleServerRuntime;
import net.openan.a2at.sample.service_recovery.server.runtime.SampleServerRuntimeFactory;
import net.openan.a2at.sample.service_recovery.server.runtime.ServerBind;
import net.openan.a2at.sample.service_recovery.server.runtime.ServerBootstrapResult;
import net.openan.a2at.sample.service_recovery.shared.logging.SampleLoggingFormatter;
import net.openan.a2at.sample.service_recovery.shared.mock.SampleMockLlmInstaller;
import net.openan.a2at.sample.service_recovery.shared.registry.RegistryAgentCardMapper;
import org.a2aproject.sdk.server.requesthandlers.RequestHandler;

/**
 * Main entry orchestration for the service-recovery server sample.
 *
 * @since 2026-08
 */
public final class ServerSampleMain {

    private static final String MOCK_RESOURCE_ROOT = "sample/service-recovery/mock_responses";

    private ServerSampleMain() {}

    /**
     * Resolves the sample environment file path, preferring an explicit argument.
     *
     * @param args command line arguments, only the first one is used
     * @return resolved environment file path
     */
    public static Path resolveEnvPath(String[] args) {
        return args.length > 0 ? Path.of(args[0]) : DefaultSampleServerRuntime.resolveDefaultEnvPath();
    }

    /**
     * Runs the server bootstrap and returns its result.
     *
     * @param envPath sample environment file path
     * @param runtimeFactory server runtime factory
     * @param logSink log sink, may be null
     * @return bootstrap result carrying the bind address, app and server handle
     */
    public static ServerBootstrapResult runMain(
            Path envPath, SampleServerRuntimeFactory runtimeFactory, Consumer<String> logSink) {
        boolean mockNeeded = SampleMockLlmInstaller.isMockNeeded(envPath);
        SampleMockLlmInstaller.installLlmLogger(mockNeeded, "server");
        Path resolvedEnvPath = SampleMockLlmInstaller.resolveEnvPath(envPath, MOCK_RESOURCE_ROOT);
        if (!resolvedEnvPath.equals(envPath)) {
            emit(logSink, "[server] no LLM API key found, using mock LLM responses for e2e");
        }
        SampleServerRuntime runtime = runtimeFactory.create(resolvedEnvPath);
        ServerBind bind = runtime.resolveBind();
        Map<String, Object> agentCard = runtime.buildAgentCard(bind.host(), bind.port());
        NotificationPromptValidator notificationValidator = runtime.buildNotificationValidator(resolvedEnvPath);
        Object app = runtime.buildApp(agentCard, notificationValidator);
        Map<String, Object> registrationPayload;
        if (runtime instanceof A2AJavaServerRuntime a2aJavaServerRuntime
                && a2aJavaServerRuntime.createRestApplication(bind.host(), bind.port())
                        instanceof org.a2aproject.sdk.spec.AgentCard agentCardModel) {
            registrationPayload = RegistryAgentCardMapper.toRegistryRegistrationPayload(agentCardModel);
        } else {
            registrationPayload = ServerSampleAgentCardBuilder.buildRegistrationPayload(bind.host(), bind.port());
        }
        Map<String, Object> registrationResult = runtime.registerAgentCard(registrationPayload, resolvedEnvPath);
        AutoCloseable serverHandle = null;
        if (runtime instanceof A2AJavaServerRuntime a2aJavaServerRuntime
                && app instanceof RequestHandler requestHandler
                && a2aJavaServerRuntime.createRestApplication(bind.host(), bind.port())
                        instanceof org.a2aproject.sdk.spec.AgentCard agentCardModel) {
            serverHandle = EmbeddedA2AHttpServer.start(bind.host(), bind.port(), agentCardModel, requestHandler);
        }
        return new ServerBootstrapResult(bind.host(), bind.port(), app, serverHandle, registrationResult);
    }

    /**
     * Sample entry point.
     *
     * @param args optional environment file path as the first argument
     */
    public static void main(String[] args) {
        runMain(
                resolveEnvPath(args),
                envPath -> new DefaultSampleServerRuntime(envPath, System.out::println),
                System.out::println);
    }

    private static void emit(Consumer<String> logSink, String message) {
        if (logSink != null) {
            logSink.accept(SampleLoggingFormatter.timestamped(message));
        }
    }
}
