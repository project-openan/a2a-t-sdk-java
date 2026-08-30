package net.openan.a2at.sample.negotiation.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.openan.a2at.sample.negotiation.shared.InformationNegotiationSchemas;
import org.junit.jupiter.api.Test;

class NegotiationFromDataApiEvalAppTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> REQUIRED_FIELDS = Set.of("任务对象", "投诉分类", "OSS侧事件流水号");
    private static final Set<String> OPTIONAL_FIELDS = Set.of("问题发生时间", "投诉详情");

    @Test
    void should_DelegateToTheSharedInformationNegotiationSchemas() {
        assertEquals(
                InformationNegotiationSchemas.propose(), NegotiationFromDataApiEvalApp.extractionSchema("propose"));
        assertEquals(InformationNegotiationSchemas.accept(), NegotiationFromDataApiEvalApp.extractionSchema("accept"));
        assertEquals(InformationNegotiationSchemas.reject(), NegotiationFromDataApiEvalApp.extractionSchema("reject"));
        assertThrows(IllegalArgumentException.class, () -> NegotiationFromDataApiEvalApp.extractionSchema("unknown"));
    }

    @Test
    void should_CoverOnlyRequiredFieldCombinationsInSuccessfulProposes() throws IOException {
        List<Map<String, Object>> cases = cases();
        Set<Set<String>> actualCombinations = new HashSet<>();

        for (Map<String, Object> evalCase : cases) {
            if (!"propose".equals(evalCase.get("api")) || !succeeds(evalCase)) {
                continue;
            }
            Map<String, Object> input = map(evalCase.get("input"));
            Set<String> fields = new TreeSet<>(map(input.get("items")).keySet());
            assertFalse(fields.isEmpty());
            assertTrue(REQUIRED_FIELDS.containsAll(fields));
            assertTrue(disjoint(fields, OPTIONAL_FIELDS));
            if (fields.size() == 1) {
                assertNull(input.get("relationship"));
            } else {
                assertTrue(String.valueOf(input.get("relationship")).contains("AND"));
            }
            actualCombinations.add(fields);
        }

        assertEquals(allNonEmptySubsets(REQUIRED_FIELDS), actualCombinations);
    }

    @Test
    void should_AcceptOrRejectOnlyFieldsThatCanBeNegotiated() throws IOException {
        for (Map<String, Object> evalCase : cases()) {
            String api = String.valueOf(evalCase.get("api"));
            if (!("accept".equals(api) || "reject".equals(api)) || !succeeds(evalCase)) {
                continue;
            }
            Map<String, Object> items = map(map(evalCase.get("input")).get("items"));
            assertFalse(items.isEmpty());
            assertTrue(REQUIRED_FIELDS.containsAll(items.keySet()));
            assertTrue(disjoint(items.keySet(), OPTIONAL_FIELDS));
            if ("reject".equals(api)) {
                items.values()
                        .forEach(reason -> assertTrue(String.valueOf(reason).contains("无法提供")));
            }
        }
    }

    @Test
    void should_KeepSharedScenarioWithinPrivateLineComplaintDomain() throws IOException {
        Map<String, Object> scenario = resource("sample/negotiation/scenario.json");
        Map<String, Object> taskSchema = map(scenario.get("task_schema"));
        assertEquals(List.of("任务对象", "任务上下文"), taskSchema.get("required"));
        String missingContext =
                String.valueOf(map(scenario.get("missing_params")).get("任务上下文"));
        String filledContext = String.valueOf(map(scenario.get("filled_params")).get("任务上下文"));
        assertFalse(missingContext.contains("投诉分类"));
        assertFalse(missingContext.contains("OSS侧事件流水号"));
        assertTrue(filledContext.contains("投诉分类"));
        assertTrue(filledContext.contains("OSS侧事件流水号"));

        String serialized = MAPPER.writeValueAsString(scenario);
        assertFalse(serialized.contains("无线节点"));
        assertFalse(serialized.contains("节能"));
    }

    private static List<Map<String, Object>> cases() throws IOException {
        return maps(resource("sample/negotiation/eval/fromdata-api-suite.json").get("cases"));
    }

    private static Map<String, Object> resource(String resource) throws IOException {
        try (InputStream input =
                NegotiationFromDataApiEvalAppTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, resource);
            return MAPPER.readValue(input, new TypeReference<>() {});
        }
    }

    private static boolean succeeds(Map<String, Object> evalCase) {
        return Boolean.TRUE.equals(map(evalCase.get("expect")).get("succeeds"));
    }

    private static boolean disjoint(Set<String> left, Set<String> right) {
        return left.stream().noneMatch(right::contains);
    }

    private static Set<Set<String>> allNonEmptySubsets(Set<String> source) {
        List<String> fields = List.copyOf(source);
        Set<Set<String>> subsets = new HashSet<>();
        for (int mask = 1; mask < (1 << fields.size()); mask++) {
            Set<String> subset = new TreeSet<>();
            for (int index = 0; index < fields.size(); index++) {
                if ((mask & (1 << index)) != 0) {
                    subset.add(fields.get(index));
                }
            }
            subsets.add(subset);
        }
        return subsets;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> maps(Object value) {
        return (List<Map<String, Object>>) value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }
}
