package net.openan.a2at.sample.negotiation.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.openan.a2at.sample.negotiation.shared.DemoConstants;
import net.openan.a2at.sample.negotiation.shared.NegotiationMessage;
import net.openan.a2at.sample.negotiation.shared.NegotiationStrategy;
import net.openan.a2at.sample.negotiation.shared.ScenarioData;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.SlotValidationError;
import net.openan.a2at.sdk.core.validation.ContentValidationException;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.NegotiationPerformative;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;
import net.openan.a2at.sdk.server.A2ATServer;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.DataPart;
import org.a2aproject.sdk.spec.Message;

/**
 * Server-side agent executor that drives the 4-message negotiation flow for the SPN private-line-complaint diagnosis.
 *
 * <p>Receives one A2A request, extracts the Task-T prompt from {@code Message.metadata}, validates it through the SDK
 * ({@link A2ATServer#validateTaskPromptAndDataFilling}) to discover which parameters are missing, and reacts:
 *
 * <ul>
 *   <li>message 1 (params missing): dynamically builds the missing-items list from the validation result and generates
 *       a Negotiation-T information-propose prompt via the {@link NegotiationStrategy} (fromData or fromText), emits it
 *       as a reply Message, and transitions to {@code INPUT_REQUIRED};
 *   <li>message 3 (params filled): accepts the negotiation and emits a dynamically generated diagnosis result as a
 *       Task-T artifact, then transitions to {@code COMPLETED}.
 * </ul>
 *
 * Nothing is hardcoded — the missing items, the accept content and the diagnosis are all derived from the Task-T prompt
 * via SDK APIs, so the demo supports arbitrary Task-T inputs.
 *
 * @since 2026-08
 */
public final class NegotiationAgentExecutor implements AgentExecutor {

    /** Parameter keys the SDK injects as negotiation-context (not business slots). */
    private static final List<String> CONTEXT_PARAM_KEYS = List.of("id", "round", "maxRounds");

    private final A2ATServer server;
    private final NegotiationStrategy strategy;
    private final Consumer<String> logSink;

    /**
     * Creates the executor.
     *
     * @param server server facade for validation and negotiation generation
     * @param strategy negotiation-message generation strategy (fromData or fromText)
     * @param logSink log output sink
     */
    public NegotiationAgentExecutor(A2ATServer server, NegotiationStrategy strategy, Consumer<String> logSink) {
        this.server = server;
        this.strategy = strategy;
        this.logSink = logSink;
    }

    @Override
    public void execute(RequestContext requestContext, AgentEmitter agentEmitter) throws A2AError {
        try {
            Message inbound = requestContext.getMessage();
            Map<String, Object> metadata =
                    inbound == null || inbound.metadata() == null ? Map.of() : inbound.metadata();
            String taskPrompt = NegotiationMessage.extractPrompt(metadata, DemoConstants.TASK_T_URI);
            Map<String, Object> negotiationContext = NegotiationMessage.extractContext(metadata);
            emit("[server] received task prompt, hasNegCtx=" + !negotiationContext.isEmpty());

            // validate the Task-T prompt through the SDK to extract parameters;
            // if semantic validation rejects (params missing), the exception carries the slot errors
            FilledParamData params;
            List<NegotiationItem> missingItems;
            try {
                params = server.validateTaskPromptAndDataFilling(
                        taskPrompt, ScenarioData.taskSchema(), DemoConstants.TASK_TEMPLATE);
                emit("[server] extracted params: " + params.data());
                missingItems = findMissingItems(params);
            } catch (ContentValidationException e) {
                emit("[server] validation rejected, extracting missing items from errors");
                params = null;
                missingItems = missingItemsFromErrors(e.errors());
            }

            if (missingItems.isEmpty()) {
                handleFilledParams(requestContext, agentEmitter, negotiationContext, params);
            } else {
                handleMissingParams(requestContext, agentEmitter, negotiationContext, missingItems);
            }
        } catch (RuntimeException exception) {
            emit("[server] executor error: " + exception);
            throw exception;
        }
    }

    @Override
    public void cancel(RequestContext requestContext, AgentEmitter agentEmitter) throws A2AError {
        agentEmitter.cancel();
    }

    // -- message 2: params missing -> Negotiation-T information propose -> INPUT_REQUIRED --
    private void handleMissingParams(
            RequestContext ctx,
            AgentEmitter emitter,
            Map<String, Object> negotiationContext,
            List<NegotiationItem> missingItems)
            throws A2AError {
        emit("[server] === message 2: params missing -> negotiation request ===");
        emit("[server] missing items: " + missingItems);

        // the negotiation context flows back to the client via the reply metadata
        Map<String, Object> replyContext = negotiationContext;

        // dynamically generate the Negotiation-T information-propose prompt via the strategy
        String relationship = missingItems.size() > 1
                ? ScenarioData.negotiationPhrasing().get("propose_relationship")
                : null;
        MetadataContent negotiationPrompt = strategy.generatePropose(
                server,
                new NegotiationContext(
                        UUID.randomUUID().toString(), 1, NegotiationContext.DEFAULT_MAX_ROUNDS, NegotiationPerformative.PROPOSE),
                missingItems,
                relationship,
                DemoConstants.NEGOTIATION_PROPOSE);
        emit("[server] negotiation request rendered");

        Message reply = buildReplyMessage(ctx, negotiationPrompt, replyContext, DemoConstants.NEGOTIATION_T_URI);
        emitter.submit(reply);
        emitter.requiresInput();
        emit("[server] -> INPUT_REQUIRED");
    }

