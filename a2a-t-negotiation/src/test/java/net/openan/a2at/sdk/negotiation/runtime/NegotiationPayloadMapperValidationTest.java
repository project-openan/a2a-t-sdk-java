package net.openan.a2at.sdk.negotiation.runtime;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import net.openan.a2at.sdk.negotiation.runtime.helper.NegotiationPayloadMapper;
import net.openan.a2at.sdk.negotiation.types.exception.NegotiationStateException;
import org.junit.jupiter.api.Test;

class NegotiationPayloadMapperValidationTest {

    @Test
    void contextFromMapRejectsNonPositiveRound() {
        assertThrows(
                NegotiationStateException.class,
                () -> NegotiationPayloadMapper.contextFromMap(Map.of(
                        "negotiationType", "clarification",
                        "negotiationId", "neg-invalid-round",
                        "round", 0,
                        "status", "in-progress",
                        "extra", Map.of())));
    }
}
