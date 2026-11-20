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

package net.openan.a2at.sample.subscribe_incident.client.request;

import java.util.Map;
import java.util.UUID;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallContext;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.TextPart;

/**
 * Builds real a2a-java message requests for the sample client flow.
 *
 * @since 2026-05
 */
public final class A2AJavaRequestBuilder {

    private A2AJavaRequestBuilder() {}

    public static BuiltA2AJavaRequest buildStreamRequest(
            String promptText, String extensionUri, String metadataPayload) {
        Message message = Message.builder()
                .messageId(UUID.randomUUID().toString())
                .role(Message.Role.ROLE_USER)
                .parts(new TextPart(metadataPayload))
                .metadata(Map.of(extensionUri, promptText))
                .build();
        MessageSendParams request = MessageSendParams.builder().message(message).build();
        ClientCallContext callContext = new ClientCallContext(Map.of(), Map.of("A2A-Extensions", extensionUri));
        return new BuiltA2AJavaRequest(request, callContext);
    }
}
