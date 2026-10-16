package net.openan.a2at.sdk.corpus.suites.notification;

import java.util.Set;
import net.openan.a2at.sdk.corpus.engine.CorpusWorkFlowSuite;

/**
 * Notification-T natural-language workflow suite: for every scenario directory under
 * {@code suites/notification/resources/}, runs the recorded SDK API flow of each case in
 * {@code input_case_from_text.json} against a real LLM.
 *
 * <p>Default API flow (also shown in the case files): {@code generateNotificationPromptFromText} →
 * {@code validateNotificationPromptAndDataFilling}.
 *
 * <p>Filters: {@code -Dcorpus.scenario=service-recovery} (glob, comma-separated) and
 * {@code -Dcase.filter=TC00000001} (glob on case id).
 */
public final class NotificationTFromTextWorkFlowTest extends CorpusWorkFlowSuite {

    @Override
    protected String extensionFolder() {
        return "notification";
    }

    @Override
    protected String inputFileName() {
        return INPUT_CASE_FROM_TEXT;
    }

    @Override
    protected String flowType() {
        return FLOW_FROM_TEXT;
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