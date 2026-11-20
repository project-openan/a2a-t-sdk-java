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

package net.openan.a2at.sdk.negotiation.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import net.openan.a2at.sdk.core.model.NegotiationPerformative;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.negotiation.content.NegotiationType;
import org.junit.jupiter.api.Test;

class NegotiationReferenceTest {

    @Test
    void typeSegmentUsesHyphenatedNames() {
        assertEquals("information-negotiation", NegotiationType.INFORMATION.typeSegment());
        assertEquals("target-negotiation", NegotiationType.TARGET.typeSegment());
        assertEquals("feasibility-negotiation", NegotiationType.FEASIBILITY.typeSegment());
    }

    @Test
    void uriComposesPrefixVersionTypeSegmentAndPerformativeSegment() {
        assertEquals(
                StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE.uri(),
                new NegotiationReference(NegotiationType.INFORMATION, NegotiationPerformative.PROPOSE, "en-US").uri());
        assertEquals(
                StandardTemplates.TARGET_NEGOTIATION_ACCEPT_REJECT.uri(),
                new NegotiationReference(NegotiationType.TARGET, NegotiationPerformative.ACCEPT, "zh-CN").uri());
        assertEquals(
                StandardTemplates.FEASIBILITY_NEGOTIATION_ACCEPT_REJECT.uri(),
                new NegotiationReference(NegotiationType.FEASIBILITY, NegotiationPerformative.REJECT, "zh-CN").uri());
    }

    @Test
    void tryParseAcceptsAllSixValidUris() {
        for (NegotiationType type : NegotiationType.values()) {
            NegotiationReference propose = requirePresent(
                    NegotiationReference.tryParse(proposeUri(type), NegotiationPerformative.PROPOSE, "zh-CN"));
            assertEquals(type, propose.type());
            assertEquals(NegotiationPerformative.PROPOSE, propose.performative());
            assertEquals(proposeUri(type), propose.uri());
            assertEquals("zh-CN", propose.language());

            NegotiationReference accept = requirePresent(
                    NegotiationReference.tryParse(acceptRejectUri(type), NegotiationPerformative.ACCEPT, "en-US"));
            assertEquals(type, accept.type());
            assertEquals(NegotiationPerformative.ACCEPT, accept.performative());
            assertEquals(acceptRejectUri(type), accept.uri());

            NegotiationReference reject = requirePresent(
                    NegotiationReference.tryParse(acceptRejectUri(type), NegotiationPerformative.REJECT, "en-US"));
            assertEquals(type, reject.type());
            assertEquals(NegotiationPerformative.REJECT, reject.performative());
            assertEquals(acceptRejectUri(type), reject.uri());
        }
    }

    @Test
    void tryParseRejectsWrongSegmentCount() {
        assertTrue(NegotiationReference.tryParse("information-negotiation/propose", NegotiationPerformative.PROPOSE, "zh-CN")
                .isEmpty());
        assertTrue(NegotiationReference.tryParse(
                        "Negotiation-T/information-negotiation/propose/v1/extra", NegotiationPerformative.PROPOSE, "zh-CN")
                .isEmpty());
        assertTrue(NegotiationReference.tryParse("foo", NegotiationPerformative.PROPOSE, "zh-CN").isEmpty());
    }

    @Test
    void tryParseRejectsWrongPrefixAndVersion() {
        assertTrue(NegotiationReference.tryParse(
                        "Task-T/information-negotiation/propose/v1", NegotiationPerformative.PROPOSE, "zh-CN")
                .isEmpty());
        assertTrue(NegotiationReference.tryParse(
                        "negotiation-t/information-negotiation/propose/v1", NegotiationPerformative.PROPOSE, "zh-CN")
                .isEmpty());
        assertTrue(NegotiationReference.tryParse(
                        "Negotiation-T/information-negotiation/propose/v2", NegotiationPerformative.PROPOSE, "zh-CN")
                .isEmpty());
    }

