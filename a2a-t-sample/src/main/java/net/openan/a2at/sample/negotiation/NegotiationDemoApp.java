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

package net.openan.a2at.sample.negotiation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.openan.a2at.sample.negotiation.client.NegotiationClient;
import net.openan.a2at.sample.negotiation.server.NegotiationServerRuntime;
import net.openan.a2at.sample.negotiation.shared.FromDataStrategy;
import net.openan.a2at.sample.negotiation.shared.FromTextStrategy;
import net.openan.a2at.sample.negotiation.shared.NegotiationStrategy;
import net.openan.a2at.sample.subscribe_incident.server.http.EmbeddedA2AHttpServer;
import net.openan.a2at.sample.subscribe_incident.shared.env.SampleEnvironmentPathResolver;
import net.openan.a2at.sample.subscribe_incident.shared.error.ValueErrorException;
import net.openan.a2at.sample.subscribe_incident.shared.mock.SampleMockLlmInstaller;
import net.openan.a2at.sdk.client.A2ATClient;
import org.a2aproject.sdk.spec.AgentCard;

/**
 * Entry point for the negotiation end-to-end demo.
 *
 * <p>Boots an embedded a2a-java HTTP server wired to a {@link NegotiationServerRuntime} (carrying the
 * {@link net.openan.a2at.sdk.server.A2ATServer} negotiation facade), then runs the {@link NegotiationClient} which
 * drives the 4-message flow over real HTTP+JSON:
 *
 * <ol>
 *   <li>client -> Task-T (params missing);
 *   <li>server -> Negotiation-T request (missing params) -> INPUT_REQUIRED;
 *   <li>client -> Task-T (params filled) + Negotiation-T accept;
 *   <li>server -> diagnosis result -> COMPLETED.
 * </ol>
 *
 * <p>A real LLM API key is always required: {@code fromData} only makes the negotiation-message generation step
 * deterministic (no LLM call for rendering the Negotiation-T prompt), while Task-T slot extraction and semantic
 * validation still go through the LLM.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * java @a2a-t-sample/target/negotiation.javaargs.txt /path/to/.env
 * java @a2a-t-sample/target/negotiation.javaargs.txt --fromText /path/to/.env
 * }</pre>
 *
 * @since 2026-08
 */
public final class NegotiationDemoApp {

    private NegotiationDemoApp() {}

    /**
     * Resolves the env file path from the first non-flag argument, falling back to the bundled template.
     *
     * @param args command-line arguments
     * @return resolved env path
     */
    public static Path resolveEnvPath(String[] args) {
        for (String arg : args) {
            if (!"--fromText".equals(arg) && !arg.startsWith("--")) {
                return Path.of(arg);
            }
        }
        Path sampleEnvDir = Path.of("a2a-t-sample", "src", "main", "resources", "sample", "negotiation");
        return SampleEnvironmentPathResolver.resolve(sampleEnvDir, "negotiation.env", "negotiation.env");
    }

    /**
     * Runs the 4-message flow with an explicit strategy choice.
     *
     * @param envPath resolved env path
     * @param useFromText true for fromText (LLM), false for fromData (rule-based)
     * @param logSink log output sink
     * @return scenario summary
     */
    public static Map<String, Object> runMain(Path envPath, boolean useFromText, Consumer<String> logSink) {
        return runMain(envPath, useFromText, true, logSink);
    }

    /**
     * Runs the 4-message flow with an explicit strategy and transport preference.
     *
     * @param envPath resolved env path
     * @param useFromText true for fromText (LLM), false for fromData (rule-based)
     * @param preferStreaming true prefers {@code message:stream} when the server supports it; false forces blocking
     *     {@code message:send}
     * @param logSink log output sink
     * @return scenario summary
     */
    public static Map<String, Object> runMain(
            Path envPath, boolean useFromText, boolean preferStreaming, Consumer<String> logSink) {
        requireLlmApiKey(envPath);
        SampleMockLlmInstaller.installLlmLogger(false, "negotiation");
        emit(logSink, "[negotiation] strategy: " + (useFromText ? "fromText (LLM)" : "fromData (rule-based)"));
        emit(
                logSink,
                "[negotiation] transport preference: "
                        + (preferStreaming ? "message:stream (fallback message:send)" : "message:send"));
        NegotiationStrategy strategy = useFromText ? new FromTextStrategy() : new FromDataStrategy();

        NegotiationServerRuntime serverRuntime = new NegotiationServerRuntime(envPath, strategy, logSink);
        String host = serverRuntime.resolveHost();
        int port = serverRuntime.resolvePort();
        emit(logSink, "[negotiation] starting embedded a2a-java HTTP server: http://" + host + ":" + port);
        AgentCard agentCard = serverRuntime.buildAgentCard(host, port);
        EmbeddedA2AHttpServer httpServer =
                EmbeddedA2AHttpServer.start(host, port, agentCard, serverRuntime.buildRequestHandler());
        emit(logSink, "[negotiation] embedded a2a-java HTTP server started");

        A2ATClient clientFacade = new A2ATClient(envPath);
        NegotiationClient client = new NegotiationClient(clientFacade, strategy, logSink, preferStreaming);
        try {
            Map<String, Object> summary = client.runFourMessageFlow(serverRuntime, envPath);
            emit(logSink, "[negotiation] === Summary ===");
            emit(logSink, "[negotiation] " + summary);
            return summary;
        } finally {
            client.close();
            httpServer.close();
            serverRuntime.close();
        }
    }

    /**
     * Entry point.
     *
     * @param args optional {@code --fromText} flag, optional {@code --no-stream} flag, and the path to the {@code .env}
     *     file
     */
    public static void main(String[] args) {
        boolean useFromText = false;
        boolean preferStreaming = true;
        for (String arg : args) {
            if ("--fromText".equals(arg)) {
                useFromText = true;
            }
            if ("--no-stream".equals(arg)) {
                preferStreaming = false;
            }
        }
        runMain(resolveEnvPath(args), useFromText, preferStreaming, System.out::println);
    }

    /**
     * Fails fast when the env file does not carry a usable LLM API key. The demo always needs a real key: fromData only
     * removes the LLM call from negotiation-message generation, while Task-T slot extraction and semantic validation
     * still call the LLM.
     */
    private static void requireLlmApiKey(Path envPath) {
        Map<String, String> values = readEnv(envPath);
        String apiKey = values.get("A2AT_LLM_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new ValueErrorException(
                    "The negotiation demo requires a real LLM API key. Set A2AT_LLM_API_KEY in " + envPath
                            + " (fromData only skips the LLM for negotiation-message generation; "
                            + "Task-T slot extraction and semantic validation still call the LLM).");
        }
    }

    private static Map<String, String> readEnv(Path envPath) {
        Map<String, String> values = new LinkedHashMap<>();
        if (envPath == null || !Files.exists(envPath)) {
            return values;
        }
        try {
            for (String rawLine : Files.readAllLines(envPath)) {
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

    private static void emit(Consumer<String> logSink, String message) {
        if (logSink != null) {
            logSink.accept(message);
        }
    }
}
