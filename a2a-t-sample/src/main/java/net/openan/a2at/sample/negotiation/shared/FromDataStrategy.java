package net.openan.a2at.sample.negotiation.shared;

import java.util.List;
import net.openan.a2at.sdk.client.A2ATClient;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.negotiation.content.InformationEndingContent;
import net.openan.a2at.sdk.negotiation.content.InformationProposeContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationConclusion;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.negotiation.content.NegotiationEndingData;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;
import net.openan.a2at.sdk.negotiation.content.NegotiationProposeData;
import net.openan.a2at.sdk.server.A2ATServer;

/**
 * Rule-based negotiation strategy: builds typed records and calls the deterministic fromData API.
 *
 * <p>No LLM call is made for negotiation message generation — the SDK renders the typed content directly from the
 * template. This is the "structured data" path.
 *
 * @since 2026-08
 */
public final class FromDataStrategy implements NegotiationStrategy {

    @Override
    public MetadataContent generatePropose(
            A2ATServer server,
            NegotiationContext ctx,
            List<NegotiationItem> missingItems,
            String relationship,
            String templateUri) {
        return server.generateNegotiationProposePromptFromData(
                new NegotiationProposeData(ctx, new InformationProposeContent(missingItems, relationship)),
                templateUri);
    }

    @Override
    public MetadataContent generateAccept(
            A2ATClient facade, NegotiationContext ctx, List<NegotiationItem> filledItems, String templateUri) {
        return facade.generateNegotiationAcceptPromptFromData(
                new NegotiationEndingData(ctx, new InformationEndingContent(NegotiationConclusion.ACCEPT, filledItems)),
                templateUri);
    }

    @Override
    public MetadataContent generateAcceptServer(
            A2ATServer server, NegotiationContext ctx, List<NegotiationItem> filledItems, String templateUri) {
        return server.generateNegotiationAcceptPromptFromData(
                new NegotiationEndingData(ctx, new InformationEndingContent(NegotiationConclusion.ACCEPT, filledItems)),
                templateUri);
    }
}
