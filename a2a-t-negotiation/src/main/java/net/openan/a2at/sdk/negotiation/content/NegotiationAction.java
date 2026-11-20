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
 * Action a feasibility negotiation propose message performs.
 *
 * <p>The action is not rendered into the message; it selects which conditional sections of the feasibility propose
 * template are emitted.
 *
 * @since 2026-08
 */
public enum NegotiationAction {

    /** Asks the counterpart to evaluate whether the request is feasible. */
    REQUEST_FEASIBILITY_EVALUATION,

    /** Reports that the request is infeasible and proposes an alternative. */
    PROPOSE_ALTERNATIVE_ON_FAILURE
}