    // -- message 4: params filled -> accept negotiation -> diagnosis result -> COMPLETED --
    private void handleFilledParams(
            RequestContext ctx, AgentEmitter emitter, Map<String, Object> negotiationContext, FilledParamData params)
            throws A2AError {
        emit("[server] === message 4: params filled -> diagnosis result ===");

        // build the filled-items list from the validated params (not hardcoded)
        List<NegotiationItem> filledItems = extractFilledItems(params);

        // dynamically generate the Negotiation-T accept via the strategy
        MetadataContent acceptPrompt = strategy.generateAcceptServer(
                server,
                new NegotiationContext(
                        UUID.randomUUID().toString(), 1, NegotiationContext.DEFAULT_MAX_ROUNDS, NegotiationPerformative.ACCEPT),
                filledItems,
                DemoConstants.NEGOTIATION_ACCEPT);
        emit("[server] negotiation accept rendered");

        // dynamically generate the diagnosis from the extracted params (not hardcoded)
        String diagnosis = DiagnosisService.diagnose(params);
        emitter.addArtifact(
                List.<org.a2aproject.sdk.spec.Part<?>>of(new DataPart(Map.of(DemoConstants.TASK_T_URI, diagnosis))),
                "faultManagement.Diagnosis",
                "SPN private-line diagnosis result",
                Map.of(DemoConstants.TEMPLATE_URI_KEY, DemoConstants.TASK_TEMPLATE),
                false,
                true);
        emit("[server] diagnosis artifact emitted");
        emitter.complete();
        emit("[server] -> COMPLETED");
    }

    /**
     * Finds parameters that are missing (null or blank) from the validated Task-T params. Skips SDK context parameters
     * (id, round, maxRounds).
     */
    private static List<NegotiationItem> findMissingItems(FilledParamData params) {
        List<NegotiationItem> missing = new ArrayList<>();
        if (params == null || params.data() == null) {
            return missing;
        }
        for (Map.Entry<String, Object> entry : params.data().entrySet()) {
            String key = entry.getKey();
            if (CONTEXT_PARAM_KEYS.contains(key)) {
                continue;
            }
            Object value = entry.getValue();
            if (isMissing(value)) {
                missing.add(missingItem(key));
            }
        }
        return missing;
    }

    /**
     * Builds the missing-items list from the semantic validation errors. Each slot error becomes a NegotiationItem
     * asking the counterpart to provide that slot.
     */
    private static List<NegotiationItem> missingItemsFromErrors(List<SlotValidationError> errors) {
        List<NegotiationItem> missing = new ArrayList<>();
        if (errors == null) {
            return missing;
        }
        for (SlotValidationError error : errors) {
            String slotName = error.slotName();
            if (slotName != null && !slotName.isBlank()) {
                missing.add(missingItem(slotName));
            }
        }
        return missing;
    }

    /**
     * Builds one missing-item entry from the phrasing template in the scenario configuration
     * ({@code missing_item_hint}, {@code {slot}} and {@code {description}} placeholders), falling back to the bare slot
     * name when no template is configured.
     */
    private static NegotiationItem missingItem(String slotName) {
        String template = ScenarioData.negotiationPhrasing().getOrDefault("missing_item_hint", "{slot}");
        String description = slotDescription(slotName);
        String hint =
                template.replace("{slot}", slotName).replace("{description}", description == null ? "" : description);
        return new NegotiationItem(slotName, hint);
    }

    /** Looks up the description of one slot in the scenario Task-T schema. */
    private static String slotDescription(String slotName) {
        Object properties = ScenarioData.taskSchema().get("properties");
        if (properties instanceof Map<?, ?> propertyMap && propertyMap.get(slotName) instanceof Map<?, ?> slot) {
            Object description = slot.get("description");
            if (description instanceof String text && !text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    /** Extracts the filled (non-null, non-blank) parameters as NegotiationItems. */
    private static List<NegotiationItem> extractFilledItems(FilledParamData params) {
        List<NegotiationItem> items = new ArrayList<>();
        if (params == null || params.data() == null) {
            return items;
        }
        for (Map.Entry<String, Object> entry : params.data().entrySet()) {
            String key = entry.getKey();
            if (CONTEXT_PARAM_KEYS.contains(key)) {
                continue;
            }
            Object value = entry.getValue();
            if (!isMissing(value)) {
                items.add(new NegotiationItem(key, String.valueOf(value)));
            }
        }
        return items;
    }

    private static boolean isMissing(Object value) {
        return value == null || (value instanceof String s && s.isBlank());
    }

    private static Message buildReplyMessage(
            RequestContext ctx, MetadataContent content, Map<String, Object> contextMap, String extensionUri) {
        Map<String, Object> replyMetadata =
                NegotiationMessage.buildMetadata(extensionUri, content.promptText(), content.templateUri(), contextMap);
        return Message.builder()
                .messageId(UUID.randomUUID().toString())
                .contextId(ctx.getContextId())
                .taskId(ctx.getTaskId())
                .role(Message.Role.ROLE_AGENT)
                .parts(new org.a2aproject.sdk.spec.TextPart(content.promptText()))
                .metadata(replyMetadata)
                .build();
    }

    private void emit(String message) {
        if (logSink != null) {
            logSink.accept(message);
        }
    }
}
