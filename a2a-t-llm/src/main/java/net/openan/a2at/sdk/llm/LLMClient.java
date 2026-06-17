package net.openan.a2at.sdk.llm;

import java.nio.file.Path;
import java.util.Map;
import net.openan.a2at.sdk.core.model.DotEnvConfigSource;
import net.openan.a2at.sdk.llm.adapter.LLMAdapter;
import net.openan.a2at.sdk.llm.adapter.OpenAICompatibleAdapter;
import net.openan.a2at.sdk.llm.model.LLMResponse;
import net.openan.a2at.sdk.llm.model.StructuredGenerationRequest;
import net.openan.a2at.sdk.llm.config.LlmClientConfig;

/**
 * Default facade entry for structured LLM generation.
 * The caller provides the `.env` file path explicitly, typically after copying the repository `env.example`.
 *
 * @since 2026-05
 */
public class LLMClient {

    private final Path envPath;

    private final LlmClientConfig clientConfig;

    private final LLMAdapter adapter;

    /**
     * Creates one facade bound to one caller-supplied `.env` file path.
     *
     * @param envPath caller-supplied `.env` file path
     */
    public LLMClient(Path envPath) {
        this(envPath, loadConfig(envPath), null);
    }

    /**
     * Creates one facade bound to one caller-supplied `.env` file path and one explicitly injected adapter.
     *
     * @param envPath caller-supplied `.env` file path
     * @param adapter explicit adapter override
     */
    protected LLMClient(Path envPath, LLMAdapter adapter) {
        this(envPath, loadConfig(envPath), adapter);
    }

    private LLMClient(Path envPath, LlmClientConfig clientConfig, LLMAdapter adapter) {
        this.envPath = envPath;
        this.clientConfig = clientConfig;
        this.adapter = adapter == null ? new OpenAICompatibleAdapter(clientConfig) : adapter;
    }

    /**
     * Executes one structured generation request.
     *
     * @param request structured generation request
     * @return structured generation response
     */
    public LLMResponse structured(StructuredGenerationRequest request) {
        return adapter.structured(request);
    }

    private static LlmClientConfig loadConfig(Path envPath) {
        Map<String, String> values = DotEnvConfigSource.load(envPath);
        return LlmClientConfig.fromMap(values);
    }
}
