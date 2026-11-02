package net.openan.a2at.sdk.corpus.engine.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;

/**
 * Unified runtime configuration for the accuracy verification corpus.
 *
 * <p>Configuration keys follow the project-root {@code env.example} naming ({@code A2AT_LANGUAGE} and the
 * {@code A2AT_LLM_*} family), so the corpus {@code env.example} is a tuned subset of the root template and one set of
 * names serves both. One shared configuration serves every -T extension suite: values are resolved in the order
 * system properties ({@code a2at.language}, {@code a2at.llm.*}) &gt; the module-root {@code .env} file
 * ({@code A2AT_LANGUAGE}, {@code A2AT_LLM_*}) &gt; process environment variables. {@code A2AT_LANGUAGE} selects both
 * the prompt-resource language and the language-specific case-file variant
 * ({@code input_case_from_*_en-US.json}); it defaults to {@code zh-CN} so the committed Chinese corpus keeps working
 * unchanged.
 *
 * <p>This module must run against a real LLM; a missing or invalid configuration therefore fails fast with an
 * actionable error instead of producing silently wrong results. CI excludes this module entirely, so the fail-fast
 * never disturbs the pipeline.
 */
public final class CorpusEnvConfig {

    /** Environment-file keys (also accepted as process environment variables); names mirror the root {@code env.example}. */
    static final String ENV_LANGUAGE = "A2AT_LANGUAGE";

    static final String ENV_PROVIDER = "A2AT_LLM_PROVIDER";

    static final String ENV_BASE_URL = "A2AT_LLM_BASE_URL";

    static final String ENV_API_KEY = "A2AT_LLM_API_KEY";

    static final String ENV_MODEL = "A2AT_LLM_MODEL";

    static final String ENV_TEMPERATURE = "A2AT_LLM_TEMPERATURE";

    static final String ENV_TIMEOUT_SECONDS = "A2AT_LLM_TIMEOUT_SECONDS";

    static final String ENV_MAX_ATTEMPTS = "A2AT_LLM_MAX_ATTEMPTS";

    static final String ENV_MAX_TOKENS = "A2AT_LLM_MAX_TOKENS";

    /** System-property keys, taking precedence over the {@code .env} file and environment variables. */
    private static final String PROP_LANGUAGE = "a2at.language";

    private static final String PROP_PROVIDER = "a2at.llm.provider";

    private static final String PROP_BASE_URL = "a2at.llm.base.url";

    private static final String PROP_API_KEY = "a2at.llm.api.key";

    private static final String PROP_MODEL = "a2at.llm.model";

    private static final String PROP_TEMPERATURE = "a2at.llm.temperature";

    private static final String PROP_TIMEOUT_SECONDS = "a2at.llm.timeout.seconds";

    private static final String PROP_MAX_ATTEMPTS = "a2at.llm.max.attempts";

    private static final String PROP_MAX_TOKENS = "a2at.llm.max.tokens";

    private static final String DEFAULT_PROVIDER = "openai";

    private static final int DEFAULT_MAX_ATTEMPTS = 3;

    private static CorpusEnvConfig instance;

    private final String language;

    private final String provider;

    private final String baseUrl;

    private final String apiKey;

    private final String model;

    private final Double temperature;

    private final Double timeoutSeconds;

    private final int maxAttempts;

    private final Integer maxTokens;

    private CorpusEnvConfig(
            String language,
            String provider,
            String baseUrl,
            String apiKey,
            String model,
            Double temperature,
            Double timeoutSeconds,
            int maxAttempts,
            Integer maxTokens) {
        this.language = language;
        this.provider = provider;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.temperature = temperature;
        this.timeoutSeconds = timeoutSeconds;
        this.maxAttempts = maxAttempts;
        this.maxTokens = maxTokens;
    }

    public static synchronized CorpusEnvConfig load() {
        if (instance == null) {
            instance = create();
        }
        return instance;
    }

    private static CorpusEnvConfig create() {
        return create(readEnvFile(envFile()));
    }

