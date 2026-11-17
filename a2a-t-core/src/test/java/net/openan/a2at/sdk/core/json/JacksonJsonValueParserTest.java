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

package net.openan.a2at.sdk.core.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import net.openan.a2at.sdk.core.exception.A2ATError;
import org.junit.jupiter.api.Test;

class JacksonJsonValueParserTest {

    @Test
    void parseObjectReturnsJsonObjectFields() {
        JsonValueParser parser = new JacksonJsonValueParser();

        Map<String, Object> parsed = parser.parseObject("{\"matched\":true,\"scenario_code\":\"ran-energy-saving\"}");

        assertEquals(true, parsed.get("matched"));
        assertEquals("ran-energy-saving", parsed.get("scenario_code"));
    }

    @Test
    void parseObjectRejectsNonObjectPayload() {
        JsonValueParser parser = new JacksonJsonValueParser();

        A2ATError error = assertThrows(A2ATError.class, () -> parser.parseObject("[\"not-object\"]"));

        assertEquals("Structured JSON payload must be a JSON object.", error.getMessage());
    }
}
