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

package net.openan.a2at.sdk.negotiation.content;

/**
 * Kind of negotiation a negotiation message belongs to.
 *
 * <p>The enum also owns the hyphenated URI segment used both in template URIs and in template resource paths, so that
 * URIs and on-disk locations can never drift apart.
 *
 * @since 2026-08
 */
public enum NegotiationType {

    /** Negotiation about missing information the requester needs. */
    INFORMATION("information-negotiation"),

    /** Negotiation about aligning the target and intent of a request. */
    TARGET("target-negotiation"),

    /** Negotiation about whether a request can be fulfilled at all. */
    FEASIBILITY("feasibility-negotiation");

    private final String typeSegment;

    NegotiationType(String typeSegment) {
        this.typeSegment = typeSegment;
    }

    /**
     * Returns the hyphenated URI segment identifying this negotiation type.
     *
     * @return URI segment such as {@code information-negotiation}
     */
    public String typeSegment() {
        return typeSegment;
    }
}