    static CorpusEnvConfig create(Map<String, String> envFileValues) {
        String language = resolve(ENV_LANGUAGE, PROP_LANGUAGE, envFileValues);
        if (isBlank(language)) {
            language = CorpusLanguage.DEFAULT_LANGUAGE;
        } else {
            language = language.trim();
        }
        String provider = resolve(ENV_PROVIDER, PROP_PROVIDER, envFileValues);
        if (isBlank(provider)) {
            provider = DEFAULT_PROVIDER;
        }
        String baseUrl = resolve(ENV_BASE_URL, PROP_BASE_URL, envFileValues);
        String apiKey = resolve(ENV_API_KEY, PROP_API_KEY, envFileValues);
        String model = resolve(ENV_MODEL, PROP_MODEL, envFileValues);

        StringBuilder problems = new StringBuilder();
        String languageProblem = languageProblem(language);
        if (languageProblem != null) {
            problems.append("\n").append(languageProblem);
        }
        if (!DEFAULT_PROVIDER.equals(provider.trim())) {
            problems.append("\n- ")
                    .append(ENV_PROVIDER)
                    .append(" must be \"")
                    .append(DEFAULT_PROVIDER)
                    .append("\" (currently \"")
                    .append(provider.trim())
                    .append("\")");
        }
        if (isBlank(baseUrl)) {
            problems.append("\n- ").append(ENV_BASE_URL).append(" (or -D").append(PROP_BASE_URL)
                    .append(") is not configured");
        }
        if (isBlank(apiKey)) {
            problems.append("\n- ").append(ENV_API_KEY).append(" (or -D").append(PROP_API_KEY)
                    .append(") is not configured");
        }
        if (isBlank(model)) {
            problems.append("\n- ").append(ENV_MODEL).append(" (or -D").append(PROP_MODEL)
                    .append(") is not configured");
        }
        if (problems.length() > 0) {
            throw new IllegalStateException(
                    "a2a-t-corpus requires a real LLM configuration; invalid or missing values:"
                            + problems
                            + "\nCopy a2a-t-corpus/env.example to a2a-t-corpus/.env and fill in the required"
                            + " entries (or override them with system properties)."
                            + "\nCI pipelines should exclude this module with -pl '!a2a-t-corpus'.");
        }

        return new CorpusEnvConfig(
                language,
                provider.trim(),
                baseUrl.trim(),
                apiKey.trim(),
                model.trim(),
                parseOptionalDouble(ENV_TEMPERATURE, PROP_TEMPERATURE, envFileValues),
                parseOptionalDouble(ENV_TIMEOUT_SECONDS, PROP_TIMEOUT_SECONDS, envFileValues),
                parseMaxAttempts(ENV_MAX_ATTEMPTS, PROP_MAX_ATTEMPTS, envFileValues),
                parseOptionalInteger(ENV_MAX_TOKENS, PROP_MAX_TOKENS, envFileValues));
    }

    /**
     * Resolves only the corpus prompt language, without validating the LLM configuration.
     *
     * <p>The corpus selects its language-specific case files at test-collection time, before the lazy
     * {@link #load()} runtime loading. This helper therefore touches only {@code A2AT_LANGUAGE}; it keeps the
     * collection path free of the fail-fast LLM checks.
     *
     * @return the configured language, or {@code zh-CN} when unset.
     * @throws IllegalStateException when the configured language is not supported by the bundled resources.
     */
    public static String resolveLanguage() {
        return resolveLanguage(readEnvFile(envFile()), System.getenv());
    }

    static String resolveLanguage(Map<String, String> envFileValues, Map<String, String> processEnvironment) {
        String language = resolve(ENV_LANGUAGE, PROP_LANGUAGE, envFileValues, processEnvironment);
        if (isBlank(language)) {
            return CorpusLanguage.DEFAULT_LANGUAGE;
        }
        language = language.trim();
        String problem = languageProblem(language);
        if (problem != null) {
            throw new IllegalStateException(
                    "a2a-t-corpus has an invalid prompt-language configuration:"
                            + "\n"
                            + problem
                            + "\nSet "
                            + ENV_LANGUAGE
                            + " in the module-root .env file or the process environment (or override it with -D"
                            + PROP_LANGUAGE
                            + ").");
        }
        return language;
    }

