package net.openan.a2at.sdk.prompt.resources.loader;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.openan.a2at.sdk.core.exception.ResourceNotFoundException;
import net.openan.a2at.sdk.core.exception.A2ATError;
import net.openan.a2at.sdk.core.resources.ClasspathResourceDirectories;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotJsonSchema;
import net.openan.a2at.sdk.prompt.resources.model.PromptSlotSchema;
import net.openan.a2at.sdk.resources.ClasspathPromptResourceLoader;
import net.openan.a2at.sdk.resources.PromptResourceKey;

/**
 * Loads shared slot schemas from packaged classpath prompt resources.
 *
 * <p>The slot types are probed in a fixed order first and then in the order the extension directories appear under
 * {@code prompt_resources/slots/} on the classpath, so extensions bundled later are loadable without extending a
 * hardcoded list.
 *
 * @since 2026-06
 */
public final class ClasspathPromptSlotSchemaLoader implements PromptSlotSchemaLoader {

    private static final List<String> KNOWN_SLOT_TYPES =
            List.of(StandardTemplates.TASK_EXTENSION_NAME, StandardTemplates.NOTIFICATION_EXTENSION_NAME);

    private static final List<String> SLOT_TYPES = discoverSlotTypes();

    private final ClasspathPromptResourceLoader resourceLoader;

    public ClasspathPromptSlotSchemaLoader(ClasspathPromptResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public PromptSlotSchema loadSlotSchema(String scenarioCode, String language) {
        java.util.Optional<TemplateUri> parsed = TemplateUri.parse(scenarioCode);
        if (parsed.isPresent()) {
            TemplateUri uri = parsed.orElseThrow();
            return load(uri, scenarioCode, language);
        }
        for (String slotType : SLOT_TYPES) {
            for (TemplateUri candidate : bareCodeCandidates(slotType, scenarioCode)) {
                try {
                    return load(candidate, scenarioCode, language);
                } catch (ResourceNotFoundException ignored) {
                    // try next candidate layout
                }
            }
        }
        throw new ResourceNotFoundException(
                "Prompt resource file does not exist.",
                "prompt_resources/slots/*/network-layer/" + scenarioCode + "/v1/" + language
                        + "/slot.json (or the layout without the network-layer segment)");
    }

    private PromptSlotSchema load(TemplateUri candidate, String scenarioCode, String language) {
        try {
            String payload = resourceLoader.loadText(PromptResourceKey.slotSchema(candidate, language, "slot.json"));
            return PromptResourceJsonParser.parse(payload, PromptSlotJsonSchema.class)
                    .toPromptSlotSchema(scenarioCode);
        } catch (JsonProcessingException exception) {
            throw new A2ATError("Failed to parse slot schema: " + scenarioCode, exception);
        }
    }

    /**
     * Returns the candidate template URIs a bare scenario code can address under one slot type: the
     * {@code network-layer} domain layout first, then the plain layout.
     */
    private static List<TemplateUri> bareCodeCandidates(String slotType, String scenarioCode) {
        return List.of(
                TemplateUri.of(slotType, StandardTemplates.NETWORK_LAYER_SEGMENT,
                        scenarioCode),
                TemplateUri.of(slotType, scenarioCode));
    }

    private static List<String> discoverSlotTypes() {
        Set<String> types = new LinkedHashSet<>(KNOWN_SLOT_TYPES);
        try {
            types.addAll(ClasspathResourceDirectories.list("prompt_resources/slots/"));
        } catch (Exception ignored) {
            // classpath enumeration is unavailable; fall back to the known types only
        }
        return List.copyOf(types);
    }
}
