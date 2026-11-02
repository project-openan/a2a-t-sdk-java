package net.openan.a2at.sample.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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
 * <p>The client and the server facade expose the negotiation generation and validation APIs as one symmetric
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

    private static final List<String> INTERNAL_TEMPLATE_QUERY_METHODS = List.of("getPrompts", "getPrompt");

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
    void neitherFacadeExposesTheTemplateQueryMethodsPublicly() {
        for (Class<?> facade : List.of(A2ATClient.class, A2ATServer.class)) {
            for (String queryMethod : INTERNAL_TEMPLATE_QUERY_METHODS) {
                boolean exposed = Arrays.stream(facade.getMethods())
                        .anyMatch(method -> method.getName().equals(queryMethod));
                assertFalse(
                        exposed,
                        facade.getSimpleName() + " must not expose the template query method " + queryMethod
                                + " publicly; it is an SDK-internal interface");
            }
        }
    }

    @Test
    void internalTemplateQueryMethodsArePrivateAndSymmetric() throws NoSuchMethodException {
        assertPrivateAndSymmetric("getPrompts");
        assertPrivateAndSymmetric("getPrompt", String.class);
    }

    private static void assertPrivateAndSymmetric(String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Method clientMethod = A2ATClient.class.getDeclaredMethod(name, parameterTypes);
        Method serverMethod = A2ATServer.class.getDeclaredMethod(name, parameterTypes);
        assertTrue(
                Modifier.isPrivate(clientMethod.getModifiers()),
                "A2ATClient." + name + " must be private (SDK-internal interface)");
        assertTrue(
                Modifier.isPrivate(serverMethod.getModifiers()),
                "A2ATServer." + name + " must be private (SDK-internal interface)");
        assertEquals(methodSignature(clientMethod), methodSignature(serverMethod));
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
