package net.openan.a2at.sdk.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the parallel string constant set of {@link StandardTemplates}.
 *
 * <p>Pins the typed {@link TemplateUri} constants and their raw {@code _URI} string twins in lockstep so the two
 * spellings of the same template cannot drift apart, and mirrors the family aggregates the same way.
 *
 * @since 2026-08
 */
class StandardTemplatesStringConstantsTest {

    @Test
    void should_keepTypedAndStringConstantsInLockstep() {
        assertEquals(StandardTemplates.ENERGY_SAVING.uri(), StandardTemplates.ENERGY_SAVING_URI);
        assertEquals(StandardTemplates.PRIVATE_LINE_COMPLAINT.uri(), StandardTemplates.PRIVATE_LINE_COMPLAINT_URI);
        assertEquals(StandardTemplates.SUBSCRIBE_INCIDENT.uri(), StandardTemplates.SUBSCRIBE_INCIDENT_URI);
        assertEquals(StandardTemplates.SERVICE_RECOVERY.uri(), StandardTemplates.SERVICE_RECOVERY_URI);
        assertEquals(
                StandardTemplates.AUTHORIZATION_POLICY_MANAGEMENT.uri(),
                StandardTemplates.AUTHORIZATION_POLICY_MANAGEMENT_URI);
        assertEquals(
                StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE.uri(),
                StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE_URI);
        assertEquals(
                StandardTemplates.INFORMATION_NEGOTIATION_ACCEPT_REJECT.uri(),
                StandardTemplates.INFORMATION_NEGOTIATION_ACCEPT_REJECT_URI);
        assertEquals(StandardTemplates.TARGET_NEGOTIATION_PROPOSE.uri(), StandardTemplates.TARGET_NEGOTIATION_PROPOSE_URI);
        assertEquals(
                StandardTemplates.TARGET_NEGOTIATION_ACCEPT_REJECT.uri(),
                StandardTemplates.TARGET_NEGOTIATION_ACCEPT_REJECT_URI);
        assertEquals(
                StandardTemplates.FEASIBILITY_NEGOTIATION_PROPOSE.uri(),
                StandardTemplates.FEASIBILITY_NEGOTIATION_PROPOSE_URI);
        assertEquals(
                StandardTemplates.FEASIBILITY_NEGOTIATION_ACCEPT_REJECT.uri(),
                StandardTemplates.FEASIBILITY_NEGOTIATION_ACCEPT_REJECT_URI);
        assertEquals(StandardTemplates.NEGOTIATION_ABORT.uri(), StandardTemplates.NEGOTIATION_ABORT_URI);
    }

    @Test
    void should_keepFamilyAggregatesInLockstep() {
        assertEquals(uriList(StandardTemplates.TASK), StandardTemplates.TASK_URIS);
        assertEquals(uriList(StandardTemplates.NOTIFICATION), StandardTemplates.NOTIFICATION_URIS);
        assertEquals(uriList(StandardTemplates.AUTHORIZATION), StandardTemplates.AUTHORIZATION_URIS);
        assertEquals(uriList(StandardTemplates.NEGOTIATION), StandardTemplates.NEGOTIATION_URIS);
    }

    @Test
    void should_exposeOneStringConstantPerTypedConstant() {
        List<String> typed = Arrays.stream(StandardTemplates.class.getDeclaredFields())
                .filter(field -> field.getType() == TemplateUri.class)
                .map(Field::getName)
                .sorted()
                .toList();
        List<String> strings = Arrays.stream(StandardTemplates.class.getDeclaredFields())
                .filter(field -> field.getType() == String.class)
                .map(Field::getName)
                .filter(name -> name.endsWith("_URI"))
                .sorted()
                .toList();
        // every typed constant ENERGY_SAVING must have exactly one string twin ENERGY_SAVING_URI, and no other
        // *_URI string constant may exist
        List<String> expected = typed.stream().map(name -> name + "_URI").sorted().toList();
        assertEquals(expected, strings);
    }

    private static List<String> uriList(List<TemplateUri> uris) {
        return uris.stream().map(TemplateUri::uri).toList();
    }
}
