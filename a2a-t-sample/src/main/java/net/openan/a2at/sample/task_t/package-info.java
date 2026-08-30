/**
 * Self-contained {@code Task-T} accuracy demonstration sample for the A2A-T Java SDK.
 *
 * <p>The demo closes the evaluation loop between the client facade ({@code A2ATClient}) and the server facade
 * ({@code A2ATServer}) over the {@code Task-T/network-layer/private-line-complaint/v1} template: each sample generates
 * a structured prompt with one of the two client APIs, the server validates the prompt and re-extracts the parameters,
 * and the extracted values are compared against the sample ground truth to compute field-level accuracy and
 * sample-level pass rates.
 *
 * @since 2026-08
 */
package net.openan.a2at.sample.task_t;
