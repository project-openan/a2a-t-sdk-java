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

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import net.openan.a2at.sdk.core.resources.PathSegments;
import net.openan.a2at.sdk.core.model.NegotiationPerformative;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.negotiation.content.NegotiationType;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Addressing key for one negotiation template: negotiation type, performative and language.
 *
 * <p>The reference composes the template URI from the type segment and the URI segment of the performative, so the URI
 * spelling has a single source ({@link #uriSegmentOf(NegotiationPerformative)}). The language is query context rather
 * than part of the resource identity and is therefore not part of the URI.
 *
 * <p>The performative has four values while the template URI layer only distinguishes three segments: {@code ACCEPT}
 * and {@code REJECT} share the {@code accept-reject} segment, differing only in the conclusion value filled into the
 * template slot, and {@code ABORT} is addressed by the single type-independent common abort template. Typed
 * references address the templates of one negotiation type; the abort performative is type-independent and is
 * addressed by that common template with a {@code null} type.
 *
 * @param type negotiation type addressed by the reference; null only for the abort performative, whose common template
 *     is type-independent
 * @param performative communicative intent addressed by the reference; accept and reject share the same template
 * @param language locale identifier such as {@code zh-CN} or {@code en-US}
 * @since 2026-08
 */
public record NegotiationReference(
        @Nullable NegotiationType type, NegotiationPerformative performative, String language) {

    private static final String URI_PREFIX = StandardTemplates.NEGOTIATION_EXTENSION_NAME;

    private static final String URI_VERSION_SEGMENT = TemplateUri.DEFAULT_TEMPLATE_VERSION;

    private static final String TYPE_SEGMENT_SUFFIX = "-negotiation";

    private static final String COMMON_TYPE_SEGMENT = "common";

    /**
     * Validates the reference fields.
     *
     * @throws NullPointerException if the performative is null
     * @throws IllegalArgumentException if the type is null on a typed performative, non-null on the abort performative,
     *     or the language is not a simple path segment
     */
    public NegotiationReference {
        Objects.requireNonNull(performative, "Negotiation reference performative must not be null.");
        if (performative == NegotiationPerformative.ABORT && type != null) {
            throw new IllegalArgumentException(
                    "Negotiation reference of the ABORT performative is type-independent; the type must be null but"
                            + " was " + type + ".");
        }
        if (performative != NegotiationPerformative.ABORT && type == null) {
            throw new IllegalArgumentException(
                    "Negotiation reference type must not be null for the " + performative + " performative; only the"
                            + " ABORT performative is type-independent.");
        }
        if (!PathSegments.isSimpleSegment(language)) {
            throw new IllegalArgumentException(
                    "Negotiation reference language must be a non-blank simple path segment but was " + language + ".");
        }
    }

    /**
     * Returns the template URI segment of a performative.
     *
     * <p>The performative has four values but the template URI layer only distinguishes three segments: {@code ACCEPT}
     * and {@code REJECT} share the {@code accept-reject} segment. This mapping is the single source of the segment
     * spelling.
     *
     * @param performative performative to map to its template URI segment
     * @return {@code propose} for PROPOSE, {@code accept-reject} for ACCEPT and REJECT, and {@code abort} for ABORT
     * @throws NullPointerException if the performative is null
     */
    public static String uriSegmentOf(NegotiationPerformative performative) {
        Objects.requireNonNull(performative, "Negotiation performative must not be null.");
        return switch (performative) {
            case PROPOSE -> "propose";
            case ACCEPT, REJECT -> "accept-reject";
            case ABORT -> "abort";
        };
    }

    /**
     * Returns the hyphenated URI segment of the referenced negotiation type.
     *
     * @return URI segment such as {@code information-negotiation}; {@code common} for the type-independent abort
     *     performative
     */
    public String typeSegment() {
        return type == null ? COMMON_TYPE_SEGMENT : type.typeSegment();
    }

    /**
     * Returns the template URI of the referenced template.
     *
     * @return template URI such as {@code Negotiation-T/information-negotiation/propose/v1}
     */
    public String uri() {
        return String.join("/", URI_PREFIX, typeSegment(), uriSegmentOf(performative), URI_VERSION_SEGMENT);
    }

    /**
     * Returns the typed template URI of the referenced template.
     *
     * <p>The exact inverse of {@link #fromTemplateUri(TemplateUri, NegotiationPerformative, String)}: the URI is
     * composed from the same extension-name constant and default version segment the parser accepts, so
     * {@code fromTemplateUri(reference.templateUri(), reference.performative(), ...)} always addresses the same
     * template.
     *
     * @return typed template URI such as {@code Negotiation-T/information-negotiation/propose/v1}
     */
    public @NonNull TemplateUri templateUri() {
        return TemplateUri.of(URI_PREFIX, List.of(typeSegment(), uriSegmentOf(performative)), URI_VERSION_SEGMENT);
    }

    /**
     * Tries to parse a template URI into a reference, checking it against the expected performative.
     *
     * <p>The URI layer cannot distinguish accept from reject because both share the {@code accept-reject} segment; the
     * expected performative disambiguates the parsed result, which therefore always carries the expected performative.
     *
     * @param templateUri template URI to parse, such as {@code Negotiation-T/target-negotiation/accept-reject/v1} or
     *     {@code Negotiation-T/common/abort/v1}
     * @param expectedPerformative performative the caller is operating on; the parsed reference carries this
     *     performative
     * @param language locale identifier for the parsed reference
     * @return reference addressed by the URI carrying the expected performative, or an empty result when the URI is
     *     null, blank or malformed (wrong segment count, prefix, type segment or trailing version segment) or its URI
     *     segment does not match the expected performative
     * @throws NullPointerException if the expected performative is null
     */
    public static Optional<NegotiationReference> tryParse(
            @Nullable String templateUri, @NonNull NegotiationPerformative expectedPerformative, String language) {
        Objects.requireNonNull(expectedPerformative, "Expected negotiation performative must not be null.");
        return TemplateUri.parse(templateUri).flatMap(uri -> fromTemplateUri(uri, expectedPerformative, language));
    }

    /**
     * Derives a reference from a typed template URI, checking it against the expected performative.
     *
     * <p>The typed variant of {@link #tryParse(String, NegotiationPerformative, String)}: because a
     * {@link TemplateUri} is always structurally well formed, the checks operate on the URI components directly
     * instead of splitting a raw string. The URI layer cannot distinguish accept from reject because both share the
     * {@code accept-reject} segment; the expected performative disambiguates the result, which therefore always
     * carries the expected performative.
     *
     * <p>The abort performative is addressed by the common abort template: a {@code common}/{@code abort} segment pair
     * yields a reference with a null type, and every other segment shape is rejected for that performative.
     *
     * @param templateUri typed template URI such as {@code Negotiation-T/target-negotiation/accept-reject/v1} or
     *     {@code Negotiation-T/common/abort/v1}
     * @param expectedPerformative performative the caller is operating on; the derived reference carries this
     *     performative
     * @param language locale identifier for the derived reference
     * @return reference addressed by the URI carrying the expected performative, or an empty result when the URI does
     *     not address a negotiation template of the expected performative (wrong extension name, path segment count,
     *     type segment, URI segment or template version)
     * @throws NullPointerException if the template URI or the expected performative is null
     */
    public static Optional<NegotiationReference> fromTemplateUri(
            @NonNull TemplateUri templateUri, @NonNull NegotiationPerformative expectedPerformative, String language) {
        Objects.requireNonNull(templateUri, "Template URI must not be null.");
        Objects.requireNonNull(expectedPerformative, "Expected negotiation performative must not be null.");
        if (!URI_PREFIX.equals(templateUri.extensionName())) {
            return Optional.empty();
        }
        List<String> segments = templateUri.pathSegments();
        if (segments.size() != 2) {
            return Optional.empty();
        }
        NegotiationType parsedType = null;
        if (COMMON_TYPE_SEGMENT.equals(segments.get(0))) {
            if (expectedPerformative != NegotiationPerformative.ABORT) {
                return Optional.empty();
            }
        } else {
            parsedType = parseTypeSegment(segments.get(0));
            if (expectedPerformative == NegotiationPerformative.ABORT || parsedType == null) {
                return Optional.empty();
            }
        }
        if (!uriSegmentMatches(segments.get(1), expectedPerformative)) {
            return Optional.empty();
        }
        if (!URI_VERSION_SEGMENT.equals(templateUri.templateVersion())) {
            return Optional.empty();
        }
        return Optional.of(new NegotiationReference(parsedType, expectedPerformative, language));
    }

    private static NegotiationType parseTypeSegment(String typeSegment) {
        if (!typeSegment.endsWith(TYPE_SEGMENT_SUFFIX)) {
            return null;
        }
        String typeName = typeSegment.substring(0, typeSegment.length() - TYPE_SEGMENT_SUFFIX.length());
        for (NegotiationType candidate : NegotiationType.values()) {
            if (candidate.name().toLowerCase(Locale.ROOT).equals(typeName)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean uriSegmentMatches(String uriSegment, NegotiationPerformative expectedPerformative) {
        if (!"propose".equals(uriSegment) && !"accept-reject".equals(uriSegment)
                && !"abort".equals(uriSegment)) {
            return false;
        }
        return uriSegment.equals(uriSegmentOf(expectedPerformative));
    }
}
