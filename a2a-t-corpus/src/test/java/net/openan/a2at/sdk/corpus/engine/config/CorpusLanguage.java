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

package net.openan.a2at.sdk.corpus.engine.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Language constants and case-file naming conventions of the accuracy verification corpus.
 *
 * <p>The committed cases are {@code zh-CN} and keep the historical {@code input_case_from_*.json} names; every other
 * supported language appends its locale tag before the extension, for example {@code input_case_from_text_en-US.json}.
 * The SDK bundles prompt resources for exactly the languages in {@link #SUPPORTED_LANGUAGES}, so the corpus accepts the
 * same set as case-file variants.
 */
public final class CorpusLanguage {

    /** Default prompt and case-file language of the corpus (the committed cases are zh-CN). */
    public static final String DEFAULT_LANGUAGE = "zh-CN";

    /** Languages the SDK bundles prompt resources for and the corpus accepts as case-file variants. */
    public static final Set<String> SUPPORTED_LANGUAGES = Set.of("zh-CN", "en-US");

    private CorpusLanguage() {}

    /**
     * Returns the case-file name of one language: the default language keeps the base name, every other language
     * appends its locale tag before the extension.
     */
    public static String localizedCaseFile(String baseName, String language) {
        if (DEFAULT_LANGUAGE.equals(language)) {
            return baseName;
        }
        int dot = baseName.lastIndexOf('.');
        if (dot < 0) {
            return baseName + "_" + language;
        }
        return baseName.substring(0, dot) + "_" + language + baseName.substring(dot);
    }

    /** Returns the default case file followed by every optional language variant, in sorted language order. */
    public static List<String> caseFileVariants(String baseName) {
        List<String> variants = new ArrayList<>();
        variants.add(baseName);
        for (String language : new TreeSet<>(SUPPORTED_LANGUAGES)) {
            if (!DEFAULT_LANGUAGE.equals(language)) {
                variants.add(localizedCaseFile(baseName, language));
            }
        }
        return variants;
    }
}
