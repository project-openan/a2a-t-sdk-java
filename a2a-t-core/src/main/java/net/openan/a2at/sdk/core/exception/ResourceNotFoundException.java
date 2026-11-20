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

package net.openan.a2at.sdk.core.exception;

import org.jspecify.annotations.NonNull;

/**
 * Raised when a referenced SDK resource cannot be resolved.
 *
 * @since 2026-06
 */
public final class ResourceNotFoundException extends A2ATError {

    private final String resourcePath;

    /**
     * Creates a resource-not-found exception for one SDK classpath resource.
     *
     * @param message failure message
     * @param resourcePath missing classpath resource path
     */
    public ResourceNotFoundException(String message, @NonNull String resourcePath) {
        super(message);
        this.resourcePath = resourcePath;
    }

    /**
     * Returns the missing classpath resource path.
     *
     * @return missing resource path
     */
    public @NonNull String resourcePath() {
        return resourcePath;
    }
}
