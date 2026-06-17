package net.openan.a2at.sdk.prompt.analysis.model;

/**
 * Result of scenario recognition.
 *
 * @param matched whether a scenario was matched
 * @param scenarioCode matched scenario code when available
 * @param errorMessage validation or recognition message when unmatched
 * @since 2026-06
 */
public record ScenarioRecognitionResult(boolean matched, String scenarioCode, String errorMessage) {}
