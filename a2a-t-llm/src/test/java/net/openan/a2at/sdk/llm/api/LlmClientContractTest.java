package net.openan.a2at.sdk.llm.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.llm.LLMClient;
import org.junit.jupiter.api.Test;

class LlmClientContractTest {

    @Test
    void llmClientIsProviderFacingInterface() {
        assertTrue(LLMClient.class.isInterface());
        assertFalse(Modifier.isFinal(LLMClient.class.getModifiers()));
    }

    @Test
    void llmClientExposesStructuredMethodWithSimplifiedSignature() throws Exception {
        Class<?> responseType = assertDoesNotThrow(() -> Class.forName("net.openan.a2at.sdk.llm.LLMResponse"));

        Method method = LLMClient.class.getMethod("structured", List.class, Map.class, Double.class, Integer.class);

        assertEquals(responseType, method.getReturnType());
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertFalse(Modifier.isStatic(method.getModifiers()));
    }
}