    private static String languageProblem(String language) {
        if (CorpusLanguage.SUPPORTED_LANGUAGES.contains(language)) {
            return null;
        }
        String supported = String.join(", ", new TreeSet<>(CorpusLanguage.SUPPORTED_LANGUAGES));
        return "- " + ENV_LANGUAGE + " must be one of: " + supported + " (currently \"" + language + "\")";
    }

    private static Map<String, String> readEnvFile(Path envFile) {
        Path normalizedEnvFile = envFile.toAbsolutePath().normalize();
        Map<String, String> values = new LinkedHashMap<>();
        if (!Files.isRegularFile(normalizedEnvFile)) {
            return values;
        }
        try {
            for (String rawLine : Files.readAllLines(normalizedEnvFile, StandardCharsets.UTF_8)) {
                String line = rawLine.strip();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int equals = line.indexOf('=');
                if (equals <= 0) {
                    continue;
                }
                String key = line.substring(0, equals).strip();
                String value = stripQuotes(line.substring(equals + 1).strip());
                values.put(key, value);
            }
        } catch (IOException error) {
            throw new IllegalStateException("Failed to read " + normalizedEnvFile + ": " + error.getMessage(), error);
        }
        return values;
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private static String resolve(String envKey, String propKey, Map<String, String> envFileValues) {
        return resolve(envKey, propKey, envFileValues, System.getenv());
    }

    private static String resolve(
            String envKey, String propKey, Map<String, String> envFileValues, Map<String, String> processEnvironment) {
        String fromProperty = System.getProperty(propKey);
        if (fromProperty != null && !fromProperty.isBlank()) {
            return fromProperty.strip();
        }
        String fromFile = envFileValues.get(envKey);
        if (fromFile != null && !fromFile.isBlank()) {
            return fromFile;
        }
        String fromEnvironment = processEnvironment.get(envKey);
        return fromEnvironment == null ? "" : fromEnvironment;
    }

    private static Double parseOptionalDouble(String envKey, String propKey, Map<String, String> envFileValues) {
        String raw = resolve(envKey, propKey, envFileValues);
        if (isBlank(raw)) {
            return null;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException error) {
            throw new IllegalStateException(envKey + " must be numeric: '" + raw.trim() + "'", error);
        }
    }

    private static Integer parseOptionalInteger(String envKey, String propKey, Map<String, String> envFileValues) {
        String raw = resolve(envKey, propKey, envFileValues);
        if (isBlank(raw)) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException error) {
            throw new IllegalStateException(envKey + " must be an integer: '" + raw.trim() + "'", error);
        }
    }

    private static int parseMaxAttempts(String envKey, String propKey, Map<String, String> envFileValues) {
        String raw = resolve(envKey, propKey, envFileValues);
        if (isBlank(raw)) {
            return DEFAULT_MAX_ATTEMPTS;
        }
        int parsed;
        try {
            parsed = Integer.parseInt(raw.trim());
        } catch (NumberFormatException error) {
            throw new IllegalStateException(envKey + " must be an integer between 1 and 10: '" + raw.trim() + "'", error);
        }
        if (parsed < 1 || parsed > 10) {
            throw new IllegalStateException(envKey + " must be between 1 and 10: " + parsed);
        }
        return parsed;
    }

    /**
     * Returns the {@code .env} path as seen by the SDK builders, regardless of whether the file exists.
     */
    public Path envPath() {
        return envFile().toAbsolutePath().normalize();
    }

    private static Path envFile() {
        return Path.of(System.getProperty("user.dir", "."), ".env");
    }

    public String language() {
        return language;
    }

    public String provider() {
        return provider;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String apiKey() {
        return apiKey;
    }

    public String model() {
        return model;
    }

    public Double temperature() {
        return temperature;
    }

    public Double timeoutSeconds() {
        return timeoutSeconds;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public Integer maxTokens() {
        return maxTokens;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}