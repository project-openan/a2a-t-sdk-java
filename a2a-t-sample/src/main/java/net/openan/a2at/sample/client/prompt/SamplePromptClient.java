package net.openan.a2at.sample.client.prompt;

import net.openan.a2at.sdk.client.model.PromptGenerationResult;

/**
 * Prompt-generation bridge used by the client sample flow.
 *
 * @since 2026-05
 */
public interface SamplePromptClient {

    PromptGenerationResult generateTaskPrompt(Object userInput);
}


