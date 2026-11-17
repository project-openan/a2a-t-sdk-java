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

import net.openan.a2at.sdk.core.model.StandardTemplates;

/**
 * Extension URIs and metadata keys used by the negotiation end-to-end demo.
 *
 * <p>Centralises every A2A-T identifier so the client and server flows reference them from one place, making the demo
 * easier to read and adapt. Template URIs come from {@link StandardTemplates}.
 *
 * @since 2026-08
 */
public final class DemoConstants {

    private DemoConstants() {}

    /** Task-T extension URI (NL variant, used for sending per SDK convention). */
    public static final String TASK_T_URI =
            "https://projects.tmforum.org/a2aproject/telecommunication/extensions/Task-T/NL/v1";

    /** Negotiation-T extension URI (NL variant, used for sending per SDK convention). */
    public static final String NEGOTIATION_T_URI =
            "https://projects.tmforum.org/a2aproject/telecommunication/extensions/Negotiation-T/NL/v1";

    /** Task-T template URI for the SPN private-line-complaint diagnosis scenario. */
    public static final String TASK_TEMPLATE = StandardTemplates.PRIVATE_LINE_COMPLAINT_URI;

    /** Negotiation-T information propose template (request missing information). */
    public static final String NEGOTIATION_PROPOSE = StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE_URI;

    /** Negotiation-T information accept-reject template (accept after params filled). */
    public static final String NEGOTIATION_ACCEPT = StandardTemplates.INFORMATION_NEGOTIATION_ACCEPT_REJECT_URI;

    /** Metadata key carrying the JSON-serialised negotiation context map. */
    public static final String NEGOTIATION_CONTEXT_KEY = "negotiation_context";

    /** Metadata key carrying the template URI. */
    public static final String TEMPLATE_URI_KEY = "template_uri";
}
