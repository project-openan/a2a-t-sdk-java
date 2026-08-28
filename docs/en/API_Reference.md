# 1 API Reference

## 1.1 Introduction

The public APIs of the A2A-T SDK converge on two facades: the client facade `A2ATClient` (prompt generation, negotiation message generation and validation) and the server facade `A2ATServer` (prompt validation, negotiation message generation and validation). The Negotiation-T APIs have exactly the same signature and semantics on both facades; the generation APIs of Task-T / Notification-T / Authorization-T live on `A2ATClient`, and the validation APIs live on `A2ATServer`.

- **API Overview**:

| Category | API | Client/Server | Description | LLM Involved |
| ---- | ------- | -------- | ------- |---------|
| Negotiation-T | `generateNegotiationProposePromptFromText` | A2A-T Client / A2A-T Server | Generates a negotiation propose message from natural-language text | Yes (1 LLM content-extraction call) |
| Negotiation-T | `generateNegotiationAcceptPromptFromText` | A2A-T Client / A2A-T Server | Generates a negotiation accept message from natural-language text | Yes (1 LLM content-extraction call) |
| Negotiation-T | `generateNegotiationRejectPromptFromText` | A2A-T Client / A2A-T Server | Generates a negotiation reject message from natural-language text | Yes (1 LLM content-extraction call) |
| Negotiation-T | `generateNegotiationProposePromptFromData` | A2A-T Client / A2A-T Server | Deterministically generates a negotiation propose message from structured data (no LLM call) | No |
| Negotiation-T | `generateNegotiationAcceptPromptFromData` | A2A-T Client / A2A-T Server | Deterministically generates a negotiation accept message from structured data (no LLM call) | No |
| Negotiation-T | `generateNegotiationRejectPromptFromData` | A2A-T Client / A2A-T Server | Deterministically generates a negotiation reject message from structured data (no LLM call) | No |
| Negotiation-T | `validateProposePromptAndDataFilling` | A2A-T Client / A2A-T Server | Validates a negotiation propose message and extracts parameters per a schema | Yes (1 LLM semantic-validation call) |
| Negotiation-T | `validateAcceptPromptAndDataFilling` | A2A-T Client / A2A-T Server | Validates a negotiation accept message and extracts parameters per a schema | Yes (1 LLM semantic-validation call) |
| Negotiation-T | `validateRejectPromptAndDataFilling` | A2A-T Client / A2A-T Server | Validates a negotiation reject message and extracts parameters per a schema | Yes (1 LLM semantic-validation call) |
| Negotiation-T | `generateNegotiationAbortPromptFromText` | A2A-T Client / A2A-T Server | Generates a negotiation abort message from natural-language text | Yes (1 LLM content-extraction call) |
| Negotiation-T | `generateNegotiationAbortPromptFromData` | A2A-T Client / A2A-T Server | Deterministically generates a negotiation abort message from structured data (no LLM call) | No |
| Negotiation-T | `validateAbortPromptAndDataFilling` | A2A-T Client / A2A-T Server | Validates a negotiation abort message and extracts parameters per the schema | Yes (1 LLM semantic-validation call) |
| Task-T | `generateTaskPromptFromText` | A2A-T Client | Generates a task prompt from natural-language text with the specified Task-T template (skips scenario recognition) | Yes (1 LLM slot-extraction call) |
| Task-T | `generateTaskPromptFromDataWithSchema` | A2A-T Client | Generates a task prompt from structured data plus a semantic schema with the specified Task-T template | Yes (1 LLM slot-extraction call) |
| Task-T | `validateTaskPromptAndDataFilling` | A2A-T Server | Validates a Task-T task prompt and extracts parameters per a schema | Yes (1 LLM semantic-validation and extraction call) |
| Notification-T | `generateNotificationPromptFromText` | A2A-T Client | Generates a notification subscription prompt from natural-language text with the specified Notification-T template | Yes (1 LLM slot-extraction call) |
| Notification-T | `generateNotificationPromptFromDataWithSchema` | A2A-T Client | Generates a notification subscription prompt from structured data plus a semantic schema with the specified Notification-T template | Yes (1 LLM slot-extraction call) |
| Notification-T | `validateNotificationPromptAndDataFilling` | A2A-T Server | Validates a Notification-T prompt and extracts parameters per a schema | Yes (1 LLM semantic-validation and extraction call) |
| Authorization-T | `generateAuthPromptFromText` | A2A-T Client | Generates an authorization prompt from natural-language text with the specified Authorization-T template | Yes (1 LLM slot-extraction call) |
| Authorization-T | `generateAuthPromptFromDataWithSchema` | A2A-T Client | Generates an authorization prompt from structured data plus a semantic schema with the specified Authorization-T template | Yes (1 LLM slot-extraction call) |
| Authorization-T | `validateAuthPromptAndDataFilling` | A2A-T Server | Validates an Authorization-T prompt and extracts parameters per a schema | Yes (1 LLM semantic-validation and extraction call) |
| General | `generateTaskPrompt` | A2A-T Client | Generates a task prompt from natural-language or structured input via scenario recognition | Yes (2 LLM calls: scenario recognition + slot extraction) |
| General | `checkTaskPrompt` | A2A-T Server | Validates the scenario, template, and slot compliance of a task prompt | Yes (3 LLM calls: scenario recognition + slot extraction + semantic validation) |

**Common Data Types and Conventions**

- **TemplateUri:** the template URI value type. Prefer building it with the `net.openan.a2at.sdk.core.model.StandardTemplates` constants, e.g. `StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE` (URI `Negotiation-T/information-negotiation/propose/v1`); for strings coming from outside the code, parse them with `TemplateUri.parse(String)`, which returns an `Optional<TemplateUri>` and never throws.
- **Negotiation session context:** `NegotiationContext(id, round, maxRounds, performative)` (id in UUID form, round starting at 1, default round budget `DEFAULT_MAX_ROUNDS = 5`; performative states which kind of negotiation message the context travels with: `PROPOSE` / `ACCEPT` / `REJECT` / `ABORT`); it travels with the message metadata and never goes through the LLM. `NegotiationContext.of(id, round, performative)` uses the default budget, and `nextRound()` advances the round.
- **Exception hierarchy:** every SDK processing failure is a subclass of `A2ATError` — generation failures throw `PromptGenerationException` (Task-T / Notification-T / Authorization-T) or `NegotiationGenerationException` (Negotiation-T); validation-plus-extraction failures throw `ContentValidationException` (Task-T / Notification-T / Authorization-T, carrying the `errors()` slot error details and the `params()` partial extraction parameters) or `NegotiationParamExtractionException` (Negotiation-T). Catching `A2ATError` covers all processing failures, and `getCode()` returns the machine-readable error code.
- **SlotValidationError:** per-slot validation error details, returned with validation-failure exceptions or failure payloads; the failure structures in the "Output" sections of each API all reference this definition:

| Field | Type | Description |
| ---- | ---- | ---- |
| slotName | String | Name of the slot the error refers to |
| code | String | Slot-level error code, e.g. `missing_required` (required value missing), `invalid_value` (invalid value), `format_error` (format error) |
| message | String | Slot-level error description |


## 1.2 Constraints and Limitations

Some APIs involve LLM calls. Control the call rate and concurrency according to the concurrency capacity of the connected model service.

## 1.3 API Description

### 1.3.1 generateNegotiationProposePromptFromText

**API Definition**

```java
public MetadataContent generateNegotiationProposePromptFromText(
        String text, NegotiationContext context, TemplateUri templateUri)
```

**Typical scenarios**: after receiving a Task-T task message with missing parameters, the server agent uses natural language to send a "supplement the missing information" negotiation request to the client agent; also applicable when the client agent initiates a target-clarification or feasibility-evaluation request to the server.

**Function Description**: generates a structured negotiation propose-phase message from natural-language text. Execution flow: the template is loaded first (a missing template fails fast without consuming an LLM request), then one LLM content-extraction step runs (extracting typed content from the free text under the template-URI constraint, retried up to the `A2AT_LLM_MAX_ATTEMPTS` limit on retryable error codes), and finally the template is rendered deterministically. Applicable to the initiator of information/target/feasibility negotiations. The `templateUri` phase segment must be `propose`.

**Input**

| Parameter | Type | Required | Description |
| ---- | ---- | ---- | ---- |
| text | String | Yes | Natural-language text describing the negotiation proposal (e.g. the list of missing information items to request) |
| context | NegotiationContext | Yes | Negotiation session context, injected directly into the `negotiationContext` metadata of the generated message without going through the LLM |
| templateUri | TemplateUri | Yes | Propose template, e.g. `StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE` |

**Request Example**

```java
import java.nio.file.Path;
import java.util.Map;
import net.openan.a2at.sdk.client.A2ATClient;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.StandardTemplates;

A2ATClient client = new A2ATClient(Path.of("client.env"));

NegotiationContext ctx = new NegotiationContext(
        "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3", 1, NegotiationContext.DEFAULT_MAX_ROUNDS,
        NegotiationPerformative.PROPOSE);

MetadataContent propose = client.generateNegotiationProposePromptFromText(
        "Please provide the following missing information: 1. Access port name: please provide the service access port name; "
                + "2. Complaint category: private line interruption or poor private line quality. "
                + "Both parameters are required; diagnosis cannot start without them.",
        ctx,
        StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE);

// The generated metadata travels with the A2A message
Map<String, Object> metadata = propose.buildMetadataContent();
```

**Output**

On success, returns `MetadataContent`:

| Field/Method | Type | Description |
| --------- | ---- | ---- |
| templateUri | String | Template URI used to generate the message, e.g. `Negotiation-T/information-negotiation/propose/v1` |
| promptText | String | Rendered negotiation message text, carried as the value keyed by the extension URI in A2A message metadata |
| extensionUri | String | TMF extension URI (`https://projects.tmforum.org/a2aproject/telecommunication/extensions/Negotiation-T/v1`), i.e. the key of the message in metadata |
| negotiationContext | NegotiationContext | Negotiation session context (id / round / maxRounds), carried with the message without going through the LLM |
| buildMetadataContent() | Map&lt;String, Object&gt; | Builds the map that can be placed directly into `Message.metadata`: extension URI → message text, `templateUri` → template URI, `negotiationContext` → nested context object |

