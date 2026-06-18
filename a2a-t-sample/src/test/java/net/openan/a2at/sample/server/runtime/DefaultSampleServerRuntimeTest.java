package net.openan.a2at.sample.server.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
}
