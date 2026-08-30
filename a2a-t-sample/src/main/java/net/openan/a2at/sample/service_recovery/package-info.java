/**
 * Service-recovery Notification-T sample.
 *
 * <p>This sample exercises the Notification-T {@code service-recovery} scenario end to end:
 *
 * <ul>
 *   <li>{@code client} / {@code server} / {@code shared} — full HTTP e2e sample mirroring the subscribe-incident
 *       layout: the client generates the subscription prompt with the two Notification-T generation APIs, sends it over
 *       the real a2a-java REST transport, and the server validates the rendered prompt with
 *       {@code validateAndFillingNotificationData} before reporting mock service recovery events as artifacts.
 * </ul>
 *
 * <p>The sample inputs and checks target the zh-CN resources; keep {@code A2AT_LANGUAGE=zh-CN} in the sample
 * environment files.
 *
 * @since 2026-08
 */
package net.openan.a2at.sample.service_recovery;
