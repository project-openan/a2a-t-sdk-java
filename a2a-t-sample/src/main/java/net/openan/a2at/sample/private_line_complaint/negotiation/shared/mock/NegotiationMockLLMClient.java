package net.openan.a2at.sample.private_line_complaint.negotiation.shared.mock;

import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.LLMClientConfig;
import net.openan.a2at.sdk.llm.LLMResponse;

/**
 * Deterministic structured LLM used by the information negotiation sample.
 *
 * <p>Responses are selected from the requested schema and text, so client and server calls can run in either order
 * without a shared call counter.
 */
public final class NegotiationMockLLMClient implements LLMClient {

    private static final String PROPOSE_CONTENT =
            """
            {"items":[{"name":"接入端口名称","value":"物理端口或逻辑端口名称"},
            {"name":"投诉分类","value":"专线中断或专线质差"}],"relationship":null}
            """;

    private static final String ACCEPT_CONTENT =
            """
            {"conclusion":"Accept","items":[{"name":"接入端口名称",
            "value":"P781-珠江新城-PTN7900-23-TPA1EG24-17(cvlan=100)"},
            {"name":"投诉分类","value":"专线质差"}]}
            """;

    private static final String REJECT_CONTENT =
            """
            {"conclusion":"Reject","items":[{"name":"接入端口名称",
            "value":"当前账号没有资源系统查询权限"},{"name":"投诉分类",
            "value":"当前账号没有资源系统查询权限"}]}
            """;

    private static final String PROPOSE_PARAMS =
            """
            {"items":[{"name":"接入端口名称","requirement":"物理端口或逻辑端口名称"},
            {"name":"投诉分类","requirement":"专线中断或专线质差"}],"relationship":null}
            """;

    private static final String ACCEPT_PARAMS =
            """
            {"items":[{"name":"接入端口名称","value":"P781-珠江新城-PTN7900-23-TPA1EG24-17(cvlan=100)"},
            {"name":"投诉分类","value":"专线质差"}]}
            """;

    private static final String REJECT_PARAMS =
            """
            {"items":[{"name":"接入端口名称","reason":"当前账号没有资源系统查询权限"},
            {"name":"投诉分类","reason":"当前账号没有资源系统查询权限"}]}
            """;

    public NegotiationMockLLMClient(LLMClientConfig config) {
        // Mock responses are independent from provider settings.
    }

    @Override
    @SuppressWarnings("unchecked")
    public LLMResponse structured(
            List<Map<String, String>> messages, Map<String, Object> jsonSchema, Double temperature, Integer maxTokens) {
        Map<String, Object> properties = propertiesOf(jsonSchema);
        String content;
        if (properties.containsKey("semantic_verdict")) {
            boolean reject = isRejectPrompt(lastMessage(messages));
            content = semanticResult(reject ? REJECT_PARAMS : semanticParamsForPrompt(messages));
        } else if (properties.containsKey("relationship") && properties.containsKey("items")) {
            content = PROPOSE_CONTENT;
        } else if (properties.containsKey("conclusion") && properties.containsKey("items")) {
            content = lastMessage(messages).contains("拒绝") ? REJECT_CONTENT : ACCEPT_CONTENT;
        } else {
            throw new IllegalArgumentException("Unsupported negotiation mock LLM schema: " + properties.keySet());
        }
        return new LLMResponse(
                content,
                "negotiation-sample-mock",
                Map.of("prompt_tokens", 0, "completion_tokens", 0, "total_tokens", 0),
                Map.of());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> propertiesOf(Map<String, Object> schema) {
        Object value = schema.get("properties");
        if (!(value instanceof Map<?, ?> properties)) {
            throw new IllegalArgumentException("Negotiation mock LLM schema has no top-level properties");
        }
        return (Map<String, Object>) properties;
    }

    private static String semanticParamsForPrompt(List<Map<String, String>> messages) {
        String prompt = lastMessage(messages);
        if (isAcceptPrompt(prompt)) {
            return ACCEPT_PARAMS;
        }
        return PROPOSE_PARAMS;
    }

    private static String semanticResult(String params) {
        return "{\"semantic_verdict\":true,\"negotiation_type\":\"information\",\"errors\":[],\"params\":"
                + params
                + "}";
    }

    private static String lastMessage(List<Map<String, String>> messages) {
        return messages.isEmpty() ? "" : messages.get(messages.size() - 1).getOrDefault("content", "");
    }

    private static boolean isAcceptPrompt(String prompt) {
        return prompt.matches("(?s).*## 信息协商结果\\R\\s*Accept.*")
                || prompt.matches("(?s).*## Information Negotiation Result\\R\\s*Accept.*");
    }

    private static boolean isRejectPrompt(String prompt) {
        return prompt.matches("(?s).*## 信息协商结果\\R\\s*Reject.*")
                || prompt.matches("(?s).*## Information Negotiation Result\\R\\s*Reject.*");
    }
}
