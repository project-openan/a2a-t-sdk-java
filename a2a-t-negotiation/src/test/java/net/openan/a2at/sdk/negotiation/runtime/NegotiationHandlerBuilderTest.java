package net.openan.a2at.sdk.negotiation.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import net.openan.a2at.sdk.negotiation.store.impl.InMemoryNegotiationStore;
import net.openan.a2at.sdk.negotiation.handler.ClarificationNegotiation;
import net.openan.a2at.sdk.negotiation.handler.InformationNegotiation;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationType;
import org.junit.jupiter.api.Test;

class NegotiationHandlerBuilderTest {

    @Test
    void builderCreatesHandlerWithStoreAndMultipleNegotiationTypes() {
        NegotiationHandler handler = NegotiationHandler.builder()
                .store(new InMemoryNegotiationStore())
                .register(NegotiationType.CLARIFICATION, new ClarificationNegotiation())
                .register(NegotiationType.INFORMATION, new InformationNegotiation())
                .build();

        Map<String, Object> startPayload = handler.start(NegotiationType.INFORMATION, "latest prompt", Map.of());
        Map<String, Object> context = cast(startPayload.get(NegotiationHandler.NEGOTIATION_CONTEXT_KEY));
        Map<String, Object> result = handler.receive("latest prompt", context);

        assertTrue((Boolean) result.get("needResponse"));
        assertEquals("latest prompt", result.get("message"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object value) {
        return (Map<String, Object>) value;
    }
}
