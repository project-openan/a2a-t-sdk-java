# A2A-T Negotiation (Negotiation-T) API Reference

> Negotiation volume of [A2A-T-SDK-API-Reference.md](../zh/A2A-T-SDK-API-Reference.md), covering the negotiation extension API surface for per-capability lookup.

---

## 1. Capability Overview

The negotiation extension (Negotiation-T) lets client and server align before task execution: information gathering, target alignment, feasibility confirmation, and negotiation termination. The SDK exposes the capability in three layers:

| Layer | Responsibility | Entry points |
|---|---|---|
| Runtime state machine | turn/round advancement, state validation, context persistence | `startNegotiation` / `receiveNegotiation` / `continueNegotiation` |
| Content generation | renders negotiation messages from typed content (fromData deterministic, fromText with one LLM extraction step) | `generateNegotiation*PromptFromData/FromText` |
| Validation & parameter extraction | rule gate + semantic validation + parameter merge, extracting structured parameters from rendered messages | `validate*PromptAndDataFilling` |

All three layers are exposed through the `A2ATClient` / `A2ATServer` facades with **fully symmetric method signatures** (see [NegotiationV3ApiSurfaceTest](../../a2a-t-sample/src/test/java/net/openan/a2at/sdk/sample/api/NegotiationV3ApiSurfaceTest.java)). The differences are role binding only, plus the server-side prompt compliance check.

### 1.1 Maven coordinates

```xml
<dependency>
    <groupId>net.openan.a2a-t.sdk</groupId>
    <artifactId>a2a-t-client</artifactId>
    <version>1.0.0</version>
</dependency>
```

Use `a2a-t-server` instead on the server side. The negotiation module arrives as a transitive dependency of either; no separate dependency is needed.

### 1.2 Negotiation-related `.env` configuration

| Key | Default | Description |
|---|---|---|
| A2AT_LANGUAGE | en-US | Message language, `zh-CN` or `en-US` |
| A2AT_PROMPT_SOURCE_TYPE | local_file | `classpath` uses packaged resources, `local_file` a local directory |
| A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR | (packaged dir) | Local resource root; relative paths resolve against the .env directory |
| A2AT_LLM_PROVIDER | (required) | Only `openai` is supported (DeepSeek, Azure and other OpenAI-compatible endpoints) |
| A2AT_LLM_MODEL | (required) | Model name |
| A2AT_LLM_API_KEY | (required) | API key |
| A2AT_LLM_BASE_URL | (optional) | Base URL of the OpenAI-compatible API |
| A2AT_NEGOTIATION_STATE_STORE_TYPE | in_memory | Negotiation state store type; currently only `in_memory` |

---

## 2. Extension URIs

Defined in [ExtensionUriConstants](../../a2a-t-core/src/main/java/net/openan/a2at/sdk/core/model/ExtensionUriConstants.java):

| Purpose | URI |
|---|---|
| Negotiation-T (canonical, for sending) | `https://projects.tmforum.org/a2aproject/telecommunication/extensions/Negotiation-T/v1` |
| Negotiation-T (NL legacy, accepted on receive) | `https://projects.tmforum.org/a2aproject/telecommunication/extensions/Negotiation-T/NL/v1` |

The SDK sends new messages with the canonical URI as `MetadataContent.extensionUri()`; the receive-side payload mapper also recognizes the NL alias for backward compatibility.

---

## 3. Runtime State Machine API

Both client and server expose the three methods driving the start/receive/continue flow. They operate on the `types.model`-layer `NegotiationContext` (not the content-layer context; the two are not interchangeable, see §7.1).

### 3.1 startNegotiation — start a negotiation

```java
Map<String, Object> startNegotiation(
    NegotiationType type,
    String contentText,
    Map<String, Object> facts)
```

- `type`: negotiation type (`INFORMATION` / `TARGET` / `FEASIBILITY`)
- `contentText`: negotiation message text
- `facts`: structured facts to attach; when non-empty they land in the top-level `facts` of the payload

Generates a UUID as `negotiationId`, creates a round=1, status=`in-progress` context, stores it, and returns the initial round payload:

