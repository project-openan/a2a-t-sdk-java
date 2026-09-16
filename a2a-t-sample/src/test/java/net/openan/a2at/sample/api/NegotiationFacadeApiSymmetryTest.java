package net.openan.a2at.sample.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.openan.a2at.sdk.client.A2ATClient;
import net.openan.a2at.sdk.server.A2ATServer;
import org.junit.jupiter.api.Test;

/**
 * Guards the negotiation content-layer API surface of both high-level facades.
 *
 * <p>The client and the server facade expose the negotiation generation, query and validation APIs as one symmetric
 * surface: identical method names, parameter types and return types. This test fails when one facade drifts from the
 * other.
 */
class NegotiationFacadeApiSymmetryTest {

    private static final List<String> NEGOTIATION_API_METHODS = List.of(
            "generateNegotiationProposePromptFromData",
            "generateNegotiationAcceptPromptFromData",
            "generateNegotiationRejectPromptFromData",
            "generateNegotiationAbortPromptFromData",
            "generateNegotiationProposePromptFromText",
            "generateNegotiationAcceptPromptFromText",
            "generateNegotiationRejectPromptFromText",
            "generateNegotiationAbortPromptFromText",
            "validateProposePromptAndDataFilling",
            "validateAcceptPromptAndDataFilling",
            "validateRejectPromptAndDataFilling",
            "validateAbortPromptAndDataFilling");

    private static final List<String> REMOVED_NEGOTIATION_QUERY_METHODS =
            List.of("getNegotiationPrompts", "getNegotiationPrompt");

    private static final List<String> CROSS_EXTENSION_QUERY_METHODS = List.of("getPrompts", "getPrompt");

    @Test
    void bothFacadesExposeTheSameTwelveNegotiationApiMethods() {
        Map<String, String> clientSurface = negotiationApiSurface(A2ATClient.class);
        Map<String, String> serverSurface = negotiationApiSurface(A2ATServer.class);

        assertEquals(new HashSet<>(NEGOTIATION_API_METHODS), clientSurface.keySet());
        assertEquals(clientSurface, serverSurface);
    }

    @Test
    void neitherFacadeExposesTheRemovedNegotiationQueryMethods() {
        for (Class<?> facade : List.of(A2ATClient.class, A2ATServer.class)) {
            for (String removedMethod : REMOVED_NEGOTIATION_QUERY_METHODS) {
                boolean present = Arrays.stream(facade.getMethods())
                        .anyMatch(method -> method.getName().equals(removedMethod));
                assertFalse(
                        present,
                        facade.getSimpleName() + " must not expose the removed " + removedMethod + " query method");
            }
        }
    }

    @Test
    void bothFacadesExposeTheSameCrossExtensionQueryMethods() {
        assertEquals(
                new HashSet<>(CROSS_EXTENSION_QUERY_METHODS),
                Arrays.stream(A2ATClient.class.getMethods())
                        .filter(method -> CROSS_EXTENSION_QUERY_METHODS.contains(method.getName()))
                        .map(Method::getName)
                        .collect(Collectors.toSet()));
        assertEquals(
                Arrays.stream(A2ATClient.class.getMethods())
                        .filter(method -> CROSS_EXTENSION_QUERY_METHODS.contains(method.getName()))
                        .map(NegotiationFacadeApiSymmetryTest::methodSignature)
                        .collect(Collectors.toSet()),
                Arrays.stream(A2ATServer.class.getMethods())
                        .filter(method -> CROSS_EXTENSION_QUERY_METHODS.contains(method.getName()))
                        .map(NegotiationFacadeApiSymmetryTest::methodSignature)
                        .collect(Collectors.toSet()));
    }

    private static Map<String, String> negotiationApiSurface(Class<?> facade) {
        return Arrays.stream(facade.getMethods())
                .filter(method -> NEGOTIATION_API_METHODS.contains(method.getName()))
                .collect(Collectors.toMap(Method::getName, NegotiationFacadeApiSymmetryTest::methodSignature));
    }

    private static String methodSignature(Method method) {
        String parameters =
                Arrays.stream(method.getParameterTypes()).map(Class::getName).collect(Collectors.joining(","));
        return parameters + "->" + method.getReturnType().getName();
    }
}
