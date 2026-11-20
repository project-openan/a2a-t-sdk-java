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

package net.openan.a2at.sdk.prompt.resources.catalog;

/**
 * Extracts the description convention shared by every prompt template of the resource tree.
 *
 * <p>A template carries its description as a leading HTML comment on the first line; a template without such a
 * comment reports an empty description. The convention is shared between the template loaders and the template
 * catalog so that both produce equal {@link net.openan.a2at.sdk.core.model.PromptTemplate} records for the same
 * file.
 *
 * @since 2026-08
 */
public final class TemplateDescriptions {

    private static final String COMMENT_OPEN = "<!--";

    private static final String COMMENT_CLOSE = "-->";

    private TemplateDescriptions() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    /**
     * Extracts the template description from a leading HTML comment.
     *
     * @param content full template text
     * @return stripped comment text of the first line when it is an HTML comment, otherwise an empty string
     */
    public static String extract(String content) {
        int firstLineBreak = content.indexOf('\n');
        String firstLine = firstLineBreak < 0 ? content : content.substring(0, firstLineBreak);
        firstLine = firstLine.strip();
        if (firstLine.startsWith(COMMENT_OPEN)
                && firstLine.endsWith(COMMENT_CLOSE)
                && firstLine.length() >= COMMENT_OPEN.length() + COMMENT_CLOSE.length()) {
            return firstLine
                    .substring(COMMENT_OPEN.length(), firstLine.length() - COMMENT_CLOSE.length())
                    .strip();
        }
        return "";
    }
}
