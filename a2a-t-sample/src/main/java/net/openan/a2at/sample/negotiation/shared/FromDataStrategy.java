/*
 * Copyright (c) 2026 Huawei Technologies Co., Ltd.
 * All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 *    Licensed under the Apache License, Version 2.0 (the "License"); you may
 *    not use this file except in compliance with the License. You may obtain
 *    a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 */

package net.openan.a2at.sample.negotiation.shared;

import java.util.List;
import net.openan.a2at.sdk.client.A2ATClient;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.negotiation.content.InformationEndingContent;
import net.openan.a2at.sdk.negotiation.content.InformationProposeContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationConclusion;
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
