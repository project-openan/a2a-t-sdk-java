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

package net.openan.a2at.sample.service_recovery.client.prompt;

import java.util.Map;
import net.openan.a2at.sdk.client.A2ATClient;
import net.openan.a2at.sdk.core.model.MetadataContent;

/**
 * {@link SamplePromptClient} adapter delegating to the real {@link A2ATClient} facade.
 *
 * <p>Both generation entry points share one facade instance, proving the two Notification-T generation APIs can run
 * sequentially over the same client configuration.
 *
 * @since 2026-08
 */
public final class A2ATNotificationPromptClient implements SamplePromptClient {

    private final A2ATClient delegate;

    /**
     * Creates the adapter over one client facade.
     *
     * @param delegate SDK client facade used for the Notification-T generation calls
     */
    public A2ATNotificationPromptClient(A2ATClient delegate) {
        this.delegate = delegate;
    }

    @Override
    public MetadataContent generateNotificationPromptFromText(String text, String templateUri) {
        return delegate.generateNotificationPromptFromText(text, templateUri);
    }

    @Override
    public MetadataContent generateNotificationPromptFromDataWithSchema(
            Map<String, Object> data, Map<String, Object> schema, String templateUri) {
        return delegate.generateNotificationPromptFromDataWithSchema(data, schema, templateUri);
    }
}
