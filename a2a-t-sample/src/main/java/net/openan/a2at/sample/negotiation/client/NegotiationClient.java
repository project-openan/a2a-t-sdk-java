package net.openan.a2at.sample.negotiation.client;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.openan.a2at.sample.negotiation.server.NegotiationServerRuntime;
import net.openan.a2at.sample.negotiation.shared.DemoConstants;
import net.openan.a2at.sample.negotiation.shared.NegotiationMessage;
import net.openan.a2at.sample.negotiation.shared.NegotiationStrategy;
import net.openan.a2at.sample.negotiation.shared.ScenarioData;
import net.openan.a2at.sample.subscribe_incident.client.flow.SampleStreamTerminalStateDecider;
import net.openan.a2at.sdk.client.A2ATClient;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.NegotiationPerformative;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;
import net.openan.a2at.sdk.negotiation.runtime.helper.NegotiationPayloadMapper;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationType;
import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.client.ClientEvent;
import org.a2aproject.sdk.client.MessageEvent;
import org.a2aproject.sdk.client.TaskEvent;
import org.a2aproject.sdk.client.TaskUpdateEvent;
import org.a2aproject.sdk.client.config.ClientConfig;
import org.a2aproject.sdk.client.transport.rest.RestTransport;
import org.a2aproject.sdk.client.transport.rest.RestTransportConfig;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallContext;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TextPart;

/**
 * Client that drives the 4-message negotiation flow over real HTTP A2A.
 *
 * <p>Flow:
 *
 * <ol>
 *   <li>message 1: generate a Task-T prompt with missing params and send it to the agent;
 *   <li>message 2: receive the Negotiation-T request (server detected missing params -> INPUT_REQUIRED);
 *   <li>message 3: generate a Task-T prompt with filled params + a Negotiation-T accept and send it;
 *   <li>message 4: receive the diagnosis result artifact (server -> COMPLETED).
 * </ol>
 *
 * <p>The transport endpoint is selected from the server's declared capability: when the AgentCard advertises
 * {@code streaming=true} the request travels as {@code message:stream} and the reply is aggregated from the streamed
 * client events; otherwise a blocking {@code message:send} round trip is used. Both paths produce the same reply
 * carrier for the flow, so the scenario logic stays endpoint-agnostic.
 *
 * @since 2026-08
 */
public final class NegotiationClient {

    private static final long STREAM_TIMEOUT_SECONDS = 120;

    private final A2ATClient client;
    private final Consumer<String> logSink;
    private final NegotiationStrategy strategy;
    private final boolean preferStreaming;
    private Client a2aClient;

    /**
     * Creates the negotiation client.
     *
     * @param client client facade used for prompt generation and the negotiation state machine
     * @param strategy negotiation-message generation strategy (fromData or fromText)
     * @param logSink log output sink
     */
    public NegotiationClient(A2ATClient client, NegotiationStrategy strategy, Consumer<String> logSink) {
        this(client, strategy, logSink, true);
    }

    /**
     * Creates the negotiation client with an explicit endpoint preference.
     *
     * @param client client facade used for prompt generation and the negotiation state machine
     * @param strategy negotiation-message generation strategy (fromData or fromText)
     * @param logSink log output sink
     * @param preferStreaming true selects {@code message:stream} when the server advertises streaming support; false
     *     always uses blocking {@code message:send}
     */
    public NegotiationClient(
            A2ATClient client, NegotiationStrategy strategy, Consumer<String> logSink, boolean preferStreaming) {
        this.client = client;
        this.strategy = strategy;
        this.logSink = logSink;
        this.preferStreaming = preferStreaming;
    }

