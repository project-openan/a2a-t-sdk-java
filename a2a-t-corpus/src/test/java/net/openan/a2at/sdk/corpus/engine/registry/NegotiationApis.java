package net.openan.a2at.sdk.corpus.engine.registry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.NegotiationPerformative;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.negotiation.content.FeasibilityEndingContent;
import net.openan.a2at.sdk.negotiation.content.FeasibilityProposeContent;
import net.openan.a2at.sdk.negotiation.content.InformationEndingContent;
import net.openan.a2at.sdk.negotiation.content.InformationProposeContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationAbortContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationAbortData;
import net.openan.a2at.sdk.negotiation.content.NegotiationAction;
import net.openan.a2at.sdk.negotiation.content.NegotiationConclusion;
import net.openan.a2at.sdk.negotiation.content.NegotiationEndingContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationEndingData;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;
import net.openan.a2at.sdk.negotiation.content.NegotiationProposeContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationProposeData;
import net.openan.a2at.sdk.negotiation.content.NegotiationType;
import net.openan.a2at.sdk.negotiation.content.TargetEndingContent;
import net.openan.a2at.sdk.negotiation.content.TargetProposeContent;
import net.openan.a2at.sdk.negotiation.generation.NegotiationGenerationOrchestrator;

public final class NegotiationApis {

    private NegotiationApis() {}

    public static void registerNegotiationApis(
            ApiRegistry registry, NegotiationGenerationOrchestrator orchestrator) {

        // ---- from-text generation ----

        registry.register("generateNegotiationProposePromptFromText", args -> metadataToMap(
                orchestrator.generateProposeFromText(
                        Args.text(args, "text"),
                        Args.context(args, "context"),
                        Args.templateUri(args, "templateUri"))));
        registry.register("generateNegotiationAcceptPromptFromText", args -> metadataToMap(
                orchestrator.generateAcceptFromText(
                        Args.text(args, "text"),
                        Args.context(args, "context"),
                        Args.templateUri(args, "templateUri"))));
        registry.register("generateNegotiationRejectPromptFromText", args -> metadataToMap(
                orchestrator.generateRejectFromText(
                        Args.text(args, "text"),
                        Args.context(args, "context"),
                        Args.templateUri(args, "templateUri"))));
        registry.register("generateNegotiationAbortPromptFromText", args -> metadataToMap(
                orchestrator.generateAbortFromText(
                        Args.text(args, "text"),
                        Args.context(args, "context"),
                        Args.templateUri(args, "templateUri"))));

        // ---- from-data generation ----

        registry.register("generateNegotiationProposePromptFromData", args -> metadataToMap(
                orchestrator.generateProposeFromData(
                        Args.proposeData(args, "data"),
                        Args.templateUri(args, "templateUri"))));
        registry.register("generateNegotiationAcceptPromptFromData", args -> metadataToMap(
                orchestrator.generateAcceptFromData(
                        Args.endingData(args, "data", NegotiationPerformative.ACCEPT),
                        Args.templateUri(args, "templateUri"))));
        registry.register("generateNegotiationRejectPromptFromData", args -> metadataToMap(
                orchestrator.generateRejectFromData(
                        Args.endingData(args, "data", NegotiationPerformative.REJECT),
                        Args.templateUri(args, "templateUri"))));
        registry.register("generateNegotiationAbortPromptFromData", args -> metadataToMap(
                orchestrator.generateAbortFromData(
                        Args.abortData(args, "data"),
                        Args.templateUri(args, "templateUri"))));

        // ---- validation ----

        registry.register("validateProposePromptAndDataFilling", args -> filledToMap(
                orchestrator.validateProposePromptAndDataFilling(
                        Args.text(args, "promptText"),
                        Args.optionalContext(args, "context"),
                        Args.map(args, "schema"),
                        Args.templateUri(args, "templateUri"))));
        registry.register("validateAcceptPromptAndDataFilling", args -> filledToMap(
                orchestrator.validateAcceptPromptAndDataFilling(
                        Args.text(args, "promptText"),
                        Args.optionalContext(args, "context"),
                        Args.map(args, "schema"),
                        Args.templateUri(args, "templateUri"))));
        registry.register("validateRejectPromptAndDataFilling", args -> filledToMap(
                orchestrator.validateRejectPromptAndDataFilling(
                        Args.text(args, "promptText"),
                        Args.optionalContext(args, "context"),
                        Args.map(args, "schema"),
                        Args.templateUri(args, "templateUri"))));
        registry.register("validateAbortPromptAndDataFilling", args -> filledToMap(
                orchestrator.validateAbortPromptAndDataFilling(
                        Args.text(args, "promptText"),
                        Args.optionalContext(args, "context"),
                        Args.map(args, "schema"),
                        Args.templateUri(args, "templateUri"))));
    }

