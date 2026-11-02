/**
 * Negotiation end-to-end demo: SPN private-line-complaint diagnosis over real a2a-java HTTP A2A.
 *
 * <p>Implements the 4-message flow from the A2A-T protocol spec (§7.3/§7.4):
 *
 * <ol>
 *   <li>client -> Task-T (params missing);
 *   <li>server -> Negotiation-T information request (missing params) -> INPUT_REQUIRED;
 *   <li>client -> Task-T (params filled) + Negotiation-T accept;
 *   <li>server -> diagnosis result artifact -> COMPLETED.
 * </ol>
 *
 * Layout:
 *
 * <ul>
 *   <li>{@code client/} - 4-message client orchestration over a2a-java RestTransport;
 *   <li>{@code server/} - AgentExecutor (Task-T validation -> negotiation request -> diagnosis) + runtime;
 *   <li>{@code shared/} - A2A metadata bridge, scenario data, extension/template URI constants.
 * </ul>
 *
 * The core path uses deterministic {@code fromData} generation with the negotiation session context carried in the
 * message metadata (no LLM key needed); optional {@code validateAndFilling} runs when a real LLM key is configured.
 *
 * @since 2026-08
 */
package net.openan.a2at.sample.negotiation;
