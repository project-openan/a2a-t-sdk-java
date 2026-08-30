package net.openan.a2at.sample.subscribe_incident.server.http;

import com.google.protobuf.util.JsonFormat;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Flow;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.a2aproject.sdk.grpc.SendMessageRequest;
import org.a2aproject.sdk.grpc.StreamResponse;
import org.a2aproject.sdk.grpc.utils.ProtoUtils;
import org.a2aproject.sdk.server.AgentCardCacheMetadata;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.requesthandlers.RequestHandler;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.transport.rest.handler.RestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Minimal embedded HTTP host that exposes the real a2a-java REST transport endpoints required by the sample.
 *
 * @since 2026-05
 */
public final class EmbeddedA2AHttpServer implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(EmbeddedA2AHttpServer.class);
    private static final int SERVER_THREAD_COUNT = 8;

    private final HttpServer server;
    private final ExecutorService executorService;

    private EmbeddedA2AHttpServer(HttpServer server, ExecutorService executorService) {
        this.server = server;
        this.executorService = executorService;
    }

    public static EmbeddedA2AHttpServer start(
            String host, int port, AgentCard agentCard, RequestHandler requestHandler) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(host, port), 0);
            ExecutorService executorService = new ThreadPoolExecutor(
                    SERVER_THREAD_COUNT, SERVER_THREAD_COUNT, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
            server.setExecutor(executorService);

            RestHandler restHandler = new RestHandler(
                    agentCard, new AgentCardCacheMetadata(agentCard, null), requestHandler, Runnable::run);
            server.createContext("/", exchange -> handle(exchange, restHandler));
            server.start();
            return new EmbeddedA2AHttpServer(server, executorService);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to start embedded a2a-java REST server", exception);
        }
    }

    @Override
    public void close() {
        server.stop(0);
        executorService.shutdown();
    }

    private static void handle(HttpExchange exchange, RestHandler restHandler) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod()) && "/".equals(path)) {
                sendResponse(exchange, restHandler.getAgentCard());
                return;
            }
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod()) && "/message:stream".equals(path)) {
                streamMessageResponse(
                        exchange,
                        restHandler,
                        requestHandler(restHandler),
                        buildCallContext(exchange),
                        readRequestBody(exchange));
                return;
            }
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod()) && "/message:send".equals(path)) {
                sendResponse(
                        exchange, restHandler.sendMessage(buildCallContext(exchange), "", readRequestBody(exchange)));
                return;
            }

            exchange.sendResponseHeaders(404, -1);
        } finally {
            exchange.close();
        }
    }

    private static ServerCallContext buildCallContext(HttpExchange exchange) {
        Map<String, String> headers = new LinkedHashMap<>();
        exchange.getRequestHeaders()
                .forEach((key, value) -> headers.put(key.toLowerCase(Locale.ROOT), String.join(",", value)));
        aliasHeader(headers, "A2A-Extensions");
        aliasHeader(headers, "X-A2A-Extensions");
        aliasHeader(headers, "A2A-Protocol-Version");
        aliasHeader(headers, "A2A-Version");
        String extensionHeader = firstHeader(headers, "A2A-Extensions", "X-A2A-Extensions");
        Set<String> requestedExtensions = extensionHeader.isBlank() ? Set.of() : Set.of(extensionHeader.split(","));
        return new ServerCallContext(
                null,
                Map.of("headers", headers),
                requestedExtensions,
                firstNullableHeader(headers, "A2A-Protocol-Version", "A2A-Version"));
    }

    private static void aliasHeader(Map<String, String> headers, String expectedName) {
        String value = headers.get(expectedName.toLowerCase(Locale.ROOT));
        if (value != null) {
            headers.put(expectedName, value);
        }
    }

    private static String firstHeader(Map<String, String> headers, String first, String second) {
        String value = firstNullableHeader(headers, first, second);
        return value == null ? "" : value;
    }

    private static String firstNullableHeader(Map<String, String> headers, String first, String second) {
        String firstValue = headers.get(first);
        if (firstValue != null) {
            return firstValue;
        }
        return headers.get(second);
    }

    private static String readRequestBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static RequestHandler requestHandler(RestHandler restHandler) {
        try {
            java.lang.reflect.Field field = RestHandler.class.getDeclaredField("requestHandler");
            field.setAccessible(true);
            Object value = field.get(restHandler);
            if (value instanceof RequestHandler requestHandler) {
                return requestHandler;
            }
            throw new IllegalStateException("a2a-java REST handler field is not a RequestHandler.");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to access a2a-java request handler", exception);
        }
    }

    private static void sendResponse(HttpExchange exchange, RestHandler.HTTPRestResponse response) throws IOException {
        applyHeaders(exchange, response);
        byte[] body =
                response.getBody() == null ? new byte[0] : response.getBody().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(response.getStatusCode(), body.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
            outputStream.flush();
        }
    }

    private static void streamResponse(HttpExchange exchange, RestHandler.HTTPRestStreamingResponse response)
            throws IOException {
        applyHeaders(exchange, response);
        exchange.sendResponseHeaders(response.getStatusCode(), 0);
        AtomicLong sequence = new AtomicLong(0L);
        CountDownLatch completed = new CountDownLatch(1);
        OutputStream outputStream = exchange.getResponseBody();
        response.getPublisher().subscribe(new Flow.Subscriber<>() {
            private Flow.Subscription subscription;

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                this.subscription = subscription;
                subscription.request(1);
            }

            @Override
            public void onNext(String item) {
                try {
                    String payload = formatSseFrame(sequence.incrementAndGet(), item);
                    outputStream.write(payload.getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                    subscription.request(1);
                } catch (IOException exception) {
                    subscription.cancel();
                    completed.countDown();
                }
            }

            @Override
            public void onError(Throwable throwable) {
                completed.countDown();
            }

            @Override
            public void onComplete() {
                completed.countDown();
            }
        });

        try {
            completed.await();
        } catch (InterruptedException exception) {
            completed.countDown();
            throw new IOException("Interrupted while waiting for stream response completion", exception);
        } finally {
            outputStream.close();
        }
    }

    private static void streamMessageResponse(
            HttpExchange exchange,
            RestHandler restHandler,
            RequestHandler requestHandler,
            ServerCallContext callContext,
            String requestBody)
            throws IOException {
        try {
            SendMessageRequest.Builder builder = SendMessageRequest.newBuilder();
            JsonFormat.parser().merge(requestBody, builder);
            var request = ProtoUtils.FromProto.messageSendParams(builder.build());
            requestHandler.validateRequestedTask(request.message().taskId());
            Flow.Publisher<StreamingEventKind> publisher = requestHandler.onMessageSendStream(request, callContext);
            LOGGER.debug("[server] stream-publisher-created");

            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            AtomicLong sequence = new AtomicLong(0L);
            CountDownLatch completed = new CountDownLatch(1);
            OutputStream outputStream = exchange.getResponseBody();
            publisher.subscribe(new Flow.Subscriber<>() {
                private Flow.Subscription subscription;

                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    this.subscription = subscription;
                    LOGGER.debug("[server] stream-subscriber-onSubscribe");
                    subscription.request(1);
                }

                @Override
                public void onNext(StreamingEventKind item) {
                    try {
                        String payload = JsonFormat.printer().print(toStreamResponse(item));
                        String encoded = formatSseFrame(sequence.incrementAndGet(), payload);
                        LOGGER.trace(
                                "[server] sse-write: {}",
                                encoded.replace("\r", "\\r").replace("\n", "\\n"));
                        outputStream.write(encoded.getBytes(StandardCharsets.UTF_8));
                        outputStream.flush();
                        LOGGER.trace("[server] sse-write-flushed");
                        subscription.request(1);
                    } catch (IOException exception) {
                        LOGGER.warn("[server] sse-write-failed", exception);
                        subscription.cancel();
                        completed.countDown();
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    LOGGER.warn("[server] stream-subscriber-onError", throwable);
                    completed.countDown();
                }

                @Override
                public void onComplete() {
                    LOGGER.debug("[server] stream-subscriber-onComplete");
                    completed.countDown();
                }
            });

            try {
                completed.await();
            } catch (InterruptedException exception) {
                completed.countDown();
                throw new IOException("Interrupted while waiting for message stream completion", exception);
            } finally {
                outputStream.close();
            }
        } catch (A2AError exception) {
            sendResponse(exchange, restHandler.createErrorResponse(exception));
        }
    }

    static String formatSseFrame(long sequence, String payload) {
        StringBuilder builder = new StringBuilder();
        builder.append(String.format(Locale.ROOT, "id:%d\n", sequence));
        String normalized = payload == null ? "" : payload.replace("\r\n", "\n").replace('\r', '\n');
        String compact = normalized.lines().map(String::trim).reduce("", String::concat);
        builder.append("data:").append(compact).append('\n');
        builder.append('\n');
        return builder.toString();
    }

    private static StreamResponse toStreamResponse(StreamingEventKind event) {
        StreamResponse.Builder builder = StreamResponse.newBuilder();
        if (event instanceof Message message) {
            builder.setMessage(ProtoUtils.ToProto.message(message));
            return builder.build();
        }
        if (event instanceof Task task) {
            builder.setTask(ProtoUtils.ToProto.task(task));
            return builder.build();
        }
        if (event instanceof TaskStatusUpdateEvent statusUpdateEvent) {
            builder.setStatusUpdate(ProtoUtils.ToProto.taskStatusUpdateEvent(statusUpdateEvent));
            return builder.build();
        }
        if (event instanceof TaskArtifactUpdateEvent artifactUpdateEvent) {
            builder.setArtifactUpdate(ProtoUtils.ToProto.taskArtifactUpdateEvent(artifactUpdateEvent));
            return builder.build();
        }
        throw new IllegalArgumentException("Unsupported streaming event: " + event);
    }

    private static void applyHeaders(HttpExchange exchange, RestHandler.HTTPRestResponse response) {
        if (response.getContentType() != null && !response.getContentType().isBlank()) {
            exchange.getResponseHeaders().set("Content-Type", response.getContentType());
        }
        if (response.getHeaders() != null) {
            response.getHeaders().forEach((key, value) -> {
                if (value != null) {
                    exchange.getResponseHeaders().set(key, value);
                }
            });
        }
    }
}