    @Test
    void tryParseRejectsMissingTypeSegmentSuffixAndUnderscoreVariant() {
        assertTrue(NegotiationReference.tryParse("Negotiation-T/information/propose/v1", NegotiationPerformative.PROPOSE, "zh-CN")
                .isEmpty());
        assertTrue(NegotiationReference.tryParse(
                        "Negotiation-T/information_negotiation/propose/v1", NegotiationPerformative.PROPOSE, "zh-CN")
                .isEmpty());
    }

    @Test
    void tryParseRejectsUnknownType() {
        assertTrue(NegotiationReference.tryParse(
                        "Negotiation-T/unknown-negotiation/propose/v1", NegotiationPerformative.PROPOSE, "zh-CN")
                .isEmpty());
    }

    @Test
    void tryParseRejectsIllegalPerformativeSegment() {
        assertTrue(NegotiationReference.tryParse(
                        "Negotiation-T/information-negotiation/propose-x/v1", NegotiationPerformative.PROPOSE, "zh-CN")
                .isEmpty());
        assertTrue(NegotiationReference.tryParse(
                        "Negotiation-T/information-negotiation/accept/v1", NegotiationPerformative.PROPOSE, "zh-CN")
                .isEmpty());
    }

    @Test
    void tryParseRejectsPerformativeMismatchAgainstExpectedPerformative() {
        Optional<NegotiationReference> parsed = NegotiationReference.tryParse(
                StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE.uri(), NegotiationPerformative.ACCEPT, "zh-CN");

        assertTrue(parsed.isEmpty());
    }

    @Test
    void uriComposesCommonAbortUriForAbortPerformative() {
        NegotiationReference abort = new NegotiationReference(null, NegotiationPerformative.ABORT, "en-US");
        assertEquals(StandardTemplates.NEGOTIATION_ABORT.uri(), abort.uri());
        assertEquals(StandardTemplates.NEGOTIATION_ABORT, abort.templateUri());
        assertNull(abort.type());
        assertEquals("common", abort.typeSegment());
    }

    @Test
    void constructorBindsAbortPerformativeToNullTypeOnly() {
        IllegalArgumentException typedAbort = assertThrows(
                IllegalArgumentException.class,
                () -> new NegotiationReference(NegotiationType.INFORMATION, NegotiationPerformative.ABORT, "zh-CN"));
        IllegalArgumentException untypedPropose = assertThrows(
                IllegalArgumentException.class,
                () -> new NegotiationReference(null, NegotiationPerformative.PROPOSE, "zh-CN"));
        assertTrue(typedAbort.getMessage().contains("ABORT"));
        assertTrue(untypedPropose.getMessage().contains("null"));
    }

    @Test
    void tryParseAcceptsCommonAbortUri() {
        NegotiationReference abort = requirePresent(
                NegotiationReference.tryParse(StandardTemplates.NEGOTIATION_ABORT.uri(), NegotiationPerformative.ABORT, "zh-CN"));
        assertNull(abort.type());
        assertEquals(NegotiationPerformative.ABORT, abort.performative());
        assertEquals(StandardTemplates.NEGOTIATION_ABORT.uri(), abort.uri());
        assertEquals("zh-CN", abort.language());
    }

    @Test
    void tryParseRejectsCommonSegmentWithTypedPerformatives() {
        assertTrue(NegotiationReference.tryParse("Negotiation-T/common/propose/v1", NegotiationPerformative.PROPOSE, "zh-CN")
                .isEmpty());
        assertTrue(NegotiationReference.tryParse(
                        "Negotiation-T/common/accept-reject/v1", NegotiationPerformative.ACCEPT, "zh-CN")
                .isEmpty());
    }

    @Test
    void tryParseRejectsAbortSegmentOnTypedReference() {
        assertTrue(NegotiationReference.tryParse(
                        "Negotiation-T/information-negotiation/abort/v1", NegotiationPerformative.ABORT, "zh-CN")
                .isEmpty());
    }

