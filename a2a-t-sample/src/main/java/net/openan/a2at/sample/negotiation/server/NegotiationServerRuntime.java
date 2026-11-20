/*
 * Copyright (c) 2026 Huawei Technologies Co., Ltd.
 * All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 *    Licensed under the Apache License, Version 2.0 (the "License"); you may
 *    not use this file except in compliance with the License. You may obtain
 *    a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 */

package net.openan.a2at.sample.negotiation.server;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.openan.a2at.sample.negotiation.shared.NegotiationStrategy;
import net.openan.a2at.sample.subscribe_incident.shared.error.ValueErrorException;
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
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentProvider;

/**
 * Runtime assembly for the negotiation sample server, wiring the real a2a-java REST transport to the negotiation
 * facade.
 *
 * <p>Builds a {@link DefaultRequestHandler} backed by a {@link NegotiationAgentExecutor} that runs every inbound A2A
 * request through the {@link A2ATServer} negotiation API. The handler is started in an embedded HTTP server so the
 * client can reach it over real HTTP+JSON. The AgentCard declares both Task-T and Negotiation-T extensions and
 * streaming=true.
 *
 * @since 2026-08
 */
public final class NegotiationServerRuntime implements AutoCloseable {

    private final Path envPath;
    private final Consumer<String> logSink;
    private final ExecutorService executor;
    private final A2ATServer server;
    private final NegotiationStrategy strategy;

    public NegotiationServerRuntime(Path envPath, NegotiationStrategy strategy, Consumer<String> logSink) {
        this.envPath = envPath;
        this.strategy = strategy;
        this.logSink = logSink;
        this.executor = new ThreadPoolExecutor(4, 4, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
        this.server = new A2ATServer(envPath);
    }

    public A2ATServer server() {
        return server;
    }

    public String resolveHost() {
        return readEnv(envPath).getOrDefault("A2AT_SAMPLE_HOST", "127.0.0.1");
    }

    public int resolvePort() {
        String portValue = readEnv(envPath).getOrDefault("A2AT_SAMPLE_PORT", "26335");
        try {
            return Integer.parseInt(portValue);
        } catch (NumberFormatException exception) {
            throw new ValueErrorException("Invalid A2AT_SAMPLE_PORT: " + portValue);
        }
    }

    public AgentCard buildAgentCard(String host, int port) {
        AgentInterface agentInterface = new AgentInterface("HTTP+JSON", "http://" + host + ":" + port + "/", "", "1.0");
        // URL must end with / so RestTransport appends the path segment correctly
        AgentCapabilities capabilities = new AgentCapabilities(
                true,
                false,
                false,
                List.of(
                        new org.a2aproject.sdk.spec.AgentExtension(
                                net.openan.a2at.sample.negotiation.shared.DemoConstants.TASK_T_URI,
                                Map.of(),
                                false,
                                "Task-T extension"),
                        new org.a2aproject.sdk.spec.AgentExtension(
                                net.openan.a2at.sample.negotiation.shared.DemoConstants.NEGOTIATION_T_URI,
                                Map.of(),
                                false,
                                "Negotiation-T extension")));
        return new AgentCard(
                "SPN Negotiation Agent",
                "SPN private-line-complaint diagnosis with negotiation",
                new AgentProvider("Huawei", "https://www.huawei.com"),
                "1.0.0",
                null,
                capabilities,
                List.of("application/json", "text/plain"),
                List.of("application/json", "text/plain"),
                List.of(new org.a2aproject.sdk.spec.AgentSkill(
                        "negotiation-diagnosis",
                        "Negotiation diagnosis",
                        "Task-T with Negotiation-T parameter completion",
                        List.of("negotiation", "diagnosis"),
                        null,
                        null,
                        null,
                        null)),
                Map.of(),
                List.of(),
                null,
                List.of(agentInterface),
                List.of());
    }

    public RequestHandler buildRequestHandler() {
        InMemoryTaskStore taskStore = new InMemoryTaskStore();
        MainEventBus mainEventBus = new MainEventBus();
        InMemoryQueueManager queueManager = new InMemoryQueueManager(taskStore, mainEventBus);
        PushNotificationConfigStore pushNotificationConfigStore = new InMemoryPushNotificationConfigStore();
        PushNotificationSender pushNotificationSender = new BasePushNotificationSender(pushNotificationConfigStore);
        MainEventBusProcessor mainEventBusProcessor =
                new MainEventBusProcessor(mainEventBus, taskStore, pushNotificationSender, queueManager);
        startMainEventBusProcessor(mainEventBusProcessor);
        AgentExecutor agentExecutor = new NegotiationAgentExecutor(server, strategy, logSink);
        return DefaultRequestHandler.create(
                agentExecutor,
                taskStore,
                queueManager,
                pushNotificationConfigStore,
                mainEventBusProcessor,
                executor,
                executor);
    }

    @Override
    public void close() {
        executor.shutdownNow();
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
            throw new ValueErrorException("Failed to start MainEventBusProcessor", exception);
        }
    }

    private static Map<String, String> readEnv(Path envPath) {
        Map<String, String> values = new LinkedHashMap<>();
        try {
            for (String rawLine : java.nio.file.Files.readAllLines(envPath)) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) {
                    continue;
                }
                int separator = line.indexOf('=');
                values.put(
                        line.substring(0, separator).trim(),
                        line.substring(separator + 1).trim());
            }
        } catch (java.io.IOException exception) {
            throw new ValueErrorException("Failed to read env file: " + envPath);
        }
        return values;
    }
}
