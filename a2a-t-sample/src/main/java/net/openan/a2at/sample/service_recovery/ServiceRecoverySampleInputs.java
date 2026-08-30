package net.openan.a2at.sample.service_recovery;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Loads the sample inputs and schemas from the classpath resources under {@code sample/service-recovery/}.
 *
 * <p>Four artifacts live as resources so they can be edited without touching Java code:
 *
 * <ul>
 *   <li>{@code client/input-with-text.txt} — the natural-language subscription request;
 *   <li>{@code client/input-with-data.json} — the structured subscription request consumed by
 *       {@code generateNotificationPromptFromDataWithSchema};
 *   <li>{@code client/schema.json} — the data schema guiding the structured extraction;
 *   <li>{@code server/schema.json} — the parameter schema handed to
 *       {@code A2ATServer.validateAndFillingNotificationData}.
 *
 * @since 2026-08
 */
public final class ServiceRecoverySampleInputs {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String INPUT_RESOURCE = "sample/service-recovery/client/input-with-text.txt";

    private static final String STRUCTURED_INPUT_RESOURCE = "sample/service-recovery/client/input-with-data.json";

    private static final String DATA_SCHEMA_RESOURCE = "sample/service-recovery/client/schema.json";

    private static final String VALIDATION_SCHEMA_RESOURCE = "sample/service-recovery/server/schema.json";

    /** Template URI verified by this sample (network-layer domain layout of the bundled template). */
    public static final String TEMPLATE_URI = "Notification-T/network-layer/service-recovery/v1";

    /** Number of notification reports each accepted subscription emits before the task completes. */
    public static final int NOTIFICATION_REPORT_COUNT = 5;

    /** Interval in seconds between two consecutive notification reports of one subscription. */
    public static final long NOTIFICATION_REPORT_INTERVAL_SECONDS = 5L;

    /** Server-side parameter name of the topic (English business identifier). */
    public static final String PARAM_TOPIC = "topic";

    /** Server-side parameter name of the subscription condition (English business identifier). */
    public static final String PARAM_SUBSCRIPTION_CONDITION = "subscriptionCondition";

    /** Server-side parameter name of the notification data format (English business identifier). */
    public static final String PARAM_NOTIFICATION_DATA_FORMAT = "notificationDataFormat";

    /** AgentCard query name of the sample server. */
    public static final String AGENT_NAME = "SPN Service Recovery Agent";

    /** AgentCard query organization of the sample server. */
    public static final String AGENT_ORGANIZATION = "Huawei";

    private ServiceRecoverySampleInputs() {}

    /**
     * Loads the natural-language subscription request from {@code sample/service-recovery/client/input-with-text.txt}.
     *
     * @return the natural-language input text
     */
    public static String naturalLanguageInput() {
        return readText(INPUT_RESOURCE);
    }

    /**
     * Loads the structured subscription request consumed by {@code generateNotificationPromptFromDataWithSchema} from
     * {@code sample/service-recovery/client/input-with-data.json}.
     *
     * <p>The keys follow the English business-style parameter names of the server-side validation schema
     * ({@code topic}/{@code subscriptionCondition}/{@code notificationDataFormat}), demonstrating that the
     * schema-guided generation path maps caller-provided structured data onto the template slots.
     *
     * @return structured notification input as a string-to-object map
     */
    public static Map<String, Object> structuredInput() {
        return readJson(STRUCTURED_INPUT_RESOURCE);
    }

    /**
     * Loads the data schema guiding the structured extraction of {@code generateNotificationPromptFromDataWithSchema}
     * from {@code sample/service-recovery/client/schema.json}.
     *
     * <p>The schema describes the two input-with-data fields (type, description, required) and is handed to the SDK
     * verbatim; the SDK prints its entries into the LLM slot-extraction prompt.
     *
     * @return data schema map for schema-guided extraction
     */
    public static Map<String, Object> dataSchema() {
        return readJson(DATA_SCHEMA_RESOURCE);
    }

    /**
     * Loads the parameter schema handed to {@code A2ATServer.validateAndFillingNotificationData} from
     * {@code sample/service-recovery/server/schema.json}.
     *
     * <p>The schema mirrors the slot.json format ({@code $schema}/{@code additionalProperties}/
     * {@code description}/{@code examples}/{@code x-a2at-value-constraint}), but the property keys are English
     * business-style identifiers ({@code topic}/{@code notificationDataFormat}) instead of the zh-CN slot names,
     * demonstrating that the validation API fills parameters per the caller-provided schema rather than echoing
     * template slots.
     *
     * @return parameter JSON schema for validation and parameter extraction
     */
    public static Map<String, Object> validationParamSchema() {
        return readJson(VALIDATION_SCHEMA_RESOURCE);
    }

    private static String readText(String resourcePath) {
        try (InputStream inputStream =
                ServiceRecoverySampleInputs.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalStateException("Sample resource not found: " + resourcePath);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read sample resource: " + resourcePath, exception);
        }
    }

    private static Map<String, Object> readJson(String resourcePath) {
        try (InputStream inputStream =
                ServiceRecoverySampleInputs.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalStateException("Sample resource not found: " + resourcePath);
            }
            return OBJECT_MAPPER.readValue(inputStream, new TypeReference<Map<String, Object>>() {});
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read sample resource: " + resourcePath, exception);
        }
    }
}