On failure, throws `NegotiationGenerationException` (an `A2ATError` subclass, a runtime exception):

| Member | Type | Description |
| ---- | ---- | ---- |
| getCode() | String | Machine-readable error code, see below |
| getMessage() | String | Human-readable failure description |
| getCause() | Throwable | Root cause, may be null |

Error codes:

- `template_not_found` (template or prompt resource missing)

- `negotiation_content_extract_failed` (failed to extract structured content from the text, retryable)

- `negotiation_llm_infrastructure_error` (LLM infrastructure failure, retryable)

- `negotiation_invalid_input` (text is blank, the extracted content contradicts the phase, or the confirm request contradicts the other sections)

- `negotiation_slot_missing` (a required slot is missing)

A null argument throws `NullPointerException`; a templateUri whose phase segment is not `propose` throws `IllegalArgumentException`.

**Response Example**

```text
templateUri : Negotiation-T/information-negotiation/propose/v1
extensionUri: https://projects.tmforum.org/a2aproject/telecommunication/extensions/Negotiation-T/v1
promptText  :
## Information Negotiation
Please supplement the relevant content based on <Required Information Items>.

## Required Information Items
1. Access Port Name: e.g. P533-Zhujiang Old Town-PTN3900-23-TPA1EG24-1
2. Complaint Category: e.g. dedicated-line quality degradation
3. Private Line Service Identifier
Relationship between missing items: OR
```

### 1.3.2 generateNegotiationAcceptPromptFromText

**API Definition**

```java
public MetadataContent generateNegotiationAcceptPromptFromText(
        String text, NegotiationContext context, TemplateUri templateUri)
```

**Typical scenarios**: after receiving the peer's information-negotiation request, the negotiation responder (usually the client agent) supplements/delivers the requested information in natural language and generates an accept message to return, e.g. confirming that diagnosis can start after supplementing the access port name and complaint category.

**Function Description**: generates a negotiation accept message from natural-language text. One LLM content-extraction step (the extracted conclusion must be `ACCEPT`, otherwise it is rejected with `negotiation_invalid_input`) plus deterministic rendering. Applicable to the negotiation responder supplementing/delivering information. The `templateUri` phase segment must be `accept-reject`.

**Input**

| Parameter | Type | Required | Description |
| ---- | ---- | ---- | ---- |
| text | String | Yes | Natural-language text describing the acceptance (e.g. the list of supplemented/delivered information items) |
| context | NegotiationContext | Yes | Negotiation session context |
| templateUri | TemplateUri | Yes | Accept-reject template, e.g. `StandardTemplates.INFORMATION_NEGOTIATION_ACCEPT_REJECT` |

**Request Example**

```java
MetadataContent accept = client.generateNegotiationAcceptPromptFromText(
        "I agree to supplement the following information: 1. Access port name: P533-Zhujiang Old Town-PTN3900-23-TPA1EG24-1; "
                + "2. Complaint category: dedicated-line quality degradation. "
                + "The information is complete and diagnosis can start.",
        ctx,
        StandardTemplates.INFORMATION_NEGOTIATION_ACCEPT_REJECT);
```

**Output**