```json
{
  "https://.../Negotiation-T/NL/v1": {
    "message": "negotiation message text",
    "negotiationType": "target",
    "negotiationId": "uuid",
    "round": 1,
    "status": "in-progress",
    "extra": {}
  },
  "facts": {}
}
```

> Starting performs no state validation and throws no state exceptions.

### 3.2 receiveNegotiation — receive a peer negotiation message

```java
Map<String, Object> receiveNegotiation(
    String message,
    Map<String, Object> context)
```

- `message`: the received negotiation message text
- `context`: the transport context payload sent by the peer (the map under the extension URI)

First deserializes the context with `NegotiationPayloadMapper.contextFromMap`, then runs state validation in a fixed order:

1. Rollback: reject when incoming round < stored round
2. Terminal reopen: reject when stored is terminal and incoming is in-progress
3. Terminal change: reject when stored is terminal and incoming status differs
4. Max rounds: when in-progress and round >= 8, return a "max rounds reached, please reject" advisory (store not updated)
5. Round jump: reject when incoming round > stored round + 1

After passing, dispatches to the handler by type and returns:

```json
{
  "needResponse": true,
  "facts": {},
  "message": "processed message",
  "context": {
    "negotiationType": "...",
    "negotiationId": "...",
    "round": 1,
    "status": "in-progress",
    "extra": {}
  }
}
```

### 3.3 continueNegotiation — advance a negotiation locally

```java
Map<String, Object> continueNegotiation(
    NegotiationContext context,
    NegotiationStatus status,
    String contentText)
```

- `context`: current negotiation context snapshot (must match local storage exactly, preventing forked branches)
- `status`: next-round status (`IN_PROGRESS` / `AGREED` / `REJECTED`)
- `contentText`: next-round message content

Validation: stored record exists, context round equals stored round, stored status is still in-progress. On success round is incremented, the new record is stored, and the payload is returned. **facts is always an empty map.** Only in-progress → agreed / rejected transitions are allowed; terminal states are final.

### 3.4 State machine enums

```java
enum NegotiationType   { INFORMATION, TARGET, FEASIBILITY }
enum NegotiationStatus { IN_PROGRESS, AGREED, REJECTED }
enum NegotiationRole   { CLIENT, SERVER }
```

In payloads, type/status use the lowercased enum name with underscores as hyphens (e.g. `in-progress`); deserialization converts hyphens back to underscores and uppercases.

---

## 4. Content Generation API

Both facades proxy `NegotiationContentService`, which drives `NegotiationGenerationOrchestrator`. Every pipeline has a `fromData` (deterministic; negotiation message generation makes no LLM call) and a `fromText` (one LLM structured-extraction step) variant, covering the propose / accept / reject / abort phases. The eight methods have byte-identical signatures on both facades.

> Note: "no LLM" applies to the **negotiation message rendering step only**. The SDK configuration (`A2AT_LLM_API_KEY` etc.) is always mandatory; other steps (Task-T slot extraction, semantic validation) still call the LLM.

### 4.1 fromData generation (deterministic; no LLM for message rendering)

```java
MetadataContent generateNegotiationProposePromptFromData(NegotiationProposeData data, TemplateUri templateUri);
MetadataContent generateNegotiationAcceptPromptFromData (NegotiationEndingData  data, TemplateUri templateUri);
MetadataContent generateNegotiationRejectPromptFromData (NegotiationEndingData  data, TemplateUri templateUri);
MetadataContent generateNegotiationAbortPromptFromData  (NegotiationAbortData   data, TemplateUri templateUri);
```

Flow: parse `templateUri` → load template → `GeneratorRegistry` exact-type dispatch → generator renders → return `MetadataContent`.

- accept requires content conclusion `ACCEPT`, reject requires `REJECT`; otherwise a `NegotiationContentException` is thrown
- abort is negotiation-type independent, rendered by the single common abort template, carrying only the termination reason (see §4.4)

### 4.2 fromText generation (one LLM extraction step)