    @Test
    void tryParseRejectsCommonAbortUriAgainstTypedPerformatives() {
        assertTrue(NegotiationReference.tryParse(StandardTemplates.NEGOTIATION_ABORT.uri(), NegotiationPerformative.PROPOSE, "zh-CN")
                .isEmpty());
        assertTrue(NegotiationReference.tryParse(StandardTemplates.NEGOTIATION_ABORT.uri(), NegotiationPerformative.ACCEPT, "zh-CN")
                .isEmpty());
    }

    @Test
    void fromTemplateUriAcceptsCommonAbortUri() {
        NegotiationReference abort = requirePresent(
                NegotiationReference.fromTemplateUri(StandardTemplates.NEGOTIATION_ABORT, NegotiationPerformative.ABORT, "zh-CN"));
        assertNull(abort.type());
        assertEquals(NegotiationPerformative.ABORT, abort.performative());
        assertEquals(StandardTemplates.NEGOTIATION_ABORT.uri(), abort.uri());

        assertEquals(
                StandardTemplates.NEGOTIATION_ABORT.uri(),
                new NegotiationReference(null, NegotiationPerformative.ABORT, "en-US").uri(),
                "fromTemplateUri must be the exact inverse of the composition for the common abort template");
    }

    @Test
    void fromTemplateUriRejectsCommonWithTypedPerformativesAndTypedAbort() {
        assertTrue(NegotiationReference.fromTemplateUri(
                        TemplateUri.of("Negotiation-T", "common", "propose"), NegotiationPerformative.PROPOSE, "zh-CN")
                .isEmpty());
        assertTrue(NegotiationReference.fromTemplateUri(
                        TemplateUri.of("Negotiation-T", "common", "abort"), NegotiationPerformative.PROPOSE, "zh-CN")
                .isEmpty());
        assertTrue(NegotiationReference.fromTemplateUri(
                        TemplateUri.of("Negotiation-T", "information-negotiation", "abort"),
                        NegotiationPerformative.ABORT,
                        "zh-CN")
                .isEmpty());
    }

