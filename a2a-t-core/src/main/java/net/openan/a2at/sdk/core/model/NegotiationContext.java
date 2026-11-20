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

package net.openan.a2at.sdk.core.model;

/**
 * Session context carried by every negotiation message.
 *
 * <p>The context identifies one negotiation session, tracks the current round, and bounds how many rounds the session
 * may run. A context is immutable; advancing to the next round produces a new instance.
 *
 * <p>The {@code performative} carries the communicative intent of the message the context travels with. On an incoming
 * message it is the performative of that message; the generation pipeline stamps the performative of the operation it
 * performs on the output context, mirroring the nested {@code negotiationContext} structure of the A2A-T Negotiation-T
 * specification.
 *
 * @param id unique identifier of the negotiation session, expected to be a UUID
 * @param round current negotiation round, 1-based
 * @param maxRounds maximum number of rounds before the negotiation must end
 * @param performative communicative intent of the message this context travels with
 * @since 2026-08
 */
public record NegotiationContext(String id, int round, int maxRounds, NegotiationPerformative performative) {

    /** Default round budget applied when a caller does not specify one. */
    public static final int DEFAULT_MAX_ROUNDS = 5;

    /**
     * Validates the context fields.
     *
     * @throws IllegalArgumentException if the id is blank, the round is below 1, maxRounds is below 1, or the
     *     performative is null
     */
    public NegotiationContext {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Negotiation context id must not be blank.");
        }
        if (round < 1) {
            throw new IllegalArgumentException(
                    "Negotiation context round must be a positive integer but was " + round + ".");
        }
        if (maxRounds < 1) {
            throw new IllegalArgumentException(
                    "Negotiation context maxRounds must be a positive integer but was " + maxRounds + ".");
        }
        if (performative == null) {
            throw new IllegalArgumentException("Negotiation context performative must not be null.");
        }
    }

    /**
     * Creates a context using the default round budget.
     *
     * @param id unique identifier of the negotiation session
     * @param round current negotiation round, 1-based
     * @param performative communicative intent of the message this context travels with
     * @return new negotiation context with {@link #DEFAULT_MAX_ROUNDS} as the round budget
     */
    public static NegotiationContext of(String id, int round, NegotiationPerformative performative) {
        return new NegotiationContext(id, round, DEFAULT_MAX_ROUNDS, performative);
    }

    /**
     * Returns the context for the next round, leaving this context unchanged.
     *
     * <p>The returned context keeps this context's performative: the performative of an outbound message is decided by
     * the generation pipeline, which stamps the operation's performative on the context it emits.
     *
     * @return new negotiation context whose round is one greater than the current round
     */
    public NegotiationContext nextRound() {
        return new NegotiationContext(id, round + 1, maxRounds, performative);
    }

    /**
     * Returns a copy of this context stamped with the given performative, leaving this context unchanged.
     *
     * @param performative communicative intent to stamp on the copy
     * @return new negotiation context with the same {@code id}, {@code round}, and {@code maxRounds} as this context
     */
    public NegotiationContext withPerformative(NegotiationPerformative performative) {
        return new NegotiationContext(id, round, maxRounds, performative);
    }

    /**
     * Reports whether the round budget has been exceeded.
     *
     * <p>A context whose round equals maxRounds is not exhausted yet; only rounds strictly beyond the budget are.
     *
     * @return {@code true} if the current round is greater than maxRounds
     */
    public boolean isExhausted() {
        return round > maxRounds;
    }
}
