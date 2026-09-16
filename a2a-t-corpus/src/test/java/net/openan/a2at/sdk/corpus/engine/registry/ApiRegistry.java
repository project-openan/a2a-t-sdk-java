package net.openan.a2at.sdk.corpus.engine.registry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Explicit registry mapping SDK facade method names to their {@link ApiHandler}. Registration is declarative (never
 * reflective): adding a new API is one registry line that the IDE can navigate. Unknown method names fail at load
 * time.
 */
public final class ApiRegistry {

    private final Map<String, ApiHandler> handlers = new LinkedHashMap<>();

    public ApiRegistry register(String apiName, ApiHandler handler) {
        if (handlers.containsKey(apiName)) {
            throw new IllegalStateException("API registered twice: " + apiName);
        }
        handlers.put(apiName, handler);
        return this;
    }

    public Optional<ApiHandler> handler(String apiName) {
        return Optional.ofNullable(handlers.get(apiName));
    }

    public java.util.Set<String> apiNames() {
        return Collections.unmodifiableSet(handlers.keySet());
    }
}