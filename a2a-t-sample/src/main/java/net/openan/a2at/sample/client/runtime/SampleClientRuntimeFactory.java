package net.openan.a2at.sample.client.runtime;

import java.nio.file.Path;

/**
 * Factory for constructing the runtime collaborators used by the client sample main flow.
 *
 * @since 2026-05
 */
public interface SampleClientRuntimeFactory {

    SampleClientRuntime create(Path envPath);
}


