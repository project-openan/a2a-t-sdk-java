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
 * Terminal outcome of a negotiation as expressed in negotiation result sections.
 *
 * <p>Only {@code ACCEPT} and {@code REJECT} are renderable conclusions of the typed negotiation templates; the
 * {@code ABORT} outcome is rendered through the type-independent common abort template, whose fixed conclusion section
 * carries no conclusion slot, so the typed generation methods reject it as a programming error.
 *
 * @since 2026-08
 */
public enum NegotiationConclusion {

    /** The negotiation parties reached an agreement. */
    ACCEPT("Accept"),

    /** The negotiation parties did not reach an agreement. */
    REJECT("Reject"),

    /** The negotiation was abandoned outside the template outcome model. */
    ABORT("Abort");

    private final String literal;

    NegotiationConclusion(String literal) {
        this.literal = literal;
    }

    /**
     * Returns the literal text filled into negotiation result slots.
     *
     * @return literal conclusion text such as {@code Accept}
     */
    public String literal() {
        return literal;
    }
}
