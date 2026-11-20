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

package net.openan.a2at.sample.subscribe_incident.client.runtime;

import net.openan.a2at.sample.subscribe_incident.client.prompt.SamplePromptClient;
import net.openan.a2at.sample.subscribe_incident.client.registry.SampleRegistryClient;
import net.openan.a2at.sample.subscribe_incident.shared.endpoint.AgentEndpointCache;

/**
 * Runtime bundle required by the client sample main flow.
 *
 * @since 2026-05
 */
public interface SampleClientRuntime extends AutoCloseable {

    SampleRegistryClient registryClient();

    SamplePromptClient promptClient();

    AgentEndpointCache endpointCache();

    @Override
    void close();
}