    /**
     * Runs the 4-message flow against one server runtime and returns a summary.
     *
     * @param serverRuntime the wired server runtime (started HTTP server)
     * @param envPath resolved env path (LLM key etc.)
     * @return scenario summary
     */
    public Map<String, Object> runFourMessageFlow(NegotiationServerRuntime serverRuntime, Path envPath) {
        String host = serverRuntime.resolveHost();
        int port = serverRuntime.resolvePort();
        emit("[client] server at http://" + host + ":" + port);

        AgentCard agentCard = serverRuntime.buildAgentCard(host, port);

        // -- message 1: Task-T with missing params --
        emit("[client] === message 1: Task-T (params missing) ===");
        MetadataContent taskMissing = client.generateTaskPromptFromDataWithSchema(
                ScenarioData.missingParams(), ScenarioData.taskSchema(), DemoConstants.TASK_TEMPLATE);
        emit("[client] Task-T prompt rendered (params missing)");

        // start the negotiation state machine with the missing-params prompt
        Map<String, Object> startPayload =
                client.startNegotiation(NegotiationType.INFORMATION, taskMissing.promptText(), Map.of());
        Map<String, Object> contextMap = NegotiationPayloadMapper.extractContextMap(startPayload);

        EventKind reply1 = send(agentCard, DemoConstants.TASK_T_URI, taskMissing, contextMap);
        emit("[client] received message 2 (negotiation request)");

        // extract the negotiation context from the reply
        Message reply1Msg = extractReplyMessage(reply1);
        Map<String, Object> negotiationContext = NegotiationMessage.extractContext(reply1Msg.metadata());

        // -- message 3: Task-T with filled params + Negotiation-T accept --
        emit("[client] === message 3: Task-T (params filled) + Negotiation-T accept ===");
        MetadataContent taskFilled = client.generateTaskPromptFromDataWithSchema(
                ScenarioData.filledParams(), ScenarioData.taskSchema(), DemoConstants.TASK_TEMPLATE);
        emit("[client] Task-T prompt rendered (params filled)");

        MetadataContent acceptPrompt = strategy.generateAccept(
                client,
                new NegotiationContext(
                        UUID.randomUUID().toString(), 1, NegotiationContext.DEFAULT_MAX_ROUNDS, NegotiationPerformative.ACCEPT),
                itemsFromFilledParams(),
                DemoConstants.NEGOTIATION_ACCEPT);
        emit("[client] Negotiation-T accept rendered");

        // merge Task-T + Negotiation-T into one message metadata
        Map<String, Object> mergedMetadata = new LinkedHashMap<>();
        mergedMetadata.put(DemoConstants.TASK_T_URI, taskFilled.promptText());
        mergedMetadata.put(DemoConstants.NEGOTIATION_T_URI, acceptPrompt.promptText());
        mergedMetadata.put(DemoConstants.TEMPLATE_URI_KEY, DemoConstants.TASK_TEMPLATE);
        mergedMetadata.put(DemoConstants.NEGOTIATION_CONTEXT_KEY, NegotiationMessage.toJson(negotiationContext));

        EventKind reply2 = sendRaw(agentCard, mergedMetadata, taskFilled.promptText(), negotiationContext);
        emit("[client] received message 4 (diagnosis result)");

        // extract diagnosis from the reply task's artifact
        String diagnosis = extractDiagnosis(reply2);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("scenario", "private-line-complaint-negotiation");
        summary.put("messages", 4);
        summary.put("outcome", "COMPLETED");
        summary.put("diagnosis", diagnosis);
        return summary;
    }

    /** Sends one extension's prompt as an A2A message and returns the raw reply event. */
    private EventKind send(
            AgentCard agentCard, String extensionUri, MetadataContent content, Map<String, Object> contextMap) {
        Map<String, Object> metadata =
                NegotiationMessage.buildMetadata(extensionUri, content.promptText(), content.templateUri(), contextMap);
        return sendRaw(agentCard, metadata, content.promptText(), contextMap);
    }

