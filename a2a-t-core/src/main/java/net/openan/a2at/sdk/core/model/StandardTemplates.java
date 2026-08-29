package net.openan.a2at.sdk.core.model;

import java.util.List;

/**
 * Constants for the built-in content templates shipped with the SDK, in the spirit of
 * {@code java.nio.charset.StandardCharsets}.
 *
 * <p>Each template is published in two spellings: a language-neutral {@link TemplateUri} and its raw {@code uri()}
 * string (constant name suffixed {@code _URI}). The typed form is the identity representation used for template
 * comparisons (for example against {@link PromptTemplate#templateUri()}) and by internal seams; the string form is
 * what the {@code A2ATClient}/{@code A2ATServer} facades take as their {@code templateUri} parameter. The language is
 * global prompt runtime context and is bound by the SDK, not by the caller. Use these constants instead of hand-written
 * URI strings to keep the spelling centralized — a consistency test pins the two forms together.
 *
 * <p>Example: {@code StandardTemplates.ENERGY_SAVING_URI} is {@code Task-T/network-layer/ran-energy-saving/v1}. The
 * Task-T and Notification-T templates carry the {@code network-layer} domain segment; Authorization-T and
 * Negotiation-T templates do not.
 *
 * @since 2026-08
 */
public final class StandardTemplates {

    private StandardTemplates() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    /** Extension name of the Task-T template family. */
    public static final String TASK_EXTENSION_NAME = "Task-T";

    /** Extension name of the Notification-T template family. */
    public static final String NOTIFICATION_EXTENSION_NAME = "Notification-T";

    /** Extension name of the Authorization-T template family. */
    public static final String AUTHORIZATION_EXTENSION_NAME = "Authorization-T";

    /** Extension name of the Negotiation-T template family. */
    public static final String NEGOTIATION_EXTENSION_NAME = "Negotiation-T";

    /** Domain segment carried by the Task-T and Notification-T template paths. */
    public static final String NETWORK_LAYER_SEGMENT = "network-layer";

    private static final String V1 = TemplateUri.DEFAULT_TEMPLATE_VERSION;

    /** Task-T template for the ran-energy-saving scenario. */
    public static final TemplateUri ENERGY_SAVING =
            TemplateUri.of(TASK_EXTENSION_NAME, NETWORK_LAYER_SEGMENT, "ran-energy-saving");

    /** Raw template URI of {@link #ENERGY_SAVING}: {@code Task-T/network-layer/ran-energy-saving/v1}. */
    public static final String ENERGY_SAVING_URI = ENERGY_SAVING.uri();

    /** Task-T template for the private-line-complaint scenario. */
    public static final TemplateUri PRIVATE_LINE_COMPLAINT =
            TemplateUri.of(TASK_EXTENSION_NAME, NETWORK_LAYER_SEGMENT, "private-line-complaint");

    /** Raw template URI of {@link #PRIVATE_LINE_COMPLAINT}: {@code Task-T/network-layer/private-line-complaint/v1}. */
    public static final String PRIVATE_LINE_COMPLAINT_URI = PRIVATE_LINE_COMPLAINT.uri();

    /** Notification-T template for the subscribe-incident scenario. */
    public static final TemplateUri SUBSCRIBE_INCIDENT =
            TemplateUri.of(NOTIFICATION_EXTENSION_NAME, NETWORK_LAYER_SEGMENT, "subscribe-incident");

    /** Raw template URI of {@link #SUBSCRIBE_INCIDENT}: {@code Notification-T/network-layer/subscribe-incident/v1}. */
    public static final String SUBSCRIBE_INCIDENT_URI = SUBSCRIBE_INCIDENT.uri();

    /** Notification-T template for the service-recovery scenario. */
    public static final TemplateUri SERVICE_RECOVERY =
            TemplateUri.of(NOTIFICATION_EXTENSION_NAME, NETWORK_LAYER_SEGMENT, "service-recovery");

    /** Raw template URI of {@link #SERVICE_RECOVERY}: {@code Notification-T/network-layer/service-recovery/v1}. */
    public static final String SERVICE_RECOVERY_URI = SERVICE_RECOVERY.uri();

    /** Authorization-T template for the authorization-policy-management scenario. */
    public static final TemplateUri AUTHORIZATION_POLICY_MANAGEMENT =
            TemplateUri.of(AUTHORIZATION_EXTENSION_NAME, "authorization-policy-management");

    /**
     * Raw template URI of {@link #AUTHORIZATION_POLICY_MANAGEMENT}:
     * {@code Authorization-T/authorization-policy-management/v1}.
     */
    public static final String AUTHORIZATION_POLICY_MANAGEMENT_URI = AUTHORIZATION_POLICY_MANAGEMENT.uri();

    /** Negotiation-T propose template for information negotiation. */
    public static final TemplateUri INFORMATION_NEGOTIATION_PROPOSE =
            TemplateUri.of(NEGOTIATION_EXTENSION_NAME, "information-negotiation", "propose");

    /**
     * Raw template URI of {@link #INFORMATION_NEGOTIATION_PROPOSE}:
     * {@code Negotiation-T/information-negotiation/propose/v1}.
     */
    public static final String INFORMATION_NEGOTIATION_PROPOSE_URI = INFORMATION_NEGOTIATION_PROPOSE.uri();

    /** Negotiation-T accept-reject template for information negotiation. */
    public static final TemplateUri INFORMATION_NEGOTIATION_ACCEPT_REJECT =
            TemplateUri.of(NEGOTIATION_EXTENSION_NAME, "information-negotiation", "accept-reject");

