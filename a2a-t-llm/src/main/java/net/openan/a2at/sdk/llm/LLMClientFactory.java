package net.openan.a2at.sdk.llm;

import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.openan.a2at.sdk.llm.providers.OpenAIClient;

/**
 * Registry and factory for LLM provider clients.
 *
 * @since 2026-06
 */
public final class LLMClientFactory {

    private static final Map<String, Class<? extends LLMClient>> CLIENTS = new LinkedHashMap<>();

    static {
        CLIENTS.put("openai", OpenAIClient.class);
    }

    private LLMClientFactory() {}

    public static void register(String provider, Class<? extends LLMClient> clientClass) {
        String normalizedProvider = normalizeProvider(provider);
        if (CLIENTS.containsKey(normalizedProvider)) {
            throw new LLMConfigError("LLM provider '" + normalizedProvider + "' is already registered");
        }
        CLIENTS.put(normalizedProvider, clientClass);
    }

    public static LLMClient create(String provider, LLMClientConfig config) {
        String normalizedProvider = normalizeProvider(provider);
        Class<? extends LLMClient> clientClass = CLIENTS.get(normalizedProvider);
        if (clientClass == null) {
            throw new LLMConfigError("Unknown llm provider: " + normalizedProvider + ". Available: " + availableProviders());
        }
        try {
            return clientClass.getConstructor(LLMClientConfig.class).newInstance(config);
        } catch (NoSuchMethodException exception) {
            throw new LLMConfigError(
                    "LLM provider '" + normalizedProvider + "' must expose a public LLMClientConfig constructor",
                    exception);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException exception) {
            throw new LLMConfigError("Failed to create LLM provider '" + normalizedProvider + "'", exception);
        }
    }

    public static List<String> availableProviders() {
        return CLIENTS.keySet().stream().sorted().toList();
    }

    private static String normalizeProvider(String value) {
        String provider = value == null ? "" : value.trim();
        if (provider.isEmpty()
                || !provider.equals(provider.toLowerCase(Locale.ROOT))
                || provider.chars().anyMatch(Character::isWhitespace)) {
            throw new LLMConfigError("LLM provider must use lowercase non-whitespace characters");
        }
        return provider;
    }
}
