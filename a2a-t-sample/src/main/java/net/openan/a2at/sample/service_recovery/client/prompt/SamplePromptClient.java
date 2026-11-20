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
import net.openan.a2at.sdk.core.model.MetadataContent;

/**
 * Prompt-generation bridge used by the client sample flow.
 *
 * <p>Exposes the two Notification-T generation entry points verified by this sample: the from-text variant and the
 * schema-guided from-data variant.
 *
 * @since 2026-08
 */
public interface SamplePromptClient {

    /**
     * Generates a notification prompt with metadata from natural-language input.
     *
     * @param text natural-language notification input
     * @param templateUri template URI identifying the target template
     * @return metadata content carrying the resolved template URI, rendered prompt text, and extension URI
     */
    MetadataContent generateNotificationPromptFromText(String text, String templateUri);

    /**
     * Generates a notification prompt with metadata from structured input and a data schema.
     *
     * @param data structured notification input as a string-to-object map
     * @param schema data schema map for schema-guided slot extraction
     * @param templateUri template URI identifying the target template
     * @return metadata content carrying the resolved template URI, rendered prompt text, and extension URI
     */
    MetadataContent generateNotificationPromptFromDataWithSchema(
            Map<String, Object> data, Map<String, Object> schema, String templateUri);
}
