package net.openan.a2at.sdk.corpus.engine.registry;

import java.util.LinkedHashMap;
import java.util.Map;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.validation.ContentValidator;

/**
 * Task-T server-side registration: the {@code validateTaskPromptAndDataFilling} step is bound to the production
 * {@link ContentValidator} assembled by {@link SdkRuntimeAssembler} from the default server builder.
 */
public final class ServerApis {

    private ServerApis() {}

    public static void registerTaskApis(ApiRegistry registry, ContentValidator taskContentValidator) {
        registry.register("validateTaskPromptAndDataFilling", args -> filledToMap(taskContentValidator.validate(
                ClientApis.Args.text(args, "promptText"),
                ClientApis.Args.map(args, "schema"),
                ClientApis.Args.templateUri(args, "templateUri"))));
    }

    static Map<String, Object> filledToMap(FilledParamData filled) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("data", filled.data() == null ? Map.of() : filled.data());
        return map;
    }
}