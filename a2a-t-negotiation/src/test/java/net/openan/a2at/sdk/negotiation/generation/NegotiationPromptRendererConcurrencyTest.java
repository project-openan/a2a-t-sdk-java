package net.openan.a2at.sdk.negotiation.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.openan.a2at.sdk.negotiation.resources.DefaultNegotiationTemplateLoader;
import net.openan.a2at.sdk.negotiation.resources.NegotiationReference;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the package-private {@link NegotiationPromptRenderer} is a pure function: repeated concurrent calls
 * with the same input must return the same result.
 */
class NegotiationPromptRendererConcurrencyTest {

    private static final int THREADS = 8;

    private static final int ITERATIONS = 200;

    @Test
    void promptRendererIsPureUnderConcurrentCalls() throws Exception {
        String template = new DefaultNegotiationTemplateLoader("zh-CN")
                .load(new NegotiationReference(
                        net.openan.a2at.sdk.negotiation.content.NegotiationType.INFORMATION,
                        net.openan.a2at.sdk.core.model.NegotiationPerformative.PROPOSE,
                        "zh-CN"))
                .content();
        Map<String, String> slots = Map.of(
                "协商上下文",
                "- id: 3dbc13b5-bd57-4c2b-b503-24e381b6c8d3\n- round: 1\n- maxRounds: 5",
                "所需信息项",
                "1. 节能区域：松山湖");
        NegotiationPromptRenderer renderer = new NegotiationPromptRenderer();
        String baseline = renderer.render(template, slots);

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<Void>> futures = new ArrayList<>();
            for (int index = 0; index < THREADS; index++) {
                futures.add(pool.submit(() -> {
                    for (int iteration = 0; iteration < ITERATIONS; iteration++) {
                        assertEquals(baseline, renderer.render(template, slots), "renderer output must be stable");
                    }
                    return null;
                }));
            }
            for (Future<Void> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
