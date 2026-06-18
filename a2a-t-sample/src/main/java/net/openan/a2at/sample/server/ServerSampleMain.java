package net.openan.a2at.sample.server;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;
import net.openan.a2at.sample.server.agentcard.ServerSampleAgentCardBuilder;
import net.openan.a2at.sample.server.flow.PromptComplianceChecker;
import net.openan.a2at.sample.server.http.EmbeddedA2AHttpServer;
import net.openan.a2at.sample.server.runtime.A2AJavaServerRuntime;
import net.openan.a2at.sample.server.runtime.DefaultSampleServerRuntime;
import net.openan.a2at.sample.server.runtime.SampleServerRuntime;
import net.openan.a2at.sample.server.runtime.SampleServerRuntimeFactory;
import net.openan.a2at.sample.server.runtime.ServerBind;
import net.openan.a2at.sample.server.runtime.ServerBootstrapResult;
import net.openan.a2at.sample.shared.registry.RegistryAgentCardMapper;
import org.a2aproject.sdk.server.requesthandlers.RequestHandler;

/**
 * Main entry orchestration for the server sample.
 *
 * @since 2026-05
 */
public final class ServerSampleMain {

    private ServerSampleMain() {
    }

    public static Path resolveEnvPath(String[] args) {
        return args.length > 0 ? Path.of(args[0]) : DefaultSampleServerRuntime.resolveDefaultEnvPath();
    }

    public static ServerBootstrapResult runMain(
            Path envPath,
            SampleServerRuntimeFactory runtimeFactory,
            Consumer<String> logSink) {
        SampleServerRuntime runtime = runtimeFactory.create(envPath);
        ServerBind bind = runtime.resolveBind();
        emit(logSink, "[server] startup: host=" + bind.host() + " port=" + bind.port());
        Map<String, Object> agentCard = runtime.buildAgentCard(bind.host(), bind.port());
        PromptComplianceChecker promptChecker = runtime.buildPromptChecker(envPath);
        Object app = runtime.buildApp(agentCard, promptChecker);
        Map<String, Object> registrationPayload;
        if (runtime instanceof A2AJavaServerRuntime a2aJavaServerRuntime
                && a2aJavaServerRuntime.createRestApplication(bind.host(), bind.port()) instanceof org.a2aproject.sdk.spec.AgentCard agentCardModel) {
            registrationPayload = RegistryAgentCardMapper.toRegistryRegistrationPayload(agentCardModel);
        } else {
            registrationPayload = ServerSampleAgentCardBuilder.buildRegistrationPayload(bind.host(), bind.port());
        }
        Map<String, Object> registrationResult = runtime.registerAgentCard(registrationPayload, envPath);
        if ("success".equals(registrationResult.get("status"))) {
            emit(logSink, "[server] agent-card registration: success");
        } else {
            emit(logSink,
                    "[server] agent-card registration failed, continuing startup: "
                            + registrationResult.getOrDefault("message", "unknown error"));
        }
        AutoCloseable serverHandle = null;
        if (runtime instanceof A2AJavaServerRuntime a2aJavaServerRuntime
                && app instanceof RequestHandler requestHandler
                && a2aJavaServerRuntime.createRestApplication(bind.host(), bind.port()) instanceof org.a2aproject.sdk.spec.AgentCard agentCardModel) {
            serverHandle = EmbeddedA2AHttpServer.start(bind.host(), bind.port(), agentCardModel, requestHandler);
            emit(logSink, "[server] http server started: http://" + bind.host() + ":" + bind.port());
        }
        return new ServerBootstrapResult(bind.host(), bind.port(), app, serverHandle, registrationResult);
    }

    public static void main(String[] args) {
        runMain(resolveEnvPath(args), envPath -> new DefaultSampleServerRuntime(envPath, System.out::println), System.out::println);
    }

    private static void emit(Consumer<String> logSink, String message) {
        if (logSink != null) {
            logSink.accept(message);
        }
    }
}

