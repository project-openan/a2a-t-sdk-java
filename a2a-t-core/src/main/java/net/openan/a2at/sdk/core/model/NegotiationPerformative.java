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

import java.util.Optional;

/**
 * Performative (negotiation primitive) of a negotiation message as defined by the A2A-T Negotiation-T specification.
 *
 * <p>A performative expresses the communicative intent of a message within a negotiation session: {@code PROPOSE}
 * puts a proposal on the table, {@code ACCEPT} and {@code REJECT} respond to a proposal, and {@code ABORT}
 * terminates the session. Pervasives are online semantics only; how a performative maps to a prompt template is a
 * concern of the negotiation layer, not of this enum.
 *
 * @since 2026-08
 */
public enum NegotiationPerformative {
    /** Puts a proposal on the table for the counterpart to evaluate. */
    PROPOSE,

    /** Accepts the counterpart's latest proposal. */
    ACCEPT,

    /** Rejects the counterpart's latest proposal. */
    REJECT,

    /** Terminates the negotiation session before an agreement is reached. */
    ABORT;

    /**
     * Parses the wire representation of a performative.
     *
     * <p>Only the exact upper-case names of the four constants ({@code PROPOSE}, {@code ACCEPT}, {@code REJECT},
     * {@code ABORT}) are accepted; lower-case, {@code null}, and unknown values yield an empty result rather than an
     * exception.
     *
     * @param value candidate wire value, may be {@code null}
     * @return the matching performative, or {@link Optional#empty()} if the value is not an exact match
     */
    public static Optional<NegotiationPerformative> tryParse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        for (NegotiationPerformative performative : values()) {
            if (performative.name().equals(value)) {
                return Optional.of(performative);
            }
        }
        return Optional.empty();
    }
}
