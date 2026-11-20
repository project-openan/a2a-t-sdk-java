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

import java.util.List;

/**
 * Content of an information negotiation terminal message.
 *
 * @param conclusion terminal conclusion of the information negotiation
 * @param items information items delivered with the terminal message
 * @since 2026-08
 */
public record InformationEndingContent(NegotiationConclusion conclusion, List<NegotiationItem> items)
        implements NegotiationEndingContent {}
