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

import net.openan.a2at.sdk.core.model.NegotiationContext;

/**
 * Input bundle for generating an abort negotiation message from typed data.
 *
 * <p>The abort message is type-independent and terminates a negotiation outside the accept/reject outcome model; the
 * {@code Abort} conclusion is fixed template text, so the content carries only the termination reason.
 *
 * @param context negotiation session context
 * @param content typed abort content carrying the termination reason
 * @since 2026-08
 */
public record NegotiationAbortData(NegotiationContext context, NegotiationAbortContent content) {}
