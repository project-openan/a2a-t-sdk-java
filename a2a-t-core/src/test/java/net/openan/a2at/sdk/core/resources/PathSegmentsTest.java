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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PathSegmentsTest {

    @Test
    void should_rejectNull_When_valueIsNull() {
        assertFalse(PathSegments.isSimpleSegment(null));
    }

    @Test
    void should_rejectBlank_When_valueIsBlank() {
        assertFalse(PathSegments.isSimpleSegment(""));
        assertFalse(PathSegments.isSimpleSegment("   "));
    }

    @Test
    void should_rejectForwardSlash_When_valueContainsSlash() {
        assertFalse(PathSegments.isSimpleSegment("a/b"));
        assertFalse(PathSegments.isSimpleSegment("/"));
    }

    @Test
    void should_rejectBackslash_When_valueContainsBackslash() {
        assertFalse(PathSegments.isSimpleSegment("a\\b"));
        assertFalse(PathSegments.isSimpleSegment("\\"));
    }

    @Test
    void should_rejectTraversal_When_valueContainsDotDot() {
        assertFalse(PathSegments.isSimpleSegment(".."));
        assertFalse(PathSegments.isSimpleSegment("a/../b"));
    }

    @Test
    void should_acceptValidSegment_When_valueIsSimple() {
        assertTrue(PathSegments.isSimpleSegment("ran-energy-saving"));
        assertTrue(PathSegments.isSimpleSegment("Task-T"));
        assertTrue(PathSegments.isSimpleSegment("zh-CN"));
        assertTrue(PathSegments.isSimpleSegment("a.b"));
    }

    @Test
    void requireSimpleSegment_rejectsNullWithLabelInMessage() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> PathSegments.requireSimpleSegment(null, "the label"));

        assertTrue(exception.getMessage().contains("the label"));
    }

    @Test
    void requireSimpleSegment_rejectsBlankWithLabelInMessage() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> PathSegments.requireSimpleSegment("   ", "the label"));

        assertTrue(exception.getMessage().contains("the label"));
    }

    @Test
    void requireSimpleSegment_rejectsSlashWithLabelInMessage() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> PathSegments.requireSimpleSegment("a/b", "the label"));

        assertTrue(exception.getMessage().contains("the label"));
    }

    @Test
    void requireSimpleSegment_rejectsBackslashWithLabelInMessage() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> PathSegments.requireSimpleSegment("a\\b", "the label"));

        assertTrue(exception.getMessage().contains("the label"));
    }

    @Test
    void requireSimpleSegment_rejectsTraversalWithLabelInMessage() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> PathSegments.requireSimpleSegment("..", "the label"));

        assertTrue(exception.getMessage().contains("the label"));
    }

    @Test
    void requireSimpleSegment_acceptsValidValues() {
        assertDoesNotThrow(() -> {
            PathSegments.requireSimpleSegment("ran-energy-saving", "segment");
            PathSegments.requireSimpleSegment("Task-T", "segment");
            PathSegments.requireSimpleSegment("zh-CN", "segment");
            PathSegments.requireSimpleSegment("a.b", "segment");
        });
    }

    @Test
    void requireSimpleRelativePath_rejectsEmptySegmentsWithLabelInMessage() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> PathSegments.requireSimpleRelativePath("a//b", "the path"));

        assertTrue(exception.getMessage().contains("the path"));
    }

    @Test
    void requireSimpleRelativePath_rejectsTraversalSegmentWithLabelInMessage() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> PathSegments.requireSimpleRelativePath("../x", "the path"));

        assertTrue(exception.getMessage().contains("the path"));
    }

    @Test
    void requireSimpleRelativePath_rejectsNullWithLabelInMessage() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> PathSegments.requireSimpleRelativePath(null, "the path"));

        assertTrue(exception.getMessage().contains("the path"));
    }

    @Test
    void requireSimpleRelativePath_acceptsValidValues() {
        assertDoesNotThrow(() -> {
            PathSegments.requireSimpleRelativePath("a/b/c", "path");
            PathSegments.requireSimpleRelativePath("Task-T/network-layer/ran-energy-saving/v1", "path");
            PathSegments.requireSimpleRelativePath("zh-CN", "path");
        });
    }
}
