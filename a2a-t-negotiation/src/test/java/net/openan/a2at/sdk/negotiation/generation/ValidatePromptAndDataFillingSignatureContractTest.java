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

package net.openan.a2at.sdk.negotiation.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.negotiation.resources.NegotiationReference;
import net.openan.a2at.sdk.negotiation.validation.NegotiationSemanticValidator;
import org.junit.jupiter.api.Test;

/**
 * Pins the compile-time contract of the validate-and-filling API: the negotiation context, the parameter schema and
 * the template URI are mandatory parts of the full signature of each of the four methods, so a caller that omits
 * any argument is rejected at compile time and no reduced-arity overload can silently appear later.
 */
class ValidatePromptAndDataFillingSignatureContractTest {

    private static final List<String> VALIDATE_PROMPT_FILLING_METHODS =
            List.of("validateProposePromptAndDataFilling", "validateAcceptPromptAndDataFilling",
                    "validateRejectPromptAndDataFilling", "validateAbortPromptAndDataFilling");

    @Test
    void everyValidatePromptAndDataFillingMethodHasExactlyTheFullFourArgumentSignature() {
        for (String methodName : VALIDATE_PROMPT_FILLING_METHODS) {
            List<Method> declared = Arrays.stream(NegotiationGenerationOrchestrator.class.getDeclaredMethods())
                    .filter(method -> method.getName().equals(methodName))
                    .toList();

            assertEquals(
                    1,
                    declared.size(),
                    methodName + " must exist exactly once: no reduced-arity overload may bypass the mandatory"
                            + " context, schema and template URI arguments");
            Method method = declared.get(0);
            assertTrue(Modifier.isPublic(method.getModifiers()), methodName + " must be public");
            Class<?>[] parameterTypes = method.getParameterTypes();
            assertEquals(4, parameterTypes.length, methodName + " must take exactly four parameters");
            assertEquals(String.class, parameterTypes[0], methodName + " first parameter is the prompt text");
            assertEquals(
                    net.openan.a2at.sdk.core.model.NegotiationContext.class,
                    parameterTypes[1],
                    methodName + " second parameter is the negotiation context");
            assertEquals(Map.class, parameterTypes[2], methodName + " third parameter is the caller parameter schema");
            assertEquals(TemplateUri.class, parameterTypes[3], methodName + " fourth parameter is the template URI");
        }
    }

    @Test
    void semanticValidatorSeamCarriesThePromptSchemaReferenceAndTemplateContentTogether() throws NoSuchMethodException {
        Method validate = NegotiationSemanticValidator.class.getMethod(
                "validate", String.class, Map.class, NegotiationReference.class, String.class);

        assertTrue(Modifier.isPublic(validate.getModifiers()));
        assertEquals(
                4,
                validate.getParameterCount(),
                "the semantic validator seam must require the prompt, the caller schema, the template reference and"
                        + " the template content together");
    }
}