    /**
     * Sends one A2A request. The endpoint follows the server's declared capability and the client preference:
     * {@code message:stream} when the AgentCard advertises streaming and streaming is preferred, otherwise
     * {@code message:send}. Streamed events are aggregated into the same reply carrier the blocking path produces
     * (final task state or last agent message).
     */
    private EventKind sendRaw(
            AgentCard agentCard, Map<String, Object> metadata, String promptText, Map<String, Object> contextMap) {
        boolean streaming = preferStreaming
                && agentCard.capabilities() != null
                && agentCard.capabilities().streaming();
        emit("[client] transport endpoint: " + (streaming ? "message:stream" : "message:send"));
        Message message = Message.builder()
                .messageId(UUID.randomUUID().toString())
                .role(Message.Role.ROLE_USER)
                .parts(new TextPart(promptText))
                .metadata(metadata)
                .build();
        MessageSendParams request = MessageSendParams.builder().message(message).build();
        String extensionsHeader = DemoConstants.TASK_T_URI + "," + DemoConstants.NEGOTIATION_T_URI;
        ClientCallContext callContext = new ClientCallContext(Map.of(), Map.of("A2A-Extensions", extensionsHeader));
        try {
            if (streaming) {
                return sendStreaming(agentCard, request, callContext);
            }
            return sendBlocking(agentCard, request, callContext);
        } catch (org.a2aproject.sdk.spec.A2AClientException exception) {
            throw new RuntimeException("a2a-java sendMessage failed: " + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted while waiting for streamed events", exception);
        }
    }

    /** Blocking {@code message:send} round trip. */
    private EventKind sendBlocking(AgentCard agentCard, MessageSendParams request, ClientCallContext callContext)
            throws org.a2aproject.sdk.spec.A2AClientException, InterruptedException {
        if (a2aClient == null) {
            a2aClient = buildClient(agentCard, false);
        }
        // Client.sendMessage falls back to the blocking transport when streaming is disabled client-side
        StreamAggregator aggregator = new StreamAggregator();
        a2aClient.sendMessage(
                request, List.of((event, ignored) -> aggregator.accept(event)), aggregator::fail, callContext);
        return aggregator.awaitReply();
    }

    /** Streaming {@code message:stream} request; events are aggregated until a terminal task state arrives. */
    private EventKind sendStreaming(AgentCard agentCard, MessageSendParams request, ClientCallContext callContext)
            throws org.a2aproject.sdk.spec.A2AClientException, InterruptedException {
        if (a2aClient == null) {
            a2aClient = buildClient(agentCard, true);
        }
        StreamAggregator aggregator = new StreamAggregator();
        a2aClient.sendMessage(
                request, List.of((event, ignored) -> aggregator.accept(event)), aggregator::fail, callContext);
        return aggregator.awaitReply();
    }

    /** Builds the a2a-java client with the requested streaming mode over the REST transport. */
    private static Client buildClient(AgentCard agentCard, boolean streaming)
            throws org.a2aproject.sdk.spec.A2AClientException {
        return Client.builder(agentCard)
                .withTransport(RestTransport.class, new RestTransportConfig())
                .clientConfig(new ClientConfig.Builder().setStreaming(streaming).build())
                .build();
    }

    /**
     * Aggregates streamed client events into one reply carrier: the final task for task-backed replies, or the last
     * agent message for message-backed replies. Also serves the blocking path, which emits a single event.
     */
    private static final class StreamAggregator {

        private final CountDownLatch done = new CountDownLatch(1);
        private final AtomicReference<Task> lastTask = new AtomicReference<>();
        private final AtomicReference<Message> lastAgentMessage = new AtomicReference<>();
        private final AtomicReference<Throwable> failure = new AtomicReference<>();

        void accept(ClientEvent event) {
            if (event instanceof TaskEvent taskEvent) {
                lastTask.set(taskEvent.getTask());
            } else if (event instanceof TaskUpdateEvent updateEvent) {
                lastTask.set(updateEvent.getTask());
            } else if (event instanceof MessageEvent messageEvent
                    && messageEvent.getMessage().role() == Message.Role.ROLE_AGENT) {
                lastAgentMessage.set(messageEvent.getMessage());
            }
            if (SampleStreamTerminalStateDecider.isTerminal(event)) {
                done.countDown();
            }
        }

        void fail(Throwable error) {
            failure.compareAndSet(null, error);
            done.countDown();
        }

        EventKind awaitReply() throws InterruptedException {
            if (!done.await(STREAM_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new RuntimeException("timed out waiting for the agent reply");
            }
            Throwable error = failure.get();
            if (error != null) {
                throw new RuntimeException("a2a-java stream failed: " + error.getMessage(), error);
            }
            Task task = lastTask.get();
            if (task != null) {
                return task;
            }
            Message message = lastAgentMessage.get();
            if (message != null) {
                return message;
            }
            throw new IllegalStateException("a2a-java reply carried neither a task nor an agent message");
        }
    }

    private static Message extractReplyMessage(EventKind reply) {
        if (reply instanceof Message directMessage) {
            return directMessage;
        }
        if (reply instanceof Task task) {
            for (int i = task.history().size() - 1; i >= 0; i--) {
                Message candidate = task.history().get(i);
                if (candidate.role() == Message.Role.ROLE_AGENT) {
                    return candidate;
                }
            }
        }
        throw new IllegalStateException("a2a-java did not return a reply message: " + reply);
    }

    private static String extractDiagnosis(EventKind reply) {
        if (reply instanceof Task task && task.artifacts() != null) {
            for (org.a2aproject.sdk.spec.Artifact artifact : task.artifacts()) {
                if (artifact.parts() != null) {
                    for (org.a2aproject.sdk.spec.Part<?> part : artifact.parts()) {
                        if (part instanceof org.a2aproject.sdk.spec.DataPart dataPart) {
                            Object data = dataPart.data();
                            Object value = (data instanceof Map<?, ?> m) ? m.get(DemoConstants.TASK_T_URI) : null;
                            if (value != null) {
                                return String.valueOf(value);
                            }
                        }
                    }
                }
            }
        }
        if (reply instanceof Message message && message.metadata() != null) {
            Object value = message.metadata().get(DemoConstants.TASK_T_URI);
            return value == null ? "" : String.valueOf(value);
        }
        return "";
    }

    /** Releases the underlying HTTP transport. */
    public void close() {
        if (a2aClient != null) {
            a2aClient.close();
        }
    }

    /** Builds the filled-items list from {@link ScenarioData#filledParams()}, adapting to any parameter set. */
    private static List<NegotiationItem> itemsFromFilledParams() {
        List<NegotiationItem> items = new ArrayList<>();
        for (Map.Entry<String, Object> entry : ScenarioData.filledParams().entrySet()) {
            items.add(new NegotiationItem(entry.getKey(), String.valueOf(entry.getValue())));
        }
        return items;
    }

    private void emit(String message) {
        if (logSink != null) {
            logSink.accept(message);
        }
    }
}
