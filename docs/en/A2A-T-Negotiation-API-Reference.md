# A2A-T Negotiation (Negotiation-T) API Reference

> Negotiation volume of [A2A-T-SDK-API-Reference.md](../zh/A2A-T-SDK-API-Reference.md), covering the negotiation extension API surface for per-capability lookup.

---

## 1. Capability Overview

The negotiation extension (Negotiation-T) lets client and server align before task execution: information gathering, target alignment, feasibility confirmation, and negotiation termination. The SDK exposes content generation and validation as stateless operations:

| Layer | Responsibility | Entry points |
|---|---|---|
| Content generation | renders negotiation messages from typed content (fromData deterministic, fromText with one LLM extraction step) | `generateNegotiation*PromptFromData/FromText` |
| Validation & parameter extraction | rule gate + semantic validation + parameter merge, extracting structured parameters from rendered messages | `validate*PromptAndDataFilling` |

Both sets of negotiation operations are exposed through the `A2ATClient` / `A2ATServer` facades with **fully symmetric method signatures** (see [NegotiationV3ApiSurfaceTest](../../a2a-t-sample/src/test/java/net/openan/a2at/sdk/sample/api/NegotiationV3ApiSurfaceTest.java)). The server also exposes task-prompt compliance checking.

Use the content APIs with a caller-managed `core.model.NegotiationContext` and send the returned metadata through the A2A transport. The SDK does not persist negotiation sessions. The legacy state-machine APIs were retired in v1.1.0 and removed for v1.2.0 ([removal change](https://github.com/project-openan/a2a-t-sdk-java/pull/209)); they are outside this reference even when an older SDK still contains them.

### 1.1 Maven coordinates

```xml
<dependency>
    <groupId>net.openan.a2a-t.sdk</groupId>
    <artifactId>a2a-t-client</artifactId>
    <version>1.1.1</version>
</dependency>
```

Use `a2a-t-server` instead on the server side. The negotiation module arrives as a transitive dependency of either; no separate dependency is needed.

### 1.2 Negotiation-related `.env` configuration

| Key | Default | Description |
|---|---|---|
| A2AT_LANGUAGE | en-US | Message language, `zh-CN` or `en-US` |
| A2AT_PROMPT_SOURCE_TYPE | classpath | `classpath` uses packaged resources, `local_file` a local directory |
| A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR | (unset) | Resource root for `local_file`; relative paths resolve against the .env directory |
| A2AT_LLM_PROVIDER | openai | Built-in OpenAI-compatible provider; additional providers can be registered through `LLMClientFactory` |
| A2AT_LLM_MODEL | (required) | Model name |
| A2AT_LLM_API_KEY | (required) | API key |
| A2AT_LLM_BASE_URL | (optional) | Base URL of the OpenAI-compatible API |

---

## 2. Extension URIs

Defined in [ExtensionUriConstants](../../a2a-t-core/src/main/java/net/openan/a2at/sdk/core/model/ExtensionUriConstants.java):

| Purpose | URI |
|---|---|
| Negotiation-T (content metadata) | `https://projects.tmforum.org/a2aproject/telecommunication/extensions/Negotiation-T/v1` |

Content generation returns the canonical URI in `MetadataContent.extensionUri()`. `buildMetadataContent()` places the message text, template URI and session context in the outgoing metadata.

---

## 3. Content Generation API

Both facades proxy `NegotiationContentService`, which drives `NegotiationGenerationOrchestrator`. Every pipeline has a `fromData` (deterministic; negotiation message generation makes no LLM call) and a `fromText` (one LLM structured-extraction step) variant, covering the propose / accept / reject / abort phases. The eight methods have byte-identical signatures on both facades.

> Note: "no LLM" applies to the **negotiation message rendering step only**. The SDK configuration (`A2AT_LLM_API_KEY` etc.) is always mandatory; other steps (Task-T slot extraction, semantic validation) still call the LLM.

### 3.1 fromData generation (deterministic; no LLM for message rendering)

```java
MetadataContent generateNegotiationProposePromptFromData(NegotiationProposeData data, String templateUri);
MetadataContent generateNegotiationAcceptPromptFromData (NegotiationEndingData  data, String templateUri);
MetadataContent generateNegotiationRejectPromptFromData (NegotiationEndingData  data, String templateUri);
MetadataContent generateNegotiationAbortPromptFromData  (NegotiationAbortData   data, String templateUri);
```

Flow: parse `templateUri` → load template → `GeneratorRegistry` exact-type dispatch → generator renders → return `MetadataContent`.

- accept requires content conclusion `ACCEPT`, reject requires `REJECT`; a mismatched conclusion raises `NegotiationGenerationException` with code `negotiation.conclusion_mismatch`
- abort is negotiation-type independent, rendered by the single common abort template, carrying only the termination reason (see §3.4)

### 3.2 fromText generation (one LLM extraction step)

```java
MetadataContent generateNegotiationProposePromptFromText(String text, NegotiationContext context, String templateUri);
MetadataContent generateNegotiationAcceptPromptFromText (String text, NegotiationContext context, String templateUri);
MetadataContent generateNegotiationRejectPromptFromText (String text, NegotiationContext context, String templateUri);
MetadataContent generateNegotiationAbortPromptFromText  (String text, NegotiationContext context, String templateUri);
```

The `NegotiationContext` here is the content-generation one (`net.openan.a2at.sdk.core.model.NegotiationContext`, see §6); it is returned alongside the message in `MetadataContent.negotiationContext()` and the nested `negotiationContext` metadata, and is never inferred by the LLM.

Flow: parse `templateUri` → load template (before the LLM call) → `DefaultNegotiationContentExtractor` one-step LLM structured extraction → `mapContent` to the typed record → render → return `MetadataContent`.

### 3.3 Return value MetadataContent

```java
record MetadataContent(
    String templateUri,    // template URI
    String promptText,     // rendered message text
    String extensionUri,   // TMF extension URI (canonical Negotiation-T URI)
    NegotiationContext negotiationContext // null for non-negotiation messages
)
```

Builds the A2A-T metadata map directly:

```java
MetadataContent mc = client.generateNegotiationProposePromptFromData(data, StandardTemplates.TARGET_NEGOTIATION_PROPOSE_URI);
Map<String, Object> metadata = mc.buildMetadataContent();
// { extensionUri -> promptText, "templateUri" -> templateUri, "negotiationContext" -> contextMap }
```

### 3.4 Input data models

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

### 3.5 Typed content models

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

### 3.6 Auxiliary types

```java
record NegotiationItem(String name, String value)  // name non-null, value nullable

enum NegotiationConclusion { ACCEPT, REJECT, ABORT }
enum NegotiationAction {
    REQUEST_FEASIBILITY_EVALUATION,
    PROPOSE_ALTERNATIVE_ON_FAILURE
}
```

Accept and reject share the `accept-reject` template, distinguished by the `NegotiationConclusion` value; ABORT uses the separate common abort template (see [Template URIs](#4-template-uris)).

---

## 4. Template URIs

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

The template URI layer collapses four performatives into three segments: `ACCEPT` and `REJECT` share the `accept-reject` segment (distinguished by the conclusion value), while `ABORT` is carried by the type-independent common template. Typed templates map to `StandardTemplates` constants (e.g. `INFORMATION_NEGOTIATION_PROPOSE_URI` for facade calls); abort maps to `StandardTemplates.NEGOTIATION_ABORT_URI`.

URI parsing rules (`NegotiationReference.tryParse`): exactly 4 slash-separated segments; segment 1 must be `Negotiation-T`; segment 2 is `{type}-negotiation` or `common` (common is abort only); segment 3 must be `propose` or `accept-reject`, and `abort` for the abort template; segment 4 must be `v1`.

## 5. Validation & Parameter Extraction API

### 5.1 Method signatures

```java
FilledParamData validateProposePromptAndDataFilling(String prompt, NegotiationContext context, Map<String, Object> schema, String templateUri);
FilledParamData validateAcceptPromptAndDataFilling (String prompt, NegotiationContext context, Map<String, Object> schema, String templateUri);
FilledParamData validateRejectPromptAndDataFilling (String prompt, NegotiationContext context, Map<String, Object> schema, String templateUri);
FilledParamData validateAbortPromptAndDataFilling  (String prompt, NegotiationContext context, Map<String, Object> schema, String templateUri);
```

- `prompt`: the rendered negotiation message text
- `context`: the content-generation `NegotiationContext` (see §6); participates in the rule gate and is merged into the returned data
- `schema`: caller-provided JSON schema describing the parameters to extract
- `templateUri`: the expected negotiation type and phase (phase must match the method; the abort method uses the common abort template)

Returns:

```java
record FilledParamData(Map<String, Object> data)
```

### 5.2 Validation pipeline

1. **Rule gate** (`DefaultNegotiationComplianceChecker`, deterministic, no LLM)
   - reads the separately supplied `NegotiationContext` from message metadata, not a section of the rendered prompt
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

## 6. Session Context

The content API uses `net.openan.a2at.sdk.core.model.NegotiationContext`, with fields `id`, `round`, `maxRounds`, and `performative`. This immutable record provides `of(id, round, performative)`, `nextRound()`, `isExhausted()`, and `DEFAULT_MAX_ROUNDS = 5`.

The caller owns session storage and routing. Pass the context decoded from the incoming `negotiationContext` metadata to validation and response generation. Generation stamps its own performative on the outgoing context without modifying the caller's context. An accept/reject/abort response keeps the received ID and round; only a new proposal calls `nextRound()`. The SDK does not maintain a separate session ID, runtime state machine or persistence store.

---

## 7. Exceptions & Error Codes

### 7.1 Exception taxonomy

| Exception | Nature | Carries |
|---|---|---|
| `IllegalArgumentException` / `NullPointerException` | programming error | message identifying the invalid or missing argument |
| `NegotiationGenerationException` | generation failure | code + message + facts + cause |
| `NegotiationParamExtractionException` | validation failure | code + message + facts + errors (per-slot) |

### 7.2 Error codes

| Code | Meaning | Retriable |
|---|---|---|
| `template.not_found` | template/prompt resource missing | no |
| `negotiation.content_extract_failed` | LLM extraction response unparseable | yes |
| `llm.invocation_failed` | LLM infrastructure failure | yes |
| `llm.response_invalid` | LLM response has an invalid structure | yes |
| `negotiation.field_missing` | required field missing | no |
| `negotiation.invalid_input` | input contradicts phase/action | no |
| `negotiation.conclusion_mismatch` | accept/reject operation does not match the content conclusion | no |
| `negotiation.rule_violation` | rule gate rejection | no |
| `negotiation.semantic_rejected` | semantic validation rejection | no |
| `input.text_too_long` | fromText input exceeds the configured maximum length | no |

### 7.3 Retry policy

`NegotiationGenerationOrchestrator.withRetry` retries LLM steps uniformly: `negotiation.content_extract_failed`, `llm.invocation_failed`, and `llm.response_invalid` are retriable; every other code fails fast. After exhaustion the original error is rethrown with its code intact. `A2AT_LLM_MAX_ATTEMPTS` defaults to 3 and is bounded to 1–10.

---

## 8. Usage Examples

`NegotiationContext` denotes `net.openan.a2at.sdk.core.model.NegotiationContext`. The caller owns session persistence, transport and message routing; generation returns the outgoing context in metadata.

### 8.1 Generate a proposal with the content API

```java
A2ATClient client = new A2ATClient(Path.of(".env"));

NegotiationContext contentCtx = NegotiationContext.of(
    UUID.randomUUID().toString(), 1, NegotiationPerformative.PROPOSE);
TargetProposeContent content = new TargetProposeContent(
    "Cross-city private-line outage diagnosis",
    List.of(new NegotiationItem("intent", "Restore the city1-city2 SPN private line")),
    null,
    List.of(new NegotiationItem("city1_omc", "Provide the city1 OMC alarm details")),
    null);
MetadataContent mc = client.generateNegotiationProposePromptFromData(
    new NegotiationProposeData(contentCtx, content),
    StandardTemplates.TARGET_NEGOTIATION_PROPOSE_URI);
Map<String, Object> metadata = mc.buildMetadataContent();
```

Send `metadata` with the A2A message. It contains the rendered text under the canonical extension URI, `templateUri`, and `negotiationContext`. The caller tracks `mc.negotiationContext()` for this session. Use that same context when validating and responding to this proposal.

### 8.2 Validate a received proposal

Here `message`, `receivedContext`, and `receivedTemplateUri` are decoded from the **same incoming message's metadata** by the transport integration. `receivedContext` preserves its `id`, `round`, `maxRounds`, and `performative`; do not replace it with a new local session context. The example receives the target proposal from 10.1.

```java
Map<String, Object> schema = Map.of(
    "type", "object",
    "properties", Map.of(
        "city1_omc_alarm", Map.of("type", "string"),
        "optical_power", Map.of("type", "number")));
FilledParamData params = client.validateProposePromptAndDataFilling(
    message, receivedContext, schema, receivedTemplateUri);
// params.data() carries id, round, maxRounds + the extracted parameters
```

Whether to accept, reject, or request another proposal is an application decision after validation.

### 8.3 Accept a proposal at the received round

After the application accepts the target proposal, generate a response from its received context:

```java
MetadataContent accepted = client.generateNegotiationAcceptPromptFromData(
    new NegotiationEndingData(receivedContext,
        new TargetEndingContent(NegotiationConclusion.ACCEPT, "Confirmed intent: restore the line", null)),
    StandardTemplates.TARGET_NEGOTIATION_ACCEPT_REJECT_URI);
Map<String, Object> metadata = accepted.buildMetadataContent();
```

The response keeps the received session ID and round; the generator stamps `ACCEPT` on the outgoing context. Only a new proposal advances the round through `nextRound()`, subject to the session's round budget.

### 8.4 Generate a negotiation message from free text

```java
NegotiationContext contentCtx = NegotiationContext.of(
    UUID.randomUUID().toString(), 1, NegotiationPerformative.PROPOSE);
MetadataContent mc = client.generateNegotiationProposePromptFromText(
    "City1-city2 SPN private line outage; city1 OMC reports port down, optical power -28dBm",
    contentCtx,
    StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE_URI);
// the LLM extracts InformationProposeContent (items + relationship) and renders it
```

### 8.5 Generate alternative ending messages

The following are alternative responses, not a sequence to send together. Each uses the context of the proposal being answered.

```java
// accept
MetadataContent acceptMc = client.generateNegotiationAcceptPromptFromData(
    new NegotiationEndingData(receivedContext,
        new TargetEndingContent(NegotiationConclusion.ACCEPT, "Confirmed intent: restore the line", null)),
    StandardTemplates.TARGET_NEGOTIATION_ACCEPT_REJECT_URI);

// reject
MetadataContent rejectMc = client.generateNegotiationRejectPromptFromData(
    new NegotiationEndingData(receivedContext,
        new TargetEndingContent(NegotiationConclusion.REJECT, null, "Insufficient resources")),
    StandardTemplates.TARGET_NEGOTIATION_ACCEPT_REJECT_URI);

// terminate the negotiation
MetadataContent abortMc = client.generateNegotiationAbortPromptFromData(
    new NegotiationAbortData(receivedContext,
        new NegotiationAbortContent("Parties could not agree on the diagnosis scope; terminating")),
    StandardTemplates.NEGOTIATION_ABORT_URI);
```

Runnable samples: [a2a-t-sample negotiation samples](../../a2a-t-sample/README.zh-CN.md#协商negotiation端到端样例).

---

## 9. Supported Scope & Limitations

| Capability | Status |
|---|---|
| Content generation (fromData/fromText) | complete; 8 public generation methods covering propose/accept/reject/abort and all three negotiation types |
| Validation & parameter extraction | complete; propose/accept/reject/abort all supported |
| Session state | carried in message metadata; persistence and lifecycle are caller-managed |
| Language coverage | zh-CN and en-US |