```java
MetadataContent generateNegotiationProposePromptFromText(String text, NegotiationContext context, TemplateUri templateUri);
MetadataContent generateNegotiationAcceptPromptFromText (String text, NegotiationContext context, TemplateUri templateUri);
MetadataContent generateNegotiationRejectPromptFromText (String text, NegotiationContext context, TemplateUri templateUri);
MetadataContent generateNegotiationAbortPromptFromText  (String text, NegotiationContext context, TemplateUri templateUri);
```

The `NegotiationContext` here is the content-generation one (`net.openan.a2at.sdk.core.model.NegotiationContext`, see §7.1); it is injected into the rendered message and never passes through the LLM.

Flow: parse `templateUri` → load template (before the LLM call) → `DefaultNegotiationContentExtractor` one-step LLM structured extraction → `mapContent` to the typed record → render → return `MetadataContent`.

### 4.3 Return value MetadataContent

```java
record MetadataContent(
    String templateUri,    // template URI
    String promptText,     // rendered message text
    String extensionUri    // TMF extension URI (canonical Negotiation-T URI)
)
```

Builds the A2A-T metadata map directly:

```java
MetadataContent mc = client.generateNegotiationProposePromptFromData(data, StandardTemplates.TARGET_NEGOTIATION_PROPOSE);
Map<String, String> metadata = mc.buildMetadataContent();
// { extensionUri -> promptText, "template_uri" -> templateUri }
```

### 4.4 Input data models

**Propose phase**

```java
record NegotiationProposeData(
    NegotiationContext context,           // content-generation context
    NegotiationProposeContent content     // typed content
)
```

**Ending phase (accept / reject)**

```java
record NegotiationEndingData(
    NegotiationContext context,
    NegotiationEndingContent content
)
```

**Abort phase (negotiation termination)**

```java
record NegotiationAbortData(
    NegotiationContext context,
    NegotiationAbortContent content       // carries the termination reason only
)
```

### 4.5 Typed content models

**Propose-phase content** (sealed interface `NegotiationProposeContent`)

```java
// information negotiation: list of missing information items
record InformationProposeContent(
    List<NegotiationItem> items,   // required, at least one
    String relationship            // optional, relationship among the items
)

// target negotiation: description + intent understanding + alignment + clarification + confirm request
record TargetProposeContent(
    String targetNegotiationDescription,               // required
    List<NegotiationItem> intentUnderstanding,          // optional (first round; null later)
    List<NegotiationItem> alignmentAndClarification,    // optional (later rounds; null on round one)
    List<NegotiationItem> requestForClarification,      // optional
    String targetConfirmRequest                         // optional, text requesting peer confirmation
)

// feasibility negotiation: description + action + evaluation content / infeasibility details + confirm request
record FeasibilityProposeContent(
    String feasibilityNegotiationDescription,               // required
    NegotiationAction action,                                // required, selects the conditional section
    List<NegotiationItem> contentsToEvaluate,                // required when action=REQUEST_FEASIBILITY_EVALUATION
    List<NegotiationItem> infeasibilityDetailsAndProposal,   // required when action=PROPOSE_ALTERNATIVE_ON_FAILURE
    String feasibilityConfirmRequest                         // optional, text requesting peer confirmation
)
```

**Ending-phase content** (sealed interface `NegotiationEndingContent`)

```java
record InformationEndingContent(NegotiationConclusion conclusion, List<NegotiationItem> items)

record TargetEndingContent(NegotiationConclusion conclusion,
                           String confirmedIntent,   // required on accept
                           String failureReason)     // required on reject

record FeasibilityEndingContent(NegotiationConclusion conclusion, String feasibilitySummary)
```

**Abort-phase content**

```java
record NegotiationAbortContent(String terminationReason)   // required, negotiation termination reason
```

### 4.6 Auxiliary types

```java
record NegotiationItem(String name, String value)  // name non-null, value nullable

enum NegotiationConclusion { ACCEPT, REJECT, ABORT }
enum NegotiationAction {
    REQUEST_FEASIBILITY_EVALUATION,
    PROPOSE_ALTERNATIVE_ON_FAILURE
}
```

