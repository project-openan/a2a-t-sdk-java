package net.openan.a2at.sdk.sample.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.openan.a2at.sdk.client.A2ATClient;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.server.A2ATServer;
import org.junit.jupiter.api.Test;

/**
 * Locks the v3 negotiation API surface of both facades and the field faces of the result records.
 *
 * <p>The client and the server expose exactly the twelve camelCase negotiation methods with their pinned parameter
 * counts, their surface carries none of the removed v2-shape method names nor the removed negotiation query methods,
 * and the result records expose exactly their documented components.
 */
class NegotiationV3ApiSurfaceTest {

    private static final Map<String, Integer> EXPECTED_NEGOTIATION_METHOD_PARAMETERS = Map.ofEntries(
            Map.entry("generateNegotiationProposePromptFromData", 2),
            Map.entry("generateNegotiationAcceptPromptFromData", 2),
            Map.entry("generateNegotiationRejectPromptFromData", 2),
            Map.entry("generateNegotiationAbortPromptFromData", 2),
            Map.entry("generateNegotiationProposePromptFromText", 3),
            Map.entry("generateNegotiationAcceptPromptFromText", 3),
            Map.entry("generateNegotiationRejectPromptFromText", 3),
            Map.entry("generateNegotiationAbortPromptFromText", 3),
            Map.entry("validateProposePromptAndDataFilling", 4),
            Map.entry("validateAcceptPromptAndDataFilling", 4),
            Map.entry("validateRejectPromptAndDataFilling", 4),
            Map.entry("validateAbortPromptAndDataFilling", 4));

    private static final List<String> REMOVED_NEGOTIATION_QUERY_METHODS =
            List.of("getNegotiationPrompts", "getNegotiationPrompt");

    private static final List<String> CROSS_EXTENSION_QUERY_METHODS = List.of("getPrompts", "getPrompt");

    private static final List<String> REMOVED_V2_METHOD_NAME_FRAGMENTS =
            List.of("FromNl", "FromJsonData", "validateAndExtractParams");

    @Test
    void bothFacadesExposeExactlyTheTwelveV3NegotiationMethods() {
        assertExactNegotiationSurface(A2ATClient.class);
        assertExactNegotiationSurface(A2ATServer.class);
    }

    @Test
    void removedNegotiationQueryMethodsAreAbsentWhileCrossExtensionQueriesRemain() {
        for (Class<?> facade : List.of(A2ATClient.class, A2ATServer.class)) {
            for (String removedMethod : REMOVED_NEGOTIATION_QUERY_METHODS) {
                assertFalse(
                        Arrays.stream(facade.getMethods())
                                .anyMatch(method -> method.getName().equals(removedMethod)),
                        facade.getSimpleName() + " must not expose the removed " + removedMethod);
            }
            for (String queryMethod : CROSS_EXTENSION_QUERY_METHODS) {
                assertTrue(
                        Arrays.stream(facade.getMethods())
                                .anyMatch(method -> method.getName().equals(queryMethod)),
                        facade.getSimpleName() + " must expose the cross-extension query " + queryMethod);
            }
        }
    }

    @Test
    void neitherFacadeCarriesRemovedV2ShapeMethodNames() {
        assertNoV2ShapeNames(A2ATClient.class);
        assertNoV2ShapeNames(A2ATServer.class);
    }

    @Test
    void resultRecordsExposeTheirPinnedFieldFaces() {
        List<String> metadataComponents = componentNames(MetadataContent.class);
        assertEquals(List.of("templateUri", "promptText", "extensionUri", "negotiationContext"), metadataComponents);
        List<String> contextComponents = componentNames(NegotiationContext.class);
        assertEquals(List.of("id", "round", "maxRounds", "performative"), contextComponents);
        List<String> filledComponents = componentNames(FilledParamData.class);
        assertEquals(List.of("data"), filledComponents);
        assertTrue(
                Arrays.stream(MetadataContent.class.getMethods())
                        .anyMatch(method -> method.getName().equals("buildMetadataContent")),
                "MetadataContent must expose buildMetadataContent");
    }

    @Test
    void allFacadeTemplateUriParamsAreStrings() {
        for (Class<?> facade : List.of(A2ATClient.class, A2ATServer.class)) {
            List<Method> methodsWithTemplateUri = Arrays.stream(facade.getDeclaredMethods())
                    .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers())
                            && !java.lang.reflect.Modifier.isStatic(method.getModifiers()))
                    .filter(method -> Arrays.stream(method.getParameters())
                            .anyMatch(parameter -> "templateUri".equals(parameter.getName())))
                    .toList();
            assertFalse(methodsWithTemplateUri.isEmpty(), facade.getSimpleName() + " must declare templateUri methods");
            for (Method method : methodsWithTemplateUri) {
                for (java.lang.reflect.Parameter parameter : method.getParameters()) {
                    if ("templateUri".equals(parameter.getName())) {
                        assertEquals(
                                String.class,
                                parameter.getType(),
                                "templateUri parameter of " + facade.getSimpleName() + "." + method.getName());
                    }
                }
            }
        }
    }

    private static void assertExactNegotiationSurface(Class<?> facade) {
        Method[] allMethods = facade.getMethods();
        List<Method> negotiationMethods = Arrays.stream(allMethods)
                .filter(method -> method.getName().startsWith("generateNegotiation")
                        || method.getName().startsWith("getNegotiation")
                        || "validateProposePromptAndDataFilling".equals(method.getName())
                        || "validateAcceptPromptAndDataFilling".equals(method.getName())
                        || "validateRejectPromptAndDataFilling".equals(method.getName())
                        || "validateAbortPromptAndDataFilling".equals(method.getName()))
                .toList();
        Set<String> names = negotiationMethods.stream().map(Method::getName).collect(Collectors.toSet());
        assertEquals(
                EXPECTED_NEGOTIATION_METHOD_PARAMETERS.keySet(),
                names,
                facade.getSimpleName() + " must expose exactly the twelve v3 negotiation methods");
        assertEquals(
                EXPECTED_NEGOTIATION_METHOD_PARAMETERS.size(),
                negotiationMethods.size(),
                facade.getSimpleName() + " must not overload any negotiation method");
        for (Method method : negotiationMethods) {
            assertEquals(
                    EXPECTED_NEGOTIATION_METHOD_PARAMETERS.get(method.getName()),
                    method.getParameterCount(),
                    "parameter count of " + facade.getSimpleName() + "." + method.getName());
        }
    }

    private static void assertNoV2ShapeNames(Class<?> facade) {
        for (Method method : facade.getMethods()) {
            String name = method.getName();
            for (String fragment : REMOVED_V2_METHOD_NAME_FRAGMENTS) {
                assertFalse(
                        name.toLowerCase(Locale.ROOT).contains(fragment.toLowerCase(Locale.ROOT)),
                        facade.getSimpleName() + " must not expose the removed v2-shape method name fragment "
                                + fragment + " but declares " + name);
            }
        }
    }

    private static List<String> componentNames(Class<?> recordType) {
        return Arrays.stream(recordType.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toList());
    }
}
