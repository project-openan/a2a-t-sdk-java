package net.openan.a2at.sample.service_recovery.server.runtime;

import java.nio.file.Path;

/**
 * Creates server-sample runtime assemblies.
 *
 * @since 2026-05
 */
@FunctionalInterface
public interface SampleServerRuntimeFactory {

    SampleServerRuntime create(Path envPath);
}