Accept and reject share the `accept-reject` template, distinguished by the `NegotiationConclusion` value; ABORT uses the separate common abort template (see §5).

---

## 5. Template URIs

Typed templates follow `Negotiation-T/{type-segment}/{phase-segment}/v1`; abort is type independent and uses the common template.

| Scenario | URI |
|---|---|
| Information propose | `Negotiation-T/information-negotiation/propose/v1` |
| Information accept/reject | `Negotiation-T/information-negotiation/accept-reject/v1` |
| Target propose | `Negotiation-T/target-negotiation/propose/v1` |
| Target accept/reject | `Negotiation-T/target-negotiation/accept-reject/v1` |
| Feasibility propose | `Negotiation-T/feasibility-negotiation/propose/v1` |
| Feasibility accept/reject | `Negotiation-T/feasibility-negotiation/accept-reject/v1` |
| Negotiation termination (common) | `Negotiation-T/common/abort/v1` |

The template URI layer collapses four performatives into three segments: `ACCEPT` and `REJECT` share the `accept-reject` segment (distinguished by the conclusion value), while `ABORT` is carried by the type-independent common template. Typed templates map to `StandardTemplates` constants (e.g. `INFORMATION_NEGOTIATION_PROPOSE`); abort maps to `StandardTemplates.NEGOTIATION_ABORT`.

URI parsing rules (`NegotiationReference.parse`): exactly 4 slash-separated segments; segment 1 must be `Negotiation-T`; segment 2 is `{type}-negotiation` or `common` (common is abort only); segment 3 must be `propose` or `accept-reject`, and `abort` for the abort template; segment 4 must be `v1`.

### 5.1 Template lookup API

```java
List<PromptTemplate>       getNegotiationPrompts();              // all negotiation templates of the current language, fixed order
Optional<PromptTemplate>   getNegotiationPrompt(String uri);    // single lookup by URI
List<PromptTemplate>       getPrompts();                         // across all extensions
Optional<PromptTemplate>   getPrompt(String uri);                // by URI across extensions
```

These methods never throw; missing entries yield an empty list or Optional.empty.

---

## 6. Validation & Parameter Extraction API

### 6.1 Method signatures

```java
FilledParamData validateProposePromptAndDataFilling(String prompt, NegotiationContext context, Map<String, Object> schema, TemplateUri templateUri);
FilledParamData validateAcceptPromptAndDataFilling (String prompt, NegotiationContext context, Map<String, Object> schema, TemplateUri templateUri);
FilledParamData validateRejectPromptAndDataFilling (String prompt, NegotiationContext context, Map<String, Object> schema, TemplateUri templateUri);
FilledParamData validateAbortPromptAndDataFilling  (String prompt, NegotiationContext context, Map<String, Object> schema, TemplateUri templateUri);
```

- `prompt`: the rendered negotiation message text
- `context`: the content-generation `NegotiationContext` (see §7.1); participates in the rule gate and is merged into the returned data
- `schema`: caller-provided JSON schema describing the parameters to extract
- `templateUri`: the expected negotiation type and phase (phase must match the method; the abort method uses the common abort template)

Returns:

```java
record FilledParamData(Map<String, Object> data)
```

### 6.2 Validation pipeline

1. **Rule gate** (`DefaultNegotiationComplianceChecker`, deterministic, no LLM)
   - splits sections on `##` and detects the negotiation context section (via the `Vocabulary` `section.context` key)
   - validates `id` as a UUID (8-4-4-4-12 hex)
   - validates `round` and `maxRounds` as positive integers with round <= maxRounds
   - performs no type inference, conclusion-value checks, or conditional-section mutual exclusion
   - on success returns the context parameters `{id, round, maxRounds}`
2. **LLM semantic validation** (`DefaultNegotiationSemanticValidator`, retriable)
   - one structured LLM call producing the verdict (pass/reject), implied type, semantic error list, and the parameters extracted per the caller schema
   - when the verdict is true, implied type must match the reference type
