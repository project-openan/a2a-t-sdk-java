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

package net.openan.a2at.sample.service_recovery.client.runtime;

import java.util.Map;
import java.util.function.Consumer;
import org.a2aproject.sdk.client.ClientEvent;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallContext;
import org.a2aproject.sdk.spec.MessageSendParams;

/**
 * Transitional seam for assembling the real a2a-java client runtime.
 *
 * @since 2026-08
 */
public interface A2AJavaClientRuntime {

    /**
     * Sends one message:stream request through the real a2a-java client.
     *
     * @param agentCard resolved registry AgentCard payload
     * @param request message send parameters
     * @param callContext call context carrying the A2A-Extensions header
     * @param logSink log sink, may be null
     * @return iterable of received client events
     */
    Iterable<ClientEvent> sendMessage(
            Map<String, Object> agentCard,
            MessageSendParams request,
            ClientCallContext callContext,
            Consumer<String> logSink);
}