On success, returns `MetadataContent` (structure same as [1.3.1](#131-generatenegotiationproposepromptfromtext)).

On failure, throws `NegotiationGenerationException` (structure same as 1.3.1). Error codes:

- `template_not_found` (template or prompt resource missing)

- `negotiation_content_extract_failed` (failed to extract structured content from the text, retryable)

- `negotiation_llm_infrastructure_error` (LLM infrastructure failure, retryable)

- `negotiation_invalid_input` (text is blank, or the extracted conclusion is not `ACCEPT`)

- `negotiation_slot_missing` (a required slot is missing)

**Response Example**

```text
templateUri : Negotiation-T/information-negotiation/accept-reject/v1
extensionUri: https://projects.tmforum.org/a2aproject/telecommunication/extensions/Negotiation-T/v1
promptText  :
## Information Negotiation Result
Accept

## Information Negotiation Result Content
1. Access Port Name: P533-Zhujiang Old Town-PTN3900-23-TPA1EG24-1
2. Complaint Category: dedicated-line quality degradation
```

### 1.3.3 generateNegotiationRejectPromptFromText

**API Definition**

```java
public MetadataContent generateNegotiationRejectPromptFromText(
        String text, NegotiationContext context, TemplateUri templateUri)
```

**Typical scenarios**: when the negotiation responder (usually the client agent) cannot satisfy the peer's negotiation request, it generates a reject message in natural language to return and end the current negotiation round, e.g. the access port name cannot be provided because the site inventory is unavailable.

**Function Description**: generates a negotiation reject message from natural-language text. One LLM content-extraction step (the extracted conclusion must be `REJECT`) plus deterministic rendering. The `templateUri` phase segment must be `accept-reject`.

**Input**: same as [generateNegotiationAcceptPromptFromText](#132-generatenegotiationacceptpromptfromtext), where text is natural language describing the rejection reason.

**Request Example**

```java
MetadataContent reject = client.generateNegotiationRejectPromptFromText(
        "I refuse to supplement the information: the access port name cannot be provided because the site inventory is unavailable. This negotiation is over.",
        ctx,
        StandardTemplates.INFORMATION_NEGOTIATION_ACCEPT_REJECT);
```

**Output**

On success, returns `MetadataContent` (structure same as [1.3.1](#131-generatenegotiationproposepromptfromtext)).

On failure, throws `NegotiationGenerationException` (structure same as 1.3.1). Error codes:

- `template_not_found` (template or prompt resource missing)

- `negotiation_content_extract_failed` (failed to extract structured content from the text, retryable)

- `negotiation_llm_infrastructure_error` (LLM infrastructure failure, retryable)

- `negotiation_invalid_input` (text is blank, or the extracted conclusion is not `REJECT`)

- `negotiation_slot_missing` (a required slot is missing)

**Response Example**

```text
templateUri : Negotiation-T/information-negotiation/accept-reject/v1
extensionUri: https://projects.tmforum.org/a2aproject/telecommunication/extensions/Negotiation-T/v1
promptText  :
## Information Negotiation Result
Reject

## Information Negotiation Result Content
1. Access Port Name: cannot be provided because the port inventory is temporarily unavailable on the workbench side
```

### 1.3.4 generateNegotiationProposePromptFromData

**API Definition**

```java
public MetadataContent generateNegotiationProposePromptFromData(
        NegotiationProposeData data, TemplateUri templateUri)
```

**Typical scenarios**: the same initiation scenarios as the fromText variant, but the input is structured data constructed by the business system (e.g. the server agent automatically generates the negotiation-request items from the missing-slot list detected by `validateTaskPromptAndDataFilling`), suitable for scenarios that require deterministic message content and want to avoid the nondeterminism of LLM extraction.

**Function Description**: deterministically generates a negotiation propose message from typed data, **without calling the LLM**: the typed content is validated first, then dispatched to the generator of the negotiation type addressed by the template URI for rendering. The `content` must match the negotiation type of `templateUri` (information / target / feasibility), and the phase segment must be `propose`.

**Input**

| Parameter | Type | Required | Description |
| ---- | ---- | ---- | ---- |
| data | `NegotiationProposeData(context, content)` | Yes | Negotiation context plus typed propose content; the content type depends on the negotiation type |
| templateUri | TemplateUri | Yes | Propose template |

Propose content of the three negotiation types:

| Negotiation type | Propose content type | Fields |
| -------- | ---------------- | ---- |
| Information | `InformationProposeContent` | `items` (list of missing items), `relationship` (relationship between the missing items, nullable) |
| Target | `TargetProposeContent` | `targetNegotiationDescription` (required), `intentUnderstanding`, `alignmentAndClarification`, `requestForClarification` (all three item lists nullable; empty lists omit the corresponding sections), `targetConfirmRequest` (nullable string; a non-null value marks this round's message category as "target clarified and requesting confirmation from the counterparty" and renders the Target Clarification Confirmation Request section with the fixed content "The target has been clarified. Do you agree to proceed with this target?"; when it is non-null, `intentUnderstanding` / `alignmentAndClarification` / `requestForClarification` must all be empty) |
| Feasibility | `FeasibilityProposeContent` | `feasibilityNegotiationDescription` (required), `action` (`NegotiationAction.REQUEST_FEASIBILITY_EVALUATION` / `PROPOSE_ALTERNATIVE_ON_FAILURE`, unchanged two values), `contentsToEvaluate`, `infeasibilityDetailsAndProposal`, `feasibilityConfirmRequest` (nullable string; a non-null value marks this round's message category as "assess as feasible and request confirmation": `action` must be `REQUEST_FEASIBILITY_EVALUATION` with `contentsToEvaluate` / `infeasibilityDetailsAndProposal` both empty; the content is fixed by the assessment category to "The target is assessed as feasible. Do you agree to proceed with this target?" (goal achievement) or "The solution is assessed as feasible. Do you agree to proceed with this solution?" (solution feasibility)) |

**Request Example**

```java
import java.util.List;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.negotiation.content.InformationProposeContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationItem;
import net.openan.a2at.sdk.negotiation.content.NegotiationProposeData;

NegotiationContext ctx = new NegotiationContext("3dbc13b5-bd57-4c2b-b503-24e381b6c8d3", 2, 5,
        NegotiationPerformative.PROPOSE);

MetadataContent propose = client.generateNegotiationProposePromptFromData(
        new NegotiationProposeData(
                ctx,
                new InformationProposeContent(
                        List.of(
                                new NegotiationItem("Access Port Name", "e.g. P533-Zhujiang Old Town-PTN3900-23-TPA1EG24-1"),
                                new NegotiationItem("Complaint Category", "e.g. dedicated-line quality degradation"),
                                new NegotiationItem("Private Line Service Identifier", null)),
                        "OR")),
        StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE);
```

**Output**

On success, returns `MetadataContent` (structure same as [1.3.1](#131-generatenegotiationproposepromptfromtext); negotiation messages always carry `negotiationContext`).

On failure, throws `NegotiationGenerationException` (structure same as 1.3.1). Error codes:

- `template_not_found` (template missing)

- `negotiation_slot_missing` (a required slot is missing during rendering)

There are also two categories of programming errors (outside the `A2ATError` tree, standard JDK exceptions): a null argument or a null context within it throws `NullPointerException`; a content type that does not match the template's negotiation type, or a phase segment that is not `propose`, throws `IllegalArgumentException`.

**Response Example**

```text
templateUri : Negotiation-T/information-negotiation/propose/v1
extensionUri: https://projects.tmforum.org/a2aproject/telecommunication/extensions/Negotiation-T/v1
promptText  :
## Information Negotiation
Please supplement the relevant content based on <Required Information Items>.

## Required Information Items
1. Access Port Name: e.g. P533-Zhujiang Old Town-PTN3900-23-TPA1EG24-1
2. Complaint Category: e.g. dedicated-line quality degradation
3. Private Line Service Identifier
Relationship between missing items: OR
```

### 1.3.5 generateNegotiationAcceptPromptFromData

**API Definition**

```java
public MetadataContent generateNegotiationAcceptPromptFromData(
        NegotiationEndingData data, TemplateUri templateUri)
```

**Typical scenarios**: the negotiation responder (usually the client agent) programmatically fills in the parameters per the peer's requested slot list and generates an accept message from structured items to return.

**Function Description**: deterministically generates a negotiation accept message from typed data, **without calling the LLM**. `content.conclusion()` must be `ACCEPT`; any other conclusion (including `ABORT`) is rejected with `IllegalArgumentException`.

**Input**

| Parameter | Type | Required | Description |
| ---- | ---- | ---- | ---- |
| data | `NegotiationEndingData(context, content)` | Yes | Negotiation context plus typed accept content (conclusion is `ACCEPT`) |
| templateUri | TemplateUri | Yes | Accept-reject template |

Accept content of the three negotiation types: `InformationEndingContent(ACCEPT, items)` (list of delivered information items), `TargetEndingContent(ACCEPT, confirmedIntent, null)` (finally confirmed intent), `FeasibilityEndingContent(ACCEPT, feasibilitySummary)` (feasibility assessment conclusion summary).

**Request Example**

```java
import net.openan.a2at.sdk.negotiation.content.InformationEndingContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationConclusion;
import net.openan.a2at.sdk.negotiation.content.NegotiationEndingData;

MetadataContent accept = client.generateNegotiationAcceptPromptFromData(
        new NegotiationEndingData(
                ctx,
                new InformationEndingContent(
                        NegotiationConclusion.ACCEPT,
                        List.of(
                                new NegotiationItem("Access Port Name", "P533-Zhujiang Old Town-PTN3900-23-TPA1EG24-1"),
                                new NegotiationItem("Complaint Category", "dedicated-line quality degradation")))),
        StandardTemplates.INFORMATION_NEGOTIATION_ACCEPT_REJECT);
```

**Output**

On success, returns `MetadataContent` (structure same as [1.3.1](#131-generatenegotiationproposepromptfromtext)).

On failure, throws `NegotiationGenerationException` (structure same as 1.3.1). Error codes:

- `template_not_found` (template missing)

- `negotiation_slot_missing` (a required slot is missing during rendering)

Programming errors: a null argument or a null context within it throws `NullPointerException`; a `conclusion` that is not `ACCEPT`, a phase segment that is not `accept-reject`, or a mismatched content type throws `IllegalArgumentException`.

**Response Example**

```text
templateUri : Negotiation-T/information-negotiation/accept-reject/v1
extensionUri: https://projects.tmforum.org/a2aproject/telecommunication/extensions/Negotiation-T/v1
promptText  :
## Information Negotiation Result
Accept

## Information Negotiation Result Content
1. Access Port Name: P533-Zhujiang Old Town-PTN3900-23-TPA1EG24-1
2. Complaint Category: dedicated-line quality degradation
```

### 1.3.6 generateNegotiationRejectPromptFromData

**API Definition**

```java
public MetadataContent generateNegotiationRejectPromptFromData(
        NegotiationEndingData data, TemplateUri templateUri)
```

**Typical scenarios**: the negotiation responder, after programmatically determining that the peer's request cannot be satisfied, generates a reject message from structured items (the items that cannot be provided and the reasons) to return.

**Function Description**: deterministically generates a negotiation reject message from typed data, **without calling the LLM**. `content.conclusion()` must be `REJECT`; any other conclusion is rejected with `IllegalArgumentException`.

**Input**: same as [generateNegotiationAcceptPromptFromData](#135-generatenegotiationacceptpromptfromdata), but with the conclusion `REJECT`. Reject content: `InformationEndingContent(REJECT, items)` (items that cannot be provided and the reasons), `TargetEndingContent(REJECT, null, failureReason)` (rejection reason), `FeasibilityEndingContent(REJECT, feasibilitySummary)` (infeasibility conclusion summary).

**Request Example**

```java
MetadataContent reject = client.generateNegotiationRejectPromptFromData(
        new NegotiationEndingData(
                ctx,
                new InformationEndingContent(
                        NegotiationConclusion.REJECT,
                        List.of(new NegotiationItem("Access Port Name", "cannot be provided because the port inventory is temporarily unavailable on the workbench side")))),
        StandardTemplates.INFORMATION_NEGOTIATION_ACCEPT_REJECT);
```

**Output**

On success, returns `MetadataContent` (structure same as [1.3.1](#131-generatenegotiationproposepromptfromtext)).

On failure, throws `NegotiationGenerationException` (structure same as 1.3.1). Error codes:

- `template_not_found` (template missing)

- `negotiation_slot_missing` (a required slot is missing during rendering)

Programming errors: a null argument or a null context within it throws `NullPointerException`; a `conclusion` that is not `REJECT`, a phase segment that is not `accept-reject`, or a mismatched content type throws `IllegalArgumentException`.

**Response Example**

```text
templateUri : Negotiation-T/information-negotiation/accept-reject/v1
extensionUri: https://projects.tmforum.org/a2aproject/telecommunication/extensions/Negotiation-T/v1
promptText  :
## Information Negotiation Result
Reject

## Information Negotiation Result Content
1. Access Port Name: cannot be provided because the port inventory is temporarily unavailable on the workbench side
```

### 1.3.7 validateProposePromptAndDataFilling

**API Definition**

```java
public FilledParamData validateProposePromptAndDataFilling(
        String prompt, NegotiationContext context, Map<String, Object> schema, TemplateUri templateUri)
```

**Typical scenarios**: an outbound self-check by the initiator (server agent) before sending a negotiation request; or the receiver (client agent) validating an inbound negotiation request and extracting the list of slots to supplement, driving the subsequent parameter filling.

**Function Description**: validates whether a negotiation propose message is a properly formatted negotiation message, and extracts parameters from it per the caller-provided JSON Schema. Pipeline: template-URI phase-segment check → deterministic rule gate (the context id must be in UUID form and the round must not exceed the budget; a null context is reported as `negotiation_invalid_input`) → one LLM semantic-validation call (which also performs the parameter extraction, retried up to the configured limit on retryable error codes) → parameter merge (the negotiation-context parameters `id` / `round` / `maxRounds` are merged with the extracted parameters; on key conflicts, **the context parameters win**).

**Input**

| Parameter | Type | Required | Description |
| ---- | ---- | ---- | ---- |
| prompt | String | Yes | Negotiation propose message text to validate (`MetadataContent.promptText()`) |
| context | NegotiationContext | No | Negotiation context traveling with the message; null is reported as `negotiation_invalid_input` |
| schema | Map&lt;String, Object&gt; | Yes | Caller-provided parameter JSON Schema declaring the parameters to extract |
| templateUri | TemplateUri | Yes | Propose template |

**Request Example**

```java
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.client.A2ATClient;
import net.openan.a2at.sdk.core.model.FilledParamData;
import net.openan.a2at.sdk.core.model.NegotiationContext;
import net.openan.a2at.sdk.core.model.StandardTemplates;

A2ATClient client = new A2ATClient(Path.of("client.env"));

Map<String, Object> schema = Map.of(
        "type", "object",
        "properties", Map.of(
                "Access Port Name", Map.of("type", "string"),
                "Complaint Category", Map.of("type", "string")),
        "required", List.of("Access Port Name"));

// proposePrompt is the negotiation propose message text sent by the peer
FilledParamData requested = client.validateProposePromptAndDataFilling(
        proposePrompt, ctx, schema, StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE);

// After filtering out the context parameters (id/round/maxRounds), the remaining keys
// are the slots to supplement
requested.data().keySet().removeAll(List.of("id", "round", "maxRounds"));
```

**Output**

On success, returns `FilledParamData`:

| Field/Method | Type | Description |
| --------- | ---- | ---- |
| data() | Map&lt;String, Object&gt; | Merged parameters: negotiation-context parameters (`id` / `round` / `maxRounds`, **winning** on key conflicts) + parameters extracted from the message per the caller's schema |

On failure, throws `NegotiationParamExtractionException` (an `A2ATError` subclass):

| Member | Type | Description |
| ---- | ---- | ---- |
| getCode() | String | Machine-readable error code, see below |
| getMessage() | String | Human-readable failure description |
| getErrors() | List&lt;SlotValidationError&gt; | Per-slot error details; structure defined in the common conventions |

Error codes:

- `negotiation_invalid_input` (the message is not a negotiation message, or the context is null)

- `negotiation_rule_violation` (the negotiation context violates a structural rule, e.g. a non-UUID id or a round beyond the budget)

- `negotiation_semantic_rejected` (semantic validation rejected the message)

- `negotiation_llm_infrastructure_error` (LLM infrastructure failure, retryable)

- `template_not_found` (validation prompt resources missing)

Programming errors: a null prompt or schema throws `NullPointerException`; a blank prompt or a mismatched phase segment throws `IllegalArgumentException`.

**Response Example**

```text
requested.data() =
{
  Access Port Name=e.g. P533-Zhujiang Old Town-PTN3900-23-TPA1EG24-1,
  Complaint Category=e.g. dedicated-line quality degradation,
  id=3dbc13b5-bd57-4c2b-b503-24e381b6c8d3,
  round=1,
  maxRounds=5
}
```

### 1.3.8 validateAcceptPromptAndDataFilling

**API Definition**

```java
public FilledParamData validateAcceptPromptAndDataFilling(
        String prompt, NegotiationContext context, Map<String, Object> schema, TemplateUri templateUri)
```

**Typical scenarios**: the initiator (server agent) validates the accept message returned by the peer, extracts the delivered parameter values, and cross-checks them against the expected fill values before continuing task execution.

**Function Description**: validates a negotiation accept message and extracts parameters per the schema. The pipeline is the same as [validateProposePromptAndDataFilling](#137-validateproposepromptanddatafilling), except that the `templateUri` phase segment must be `accept-reject`, and the message must satisfy the accept-phase semantic constraints (the conclusion is Accept and delivered content is carried).

**Input**: same as 1.3.7, where prompt is the accept message text and templateUri is the accept-reject template.

**Request Example**

```java
import net.openan.a2at.sdk.server.A2ATServer;

A2ATServer server = new A2ATServer(Path.of("server.env"));

// acceptPrompt is the accept message text returned by the client;
// acceptContext is its negotiation context
FilledParamData acceptParams = server.validateAcceptPromptAndDataFilling(
        acceptPrompt, acceptContext, schema, StandardTemplates.INFORMATION_NEGOTIATION_ACCEPT_REJECT);
```

**Output**

On success, returns `FilledParamData` (structure same as [1.3.7](#137-validateproposepromptanddatafilling)); `data()` carries the parameters extracted from the delivered content per the schema plus the context parameters.

On failure, throws `NegotiationParamExtractionException` (structure same as 1.3.7). Error codes:

- `negotiation_invalid_input` (the message is not an accept negotiation message, or the context is null)

- `negotiation_rule_violation` (the negotiation context violates a structural rule)

- `negotiation_semantic_rejected` (the conclusion is not Accept, or the content does not satisfy the accept-phase constraints)

- `negotiation_llm_infrastructure_error` (LLM infrastructure failure, retryable)

- `template_not_found` (validation prompt resources missing)

Programming errors: a null prompt or schema throws `NullPointerException`; a blank prompt or a phase segment that is not `accept-reject` throws `IllegalArgumentException`.

**Response Example**

```text
acceptParams.data() =
{
  Access Port Name=P533-Zhujiang Old Town-PTN3900-23-TPA1EG24-1,
  Complaint Category=dedicated-line quality degradation,
  id=3dbc13b5-bd57-4c2b-b503-24e381b6c8d3,
  round=1,
  maxRounds=5
}
```

### 1.3.9 validateRejectPromptAndDataFilling

**API Definition**

```java
public FilledParamData validateRejectPromptAndDataFilling(
        String prompt, NegotiationContext context, Map<String, Object> schema, TemplateUri templateUri)
```

**Typical scenarios**: the initiator (server agent) validates the reject message returned by the peer, extracts the rejection reason, and terminates the task or escalates to manual handling accordingly.

**Function Description**: validates a negotiation reject message and extracts parameters per the schema. The pipeline is the same as 1.3.7, except that the message must satisfy the reject-phase semantic constraints (the conclusion is Reject and a rejection reason is carried).

**Input**: same as 1.3.7, where prompt is the reject message text and templateUri is the accept-reject template.

**Request Example**

```java
FilledParamData rejectParams = server.validateRejectPromptAndDataFilling(
        rejectPrompt, ctx, schema, StandardTemplates.INFORMATION_NEGOTIATION_ACCEPT_REJECT);
```

**Output**

On success, returns `FilledParamData` (structure same as [1.3.7](#137-validateproposepromptanddatafilling)); `data()` carries the parameters extracted from the rejection reason per the schema plus the context parameters.

On failure, throws `NegotiationParamExtractionException` (structure same as 1.3.7). Error codes:

- `negotiation_invalid_input` (the message is not a reject negotiation message, or the context is null)

- `negotiation_rule_violation` (the negotiation context violates a structural rule)

- `negotiation_semantic_rejected` (the conclusion is not Reject, or the content does not satisfy the reject-phase constraints)

- `negotiation_llm_infrastructure_error` (LLM infrastructure failure, retryable)

- `template_not_found` (validation prompt resources missing)

Programming errors: a null prompt or schema throws `NullPointerException`; a blank prompt or a phase segment that is not `accept-reject` throws `IllegalArgumentException`.

**Response Example**

```text
rejectParams.data() =
{
  Access Port Name=cannot be provided because the port inventory is temporarily unavailable on the workbench side,
  id=3dbc13b5-bd57-4c2b-b503-24e381b6c8d3,
  round=1,
  maxRounds=5
}
```

### 1.3.10 generateTaskPromptFromText

**API Definition**

```java
public MetadataContent generateTaskPromptFromText(String text, TemplateUri templateUri)
```

**Typical scenarios**: the client agent converts the user's natural-language task description (e.g. a private-line complaint diagnosis request) into a Task-T protocol message for a specified scenario; suitable when the target template is already known and scenario recognition should be skipped.

**Function Description**: generates a task prompt message from natural-language text with the specified Task-T template, **skipping scenario recognition** (the template is explicitly specified by the caller). One LLM slot-extraction step runs, then the template is rendered deterministically. The built-in slot schema is validated during generation (a missing or invalid required slot fails fast with `slot_validation_error`).

**Input**

| Parameter | Type | Required | Description |
| ---- | ---- | ---- | ---- |
| text | String | Yes | Natural-language task description |
| templateUri | TemplateUri | Yes | Task-T template, e.g. `StandardTemplates.PRIVATE_LINE_COMPLAINT` (`Task-T/network-layer/private-line-complaint/v1`) |

**Request Example**

```java
import java.nio.file.Path;
import net.openan.a2at.sdk.client.A2ATClient;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.StandardTemplates;

A2ATClient client = new A2ATClient(Path.of("client.env"));

MetadataContent metadata = client.generateTaskPromptFromText(
        "Help me create a private-line complaint diagnosis task. The port is P781-Zhujiang New Town-PTN7900-23-TPA1EG24-17, "
                + "the customer reports poor private line quality. Starting from 8:30 AM on May 11, 2026, accessing the core "
                + "systems in Guangzhou from Shenzhen has been extremely slow, latency jumped from 12ms to 320ms, the counter "
                + "and mobile banking keep reporting connection timeouts, and the OSS sequence number is event-id-20260511-09013.",
        StandardTemplates.PRIVATE_LINE_COMPLAINT);
```

**Output**

On success, returns `MetadataContent`:

| Field/Method | Type | Description |
| --------- | ---- | ---- |
| templateUri | String | Template URI used to generate the message, e.g. `Task-T/network-layer/private-line-complaint/v1` |
| promptText | String | Rendered task prompt message text, carried as the value keyed by the extension URI in A2A message metadata |
| extensionUri | String | TMF extension URI (`https://projects.tmforum.org/a2aproject/telecommunication/extensions/Task-T/v1`), i.e. the key of the message in metadata |
| negotiationContext | NegotiationContext | Always null (not a negotiation message) |
| buildMetadataContent() | Map&lt;String, Object&gt; | Builds the two-key map that can be placed directly into `Message.metadata`: extension URI → message text, `templateUri` → template URI |

On failure, throws `PromptGenerationException` (an `A2ATError` subclass, a runtime exception):

| Member | Type | Description |
| ---- | ---- | ---- |
| getCode() | String | Machine-readable error code, see below |
| getMessage() | String | Human-readable failure description |
| failedParameters() | List&lt;SlotValidationError&gt; | Details of failed slot validations (non-empty on `slot_validation_error`); structure defined in the common conventions |

Error codes:

- `template_not_found` (template missing)

- `prompt_resource_load_error` (prompt resource load failure)

- `slot_schema_not_found` (slot schema missing)

- `llm_invocation_failed` (LLM call failure)

- `slot_validation_error` (required slot missing or invalid value)

- `render_failed` (template rendering failure)

Programming errors: a null text or templateUri throws `NullPointerException`.

**Response Example**

```text
templateUri : Task-T/network-layer/private-line-complaint/v1
extensionUri: https://projects.tmforum.org/a2aproject/telecommunication/extensions/Task-T/v1
promptText  :
## Task Type
Transport private line service complaint diagnosis

## Task Description
Based on <Task Object> and <Task Context>, perform network-side fault root cause diagnosis in the complaint scenario, achieve the complaint diagnosis goal defined in <Task Target>, and return the task processing result in the structure defined in <Expected Output>.

## Task Target
Diagnose network-side faults and return diagnostic result information such as fault root causes and repair suggestions.

## Task Object
Access port name: P781-Zhujiang New Town-PTN7900-23-TPA1EG24-17

## Task Context
1. Complaint category: poor private line quality
2. Problem occurrence time: 2026-05-11T08:21:46Z
3. OSS-side event sequence number: event-id-20260511-09013
4. Complaint details: Starting from 8:30 on May 11, the response latency from SZ to GZ suddenly increased from an average of 12ms to 320ms

## Expected Output
Requirement: The complaint diagnosis task result should include the following information:
1. Diagnosis result. Allowed values: success, failure (required)
2. Diagnosis result details (required)
3. Repair suggestions (optional)
4. Fault root cause list, where each fault root cause includes fault root cause name, detailed description, repair suggestions, fault root cause point location, etc. (optional)
```

### 1.3.11 generateTaskPromptFromDataWithSchema

**API Definition**

```java
public MetadataContent generateTaskPromptFromDataWithSchema(
        Map<String, Object> data, Map<String, Object> schema, TemplateUri templateUri)
```

**Typical scenarios**: the client agent converts structured task parameters from an upstream system (field names may differ from the template slots; the schema describes the field semantics) into a Task-T protocol message; suitable when the task parameters are already available in structured form.

**Function Description**: generates a task prompt from structured data plus a semantic schema with the specified Task-T template, **skipping scenario recognition**. The `schema` describes the business meaning of each input field (description / examples / enum, etc.), guiding slot filling and value constraints; each key of data corresponds to one slot value. The built-in slot schema is validated during generation as well (a missing or invalid required slot fails fast with `slot_validation_error`).

**Input**

| Parameter | Type | Required | Description |
| ---- | ---- | ---- | ---- |
| data | Map&lt;String, Object&gt; | Yes | Structured business-field input; keys are business field names and values are field values |
| schema | Map&lt;String, Object&gt; | Yes (non-empty) | Field-semantics JSON Schema describing the meaning and constraints of each field |
| templateUri | TemplateUri | Yes | Task-T template |

**Request Example**

```java
import java.util.Map;

Map<String, Object> data = Map.of(
        "portName", "P781-Futian Center-PTN7900-2-TPA1EG24-03",
        "complaintScenario", "poor private line quality",
        "faultStartTime", "2026-05-11T08:21:46Z",
        "ticketNo", "event-id-20260511-09013",
        "faultDetailText", "Starting from 8:30 on May 11, the response latency from SZ to GZ suddenly increased from an average of 12ms to 320ms");

Map<String, Object> semanticsSchema = Map.of(
        "type", "object",
        "properties", Map.of(
                "portName", Map.of(
                        "type", "string",
                        "description", "Business field: access port name, which uniquely identifies the complained private line object"),
                "complaintScenario", Map.of(
                        "type", "string",
                        "description", "Business field: complaint category scenario; one of private line interruption and poor private line quality is required",
                        "enum", List.of("private line interruption", "poor private line quality")),
                "faultStartTime", Map.of("type", "string", "description", "Business field: problem occurrence time"),
                "ticketNo", Map.of("type", "string", "description", "Business field: complaint work order or event sequence number received on the OSS side"),
                "faultDetailText", Map.of("type", "string", "description", "Business field: the user's free description of the fault phenomenon")),
        "required", List.of("portName", "complaintScenario"));

MetadataContent metadata = client.generateTaskPromptFromDataWithSchema(
        data, semanticsSchema, StandardTemplates.PRIVATE_LINE_COMPLAINT);
```

**Output**

On success, returns `MetadataContent` (structure same as [1.3.10](#1310-generatetaskpromptfromtext)).

On failure, throws `PromptGenerationException` (structure same as 1.3.10). Programming errors: a null argument throws `NullPointerException`; an empty schema map throws `IllegalArgumentException`.

**Response Example** (same template as 1.3.10; slot values come from the structured input, and `faultDetail` is a truncated sample value)

```text
templateUri : Task-T/network-layer/private-line-complaint/v1
extensionUri: https://projects.tmforum.org/a2aproject/telecommunication/extensions/Task-T/v1
promptText  :
## Task Type
Transport private line service complaint diagnosis

## Task Description
Based on <Task Object> and <Task Context>, perform network-side fault root cause diagnosis in the complaint scenario, achieve the complaint diagnosis goal defined in <Task Target>, and return the task processing result in the structure defined in <Expected Output>.

## Task Target
Diagnose network-side faults and return diagnostic result information such as fault root causes and repair suggestions.

## Task Object
Access port name: P781-Futian Center-PTN7900-2-TPA1EG24-03

## Task Context
1. Complaint category: poor private line quality
2. Problem occurrence time: 2026-05-11T08:21:46Z
3. OSS-side event sequence number: event-id-20260511-09013
4. Complaint details: Starting from 8:30 on May 11, the response latency from SZ to GZ suddenly increased from an average of 12ms to 320ms

## Expected Output
Requirement: The complaint diagnosis task result should include the following information:
1. Diagnosis result. Allowed values: success, failure (required)
2. Diagnosis result details (required)
3. Repair suggestions (optional)
4. Fault root cause list, where each fault root cause includes fault root cause name, detailed description, repair suggestions, fault root cause point location, etc. (optional)
```

### 1.3.12 validateTaskPromptAndDataFilling

**API Definition**

```java
public FilledParamData validateTaskPromptAndDataFilling(
        String prompt, Map<String, Object> schema, TemplateUri templateUri)
```

**Typical scenarios**: the parameter validation and extraction entry point for the server agent after receiving a Task-T message and before business execution; also the decision point for "missing-slot detection" in the negotiation flow — when a required parameter is missing, the caller decides whether to initiate a negotiation to supplement it.

**Function Description**: validates whether a Task-T task prompt message matches the template and slot constraints, and extracts parameters per the caller-provided JSON Schema. Pipeline: deterministic rule gate (built-in slot validation of the template) → one LLM semantic-validation call (which also performs the parameter extraction, retried up to the configured limit on retryable error codes).

**Input**

| Parameter | Type | Required | Description |
| ---- | ---- | ---- | ---- |
| prompt | String | Yes (non-blank) | Task prompt message text to validate (`MetadataContent.promptText()`) |
| schema | Map&lt;String, Object&gt; | Yes | Caller-provided parameter JSON Schema declaring the parameters to extract/validate and the required constraints |
| templateUri | TemplateUri | Yes | Task-T template (the prefix segment must be `Task-T`) |

**Request Example**

```java
import java.util.List;
import java.util.Map;
import net.openan.a2at.sdk.server.A2ATServer;

A2ATServer server = new A2ATServer(Path.of("server.env"));

// Server-side parameter schema (keys are server business field names, which may differ from
// the client field names; the SDK performs the cross-field adaptation)
Map<String, Object> validationSchema = Map.of(
        "type", "object",
        "properties", Map.of(
                "accessPort", Map.of("type", "string", "description", "Access port name, which uniquely identifies the complained private line object"),
                "bizScenario", Map.of(
                        "type", "string",
                        "description", "Complaint category scenario, required; only the values private line interruption and poor private line quality are allowed",
                        "enum", List.of("private line interruption", "poor private line quality")),
                "faultTime", Map.of("type", "string", "description", "Problem occurrence time"),
                "eventSerialNo", Map.of("type", "string", "description", "Complaint work order or event sequence number received on the OSS side"),
                "faultDetail", Map.of("type", "string", "description", "Complaint/fault phenomenon description")),
        "required", List.of("accessPort", "bizScenario"));

Map<String, Object> extracted = server
        .validateTaskPromptAndDataFilling(
                metadata.promptText(), validationSchema, StandardTemplates.PRIVATE_LINE_COMPLAINT)
        .data();
```

**Output**

On success, returns `FilledParamData`:

| Field/Method | Type | Description |
| --------- | ---- | ---- |
| data() | Map&lt;String, Object&gt; | Parameters extracted per the schema; keys are the parameter names declared in the schema |

On failure, throws `ContentValidationException` (an `A2ATError` subclass):

| Member | Type | Description |
| ---- | ---- | ---- |
| getCode() | String | Machine-readable error code, see below |
| getMessage() | String | Human-readable failure description |
| errors() | List&lt;SlotValidationError&gt; | Per-slot error details (slot-level error codes such as `missing_required`, `invalid_value`, `format_error`); structure defined in the common conventions |
| params() | Map&lt;String, Object&gt; | Partial extraction parameters captured before the rejection (slots that could not be extracted have null values) |

Error codes:

- `validation_semantic_rejected` (semantic validation rejected, including missing or invalid required parameters)

- `validation_llm_infrastructure_error` (LLM infrastructure failure, retryable)

- `validation_prompt_resource_not_found` (validation prompt resources missing)

Programming errors: a null prompt / schema / templateUri throws `NullPointerException`; a blank prompt throws `IllegalArgumentException`.

**Response Example**

```text
extracted =
{
  accessPort=P781-Zhujiang New Town-PTN7900-23-TPA1EG24-17,
  bizScenario=poor private line quality,
  faultTime=2026-05-11,
  eventSerialNo=event-id-20260511-09013,
  faultDetail=320ms
}
```

On validation rejection (a negative sample with a missing key slot, from case 3 of `TaskTDemoMain`):

```text
ContentValidationException: [validation_semantic_rejected] ...
    slot=task_object code=missing_required message=...
```

### 1.3.13 generateNotificationPromptFromText

**API Definition**

```java
public MetadataContent generateNotificationPromptFromText(String text, TemplateUri templateUri)
```

**Typical scenarios**: the client agent converts a natural-language subscription requirement (e.g. a service recovery event subscription) into a Notification-T subscription message for a specified scenario.

**Function Description**: generates a notification subscription prompt message from natural-language text with the specified Notification-T template, skipping scenario recognition. One LLM slot-extraction step runs, then the template is rendered deterministically, and the built-in slot schema is validated during generation.

**Input**

| Parameter | Type | Required | Description |
| ---- | ---- | ---- | ---- |
| text | String | Yes | Natural-language subscription description (notification topic, subscribe condition, notification data format, etc.) |
| templateUri | TemplateUri | Yes | Notification-T template, e.g. `StandardTemplates.SUBSCRIBE_INCIDENT`, `StandardTemplates.SERVICE_RECOVERY` |

**Request Example**

```java
import java.nio.file.Path;
import net.openan.a2at.sdk.client.A2ATClient;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.StandardTemplates;
import net.openan.a2at.sdk.core.model.TemplateUri;

A2ATClient client = new A2ATClient(Path.of("client.env"));
TemplateUri templateUri = TemplateUri.parse("Notification-T/network-layer/service-recovery/v1").orElseThrow();

MetadataContent result = client.generateNotificationPromptFromText(
        "I want to subscribe to service recovery events. The notification data format is as follows: "
                + "1. Service recovery plan execution status, allowed values: not started, ended; "
                + "2. Complaint diagnosis task sequence number; 3. OSS-side event sequence number; 4. Access port name; "
                + "5. Whether OMC automatic recovery is authorized, allowed values: yes, no; "
                + "6. Service recovery plan name; 7. Service recovery plan details",
        templateUri);
```

**Output**

On success, returns `MetadataContent` (structure same as [1.3.10](#1310-generatetaskpromptfromtext); `extensionUri` is `https://projects.tmforum.org/a2aproject/telecommunication/extensions/Notification-T/v1`).

On failure, throws `PromptGenerationException` (structure same as 1.3.10). Programming errors: a null text or templateUri throws `NullPointerException`.

**Response Example** (rendered per the template; the actual text varies with the LLM slot-extraction result. This example input does not specify a subscribe condition, so that slot is left empty)

```text
templateUri : Notification-T/network-layer/service-recovery/v1
extensionUri: https://projects.tmforum.org/a2aproject/telecommunication/extensions/Notification-T/v1
promptText  :
## Subscription Description
Please complete the network-side service recovery event subscription and reporting task based on the following <Notification Topic>, <Subscribe Condition>, <Notification Data Format>, and <Expected Output> information.

## Notification Topic
Service recovery event

## Subscribe Condition
(optional)
1. Subnetwork name. Example: xx subnetwork

## Notification Data Format
1. Service recovery plan execution status. Allowed values: not started, ended
2. Complaint diagnosis task sequence number
3. OSS-side event sequence number
4. Access port name
5. Whether OMC automatic recovery is authorized. Allowed values: yes, no
6. Service recovery plan name
7. Service recovery plan details

## Expected Output
1. Subscription result. Allowed values: success, failure
2. Subscription failure reason (optional)
3. After successful subscription, report messages according to <Notification Data Format>
```

### 1.3.14 generateNotificationPromptFromDataWithSchema

**API Definition**

```java
public MetadataContent generateNotificationPromptFromDataWithSchema(
        Map<String, Object> data, Map<String, Object> schema, TemplateUri templateUri)
```

**Typical scenarios**: the client agent converts structured subscription parameters submitted by an upstream system or UI into a Notification-T subscription message; suitable when the subscription parameters are already available in structured form.

**Function Description**: generates a notification subscription prompt from structured data plus a semantic schema with the specified Notification-T template, skipping scenario recognition. The semantic constraints are the same as 1.3.11.

**Input**

| Parameter | Type | Required | Description |
| ---- | ---- | ---- | ---- |
| data | Map&lt;String, Object&gt; | Yes | Structured subscription input (e.g. the subscribe condition and the notification data format field list) |
| schema | Map&lt;String, Object&gt; | Yes (non-empty) | Field-semantics JSON Schema |
| templateUri | TemplateUri | Yes | Notification-T template |

**Request Example**

```java
Map<String, Object> data = Map.of(
        "condition", "Subnetwork name: xx subnetwork",
        "reportFormat", List.of(
                Map.of("name", "Service recovery plan execution status", "values", List.of("not started", "ended"), "required", true),
                Map.of("name", "Complaint diagnosis task sequence number", "required", true),
                Map.of("name", "OSS-side event sequence number", "required", true),
                Map.of("name", "Access port name", "required", true),
                Map.of("name", "Whether OMC automatic recovery is authorized", "values", List.of("yes", "no"), "required", true),
                Map.of("name", "Service recovery plan name", "required", true),
                Map.of("name", "Service recovery plan details", "required", true),
                Map.of("name", "Service recovery plan execution end time", "required", false)));

Map<String, Object> dataSchema = Map.of(
        "type", "object",
        "properties", Map.of(
                "condition", Map.of("type", "string", "description", "Subscribe condition, optional. The condition description to subscribe to."),
                "reportFormat", Map.of(
                        "type", "array",
                        "description", "Notification data format, required. Describes the field list of the content to report.",
                        "items", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "name", Map.of("type", "string"),
                                        "values", Map.of("type", "array"),
                                        "required", Map.of("type", "boolean")),
                                "required", List.of("name")))),
        "required", List.of("reportFormat"));

MetadataContent result = client.generateNotificationPromptFromDataWithSchema(
        data, dataSchema, templateUri);
```

**Output**

On success, returns `MetadataContent` (structure same as [1.3.10](#1310-generatetaskpromptfromtext); `extensionUri` is `https://projects.tmforum.org/a2aproject/telecommunication/extensions/Notification-T/v1`).

On failure, throws `PromptGenerationException` (structure same as 1.3.10). Programming errors: a null argument throws `NullPointerException`; an empty schema map throws `IllegalArgumentException`.

**Response Example** (same template as 1.3.13; slot values come from the structured input: `Subscribe Condition` is filled with `condition`, and `Notification Data Format` is rendered per the `reportFormat` list — field layout is illustrative)

```text
templateUri : Notification-T/network-layer/service-recovery/v1
extensionUri: https://projects.tmforum.org/a2aproject/telecommunication/extensions/Notification-T/v1
promptText  :
## Subscription Description
Please complete the network-side service recovery event subscription and reporting task based on the following <Notification Topic>, <Subscribe Condition>, <Notification Data Format>, and <Expected Output> information.

## Notification Topic
Service recovery event

## Subscribe Condition
Subnetwork name: xx subnetwork (optional)
1. Subnetwork name. Example: xx subnetwork

## Notification Data Format
1. Service recovery plan execution status. Allowed values: not started, ended (required)
2. Complaint diagnosis task sequence number (required)
3. OSS-side event sequence number (required)
4. Access port name (required)
5. Whether OMC automatic recovery is authorized. Allowed values: yes, no (required)
6. Service recovery plan name (required)
7. Service recovery plan details (required)
8. Service recovery plan execution end time (optional)

## Expected Output
1. Subscription result. Allowed values: success, failure
2. Subscription failure reason (optional)
3. After successful subscription, report messages according to <Notification Data Format>
```

### 1.3.15 validateNotificationPromptAndDataFilling

**API Definition**

```java
public FilledParamData validateNotificationPromptAndDataFilling(
        String prompt, Map<String, Object> schema, TemplateUri templateUri)
```

**Typical scenarios**: the server agent validates a received Notification-T subscription message, extracts the subscription parameters (topic/condition/report format), and establishes the subscription accordingly.

**Function Description**: validates whether a Notification-T notification subscription prompt message matches the template and slot constraints, and extracts parameters per the caller's schema (subscription topic, subscribe condition, notification data format, etc.). The pipeline and exception semantics are the same as [validateTaskPromptAndDataFilling](#1312-validatetaskpromptanddatafilling).

**Input**

| Parameter | Type | Required | Description |
| ---- | ---- | ---- | ---- |
| prompt | String | Yes (non-blank) | Notification subscription prompt message text to validate |
| schema | Map&lt;String, Object&gt; | Yes | Caller-provided parameter JSON Schema |
| templateUri | TemplateUri | Yes | Notification-T template (the prefix segment must be `Notification-T`) |

**Request Example**

```java
import net.openan.a2at.sdk.server.A2ATServer;

A2ATServer server = new A2ATServer(Path.of("server.env"));

Map<String, Object> validationSchema = Map.of(
        "type", "object",
        "properties", Map.of(
                "topic", Map.of("type", "string", "description", "Subscription topic (required). The name of the event topic to subscribe to."),
                "subscriptionCondition", Map.of(
                        "type", "string", "description", "Subscribe condition (optional). The condition description to subscribe to."),
                "notificationDataFormat", Map.of(
                        "type", "string", "description", "Notification data format (required). The description of the notification data format to report.")),
        "required", List.of("topic", "notificationDataFormat"));

// promptText is the notification subscription prompt message text generated by the client
FilledParamData result = server.validateNotificationPromptAndDataFilling(
        promptText, validationSchema, templateUri);
```

**Output**

On success, returns `FilledParamData` (structure same as [1.3.12](#1312-validatetaskpromptanddatafilling)); `data()` holds the parameters extracted per the schema (subscription topic, subscribe condition, notification data format, etc.).

On failure, throws `ContentValidationException` (structure same as 1.3.12). Error codes:

- `validation_semantic_rejected` (required parameter missing or invalid value)

- `validation_llm_infrastructure_error` (LLM infrastructure failure, retryable)

- `validation_prompt_resource_not_found` (validation prompt resources missing)

Programming errors: a null prompt / schema / templateUri throws `NullPointerException`; a blank prompt throws `IllegalArgumentException`.

**Response Example**

```text
result.data() =
{
  topic=Service recovery event,
  subscriptionCondition=Subnetwork name: xx subnetwork,
  notificationDataFormat=Service recovery event data includes: service recovery plan execution status (not started, ended), complaint diagnosis task sequence number, OSS-side event sequence number, access port name, whether OMC automatic recovery is authorized (yes, no), service recovery plan name, service recovery plan details.
}
```

### 1.3.16 generateAuthPromptFromText

**API Definition**

```java
public MetadataContent generateAuthPromptFromText(String text, TemplateUri templateUri)
```

**Typical scenarios**: the client agent converts a natural-language authorization request (add/modify/delete/query network operation authorization policies) into an Authorization-T message.

**Function Description**: generates an authorization policy operation prompt message from natural-language text with the specified Authorization-T template, skipping scenario recognition. One LLM slot-extraction step runs, then the template is rendered deterministically. The Authorization-T slot schema is bundled with `a2a-t-resources` and works out of the box under the classpath resource source.

**Input**

| Parameter | Type | Required | Description |
| ---- | ---- | ---- | ---- |
| text | String | Yes | Natural-language authorization description (operation type + network operation authorization policy content) |
| templateUri | TemplateUri | Yes | Authorization-T template: `StandardTemplates.AUTHORIZATION_POLICY_MANAGEMENT` (`Authorization-T/authorization-policy-management/v1`) |

**Request Example**

```java
import java.nio.file.Path;
import net.openan.a2at.sdk.client.A2ATClient;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.StandardTemplates;

A2ATClient client = new A2ATClient(Path.of("client.env"));

MetadataContent result = client.generateAuthPromptFromText(
        "Add an authorization for the campus private network, use service recovery for handling, do a tunnel optimization, and leave the validity period to be filled in later",
        StandardTemplates.AUTHORIZATION_POLICY_MANAGEMENT);
```

**Output**

On success, returns `MetadataContent` (structure same as [1.3.10](#1310-generatetaskpromptfromtext); `extensionUri` is `https://projects.tmforum.org/a2aproject/telecommunication/extensions/Authorization-T/v1`).

On failure, throws `PromptGenerationException` (structure same as 1.3.10); for example, an operation type outside the "add/modify/delete/query authorization policy" range is rejected with `slot_validation_error`. Programming errors: a null text or templateUri throws `NullPointerException`.

**Response Example**

```text
templateUri : Authorization-T/authorization-policy-management/v1
extensionUri: https://projects.tmforum.org/a2aproject/telecommunication/extensions/Authorization-T/v1
promptText  :
## Authorization Policy Operation Type
add authorization policy

## Authorization Policy Operation Description
Please complete the corresponding authorization operation based on <Authorization Policy Operation Type> and <Network Operation Authorization Policy List>, and return the authorization policy operation execution result in the structure defined in <Expected Output>. <Expected Output> indicates the expected return content.

## Network Operation Authorization Policy List
campus private network, service recovery, tunnel optimization

## Expected Output
1. Authorization operation execution result. Allowed values: success, failure, partial success
2. When the authorization operation is executed successfully, return the <Network Operation Authorization Policy List> that was executed successfully
3. When the authorization operation fails or is partially successful, return a failure list containing the authorization policies and the failure reasons
```

### 1.3.17 generateAuthPromptFromDataWithSchema

**API Definition**

```java
public MetadataContent generateAuthPromptFromDataWithSchema(
        Map<String, Object> data, Map<String, Object> schema, TemplateUri templateUri)
```

**Typical scenarios**: the client agent converts structured authorization policy data submitted field by field by an authorization management UI or system into an Authorization-T message.

**Function Description**: generates an authorization policy operation prompt from structured data plus a semantic schema with the specified Authorization-T template, skipping scenario recognition. The semantic constraints are the same as 1.3.11.

**Input**

| Parameter | Type | Required | Description |
| ---- | ---- | ---- | ---- |
| data | Map&lt;String, Object&gt; | Yes | Structured authorization input (operation type, policy count, policy detail list, etc.) |
| schema | Map&lt;String, Object&gt; | Yes (non-empty) | Field-semantics JSON Schema |
| templateUri | TemplateUri | Yes | Authorization-T template |

**Request Example**

```java
import java.util.List;
import java.util.Map;

Map<String, Object> data = Map.of(
        "operationType", "add authorization policy",
        "policyCount", 2,
        "details", List.of(
                Map.of("businessScenario", "campus private network", "handlingType", "service recovery",
                        "operationName", "tunnel optimization", "validityPeriod", "permanently valid"),
                Map.of("businessScenario", "medical dedicated line", "handlingType", "service restoration",
                        "operationName", "frequency band adjustment", "validityPeriod", "2026-06-01~2030-06-18")));

Map<String, Object> schema = Map.of(
        "type", "object",
        "properties", Map.of(
                "operationType", Map.of(
                        "type", "string",
                        "enum", List.of("add authorization policy", "modify authorization policy", "delete authorization policy", "query authorization policy")),
                "policyCount", Map.of("type", "integer", "description", "Number of policies to add"),
                "details", Map.of(
                        "type", "array",
                        "description", "Policy detail list",
                        "items", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "businessScenario", Map.of("type", "string"),
                                        "handlingType", Map.of("type", "string"),
                                        "operationName", Map.of("type", "string"),
                                        "validityPeriod", Map.of("type", "string"))))));

MetadataContent result = client.generateAuthPromptFromDataWithSchema(
        data, schema, StandardTemplates.AUTHORIZATION_POLICY_MANAGEMENT);
```

**Output**

On success, returns `MetadataContent` (structure same as [1.3.10](#1310-generatetaskpromptfromtext); `extensionUri` is `https://projects.tmforum.org/a2aproject/telecommunication/extensions/Authorization-T/v1`).

On failure, throws `PromptGenerationException` (structure same as 1.3.10). Programming errors: a null argument throws `NullPointerException`; an empty schema map throws `IllegalArgumentException`.

**Response Example**

```text
templateUri : Authorization-T/authorization-policy-management/v1
extensionUri: https://projects.tmforum.org/a2aproject/telecommunication/extensions/Authorization-T/v1
promptText  :
## Authorization Policy Operation Type
add authorization policy

## Authorization Policy Operation Description
Please complete the corresponding authorization operation based on <Authorization Policy Operation Type> and <Network Operation Authorization Policy List>, and return the authorization policy operation execution result in the structure defined in <Expected Output>. <Expected Output> indicates the expected return content.

## Network Operation Authorization Policy List
campus private network, service recovery, tunnel optimization, permanently valid; medical dedicated line, service restoration, frequency band adjustment, 2026-06-01~2030-06-18

## Expected Output
1. Authorization operation execution result. Allowed values: success, failure, partial success
2. When the authorization operation is executed successfully, return the <Network Operation Authorization Policy List> that was executed successfully
3. When the authorization operation fails or is partially successful, return a failure list containing the authorization policies and the failure reasons
```

### 1.3.18 validateAuthPromptAndDataFilling

**API Definition**

```java
public FilledParamData validateAuthPromptAndDataFilling(
        String prompt, Map<String, Object> schema, TemplateUri templateUri)
```

**Typical scenarios**: the server agent validates a received Authorization-T message, extracts the operation type and policy list, and performs the authorization policy management action accordingly.

**Function Description**: validates whether an Authorization-T authorization prompt message matches the template and slot constraints, and extracts parameters per the caller's schema (operation type, policy list, etc.). The pipeline and exception semantics are the same as 1.3.12. Field requirements are validated differently per operation type: an add entry must carry business scenario / handling type / operation name / validity period; a modify entry must carry the policy identifier and the new validity period; a delete entry may be a policy identifier or condition fields.

**Input**

| Parameter | Type | Required | Description |
| ---- | ---- | ---- | ---- |
| prompt | String | Yes (non-blank) | Authorization prompt message text to validate |
| schema | Map&lt;String, Object&gt; | Yes | Caller-provided parameter JSON Schema (containing `operationType`, `policyList`, etc.) |
| templateUri | TemplateUri | Yes | Authorization-T template (the prefix segment must be `Authorization-T`) |

**Request Example**

```java
import net.openan.a2at.sdk.server.A2ATServer;

A2ATServer server = new A2ATServer(Path.of("server.env"));

Map<String, Object> paramSchema = MAPPER.readValue(
        Path.of("param-schema.json").toFile(), new TypeReference<Map<String, Object>>() {});
// paramSchema declares: operationType (enum: add/modify/delete/query authorization policy), policyList
// (array whose entries carry policyId/businessScenario/handlingType/operationName/validityPeriod)

FilledParamData result = server.validateAuthPromptAndDataFilling(
        metadata.promptText(), paramSchema, StandardTemplates.AUTHORIZATION_POLICY_MANAGEMENT);
```

**Output**

On success, returns `FilledParamData` (structure same as [1.3.12](#1312-validatetaskpromptanddatafilling)); `data()` holds the parameters extracted per the schema (operation type, policy list, etc.).

On failure, throws `ContentValidationException` (structure same as 1.3.12). Error codes:

- `validation_semantic_rejected` (required parameter missing or invalid value, e.g. an add entry missing required fields or a validity period format error)

- `validation_llm_infrastructure_error` (LLM infrastructure failure, retryable)

- `validation_prompt_resource_not_found` (validation prompt resources missing)

Programming errors: a null prompt / schema / templateUri throws `NullPointerException`; a blank prompt throws `IllegalArgumentException`.

**Response Example**

```text
result.data() =
{
  operationType=add authorization policy,
  policyList=[
    {policyId=null, businessScenario=campus private network, handlingType=service recovery, operationName=tunnel optimization, validityPeriod=permanently valid},
    {policyId=null, businessScenario=medical dedicated line, handlingType=service restoration, operationName=frequency band adjustment, validityPeriod=2026-06-01~2030-06-18}
  ]
}
```

### 1.3.19 generateTaskPrompt

**API Definition**

```java
public PromptGenerationResult generateTaskPrompt(Object userInput)
```

**Typical scenarios**: the scenario-auto-routing entry point of the client agent: without specifying a template, the SDK recognizes the business scenario of the user input and generates the corresponding message; suitable when the scenario set is known and a simplified integration is desired.

**Function Description**: the general task prompt generation entry point, which locates the template **via scenario recognition**: the natural-language or structured input first goes through LLM scenario recognition, then slot extraction and rendering are completed with the built-in template of the recognized scenario. To specify a template explicitly, use the FromText / FromDataWithSchema API families in 1.3.1x.

**Input**

| Parameter | Type | Required | Description |
| ---- | ---- | ---- | ---- |
| userInput | Object | Yes | Task description: a `String` (natural language) or a `Map<String, Object>` (structured input); both go through LLM extraction — there is no zero-LLM shortcut |

**Request Example**

```java
A2ATClient client = new A2ATClient(Path.of("client.env"));

PromptGenerationResult result = client.generateTaskPrompt(
        "Generate an Incident event subscription task: the notification topic is Incident, "
                + "the subscription levels are critical, medium, high, and low, "
                + "and the notification data format is DataPart");

if (result.success()) {
    String processedPrompt = result.promptText();  // sent as A2A message metadata
} else {
    System.out.println(result.failure().code() + ": " + result.failure().message());
}
```

**Output**

On success (`success()` is `true`; no exception is thrown, the result is returned in the record):

| Field/Method | Type | Description |
| --------- | ---- | ---- |
| success() | boolean | Always true |
| promptText() | String | Rendered task prompt message text, sent as A2A message metadata |
| failure() | PromptGenerationFailure | Always null |

On failure (`success()` is `false`; no exception is thrown, the failure payload is returned in the record):

| Field/Method | Type | Description |
| --------- | ---- | ---- |
| success() | boolean | Always false |
| promptText() | String | Always null |
| failure() | PromptGenerationFailure | Standardized failure payload; structure in the table below |

`PromptGenerationFailure` structure:

| Field | Type | Description |
| ---- | ---- | ---- |
| code | String | Machine-readable error code: `scenario_not_matched` (scenario recognition missed), `prompt_resource_load_error` (prompt resource load failure), `template_not_found` (template missing), `render_failed` (rendering failure) |
| message | String | Human-readable failure description |
| stage | String | Stage where the failure occurred: `scenario` (scenario recognition), `generation` (template loading/rendering) |

**Response Example**

On success:

```text
result.success() = true
result.promptText() =
## Subscription Description
Based on the following <Notification Topic>, <Subscribe Condition>, <Notification Data Format>, and <Expected Output> information, complete the network-side intelligent fault Incident subscription and reporting task.

## Notification Topic
The name of this topic is "incident"

## Subscribe Condition
Fault levels are "critical", "medium", "high", "low"

## Notification Data Format
Report Incident data via DataPart

## Expected Output
1. Subscription result, success or failure
2. Reason for subscription failure (optional)
```

On failure (the input does not match any built-in scenario):

```text
result.success() = false
result.failure() =
{ code=scenario_not_matched, message=Scenario recognition failed., stage=scenario }
```

### 1.3.20 checkTaskPrompt

**API Definition**

```java
public PromptComplianceResult checkTaskPrompt(String processedPromptText)
```

**Typical scenarios**: the server agent performs a protocol-completeness gate check (scenario/template/slot compliance) on a received task message, in scenarios where only a pass/fail conclusion is needed and no parameters must be extracted.

**Function Description**: the general task prompt compliance-check entry point (server side): it performs scenario matching, template compliance validation, and slot validation on the processed task prompt sent by the client (whether it is enabled is controlled by `A2AT_PROMPT_COMPLIANCE_ENABLED`). Difference from `validateTaskPromptAndDataFilling`: this API extracts no parameters and takes no caller-provided schema; it returns only the standardized pass/fail conclusion, suitable for scenarios that only need a protocol-completeness gate.

**Input**

| Parameter | Type | Required | Description |
| ---- | ---- | ---- | ---- |
| processedPromptText | String | Yes | A2A-T protocol message text sent by the client (the value keyed by the extension URI in `Message.metadata`) |

**Request Example**

```java
import net.openan.a2at.sdk.server.A2ATServer;
import net.openan.a2at.sdk.server.model.PromptComplianceResult;

A2ATServer server = new A2ATServer(Path.of("server.env"));

PromptComplianceResult result = server.checkTaskPrompt(processedPrompt);

if (result.success()) {
    System.out.println("prompt check passed");
} else {
    System.out.println(result.failure().message());
}
```

**Output**

On success (`success()` is `true`; no exception is thrown, the result is returned in the record):

| Field/Method | Type | Description |
| --------- | ---- | ---- |
| success() | boolean | Always true |
| failure() | PromptComplianceFailure | Always null |

On failure (`success()` is `false`; no exception is thrown, the failure payload is returned in the record):

| Field/Method | Type | Description |
| --------- | ---- | ---- |
| success() | boolean | Always false |
| failure() | PromptComplianceFailure | Standardized failure payload; structure in the table below |

`PromptComplianceFailure` structure:

| Field | Type | Description |
| ---- | ---- | ---- |
| code | String | Machine-readable error code: `processed_prompt_parse_error` (message parsing/scenario recognition failure), `template_not_found` (template missing), `slot_validation_error` (slot validation failure, including missing required values, out-of-range values, and format mismatches) |
| message | String | Human-readable failure description |
| stage | String | Stage where the failure occurred: `prompt_parse` (message parsing), `generation` (template loading), `slot_validation` (slot validation) |

**Response Example**

On success:

```text
prompt check passed
```

On failure (the message is missing a required slot; the error code and stage come from the server-side compliance pipeline):

```text
result.success() = false
result.failure() =
{ code=slot_validation_error, message=Required slot 'task_object' is missing., stage=slot_validation }
```

### 1.3.21 generateNegotiationAbortPromptFromText

**API Definition**

```java
public MetadataContent generateNegotiationAbortPromptFromText(
        String text, NegotiationContext context, TemplateUri templateUri)
```

**Typical scenario**: either negotiation party concludes that the negotiation cannot continue (round limit reached, timeout, token budget exhausted, etc.) and generates a negotiation abort message from a natural-language termination statement.

**Functionality**: generates a negotiation abort message from natural-language text with one LLM structured-extraction step (extracting the termination reason). The abort message is negotiation-type independent; `templateUri` must be the common abort template (`StandardTemplates.NEGOTIATION_ABORT`, URI `Negotiation-T/common/abort/v1`), and the context performative should be `ABORT`.

**Input**

| Parameter | Type | Required | Description |
| ---- | ---- | ---- | ---- |
| text | String | Yes | Natural-language description of the termination reason |
| context | `NegotiationContext` | Yes | Negotiation session context (performative `ABORT`) |
| templateUri | TemplateUri | Yes | Common abort template |

**Request example**

```java
MetadataContent abort = client.generateNegotiationAbortPromptFromText(
        "Reached the negotiation round limit. This negotiation is confirmed and ended.",
        ctx,
        StandardTemplates.NEGOTIATION_ABORT);
```

**Output**

On success returns `MetadataContent` (same structure as [1.3.1](#131-generatenegotiationproposepromptfromtext)).

On failure throws `NegotiationGenerationException` (same structure as 1.3.1). Error codes:

- `template_not_found` (template or prompt resource missing)

- `negotiation_content_extract_failed` (termination reason not extractable from the text, retriable)

- `negotiation_llm_infrastructure_error` (LLM infrastructure failure, retriable)

- `negotiation_invalid_input` (text empty or extracted content does not match the abort phase)

- `negotiation_slot_missing` (required slot missing)

A null argument throws `NullPointerException`; a templateUri whose phase segment is not `abort` throws `IllegalArgumentException`.

**Response example**

```text
templateUri : Negotiation-T/common/abort/v1
extensionUri: https://projects.tmforum.org/a2aproject/telecommunication/extensions/Negotiation-T/v1
promptText  :
## Negotiation Result
Abort

## Negotiation Termination Reason
Reached the negotiation round limit. This negotiation is confirmed and ended.
```

### 1.3.22 generateNegotiationAbortPromptFromData

**API Definition**

```java
public MetadataContent generateNegotiationAbortPromptFromData(
        NegotiationAbortData data, TemplateUri templateUri)
```

**Typical scenario**: either negotiation party programmatically decides that a termination condition holds and generates the abort message from a structured termination reason.

**Functionality**: deterministically generates a negotiation abort message from typed data, **with no LLM call**. The abort message is negotiation-type independent; `templateUri` must be the common abort template.

**Input**

| Parameter | Type | Required | Description |
| ---- | ---- | ---- | ---- |
| data | `NegotiationAbortData(context, content)` | Yes | Negotiation context (performative `ABORT`) + termination content |
| templateUri | TemplateUri | Yes | Common abort template |

Termination content: `NegotiationAbortContent(terminationReason)`, where terminationReason is the required termination reason.

**Request example**

```java
import net.openan.a2at.sdk.negotiation.content.NegotiationAbortContent;
import net.openan.a2at.sdk.negotiation.content.NegotiationAbortData;

MetadataContent abort = client.generateNegotiationAbortPromptFromData(
        new NegotiationAbortData(
                ctx,
                new NegotiationAbortContent("Reached the negotiation round limit. This negotiation is confirmed and ended.")),
        StandardTemplates.NEGOTIATION_ABORT);
```

**Output**

On success returns `MetadataContent` (same structure as [1.3.1](#131-generatenegotiationproposepromptfromtext)).

On failure throws `NegotiationGenerationException` (same structure as 1.3.1). Error codes:

- `template_not_found` (template missing)

- `negotiation_slot_missing` (required slot missing during rendering)

Programming errors: a null argument or null context throws `NullPointerException`; a blank termination reason or a phase segment other than `abort` throws `IllegalArgumentException`.

**Response example**

```text
templateUri : Negotiation-T/common/abort/v1
extensionUri: https://projects.tmforum.org/a2aproject/telecommunication/extensions/Negotiation-T/v1
promptText  :
## Negotiation Result
Abort

## Negotiation Termination Reason
Reached the negotiation round limit. This negotiation is confirmed and ended.
```

### 1.3.23 validateAbortPromptAndDataFilling

**API Definition**

```java
public FilledParamData validateAbortPromptAndDataFilling(
        String prompt, NegotiationContext context, Map<String, Object> schema, TemplateUri templateUri)
```

**Typical scenario**: one party validates the peer abort message, extracts the termination reason, and closes the local negotiation state accordingly.

**Functionality**: validates a negotiation abort message and extracts parameters per the schema. The pipeline is the same as 1.3.7, except that the message must satisfy the abort-phase semantic constraints (conclusion Abort, with a negotiation-termination-reason section) and `templateUri` must be the common abort template.

**Input**: same as 1.3.7, with prompt being the abort message text and templateUri the common abort template.

**Request example**

```java
FilledParamData abortParams = server.validateAbortPromptAndDataFilling(
        abortPrompt, ctx, schema, StandardTemplates.NEGOTIATION_ABORT);
```

**Output**

On success returns `FilledParamData` (same structure as [1.3.7](#137-validateproposepromptanddatafilling)); `data()` carries the parameters extracted from the termination reason per the schema plus the context parameters.

On failure throws `NegotiationParamExtractionException` (same structure as 1.3.7). Error codes:

- `negotiation_invalid_input` (message is not an abort negotiation message or context is null)

- `negotiation_rule_violation` (negotiation context violates the structural rules)

- `negotiation_semantic_rejected` (conclusion is not Abort or the termination-reason section is missing)

- `negotiation_llm_infrastructure_error` (LLM infrastructure failure, retriable)

- `template_not_found` (validation prompt resource missing)

Programming errors: null prompt or schema throws `NullPointerException`; a blank prompt or a phase segment other than `abort` throws `IllegalArgumentException`.

**Response example**

```text
abortParams.data() =
{
  termination_reason=Reached the negotiation round limit. This negotiation is confirmed and ended.,
  id=3dbc13b5-bd57-4c2b-b503-24e381b6c8d3,
  round=1,
  maxRounds=5
}
```