3. **Parameter merge** (deterministic)
   - context parameters (id, round, maxRounds) are written first, then the LLM-extracted parameters
   - on collision the context parameters win and a warning is logged

---

## 7. The Two Contexts & Payload Utilities

### 7.1 The two NegotiationContext types (not interchangeable)

| Class | Purpose | Fields |
|---|---|---|
| `net.openan.a2at.sdk.core.model.NegotiationContext` | content generation/validation | `id, round, maxRounds, performative` |
| `net.openan.a2at.sdk.negotiation.types.model.NegotiationContext` | state machine | `negotiationType, negotiationId, round, status` |

State-machine methods (start/receive/continue) use the `types.model` layer; content generation/validation uses the `core.model` layer. The content-generation context is a record with `of(id, round, performative)`, `nextRound()`, `isExhausted()`, and `DEFAULT_MAX_ROUNDS = 5`; `performative` is mandatory and expresses which kind of message the context travels with (`PROPOSE` puts a proposal on the table, `ACCEPT` / `REJECT` respond to a proposal, `ABORT` terminates the negotiation) — the generation API honors it when rendering the outbound message.

### 7.2 NegotiationPayloadMapper

```java
// deserialize a payload map into a state-machine context
NegotiationContext ctx = NegotiationPayloadMapper.contextFromMap(contextMap);

// serialize a state-machine context into a payload map (message/type/id/round/status/extra)
Map<String, Object> payload = NegotiationPayloadMapper.contextPayload(ctx);

// extract the negotiation context map from a full payload (canonical URI and NL alias both accepted)
Map<String, Object> contextMap = NegotiationPayloadMapper.extractContextMap(payload);

// build a full negotiation payload (extension URI as top-level key; facts at top level when non-empty)
Map<String, Object> payload = NegotiationPayloadMapper.payload(context, contentText, facts);
```

---

## 8. Exceptions & Error Codes

### 8.1 Exception taxonomy

| Exception | Nature | Carries |
|---|---|---|
| `NegotiationContentException` | programming error | message + field (path of the offending field) |
| `NegotiationGenerationException` | generation failure | code + message + cause |
| `NegotiationParamExtractionException` | validation failure | code + message + errors (per-slot) |
| `NegotiationStateException` | state machine error | message (runtime state machine layer) |

### 8.2 Error codes

| Code | Meaning | Retriable |
|---|---|---|
| `template_not_found` | template/prompt resource missing | no |
| `negotiation_content_extract_failed` | LLM extraction response unparseable | yes |
| `negotiation_llm_infrastructure_error` | LLM infrastructure failure | yes |
| `negotiation_slot_missing` | required field missing | no |
| `negotiation_invalid_input` | input contradicts phase/action | no |
| `negotiation_rule_violation` | rule gate rejection | no |
| `negotiation_semantic_rejected` | semantic validation rejection | no |
| `input_text_too_long` | fromText input exceeds the configured maximum length | no |

### 8.3 Retry policy

`NegotiationGenerationOrchestrator.withRetry` retries LLM steps uniformly: only `negotiation_content_extract_failed` and `negotiation_llm_infrastructure_error` are retriable; every other code fails fast. After exhaustion the original error is rethrown with its code intact; the default `maxAttempts` comes from the LLM config.

---

## 9. State Storage

```java
public interface NegotiationStore {
    NegotiationRecord get(String negotiationId);
    void save(NegotiationRecord record);   // negotiationId must be non-null
    void delete(String negotiationId);
    boolean cleanupExpired();
}
```

Only `InMemoryNegotiationStore` exists today; it provides no persistence. Inject a custom implementation through `NegotiationHandler.Builder.store()`.

---

## 10. Usage Examples

### 10.1 Start a negotiation and generate a propose message

