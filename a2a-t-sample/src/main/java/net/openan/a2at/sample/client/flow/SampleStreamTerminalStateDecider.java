package net.openan.a2at.sample.client.flow;

import org.a2aproject.sdk.client.ClientEvent;
import org.a2aproject.sdk.client.TaskEvent;
import org.a2aproject.sdk.client.TaskUpdateEvent;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;

/**
 * Decides when a streamed A2A task has reached a terminal state for sample client iteration.
 *
 * @since 2026-05
 */
public final class SampleStreamTerminalStateDecider {
    private SampleStreamTerminalStateDecider() {
    }

    public static boolean isTerminal(ClientEvent event) {
        if (event instanceof TaskEvent taskEvent) {
            return isTerminal(taskEvent.getTask().status().state());
        }
        if (event instanceof TaskUpdateEvent taskUpdateEvent) {
            if (taskUpdateEvent.getUpdateEvent() instanceof TaskStatusUpdateEvent statusUpdateEvent) {
                return isTerminal(statusUpdateEvent.status().state());
            }
            if (taskUpdateEvent.getUpdateEvent() instanceof TaskArtifactUpdateEvent) {
                return false;
            }
        }
        return false;
    }

    private static boolean isTerminal(TaskState state) {
        return state == TaskState.TASK_STATE_COMPLETED
                || state == TaskState.TASK_STATE_FAILED
                || state == TaskState.TASK_STATE_CANCELED
                || state == TaskState.TASK_STATE_REJECTED
                || state == TaskState.TASK_STATE_INPUT_REQUIRED
                || state == TaskState.TASK_STATE_AUTH_REQUIRED;
    }
}


