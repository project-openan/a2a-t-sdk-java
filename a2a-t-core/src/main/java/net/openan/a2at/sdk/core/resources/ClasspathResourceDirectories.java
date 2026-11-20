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

import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;

/**
 * Lists the first-level directory names of a classpath resource directory.
 *
 * <p>The enumeration covers every classpath root carrying the directory, both {@code file://} roots and {@code jar://}
 * entries, so extension directories bundled in any jar on the classpath are discovered. Callers use this to avoid
 * hardcoding resource-type lists that drift from the packaged resource tree.
 *
 * @since 2026-08
 */
public final class ClasspathResourceDirectories {

    private ClasspathResourceDirectories() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    /**
     * Lists the distinct first-level directory names of one classpath resource directory.
     *
     * @param resourceDirectory classpath directory path such as {@code prompt_resources/templates/}
     * @return distinct directory names in encounter order; empty when the directory exists on no classpath root
     * @throws IOException when a classpath root cannot be read
     */
    public static List<String> list(String resourceDirectory) throws IOException {
        Set<String> names = new LinkedHashSet<>();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = ClasspathResourceDirectories.class.getClassLoader();
        }
        Enumeration<URL> roots = classLoader.getResources(resourceDirectory);
        while (roots.hasMoreElements()) {
            URL root = roots.nextElement();
            if ("file".equals(root.getProtocol())) {
                collectFilesystemNames(root, names);
            } else if ("jar".equals(root.getProtocol())) {
                collectJarNames((JarURLConnection) root.openConnection(), resourceDirectory, names);
            }
        }
        return List.copyOf(names);
    }

    private static void collectFilesystemNames(URL root, Set<String> names) throws IOException {
        Path rootDirectory;
        try {
            rootDirectory = Path.of(root.toURI());
        } catch (URISyntaxException exception) {
            throw new IOException("Malformed classpath root URL: " + root, exception);
        }
        try (var entries = Files.list(rootDirectory)) {
            entries.filter(Files::isDirectory).forEach(path -> names.add(path.getFileName().toString()));
        }
    }

    private static void collectJarNames(JarURLConnection connection, String resourceDirectory, Set<String> names)
            throws IOException {
        String prefix = resourceDirectory.endsWith("/") ? resourceDirectory : resourceDirectory + "/";
        try (JarFile jarFile = connection.getJarFile()) {
            jarFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(entry -> firstSegmentAfter(entry.getName(), prefix))
                    .filter(name -> !name.isEmpty())
                    .forEach(names::add);
        }
    }

    private static String firstSegmentAfter(String entryName, String prefix) {
        if (!entryName.startsWith(prefix)) {
            return "";
        }
        String remainder = entryName.substring(prefix.length());
        int slash = remainder.indexOf('/');
        return slash < 0 ? remainder : remainder.substring(0, slash);
    }
}
