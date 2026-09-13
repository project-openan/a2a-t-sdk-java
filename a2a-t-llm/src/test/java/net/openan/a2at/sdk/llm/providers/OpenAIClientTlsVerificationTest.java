package net.openan.a2at.sdk.llm.providers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import net.openan.a2at.sdk.llm.LLMClientConfig;
import net.openan.a2at.sdk.llm.LLMResponse;
import net.openan.a2at.sdk.llm.LLMRuntimeError;
import org.junit.jupiter.api.Test;

/**
 * Behavioral TLS tests against a local HTTPS server that presents a self-signed certificate for {@code localhost}
 * while the client connects to {@code 127.0.0.1}, so the default path fails on both trust and hostname checks.
 */
class OpenAIClientTlsVerificationTest {

    private static final String CHAT_COMPLETION_BODY =
            "{\"id\":\"chatcmpl_test\",\"object\":\"chat.completion\",\"created\":1,"
                    + "\"model\":\"gpt-4o-mini\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\","
                    + "\"content\":\"{\\\"device_type\\\":\\\"router\\\"}\"},\"finish_reason\":\"stop\"}],"
                    + "\"usage\":{\"prompt_tokens\":7,\"completion_tokens\":2,\"total_tokens\":9}}";

    @Test
    void skipsPeerCertificateVerificationWhenDisabled() throws Exception {
        try (SelfSignedHttpsServer server = SelfSignedHttpsServer.start()) {
            OpenAIClient client = new OpenAIClient(config(server, false));

            LLMResponse response = client.structured(
                    List.of(Map.of("role", "user", "content", "extract")), Map.of("type", "object"), null, null);

            assertEquals("{\"device_type\":\"router\"}", response.content());
            assertEquals(1, server.requestCount());
        }
    }

    @Test
    void verifiesPeerCertificateByDefault() throws Exception {
        try (SelfSignedHttpsServer server = SelfSignedHttpsServer.start()) {
            OpenAIClient client = new OpenAIClient(config(server, true));

            LLMRuntimeError error = assertThrows(
                    LLMRuntimeError.class,
                    () -> client.structured(
                            List.of(Map.of("role", "user", "content", "extract")), Map.of("type", "object"), null, null));

            assertTrue(
                    hasSslFailureInCauseChain(error),
                    "expected an SSLException in the cause chain but got: " + error.getCause());
            assertEquals(0, server.requestCount());
        }
    }

    private static LLMClientConfig config(SelfSignedHttpsServer server, boolean sslVerify) {
        return new LLMClientConfig(
                "openai",
                "gpt-4o-mini",
                "sk-test",
                server.baseUrl() + "/v1",
                10,
                null,
                null,
                5.0d,
                300,
                100,
                false,
                sslVerify,
                null);
    }

    private static boolean hasSslFailureInCauseChain(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof SSLException) {
                return true;
            }
        }
        return false;
    }

    private static final class SelfSignedHttpsServer implements AutoCloseable {

        private final HttpsServer server;
        private final AtomicInteger requests = new AtomicInteger();

        private SelfSignedHttpsServer(HttpsServer server) {
            this.server = server;
        }

        static SelfSignedHttpsServer start() throws Exception {
            HttpsServer server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setHttpsConfigurator(new HttpsConfigurator(serverSslContext()));
            SelfSignedHttpsServer wrapper = new SelfSignedHttpsServer(server);
            server.createContext("/", exchange -> {
                exchange.getRequestBody().readAllBytes();
                wrapper.requests.incrementAndGet();
                byte[] body = CHAT_COMPLETION_BODY.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            server.start();
            return wrapper;
        }

        String baseUrl() {
            return "https://127.0.0.1:" + server.getAddress().getPort();
        }

        int requestCount() {
            return requests.get();
        }

        @Override
        public void close() {
            server.stop(0);
        }

        private static SSLContext serverSslContext() throws Exception {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            try (InputStream input =
                    OpenAIClientTlsVerificationTest.class.getResourceAsStream("/self-signed-llm-test-keystore.p12")) {
                keyStore.load(input, "changeit".toCharArray());
            }
            KeyManagerFactory factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            factory.init(keyStore, "changeit".toCharArray());
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(factory.getKeyManagers(), null, null);
            return context;
        }
    }
}
