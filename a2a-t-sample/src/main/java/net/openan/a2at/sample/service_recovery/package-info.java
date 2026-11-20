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

/**
 * Service-recovery Notification-T sample.
 *
 * <p>This sample exercises the Notification-T {@code service-recovery} scenario end to end:
 *
 * <ul>
 *   <li>{@code client} / {@code server} / {@code shared} — full HTTP e2e sample mirroring the subscribe-incident
 *       layout: the client generates the subscription prompt with the two Notification-T generation APIs, sends it over
 *       the real a2a-java REST transport, and the server validates the rendered prompt with
 *       {@code validateAndFillingNotificationData} before reporting mock service recovery events as artifacts.
 * </ul>
 *
 * <p>The sample inputs and checks target the zh-CN resources; keep {@code A2AT_LANGUAGE=zh-CN} in the sample
 * environment files.
 *
 * @since 2026-08
 */
package net.openan.a2at.sample.service_recovery;
