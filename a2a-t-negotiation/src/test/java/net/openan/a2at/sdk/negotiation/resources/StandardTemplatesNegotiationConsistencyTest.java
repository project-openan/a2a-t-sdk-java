package net.openan.a2at.sdk.negotiation.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.openan.a2at.sdk.core.model.NegotiationPerformative;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.negotiation.content.NegotiationType;
import org.junit.jupiter.api.Test;

/**
 * Locks the two sources of negotiation template URI spelling together: the constants in {@link StandardTemplates}
 * (a2a-t-core, cannot depend on this module) and the compositional logic in {@link NegotiationReference} backed by the
 * {@link NegotiationType}/{@link NegotiationPerformative} enums. Any drift turns this test red.
 */
class StandardTemplatesNegotiationConsistencyTest {

    private static final List<NegotiationPerformative> TYPED_PERFORMATIVES =
            List.of(NegotiationPerformative.PROPOSE, NegotiationPerformative.ACCEPT, NegotiationPerformative.REJECT);

    @Test
    void negotiationConstantsMatchReferenceComposition() {
        for (NegotiationType type : NegotiationType.values()) {
            for (NegotiationPerformative performative : TYPED_PERFORMATIVES) {
                NegotiationReference reference = new NegotiationReference(type, performative, "en-US");
                TemplateUri expected = findConstant(reference.uri());
                assertEquals(
                        reference.uri(),
                        expected.uri(),
                        "StandardTemplates has no constant matching the composed URI of " + type + "/" + performative);
            }
        }
        NegotiationReference abort = new NegotiationReference(null, NegotiationPerformative.ABORT, "en-US");
        TemplateUri abortConstant = findConstant(abort.uri());
        assertEquals(
                abort.uri(),
                abortConstant.uri(),
                "StandardTemplates has no constant matching the composed URI of the common abort template");
    }

    @Test
    void negotiationGroupCoversExactlyTheComposedUris() {
        List<String> composed = StandardTemplates.NEGOTIATION.stream()
                .map(TemplateUri::uri)
                .sorted()
                .toList();
        List<String> expected = new ArrayList<>();
        // Accept and reject share the accept-reject template, so the group carries one URI per type for them.
        for (NegotiationType type : NegotiationType.values()) {
            for (NegotiationPerformative performative :
                    List.of(NegotiationPerformative.PROPOSE, NegotiationPerformative.ACCEPT)) {
                expected.add(new NegotiationReference(type, performative, "en-US").uri());
            }
        }
        expected.add(new NegotiationReference(null, NegotiationPerformative.ABORT, "en-US").uri());
        assertEquals(expected.stream().sorted().toList(), composed);
    }

    @Test
    void everyNegotiationGroupUriIsLoadableByTheNegotiationLayer() {
        DefaultNegotiationTemplateLoader zhCnLoader = new DefaultNegotiationTemplateLoader("zh-CN");
        DefaultNegotiationTemplateLoader enUsLoader = new DefaultNegotiationTemplateLoader("en-US");

        for (TemplateUri templateUri : StandardTemplates.NEGOTIATION) {
            assertTrue(zhCnLoader.load(zhCnReference(templateUri)).content().length() > 0, templateUri.uri());
            assertTrue(enUsLoader.load(enUsReference(templateUri)).content().length() > 0, templateUri.uri());
        }
    }

    private static NegotiationReference zhCnReference(TemplateUri templateUri) {
        return referenceOf(templateUri, "zh-CN");
    }

    private static NegotiationReference enUsReference(TemplateUri templateUri) {
        return referenceOf(templateUri, "en-US");
    }

    private static NegotiationReference referenceOf(TemplateUri templateUri, String language) {
        for (NegotiationPerformative performative : NegotiationPerformative.values()) {
            java.util.Optional<NegotiationReference> reference =
                    NegotiationReference.fromTemplateUri(templateUri, performative, language);
            if (reference.isPresent()) {
                return reference.get();
            }
        }
        throw new AssertionError("No performative parses the advertised negotiation URI " + templateUri.uri());
    }

    private static TemplateUri findConstant(String uri) {
        return StandardTemplates.NEGOTIATION.stream()
                .filter(template -> template.uri().equals(uri))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No StandardTemplates constant for URI " + uri));
    }
}