```java
A2ATClient client = new A2ATClient(Path.of(".env"));

// 1. render the structured negotiation message with the content engine
NegotiationContext contentCtx = new NegotiationContext(
    UUID.randomUUID().toString(), 1,
    NegotiationContext.DEFAULT_MAX_ROUNDS, NegotiationPerformative.PROPOSE);
TargetProposeContent content = new TargetProposeContent(
    "Cross-city private-line outage diagnosis",
    List.of(new NegotiationItem("intent", "Restore the city1-city2 SPN private line")),
    null,
    List.of(new NegotiationItem("city1_omc", "Provide the city1 OMC alarm details")),
    null);
MetadataContent mc = client.generateNegotiationProposePromptFromData(
    new NegotiationProposeData(contentCtx, content),
    StandardTemplates.TARGET_NEGOTIATION_PROPOSE);

// 2. initialize the state machine with the rendered message text
Map<String, Object> payload = client.startNegotiation(
    NegotiationType.TARGET, mc.promptText(), Map.of("agent", "city1-agent"));

// 3. merge the metadata
Map<String, String> metadata = mc.buildMetadataContent();
```

### 10.2 Receive and validate a negotiation message

```java
Map<String, Object> receiveResult = client.receiveNegotiation(message, contextMap);

if (Boolean.TRUE.equals(receiveResult.get("needResponse"))) {
    Map<String, Object> schema = Map.of(
        "type", "object",
        "properties", Map.of(
            "city1_omc_alarm", Map.of("type", "string"),
            "optical_power", Map.of("type", "number")));
    FilledParamData params = client.validateProposePromptAndDataFilling(
        message, contentCtx, schema, StandardTemplates.TARGET_NEGOTIATION_PROPOSE);
    // params.data() carries id, round, maxRounds + the LLM-extracted parameters
}
```

### 10.3 Advance the negotiation to agreement

```java
net.openan.a2at.sdk.negotiation.types.model.NegotiationContext ctx =
    NegotiationPayloadMapper.contextFromMap(
        (Map<String, Object>) receiveResult.get("context"));
Map<String, Object> payload = client.continueNegotiation(
    ctx, NegotiationStatus.AGREED, "Params confirmed, starting execution");
```

### 10.4 Generate a negotiation message from free text

```java
NegotiationContext contentCtx = NegotiationContext.of("session-123", 1, NegotiationPerformative.PROPOSE);
MetadataContent mc = client.generateNegotiationProposePromptFromText(
    "City1-city2 SPN private line outage; city1 OMC reports port down, optical power -28dBm",
    contentCtx,
    StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE);
// the LLM extracts InformationProposeContent (items + relationship) and renders it
```

### 10.5 Generate ending messages

```java
// accept
MetadataContent acceptMc = client.generateNegotiationAcceptPromptFromData(
    new NegotiationEndingData(contentCtx,
        new TargetEndingContent(NegotiationConclusion.ACCEPT, "Confirmed intent: restore the line", null)),
    StandardTemplates.TARGET_NEGOTIATION_ACCEPT_REJECT);

// reject
MetadataContent rejectMc = client.generateNegotiationRejectPromptFromData(
    new NegotiationEndingData(contentCtx,
        new TargetEndingContent(NegotiationConclusion.REJECT, null, "Insufficient resources")),
    StandardTemplates.TARGET_NEGOTIATION_ACCEPT_REJECT);

// terminate the negotiation
MetadataContent abortMc = client.generateNegotiationAbortPromptFromData(
    new NegotiationAbortData(contentCtx,
        new NegotiationAbortContent("Parties could not agree on the diagnosis scope; terminating")),
    StandardTemplates.NEGOTIATION_ABORT);
```

Runnable end-to-end samples: [a2a-t-sample negotiation samples](../../a2a-t-sample/README.zh-CN.md#协商negotiation样例).

---

## 11. Supported Scope & Limitations

| Capability | Status |
|---|---|
| Runtime state machine (start/receive/continue) | complete; all three types registered |
| Content generation (fromData/fromText) | complete; 8 generators covering propose/accept/reject/abort for all three types |
| Validation & parameter extraction | complete; propose/accept/reject/abort all supported |
| Information runtime handler | substantive logic (incl. compliance checker) |
| Target / Feasibility runtime handlers | echo stubs (content layer complete, runtime not wired) |
| State persistence | in-memory only |
| Language coverage | zh-CN and en-US |
