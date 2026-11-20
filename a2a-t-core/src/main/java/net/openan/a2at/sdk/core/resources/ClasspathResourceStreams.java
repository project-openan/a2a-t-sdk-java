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

package net.openan.a2at.sdk.core.resources;

import java.io.InputStream;

/**
 * Opens classpath resources through the shared classloader fallback chain.
 *
 * <p>The context classloader of the current thread is consulted first; when it has no resource of the given path,
 * the classloader of this utility class is consulted as a fallback. A caller that finds no stream handles the
 * absence with its own domain exception, so this utility only returns {@code null}.
 *
 * @since 2026-08
 */
public final class ClasspathResourceStreams {

    private ClasspathResourceStreams() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    /**
     * Opens one classpath resource through the context classloader with a fallback to this class's classloader.
     *
     * @param resourcePath slash-separated classpath resource path
     * @return open stream of the resource, or {@code null} when no classloader provides it
     */
    public static InputStream open(String resourcePath) {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader != null) {
            InputStream stream = contextClassLoader.getResourceAsStream(resourcePath);
            if (stream != null) {
                return stream;
            }
        }
        return ClasspathResourceStreams.class.getClassLoader().getResourceAsStream(resourcePath);
    }
}
