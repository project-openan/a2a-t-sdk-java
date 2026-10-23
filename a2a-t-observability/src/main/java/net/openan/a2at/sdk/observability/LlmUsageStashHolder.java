package net.openan.a2at.sdk.observability;

import org.jspecify.annotations.Nullable;

/** ThreadLocal bridge: LLM layer -> entry span (SDK is fully synchronous). */
public final class LlmUsageStashHolder {

    private static final ThreadLocal<LlmUsageStash> STASH = new ThreadLocal<>();

    private LlmUsageStashHolder() {}

    public static void set(LlmUsageStash usage) {
        STASH.set(usage);
    }

    public static @Nullable LlmUsageStash getAndClear() {
        LlmUsageStash stash = STASH.get();
        STASH.remove();
        return stash;
    }

    public static void clear() {
        STASH.remove();
    }
}
