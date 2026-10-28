package net.openan.a2at.sdk.observability;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** Metadata-level attribute extractors (Map in / attributes out); never raises. */
public final class AttributeExtractors {

    public static final String EXTENSION_BASE = "https://projects.tmforum.org/a2aproject/telecommunication/extensions/";
    public static final String DEFAULT_TASK_TYPE_REGEX = "##\\s*任务类型\\(Task Type\\)\\s*\\n+\\s*(\\S[^\\n]*)";
    public static final String DEFAULT_NOTIFICATION_TOPIC_REGEX = "##\\s*通知主题\\s*\\n+\\s*(\\S[^\\n]*)";
    public static final String DEFAULT_AUTHORIZATION_REGEX = "##\\s*授权策略的操作类型\\s*\\n+\\s*(\\S[^\\n]*)";
    private static final Set<String> TERMINAL_PERFORMATIVES = Set.of("ACCEPT", "REJECT", "ABORT");

    private AttributeExtractors() {}

    public static @Nullable String extensionNameFromUri(@Nullable String uri) {
        if (uri == null || !uri.startsWith(EXTENSION_BASE)) {
            return null;
        }
        String rest = uri.substring(EXTENSION_BASE.length());
        int slash = rest.indexOf('/');
        String name = slash >= 0 ? rest.substring(0, slash) : rest;
        return name.isEmpty() ? null : name;
    }

    public static @Nullable String extensionNameFromMetadata(Map<String, Object> metadata) {
        for (String key : metadata.keySet()) {
            String name = extensionNameFromUri(key);
            if (name != null) {
                return name;
            }
        }
        return null;
    }

    public static boolean isNegotiationMessage(Map<String, Object> metadata) {
        return A2ATAttributes.NEGOTIATION_EXTENSION.equals(extensionNameFromMetadata(metadata));
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> negotiationAttributes(Map<String, Object> metadata) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        Object contextObj = metadata.get("negotiationContext");
        if (!(contextObj instanceof Map<?, ?> context)) {
            return attrs;
        }
        Map<String, Object> ctx = (Map<String, Object>) context;
        Object id = ctx.get("id");
        if (id instanceof String s && !s.isEmpty()) {
            attrs.put(A2ATAttributes.ATTR_NEGOTIATION_ID, s);
        }
        Integer round = wireInt(ctx.get("round"));
        if (round != null) {
            attrs.put(A2ATAttributes.ATTR_NEGOTIATION_ROUND, round);
        }
        Integer maxRounds = wireInt(ctx.get("maxRounds"));
        if (maxRounds != null) {
            attrs.put(A2ATAttributes.ATTR_NEGOTIATION_MAX_ROUNDS, maxRounds);
        }
        Object performative = ctx.get("performative");
        if (performative instanceof String p && !p.isEmpty()) {
            attrs.put(A2ATAttributes.ATTR_NEGOTIATION_PERFORMATIVE, p);
            if (TERMINAL_PERFORMATIVES.contains(p) && round != null) {
                attrs.put(A2ATAttributes.ATTR_NEGOTIATION_TOTAL_ROUNDS, round);
            }
        }
        return attrs;
    }

    private static @Nullable Integer wireInt(@Nullable Object value) {
        if (value instanceof Integer i) {
            return i;
        }
        if (value instanceof Number n && n.longValue() == n.doubleValue()) {
            return n.intValue();
        }
        return null;
    }

    public static @Nullable String normalizeTaskStateName(@Nullable String enumName) {
        if (enumName == null) {
            return null;
        }
        String prefix = "TASK_STATE_";
        return enumName.startsWith(prefix)
                ? enumName.substring(prefix.length()).toLowerCase(Locale.ROOT)
                : enumName.toLowerCase(Locale.ROOT);
    }

    public static Map<String, Object> regexAttributes(Map<String, Object> metadata, A2ATObservabilityConfig config) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        putRegex(
                attrs,
                A2ATAttributes.ATTR_TASK_TYPE,
                metadata,
                config.taskTypeRegex() != null ? config.taskTypeRegex() : DEFAULT_TASK_TYPE_REGEX);
        putRegex(
                attrs,
                A2ATAttributes.ATTR_NOTIFICATION_TOPIC,
                metadata,
                config.notificationTopicRegex() != null
                        ? config.notificationTopicRegex()
                        : DEFAULT_NOTIFICATION_TOPIC_REGEX);
        String authorization = findFirst(
                metadata,
                config.authorizationPolicyOperationTypeRegex() != null
                        ? config.authorizationPolicyOperationTypeRegex()
                        : DEFAULT_AUTHORIZATION_REGEX);
        if (authorization != null) {
            String cleaned = authorization.replaceAll("[（(][^）)]*[）)]\\s*$", "").trim();
            if (!cleaned.isEmpty()) {
                attrs.put(A2ATAttributes.ATTR_AUTHORIZATION_POLICY_OPERATION_TYPE, cleaned);
            }
        }
        return attrs;
    }

    private static void putRegex(
            Map<String, Object> attrs, String key, Map<String, Object> metadata, @Nullable String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return;
        }
        String found = findFirst(metadata, pattern);
        if (found != null && !found.isEmpty()) {
            attrs.put(key, found);
        }
    }

    private static @Nullable String findFirst(Map<String, Object> metadata, @Nullable String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return null;
        }
        Pattern compiled = Pattern.compile(pattern);
        for (Object value : metadata.values()) {
            if (value instanceof String s) {
                Matcher matcher = compiled.matcher(s);
                if (matcher.find()) {
                    return matcher.groupCount() > 0 ? matcher.group(1) : matcher.group();
                }
            }
        }
        return null;
    }
}
