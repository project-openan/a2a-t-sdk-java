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

package net.openan.a2at.sdk.negotiation.store;

import net.openan.a2at.sdk.negotiation.types.model.NegotiationRecord;

/**
 * Store abstraction for negotiation records.
 *
 * @since 2026-06
 */
public interface NegotiationStore {

    /**
     * Saves or replaces one negotiation record.
     *
     * @param record negotiation record to persist
     */
    void save(NegotiationRecord record);

    /**
     * Loads one negotiation record by identifier.
     *
     * @param negotiationId negotiation identifier
     * @return stored record or {@code null} when absent
     */
    NegotiationRecord get(String negotiationId);

    /**
     * Deletes one negotiation record by identifier.
     *
     * @param negotiationId negotiation identifier
     */
    void delete(String negotiationId);
}
