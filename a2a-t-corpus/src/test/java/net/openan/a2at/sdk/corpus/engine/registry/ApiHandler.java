package net.openan.a2at.sdk.corpus.engine.registry;

import java.util.Map;

/** Invokes one registered SDK facade method with fully resolved (post {@code $fromStep}) arguments. */
public interface ApiHandler {

    /**
     * Runs the SDK call and returns its payload as a serializable map.
     *
     * @param args resolved step arguments
     * @return serializable response payload
     */
    Map<String, Object> run(Map<String, Object> args);
}