/**
 * Authorization-T demo: configuration-driven prompt generation and compliance verification.
 *
 * <p>The demo loads scenarios from a JSON configuration file, generates authorization prompts through the
 * {@code A2ATClient} facade, validates them through the {@code A2ATServer} facade, and reports
 * {@code PASS}/{@code FAIL}/{@code ERROR} results with a non-zero exit code when any scenario fails.
 *
 * <p>Layout:
 *
 * <ul>
 *   <li>{@code AuthzPromptGenerator} - functional interface for prompt generation;
 *   <li>{@code AuthzPromptValidator} - functional interface for prompt validation;
 *   <li>{@code AuthzScenario} - immutable scenario configuration record;
 *   <li>{@code AuthzScenarioLoader} - JSON scenario file parser and validator;
 *   <li>{@code AuthzScenarioRunner} - scenario execution engine with injectable generator/validator;
 *   <li>{@code AuthzSampleMain} - entry point that wires everything together.
 * </ul>
 *
 * @since 2026-08
 */
package net.openan.a2at.sample.authz_policy;
