package net.openan.a2at.sample.authz_policy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.openan.a2at.sample.authz_policy.AuthzScenarioRunner.ScenarioOutcome;
import net.openan.a2at.sample.authz_policy.AuthzScenarioRunner.ScenarioResult;
import net.openan.a2at.sdk.core.exception.A2ATError;
import net.openan.a2at.sdk.core.exception.ErrorCatalog;
import net.openan.a2at.sdk.core.model.SlotValidationError;

/**
 * Concurrent scenario executor that runs multiple {@link AuthzScenario} instances in parallel using a fixed thread
 * pool, while preserving input order in the returned result list.
 *
 * <p>Each task runs the complete client→server flow for a single scenario. Internal failures are caught per-task and
 * converted to {@code infra.internal_error} outcomes, isolating the remaining scenarios from a single failure.
 *
 * <p>An optional {@link ProgressListener} receives per-completion callbacks (synchronized, index 1-based) and can be
 * {@code null} when no progress reporting is needed. An optional {@link AuthzReasoningCapture} records LLM reasoning
 * for each scenario when enabled.
 *
 * @since 2026-08
 */
public final class AuthzScenarioExecutor {

    private static final int DEFAULT_WORKERS = 8;

    private final AuthzScenarioRunner runner;

    /** @param runner scenario runner (stateless, safe for concurrent reuse) */
    public AuthzScenarioExecutor(AuthzScenarioRunner runner) {
        this.runner = runner;
    }

    /**
     * Progress listener invoked after each scenario completes.
     *
     * <p>Calls are serialized with a lock so the listener implementation does not need to be thread-safe. The callback
     * runs on the worker thread and must return quickly.
     *
     * @param completedIndex 1-based completion order (not input index)
     * @param outcome the scenario outcome
     * @param elapsedSeconds wall-clock time for this scenario in seconds, non-negative
     * @since 2026-08
     */
    @FunctionalInterface
    public interface ProgressListener {
        void onCompleted(int completedIndex, ScenarioOutcome outcome, double elapsedSeconds);
    }

    /**
     * Executes all scenarios concurrently and returns outcomes in the same order as the input list.
     *
     * @param scenarios scenarios to execute
     * @param paramSchema business-level parameter schema for validation
     * @param templateUri template URI for prompt generation
     * @param workers maximum number of concurrent worker threads
     * @param onCompleted optional progress callback; may be {@code null}
     * @return outcomes in input order
     * @throws IllegalArgumentException if {@code workers < 1}
     * @throws IllegalStateException if the calling thread is interrupted while waiting
     * @throws java.util.concurrent.RejectedExecutionException if a task cannot be accepted
     * @since 2026-08
     */
    public List<ScenarioOutcome> executeAll(
            List<AuthzScenario> scenarios,
            Map<String, Object> paramSchema,
            String templateUri,
            int workers,
            ProgressListener onCompleted) {
        return executeAll(scenarios, paramSchema, templateUri, workers, onCompleted, null);
    }

    /**
     * Executes all scenarios concurrently with optional reasoning capture.
     *
     * @param scenarios scenarios to execute
     * @param paramSchema business-level parameter schema for validation
     * @param templateUri template URI for prompt generation
     * @param workers maximum number of concurrent worker threads
     * @param onCompleted optional progress callback; may be {@code null}
     * @param capture optional reasoning capture; may be {@code null}
     * @return outcomes in input order
     * @throws IllegalArgumentException if {@code workers < 1}
     * @throws IllegalStateException if the calling thread is interrupted while waiting
     * @throws java.util.concurrent.RejectedExecutionException if a task cannot be accepted
     * @since 2026-08
     */
    public List<ScenarioOutcome> executeAll(
            List<AuthzScenario> scenarios,
            Map<String, Object> paramSchema,
            String templateUri,
            int workers,
            ProgressListener onCompleted,
            AuthzReasoningCapture capture) {
        int size = scenarios.size();
        if (size == 0) {
            return List.of();
        }
        int poolSize = Math.min(workers, size);
        AtomicInteger nameCounter = new AtomicInteger(0);
        ExecutorService pool = Executors.newFixedThreadPool(poolSize, r -> {
            Thread t = new Thread(r, "authz-worker-" + nameCounter.incrementAndGet());
            t.setDaemon(false);
            return t;
        });
        AtomicInteger completedCounter = new AtomicInteger(0);
        Object callbackLock = new Object();
        List<Future<ScenarioOutcome>> futures = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            final int index = i;
            futures.add(pool.submit(() -> {
                String label = scenarios.get(index).label();
                if (capture != null) {
                    capture.beginScenario(label);
                }
                long startNanos = System.nanoTime();
                ScenarioOutcome outcome;
                try {
                    outcome = runner.run(scenarios.get(index), paramSchema, templateUri);
                } catch (Throwable t) {
                    outcome = buildInternalErrorOutcome(scenarios.get(index), t);
                } finally {
                    if (capture != null) {
                        capture.endScenario();
                    }
                }
                double elapsed = (System.nanoTime() - startNanos) / 1e9;
                if (onCompleted != null) {
                    synchronized (callbackLock) {
                        onCompleted.onCompleted(completedCounter.incrementAndGet(), outcome, elapsed);
                    }
                }
                return outcome;
            }));
        }

        List<ScenarioOutcome> outcomes = new ArrayList<>(size);
        try {
            for (int i = 0; i < size; i++) {
                outcomes.add(futures.get(i).get());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pool.shutdownNow();
            throw new IllegalStateException("Scenario execution interrupted", e);
        } catch (ExecutionException e) {
            pool.shutdownNow();
            throw new IllegalStateException("Unexpected execution exception", e);
        } finally {
            pool.shutdown();
            try {
                if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
                    pool.shutdownNow();
                }
            } catch (InterruptedException e) {
                pool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        return outcomes;
    }

    /**
     * Resolves the worker count from the {@code authz.workers} system property.
     *
     * @return worker count, defaulting to {@value DEFAULT_WORKERS}
     * @throws IllegalArgumentException if the property is set to a non-integer or value &lt; 1
     * @since 2026-08
     */
    public static int resolveWorkers() {
        String raw = System.getProperty("authz.workers");
        if (raw == null || raw.isBlank()) {
            return DEFAULT_WORKERS;
        }
        int workers;
        try {
            workers = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid authz.workers value: '" + raw
                    + "'. Expected an integer >= 1 (default " + DEFAULT_WORKERS + ").");
        }
        if (workers < 1) {
            throw new IllegalArgumentException("Invalid authz.workers value: " + workers
                    + ". Expected an integer >= 1 (default " + DEFAULT_WORKERS + ").");
        }
        return workers;
    }

    private ScenarioOutcome buildInternalErrorOutcome(AuthzScenario scenario, Throwable t) {
        return new ScenarioOutcome(
                new ScenarioResult(
                        ErrorCatalog.INFRA_INTERNAL_ERROR.getCode(),
                        false,
                        new A2ATError(ErrorCatalog.INFRA_INTERNAL_ERROR.getCode(), t.getMessage(), t),
                        List.of(new SlotValidationError(
                                "_llm", ErrorCatalog.INFRA_INTERNAL_ERROR.getCode(), t.getMessage())),
                        null,
                        null,
                        null,
                        List.of()),
                null,
                null);
    }
}
