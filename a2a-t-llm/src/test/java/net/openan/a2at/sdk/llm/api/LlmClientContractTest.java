package net.openan.a2at.sdk.llm.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;

import net.openan.a2at.sdk.llm.LLMClient;
import net.openan.a2at.sdk.llm.model.LLMResponse;
import net.openan.a2at.sdk.llm.model.StructuredGenerationRequest;
import org.junit.jupiter.api.Test;

class LlmClientContractTest {

    @Test
    void llmClientExposesSinglePublicPathConstructor() {
        Constructor<?>[] constructors = LLMClient.class.getConstructors();

        assertEquals(1, constructors.length);
        assertEquals(1, constructors[0].getParameterCount());
        assertEquals(Path.class, constructors[0].getParameterTypes()[0]);
        assertTrue(Modifier.isPublic(constructors[0].getModifiers()));
    }

    @Test
    void llmClientExposesStructuredMethodReturningUnifiedResponse() throws NoSuchMethodException {
        Method method = LLMClient.class.getMethod("structured", StructuredGenerationRequest.class);

        assertEquals(LLMResponse.class, method.getReturnType());
        assertTrue(Modifier.isPublic(method.getModifiers()));
    }
}
