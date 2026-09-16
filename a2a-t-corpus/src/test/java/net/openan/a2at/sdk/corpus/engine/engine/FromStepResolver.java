package net.openan.a2at.sdk.corpus.engine.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves {@code {"$fromStep": N, "$field": "a.b"}} references inside step arguments before the step runs.
 *
 * <p>{@code $fromStep} is the 1-based index of an earlier step whose serialized response payload is navigated with the
 * dot-separated {@code $field} path. Literal values always take precedence; a reference may appear at any nesting
 * depth of the arguments map.
 */
public final class FromStepResolver {

    private FromStepResolver() {}

    /**
     * Resolves every {@code $fromStep} reference in the arguments of step {@code stepNumber}.
     *
     * @param args step arguments, possibly carrying references
     * @param stepNumber 1-based number of the step being resolved (references must target earlier steps)
     * @param priorPayloads serialized response payloads of the preceding steps, aligned by step index
     * @return a deep copy of the arguments with all references replaced
     */
    public static Map<String, Object> resolve(
            Map<String, Object> args, int stepNumber, List<Map<String, Object>> priorPayloads) {
        return (Map<String, Object>) resolveValue(args, stepNumber, priorPayloads);
    }

    private static Object resolveValue(
            Object value, int stepNumber, List<Map<String, Object>> priorPayloads) {
        if (value instanceof Map<?, ?> rawMap) {
            Map<?, ?> map = rawMap;
            if (map.containsKey("$fromStep") || map.containsKey("$field")) {
                return resolveReference(map, stepNumber, priorPayloads);
            }
            Map<String, Object> resolved = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                resolved.put(
                        String.valueOf(entry.getKey()),
                        resolveValue(entry.getValue(), stepNumber, priorPayloads));
            }
            return resolved;
        }
        if (value instanceof List<?> list) {
            List<Object> resolved = new ArrayList<>();
            for (Object entry : list) {
                resolved.add(resolveValue(entry, stepNumber, priorPayloads));
            }
            return resolved;
        }
        return value;
    }

    private static Object resolveReference(
            Map<?, ?> reference, int stepNumber, List<Map<String, Object>> priorPayloads) {
        if (reference.size() != 2 || !reference.containsKey("$fromStep") || !reference.containsKey("$field")) {
            throw new IllegalArgumentException(
                    "step" + stepNumber + " $fromStep reference must contain exactly the two keys $fromStep and $field: " + reference);
        }
        Object rawStep = reference.get("$fromStep");
        Object rawField = reference.get("$field");
        if (!(rawStep instanceof Number) || !(rawField instanceof String field) || field.isBlank()) {
            throw new IllegalArgumentException("step" + stepNumber
                    + " invalid $fromStep reference ($fromStep must be a positive integer, $field a non-blank string): " + reference);
        }
        int fromStep = ((Number) rawStep).intValue();
        if (fromStep < 1 || fromStep >= stepNumber || fromStep - 1 >= priorPayloads.size()) {
            throw new IllegalArgumentException("step" + stepNumber + " $fromStep=" + fromStep
                    + " must reference an earlier, already-executed step (1.." + (stepNumber - 1) + ")");
        }
        Map<String, Object> payload = priorPayloads.get(fromStep - 1);
        if (payload == null) {
            throw new IllegalArgumentException("step" + stepNumber + " $fromStep=" + fromStep
                    + " references a step without a success payload (it failed or was skipped)");
        }
        return navigate(payload, field, fromStep, stepNumber);
    }

    private static Object navigate(
            Map<String, Object> payload, String path, int fromStep, int stepNumber) {
        Object current = payload;
        for (String segment : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map) || !map.containsKey(segment)) {
                throw new IllegalArgumentException("step" + stepNumber + " reference $fromStep=" + fromStep
                        + " $field=" + path + " failed to resolve: field " + segment + " does not exist in the referenced step payload");
            }
            current = map.get(segment);
        }
        if (current == null) {
            throw new IllegalArgumentException("step" + stepNumber + " reference $fromStep=" + fromStep
                    + " $field=" + path + " resolved to null, which cannot be used as a step argument");
        }
        return current;
    }
}