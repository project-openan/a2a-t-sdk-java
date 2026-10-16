package net.openan.a2at.sdk.corpus.suites.notification;

import java.util.Set;
import net.openan.a2at.sdk.corpus.engine.CorpusWorkFlowSuite;

/**
 * Notification-T structured-input workflow suite: for every scenario directory under
 * {@code suites/notification/resources/}, runs the recorded SDK API flow of each case in
 * {@code input_case_from_data.json} against a real LLM.
 *
 * <p>Default API flow (also shown in the case files): {@code generateNotificationPromptFromDataWithSchema} →
 * {@code validateNotificationPromptAndDataFilling}.
 *
 * <p>Filters: {@code -Dcorpus.scenario} and {@code -Dcase.filter}, see {@link NotificationTFromTextWorkFlowTest}.
 */
public final class NotificationTFromDataWorkFlowTest extends CorpusWorkFlowSuite {

    @Override
    protected String extensionFolder() {
        return "notification";
    }

    @Override
    protected String inputFileName() {
        return INPUT_CASE_FROM_DATA;
    }

    @Override
    protected String flowType() {
        return FLOW_FROM_DATA;
    }

    @Override
    protected String runtimeKind() {
        return "notification";
    }

    @Override
    protected Set<String> apiNames() {
        return notificationApiNames();
    }
}