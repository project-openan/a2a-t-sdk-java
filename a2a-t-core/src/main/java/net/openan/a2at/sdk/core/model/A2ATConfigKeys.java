package net.openan.a2at.sdk.core.model;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Centralized configuration key constants for A2A-T SDK environment variables.
 * These keys correspond to entries in the {@code .env} file loaded by {@link DotEnvConfigSource}.
 *
 * @since 2026-06
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class A2ATConfigKeys {

    /**
     * Prompt runtime configuration keys.
     */
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class PromptRuntime {

        /** Controls the language used by prompt runtime components. */
        public static final String LANGUAGE = "A2AT_LANGUAGE";

        /** Selects the prompt source backend (e.g., classpath, local_file). */
        public static final String SOURCE_TYPE = "A2AT_PROMPT_SOURCE_TYPE";

        /** Optional root directory when A2AT_PROMPT_SOURCE_TYPE=local_file. */
        public static final String LOCAL_ROOT_DIR = "A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR";
    }

    /**
     * LLM runtime configuration keys.
     */
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class Llm {

        /** Selects the LLM provider used by the shared client (e.g., deepseek, openai_compatible). */
        public static final String PROVIDER = "A2AT_LLM_PROVIDER";

        /** Selects the default model name for the configured provider. */
        public static final String MODEL = "A2AT_LLM_MODEL";

        /** API key for the configured LLM provider. */
        public static final String API_KEY = "A2AT_LLM_API_KEY";

        /** Base URL for the configured LLM provider. */
        public static final String BASE_URL = "A2AT_LLM_BASE_URL";

        /** Number of chat history messages to keep. */
        public static final String HISTORY_WINDOW = "A2AT_LLM_HISTORY_WINDOW";

        /** Optional maximum tokens for completion calls. */
        public static final String MAX_TOKENS = "A2AT_LLM_MAX_TOKENS";

        /** Default sampling temperature. */
        public static final String TEMPERATURE = "A2AT_LLM_TEMPERATURE";

        /** Request timeout in seconds. */
        public static final String TIMEOUT_SECONDS = "A2AT_LLM_TIMEOUT_SECONDS";

        /** Maximum total number of tracked sessions. */
        public static final String SESSION_MAX_TOTAL = "A2AT_LLM_SESSION_MAX_TOTAL";

        /** Maximum number of tracked sessions per provider. */
        public static final String SESSION_MAX_PER_PROVIDER = "A2AT_LLM_SESSION_MAX_PER_PROVIDER";
    }

    /**
     * Negotiation runtime configuration keys.
     */
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class Negotiation {

        /** Selects the negotiation state store implementation (e.g., in_memory). */
        public static final String STATE_STORE_TYPE = "A2AT_NEGOTIATION_STATE_STORE_TYPE";
    }

    /**
     * Prompt compliance configuration keys.
     */
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class PromptCompliance {

        /** Enables or disables prompt compliance checks. */
        public static final String ENABLED = "A2AT_PROMPT_COMPLIANCE_ENABLED";
    }
}