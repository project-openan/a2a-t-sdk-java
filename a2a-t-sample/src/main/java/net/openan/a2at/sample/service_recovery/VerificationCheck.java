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

package net.openan.a2at.sample.service_recovery;

/**
 * One verification outcome of the service-recovery sample.
 *
 * @param name check name identifying the verified expectation
 * @param passed whether the expectation held
 * @param detail human-readable outcome detail, never null
 * @since 2026-08
 */
public record VerificationCheck(String name, boolean passed, String detail) {

    /**
     * Creates a passed check.
     *
     * @param name check name
     * @param detail outcome detail
     * @return passed check
     */
    public static VerificationCheck passed(String name, String detail) {
        return new VerificationCheck(name, true, detail);
    }

    /**
     * Creates a failed check.
     *
     * @param name check name
     * @param detail outcome detail
     * @return failed check
     */
    public static VerificationCheck failed(String name, String detail) {
        return new VerificationCheck(name, false, detail);
    }
}
