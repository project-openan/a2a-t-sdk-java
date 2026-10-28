package net.openan.a2at.sdk.observability;

import java.util.Set;

/** Span attribute keys and shared constants (values byte-identical to the Python SDK). */
public final class A2ATAttributes {

    public static final String ATTR_EXTENSION_NAME = "gen_ai.agent.a2at.extension.name";
    public static final String ATTR_TASK_ID = "gen_ai.agent.a2at.task.id";
    public static final String ATTR_TASK_STATUS = "gen_ai.agent.a2at.task.status";
    public static final String ATTR_TASK_TYPE = "gen_ai.agent.a2at.task.type";
    public static final String ATTR_NEGOTIATION_ID = "gen_ai.agent.a2at.negotiation.id";
    public static final String ATTR_NEGOTIATION_ROUND = "gen_ai.agent.a2at.negotiation.round";
    public static final String ATTR_NEGOTIATION_MAX_ROUNDS = "gen_ai.agent.a2at.negotiation.max_rounds";
    public static final String ATTR_NEGOTIATION_PERFORMATIVE = "gen_ai.agent.a2at.negotiation.performative";
    public static final String ATTR_NEGOTIATION_TOTAL_ROUNDS = "gen_ai.agent.a2at.negotiation.total_rounds";
    public static final String ATTR_NOTIFICATION_TOPIC = "gen_ai.agent.a2at.notification.topic";
    public static final String ATTR_STREAMING_EVENT_KIND = "gen_ai.agent.a2at.streaming.event.kind";
    public static final String ATTR_PUSH_NOTIFICATION_URL = "gen_ai.agent.a2at.push.notification.url";
    public static final String ATTR_SPAN_SIDE = "a2at.span.side";
    public static final String ATTR_AUTHORIZATION_POLICY_OPERATION_TYPE =
            "gen_ai.agent.a2at.authorization.policy.operation.type";
    public static final String ATTR_AUTHORIZATION_POLICY_ID = "authorization.policy.id";
    public static final String ATTR_AUTHORIZATION_OPERATION_TYPE = "authorization.operation_type";
    public static final String ATTR_AUTHORIZATION_OPERATION_RISK_LEVEL = "authorization.operation_risk_level";
    public static final String ATTR_A2A_OPERATION_NAME = "gen_ai.agent.a2a.operation.name";
    public static final String ATTR_A2A_CONTEXT_ID = "gen_ai.agent.a2a.context_id";
    public static final String ATTR_A2A_TASK_ID = "gen_ai.agent.a2a.task_id";
    public static final String ATTR_A2A_MESSAGE_ID = "gen_ai.agent.a2a.message_id";
    public static final String ATTR_A2A_ROLE = "gen_ai.agent.a2a.role";
    public static final String ATTR_A2A_EXTENSIONS = "gen_ai.agent.a2a.extensions";
    public static final String ATTR_A2A_PARTS_NUMBER = "gen_ai.agent.a2a.parts.number";
    public static final String ATTR_A2A_PROTOCOL = "gen_ai.agent.a2a.protocol";
    public static final String ATTR_A2A_REQUEST = "gen_ai.agent.a2a.request";
    public static final String ATTR_A2A_RESPONSE = "gen_ai.agent.a2a.response";
    public static final String ATTR_GEN_AI_OPERATION_NAME = "gen_ai.operation.name";
    public static final String ATTR_GEN_AI_CONVERSATION_ID = "gen_ai.conversation.id";
    public static final String ATTR_GEN_AI_USAGE_INPUT_TOKENS = "gen_ai.usage.input_tokens";
    public static final String ATTR_GEN_AI_USAGE_OUTPUT_TOKENS = "gen_ai.usage.output_tokens";
    public static final String ATTR_GEN_AI_REQUEST_MODEL = "gen_ai.request.model";
    public static final String ATTR_GEN_AI_RESPONSE_MODEL = "gen_ai.response.model";
    public static final String ATTR_GEN_AI_TOKEN_TYPE = "gen_ai.token.type";
    public static final String ATTR_GEN_AI_PROVIDER_NAME = "gen_ai.provider.name";
    public static final String ATTR_STREAMING = "streaming";
    public static final String ATTR_ERROR_TYPE = "error.type";

    public static final String NEGOTIATION_EXTENSION = "Negotiation-T";
    public static final String NEGOTIATION_SUFFIX = "-negotiation";

    public static final Set<String> EVENT_LOG_KINDS =
            Set.of("status", "artifact", "completed", "canceled", "failed", "rejected");

    private A2ATAttributes() {}
}
