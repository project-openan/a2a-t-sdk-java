package net.openan.a2at.sample.server.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultSampleServerRuntimeTest {

    @Test
    void resolveBindUsesDefaultsWhenHostAndPortAreBlank() throws IOException {
        Path envPath = Files.createTempFile("a2a-t-server", ".env");
        Files.writeString(envPath, "A2AT_SAMPLE_HOST=\nA2AT_SAMPLE_PORT=\n");

        ServerBind bind = new DefaultSampleServerRuntime(envPath, message -> {
        }).resolveBind();

        assertEquals("127.0.0.1", bind.host());
        assertEquals(8000, bind.port());
    }

    @Test
    void createSampleThreadRegistersUncaughtExceptionHandler() throws InterruptedException {
        List<String> logs = new ArrayList<>();
        RuntimeException failure = new RuntimeException("boom");

        Thread thread = DefaultSampleServerRuntime.createSampleThread(
                () -> {
                    throw failure;
                },
                logs::add);
        thread.start();
        thread.join(1000L);

        assertEquals("a2a-t-sample-server", thread.getName());
        assertTrue(thread.isDaemon());
        assertEquals(1, logs.size());
        assertTrue(logs.get(0).contains("a2a-t-sample-server"));
        assertTrue(logs.get(0).contains(RuntimeException.class.getName()));
        assertTrue(logs.get(0).contains("boom"));
    }
}