    @Test
    void tryParseReturnsEmptyForBlankAndNullUriButThrowsOnNullExpectedPerformative() {
        assertTrue(NegotiationReference.tryParse("", NegotiationPerformative.PROPOSE, "zh-CN").isEmpty());
        assertTrue(NegotiationReference.tryParse(null, NegotiationPerformative.PROPOSE, "zh-CN").isEmpty());

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> NegotiationReference.tryParse(StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE.uri(), null, "zh-CN"));
        assertEquals("Expected negotiation performative must not be null.", exception.getMessage());
    }

    @Test
    void fromTemplateUriAcceptsAllSixTypedUris() {
        for (NegotiationType type : NegotiationType.values()) {
            NegotiationReference propose = requirePresent(
                    NegotiationReference.fromTemplateUri(proposeTemplate(type), NegotiationPerformative.PROPOSE, "zh-CN"));
            assertEquals(type, propose.type());
            assertEquals(NegotiationPerformative.PROPOSE, propose.performative());
            assertEquals(proposeUri(type), propose.uri());
            assertEquals("zh-CN", propose.language());

            NegotiationReference reject = requirePresent(
                    NegotiationReference.fromTemplateUri(acceptRejectTemplate(type), NegotiationPerformative.REJECT, "en-US"));
            assertEquals(type, reject.type());
            assertEquals(NegotiationPerformative.REJECT, reject.performative());
            assertEquals(acceptRejectUri(type), reject.uri());
        }
    }

    @Test
    void fromTemplateUriRejectsNonNegotiationUris() {
        assertTrue(NegotiationReference.fromTemplateUri(
                        StandardTemplates.ENERGY_SAVING, NegotiationPerformative.PROPOSE, "zh-CN")
                .isEmpty());
        assertTrue(NegotiationReference.fromTemplateUri(
                        TemplateUri.of("negotiation-t", "information-negotiation", "propose"),
                        NegotiationPerformative.PROPOSE,
                        "zh-CN")
                .isEmpty());
    }

    @Test
    void fromTemplateUriRejectsWrongPathSegmentCount() {
        assertTrue(NegotiationReference.fromTemplateUri(
                        TemplateUri.of("Negotiation-T", "information-negotiation"),
                        NegotiationPerformative.PROPOSE,
                        "zh-CN")
                .isEmpty());
        assertTrue(NegotiationReference.fromTemplateUri(
                        TemplateUri.of("Negotiation-T", "information-negotiation", "propose", "extra"),
                        NegotiationPerformative.PROPOSE,
                        "zh-CN")
                .isEmpty());
    }

    @Test
    void fromTemplateUriRejectsWrongVersionTypeAndPerformativeSegments() {
        assertTrue(NegotiationReference.fromTemplateUri(
                        TemplateUri.of("Negotiation-T", List.of("information-negotiation", "propose"), "v2"),
                        NegotiationPerformative.PROPOSE,
                        "zh-CN")
                .isEmpty());
        assertTrue(NegotiationReference.fromTemplateUri(
                        TemplateUri.of("Negotiation-T", "information", "propose"),
                        NegotiationPerformative.PROPOSE,
                        "zh-CN")
                .isEmpty());
        assertTrue(NegotiationReference.fromTemplateUri(
                        TemplateUri.of("Negotiation-T", "information_negotiation", "propose"),
                        NegotiationPerformative.PROPOSE,
                        "zh-CN")
                .isEmpty());
        assertTrue(NegotiationReference.fromTemplateUri(
                        TemplateUri.of("Negotiation-T", "unknown-negotiation", "propose"),
                        NegotiationPerformative.PROPOSE,
                        "zh-CN")
                .isEmpty());
        assertTrue(NegotiationReference.fromTemplateUri(
                        TemplateUri.of("Negotiation-T", "information-negotiation", "propose-x"),
                        NegotiationPerformative.PROPOSE,
                        "zh-CN")
                .isEmpty());
    }

    @Test
    void fromTemplateUriRejectsPerformativeMismatchAndThrowsOnNullArguments() {
        assertTrue(NegotiationReference.fromTemplateUri(
                        StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE, NegotiationPerformative.ACCEPT, "zh-CN")
                .isEmpty());

        NullPointerException uriFailure = assertThrows(
                NullPointerException.class,
                () -> NegotiationReference.fromTemplateUri(null, NegotiationPerformative.PROPOSE, "zh-CN"));
        assertEquals("Template URI must not be null.", uriFailure.getMessage());

        NullPointerException performativeFailure = assertThrows(
                NullPointerException.class,
                () -> NegotiationReference.fromTemplateUri(
                        StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE, null, "zh-CN"));
        assertEquals("Expected negotiation performative must not be null.", performativeFailure.getMessage());
    }

    private static TemplateUri proposeTemplate(NegotiationType type) {
        return TemplateUri.of("Negotiation-T", type.typeSegment(), "propose");
    }

    private static TemplateUri acceptRejectTemplate(NegotiationType type) {
        return TemplateUri.of("Negotiation-T", type.typeSegment(), "accept-reject");
    }

    private static String proposeUri(NegotiationType type) {
        return "Negotiation-T/" + type.typeSegment() + "/propose/v1";
    }

    private static String acceptRejectUri(NegotiationType type) {
        return "Negotiation-T/" + type.typeSegment() + "/accept-reject/v1";
    }

    private static NegotiationReference requirePresent(Optional<NegotiationReference> reference) {
        assertTrue(reference.isPresent(), "expected a parsed reference but the result was empty");
        return reference.get();
    }
}
