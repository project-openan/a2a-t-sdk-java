package net.openan.a2at.sample.negotiation.shared;

import java.util.List;
import net.openan.a2at.sdk.client.A2ATClient;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;
import net.openan.a2at.sdk.server.A2ATServer;

/**
 * Strategy abstraction for generating negotiation messages, isolating the fromData (rule-based) vs fromText (LLM)
 * difference behind a single interface.
 *
 * <p>Everything else in the demo — Task-T generation, {@code validateTaskPromptAndDataFilling} parameter validation, the
 * negotiation state machine — is identical regardless of which strategy is active. Only the negotiation prompt
 * generation step differs: fromData builds typed records and calls the deterministic API; fromText passes natural
 * language and lets the SDK's LLM extraction step parse it.
 *
 * @since 2026-08
 */
public interface NegotiationStrategy {

    /**
     * Generates a propose-phase negotiation message from the missing information items.
     *
     * @param server server facade (both facades expose the same fromData/fromText methods)
     * @param ctx negotiation session context
     * @param missingItems items the counterpart should provide
     * @param relationship how the items relate (may be null)
     * @param templateUri propose template
     * @return generated metadata content
     */
    MetadataContent generatePropose(
            A2ATServer server,
            NegotiationContext ctx,
            List<NegotiationItem> missingItems,
            String relationship,
            String templateUri);

    /**
     * Generates an accept-phase negotiation message from the filled information items (client facade).
     *
     * @param facade client or server facade (both expose the same fromData/fromText methods)
     * @param ctx negotiation session context
     * @param filledItems items delivered with the accept
     * @param templateUri accept-reject template
     * @return generated metadata content
     */
    MetadataContent generateAccept(
            A2ATClient facade, NegotiationContext ctx, List<NegotiationItem> filledItems, String templateUri);

    /**
     * Generates an accept-phase negotiation message from the filled information items (server facade).
     *
     * @param server server facade
     * @param ctx negotiation session context
     * @param filledItems items delivered with the accept
     * @param templateUri accept-reject template
     * @return generated metadata content
     */
    MetadataContent generateAcceptServer(
            A2ATServer server, NegotiationContext ctx, List<NegotiationItem> filledItems, String templateUri);
}
