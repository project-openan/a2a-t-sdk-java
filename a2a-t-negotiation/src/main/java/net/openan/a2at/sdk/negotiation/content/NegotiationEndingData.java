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
 * Input bundle for generating a terminal (accept or reject) negotiation message from typed data.
 *
 * <p>Both accept and reject generation methods take this same bundle. The conclusion constraint is carried by the
 * method, not the type: {@code generateAcceptFromData} requires {@code content.conclusion() == ACCEPT} and
 * {@code generateRejectFromData} requires {@code REJECT}; generation methods enforce it and reject a mismatched
 * conclusion (including {@code ABORT}) as a content error.
 *
 * @param context negotiation session context
 * @param content typed terminal content matching the negotiation type addressed by the template URI
 * @since 2026-08
 */
public record NegotiationEndingData(NegotiationContext context, NegotiationEndingContent content) {}
