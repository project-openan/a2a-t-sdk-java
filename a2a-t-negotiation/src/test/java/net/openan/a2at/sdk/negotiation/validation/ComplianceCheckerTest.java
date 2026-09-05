package net.openan.a2at.sdk.negotiation.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.NegotiationPerformative;
import net.openan.a2at.sdk.core.model.SlotValidationError;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ComplianceCheckerTest {

    private static final String SESSION_ID = "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3";

    private final DefaultNegotiationComplianceChecker checker = new DefaultNegotiationComplianceChecker();

    static List<Object[]> violationCases() {
        return List.of(
                new Object[] {
                    "non-uuid id",
                    new NegotiationContext("not-a-uuid", 1, 5, NegotiationPerformative.PROPOSE),
                    List.of("id")
                },
                new Object[] {
                    "short id", new NegotiationContext("short", 1, 5, NegotiationPerformative.PROPOSE), List.of("id")
                },
                new Object[] {
                    "non-hexadecimal id",
                    new NegotiationContext(SESSION_ID.replace('b', 'z'), 1, 5, NegotiationPerformative.PROPOSE),
                    List.of("id")
                },
                new Object[] {
                    "round above maxRounds",
                    new NegotiationContext(SESSION_ID, 3, 2, NegotiationPerformative.PROPOSE),
                    List.of("round")
                });
    }

    @Test
    void validContextPasses() {
        NegotiationRuleCheckResult result =
                checker.check(new NegotiationContext(SESSION_ID, 2, 5, NegotiationPerformative.PROPOSE));

        assertTrue(result.passed());
        assertEquals(List.of(), result.errors());
    }

    @Test
    void roundAtTheBudgetBoundaryPasses() {
        NegotiationRuleCheckResult result =
                checker.check(new NegotiationContext(SESSION_ID, 5, 5, NegotiationPerformative.PROPOSE));

        assertTrue(result.passed());
        assertEquals(List.of(), result.errors());
    }

    @ParameterizedTest
    @MethodSource("violationCases")
    void contextViolationsFailWithTheExpectedSlotNames(
            String caseName, NegotiationContext context, List<String> expectedSlots) {
        NegotiationRuleCheckResult result = checker.check(context);

        assertFalse(result.passed(), caseName);
        assertEquals(expectedSlots.size(), result.errors().size(), caseName);
        for (int index = 0; index < expectedSlots.size(); index++) {
            assertEquals(expectedSlots.get(index), result.errors().get(index).slotName(), caseName);
        }
    }

    @Test
    void uuidFormatIsEnforcedBeyondLength() {
        NegotiationRuleCheckResult result = checker.check(
                new NegotiationContext(SESSION_ID.replace('b', 'z'), 1, 5, NegotiationPerformative.PROPOSE));

        assertFalse(result.passed());
        assertEquals("id", result.errors().get(0).slotName());
        assertEquals("negotiation.invalid_context_id", result.errors().get(0).code());
    }

    @Test
    void uppercaseHexUuidIsAccepted() {
        NegotiationRuleCheckResult result = checker.check(new NegotiationContext(
                SESSION_ID.toUpperCase(java.util.Locale.ROOT), 1, 5, NegotiationPerformative.PROPOSE));

        assertTrue(result.passed());
        assertEquals(0, result.errors().size());
    }

    @Test
    void roundAboveMaxRoundsCarriesTheOutOfRangeCode() {
        NegotiationRuleCheckResult result =
                checker.check(new NegotiationContext(SESSION_ID, 6, 5, NegotiationPerformative.PROPOSE));

        assertFalse(result.passed());
        SlotValidationError error = result.errors().get(0);
        assertEquals("round", error.slotName());
        assertEquals("negotiation.round_exceeded", error.code());
        assertTrue(error.message().contains("maximum"));
    }

    @Test
    void nullContextIsRejected() {
        assertThrows(NullPointerException.class, () -> checker.check(null));
    }

    @Test
    void resultRecordHasExactlyTheTwoPinnedComponents() {
        String[] componentNames = java.util.Arrays.stream(NegotiationRuleCheckResult.class.getRecordComponents())
                .map(component -> component.getName())
                .toArray(String[]::new);

        assertEquals(List.of("passed", "errors"), List.of(componentNames));
    }

    @Test
    void ruleErrorsCarryStructuredDetails() {
        NegotiationRuleCheckResult result =
                checker.check(new NegotiationContext("short", 1, 5, NegotiationPerformative.PROPOSE));

        SlotValidationError error = result.errors().get(0);
        assertEquals("id", error.slotName());
        assertEquals("negotiation.invalid_context_id", error.code());
        assertTrue(error.message().contains("UUID"));
    }
}