    static Map<String, Object> metadataToMap(MetadataContent content) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("templateUri", content.templateUri());
        map.put("promptText", content.promptText());
        map.put("extensionUri", content.extensionUri());
        return map;
    }

    static Map<String, Object> filledToMap(FilledParamData filled) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("data", filled.data() == null ? Map.of() : filled.data());
        return map;
    }

    // -- helper types --

    private static final class Args {

        private Args() {}

        static String text(Map<String, Object> args, String name) {
            Object value = args.get(name);
            if (!(value instanceof String str)) {
                throw new IllegalArgumentException("step argument " + name + " must be a string: " + value);
            }
            return str;
        }

        @SuppressWarnings("unchecked")
        static Map<String, Object> map(Map<String, Object> args, String name) {
            Object value = args.get(name);
            if (!(value instanceof Map)) {
                throw new IllegalArgumentException("step argument " + name + " must be an object: " + value);
            }
            Map<String, Object> typed = (Map<String, Object>) value;
            if (typed.isEmpty()) {
                throw new IllegalArgumentException("step argument " + name + " must not be an empty object");
            }
            return typed;
        }

        static TemplateUri templateUri(Map<String, Object> args, String name) {
            return ClientApis.templateUri(text(args, name));
        }

        static NegotiationContext context(Map<String, Object> args, String name) {
            return contextFromMap(map(args, name));
        }

        static NegotiationContext optionalContext(Map<String, Object> args, String name) {
            Object raw = args.get(name);
            if (raw == null) {
                return null;
            }
            if (!(raw instanceof Map)) {
                throw new IllegalArgumentException("step argument " + name + " must be an object: " + raw);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) raw;
            return contextFromMap(typed);
        }

        static NegotiationProposeData proposeData(Map<String, Object> args, String name) {
            Map<String, Object> raw = map(args, name);
            Map<String, Object> content = map(raw, "content");
            NegotiationType type = negotiationTypeOf(args);
            return new NegotiationProposeData(
                    contextFromMap(map(raw, "context")),
                    (NegotiationProposeContent) buildContent(type, NegotiationPerformative.PROPOSE, content));
        }

        static NegotiationEndingData endingData(
                Map<String, Object> args, String name, NegotiationPerformative performative) {
            Map<String, Object> raw = map(args, name);
            Map<String, Object> content = map(raw, "content");
            NegotiationType type = negotiationTypeOf(args);
            return new NegotiationEndingData(
                    contextFromMap(map(raw, "context")),
                    (NegotiationEndingContent) buildContent(type, performative, content));
        }

        static NegotiationAbortData abortData(Map<String, Object> args, String name) {
            Map<String, Object> raw = map(args, name);
            return new NegotiationAbortData(
                    contextFromMap(map(raw, "context")),
                    new NegotiationAbortContent(text(map(raw, "content"), "termination_reason")));
        }
    }

    // -- content factory --

    private static final Map<String, NegotiationType> TYPE_MAP = Map.of(
            "information-negotiation", NegotiationType.INFORMATION,
            "target-negotiation", NegotiationType.TARGET,
            "feasibility-negotiation", NegotiationType.FEASIBILITY);

    private static NegotiationType negotiationTypeOf(Map<String, Object> args) {
        String raw = Args.text(args, "templateUri");
        TemplateUri parsed = TemplateUri.parse(raw)
                .orElseThrow(() -> new IllegalArgumentException("Unparseable template URI: " + raw));
        String segment = parsed.pathSegments().get(0);
        NegotiationType type = TYPE_MAP.get(segment);
        if (type == null) {
            throw new IllegalArgumentException("Unrecognized negotiation type segment: " + segment);
        }
        return type;
    }

    private static Object buildContent(
            NegotiationType type, NegotiationPerformative performative, Map<String, Object> raw) {
        if (performative == NegotiationPerformative.ABORT) {
            return new NegotiationAbortContent(required(raw, "termination_reason", "abort content"));
        }
        if (performative == NegotiationPerformative.ACCEPT || performative == NegotiationPerformative.REJECT) {
            return buildEndingContent(type, parseConclusion(raw), raw);
        }
        return buildProposeContent(type, raw);
    }

    private static NegotiationConclusion parseConclusion(Map<String, Object> raw) {
        Object rawValue = raw.get("conclusion");
        if (rawValue == null) {
            throw new IllegalArgumentException("ending content must declare its conclusion");
        }
        return NegotiationConclusion.valueOf(String.valueOf(rawValue));
    }

    private static NegotiationEndingContent buildEndingContent(
            NegotiationType type, NegotiationConclusion conclusion, Map<String, Object> raw) {
        return switch (type) {
            case INFORMATION ->
                new InformationEndingContent(conclusion, items(raw.get("items")));
            case TARGET ->
                new TargetEndingContent(conclusion, optionalStr(raw, "confirmed_intent"),
                        optionalStr(raw, "failure_reason"));
            case FEASIBILITY ->
                new FeasibilityEndingContent(conclusion, required(raw, "feasibility_summary", "ending content"));
        };
    }

    private static NegotiationProposeContent buildProposeContent(NegotiationType type, Map<String, Object> raw) {
        return switch (type) {
            case INFORMATION ->
                new InformationProposeContent(items(raw.get("items")), optionalStr(raw, "relationship"));
            case TARGET ->
                new TargetProposeContent(
                        required(raw, "target_negotiation_description", "propose content"),
                        items(raw.get("intent_understanding")),
                        items(raw.get("alignment_and_clarification")),
                        items(raw.get("request_for_clarification")),
                        optionalStr(raw, "target_confirm_request"));
            case FEASIBILITY -> {
                Object rawAction = raw.get("action");
                if (rawAction == null) {
                    throw new IllegalArgumentException("Feasibility propose content must declare its action");
                }
                yield new FeasibilityProposeContent(
                        required(raw, "feasibility_negotiation_description", "propose content"),
                        NegotiationAction.valueOf(String.valueOf(rawAction)),
                        items(raw.get("contents_to_evaluate")),
                        items(raw.get("infeasibility_details_and_proposal")),
                        optionalStr(raw, "feasibility_confirm_request"));
            }
        };
    }

    private static NegotiationContext contextFromMap(Map<String, Object> raw) {
        return new NegotiationContext(
                (String) raw.get("id"),
                ((Number) raw.get("round")).intValue(),
                raw.containsKey("maxRounds")
                        ? ((Number) raw.get("maxRounds")).intValue()
                        : NegotiationContext.DEFAULT_MAX_ROUNDS,
                NegotiationPerformative.valueOf((String) raw.get("performative")));
    }

    private static List<NegotiationItem> items(Object raw) {
        if (raw == null) {
            return null;
        }
        if (!(raw instanceof List<?> rawList)) {
            throw new IllegalArgumentException("negotiation items must be an array: " + raw);
        }
        List<NegotiationItem> items = new ArrayList<>();
        for (Object element : rawList) {
            @SuppressWarnings("unchecked")
            Map<String, Object> itemMap = (Map<String, Object>) element;
            items.add(new NegotiationItem(
                    required(itemMap, "name", "negotiation item"),
                    optionalStr(itemMap, "value")));
        }
        return items;
    }

    private static String required(Map<String, Object> raw, String key, String where) {
        Object value = raw.get(key);
        if (!(value instanceof String str) || str.isBlank()) {
            throw new IllegalArgumentException(where + " requires a non-blank '" + key + "'");
        }
        return str;
    }

    private static String optionalStr(Map<String, Object> raw, String key) {
        Object value = raw.get(key);
        return value == null ? null : String.valueOf(value);
    }
}