    /**
     * Raw template URI of {@link #INFORMATION_NEGOTIATION_ACCEPT_REJECT}:
     * {@code Negotiation-T/information-negotiation/accept-reject/v1}.
     */
    public static final String INFORMATION_NEGOTIATION_ACCEPT_REJECT_URI =
            INFORMATION_NEGOTIATION_ACCEPT_REJECT.uri();

    /** Negotiation-T propose template for target negotiation. */
    public static final TemplateUri TARGET_NEGOTIATION_PROPOSE =
            TemplateUri.of(NEGOTIATION_EXTENSION_NAME, "target-negotiation", "propose");

    /** Raw template URI of {@link #TARGET_NEGOTIATION_PROPOSE}: {@code Negotiation-T/target-negotiation/propose/v1}. */
    public static final String TARGET_NEGOTIATION_PROPOSE_URI = TARGET_NEGOTIATION_PROPOSE.uri();

    /** Negotiation-T accept-reject template for target negotiation. */
    public static final TemplateUri TARGET_NEGOTIATION_ACCEPT_REJECT =
            TemplateUri.of(NEGOTIATION_EXTENSION_NAME, "target-negotiation", "accept-reject");

    /**
     * Raw template URI of {@link #TARGET_NEGOTIATION_ACCEPT_REJECT}:
     * {@code Negotiation-T/target-negotiation/accept-reject/v1}.
     */
    public static final String TARGET_NEGOTIATION_ACCEPT_REJECT_URI = TARGET_NEGOTIATION_ACCEPT_REJECT.uri();

    /** Negotiation-T propose template for feasibility negotiation. */
    public static final TemplateUri FEASIBILITY_NEGOTIATION_PROPOSE =
            TemplateUri.of(NEGOTIATION_EXTENSION_NAME, "feasibility-negotiation", "propose");

    /**
     * Raw template URI of {@link #FEASIBILITY_NEGOTIATION_PROPOSE}:
     * {@code Negotiation-T/feasibility-negotiation/propose/v1}.
     */
    public static final String FEASIBILITY_NEGOTIATION_PROPOSE_URI = FEASIBILITY_NEGOTIATION_PROPOSE.uri();

    /** Negotiation-T accept-reject template for feasibility negotiation. */
    public static final TemplateUri FEASIBILITY_NEGOTIATION_ACCEPT_REJECT =
            TemplateUri.of(NEGOTIATION_EXTENSION_NAME, "feasibility-negotiation", "accept-reject");

    /**
     * Raw template URI of {@link #FEASIBILITY_NEGOTIATION_ACCEPT_REJECT}:
     * {@code Negotiation-T/feasibility-negotiation/accept-reject/v1}.
     */
    public static final String FEASIBILITY_NEGOTIATION_ACCEPT_REJECT_URI =
            FEASIBILITY_NEGOTIATION_ACCEPT_REJECT.uri();

    /** Negotiation-T common abort template. */
    public static final TemplateUri NEGOTIATION_ABORT =
            TemplateUri.of(NEGOTIATION_EXTENSION_NAME, "common", "abort");

    /** Raw template URI of {@link #NEGOTIATION_ABORT}: {@code Negotiation-T/common/abort/v1}. */
    public static final String NEGOTIATION_ABORT_URI = NEGOTIATION_ABORT.uri();

    /** All built-in Task-T templates. */
    public static final List<TemplateUri> TASK = List.of(ENERGY_SAVING, PRIVATE_LINE_COMPLAINT);

    /** Raw template URIs of all built-in Task-T templates. */
    public static final List<String> TASK_URIS = List.of(ENERGY_SAVING_URI, PRIVATE_LINE_COMPLAINT_URI);

    /** All built-in Notification-T templates. */
    public static final List<TemplateUri> NOTIFICATION = List.of(SUBSCRIBE_INCIDENT, SERVICE_RECOVERY);

    /** Raw template URIs of all built-in Notification-T templates. */
    public static final List<String> NOTIFICATION_URIS = List.of(SUBSCRIBE_INCIDENT_URI, SERVICE_RECOVERY_URI);

    /** All built-in Authorization-T templates. */
    public static final List<TemplateUri> AUTHORIZATION = List.of(AUTHORIZATION_POLICY_MANAGEMENT);

    /** Raw template URIs of all built-in Authorization-T templates. */
    public static final List<String> AUTHORIZATION_URIS = List.of(AUTHORIZATION_POLICY_MANAGEMENT_URI);

    /** All built-in Negotiation-T templates. */
    public static final List<TemplateUri> NEGOTIATION = List.of(
            INFORMATION_NEGOTIATION_PROPOSE,
            INFORMATION_NEGOTIATION_ACCEPT_REJECT,
            TARGET_NEGOTIATION_PROPOSE,
            TARGET_NEGOTIATION_ACCEPT_REJECT,
            FEASIBILITY_NEGOTIATION_PROPOSE,
            FEASIBILITY_NEGOTIATION_ACCEPT_REJECT,
            NEGOTIATION_ABORT);

    /** Raw template URIs of all built-in Negotiation-T templates. */
    public static final List<String> NEGOTIATION_URIS = List.of(
            INFORMATION_NEGOTIATION_PROPOSE_URI,
            INFORMATION_NEGOTIATION_ACCEPT_REJECT_URI,
            TARGET_NEGOTIATION_PROPOSE_URI,
            TARGET_NEGOTIATION_ACCEPT_REJECT_URI,
            FEASIBILITY_NEGOTIATION_PROPOSE_URI,
            FEASIBILITY_NEGOTIATION_ACCEPT_REJECT_URI,
            NEGOTIATION_ABORT_URI);
}
