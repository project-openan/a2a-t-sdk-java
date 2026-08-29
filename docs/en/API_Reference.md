# 1 API Reference

## 1.1 Introduction

The A2A-T SDK exposes exactly two public entry points: the client entry `A2ATClient` (prompt generation, negotiation message generation and validation) and the server entry `A2ATServer` (prompt validation, negotiation message generation and validation). See the API category overview below.

- **API Overview**:

| Category | API | Client/Server | Description | LLM Involved |
| ---- | ------- | -------- | ------- |---------|
| Negotiation-T | `generateNegotiationProposePromptFromText` | A2A-T Client / A2A-T Server | Generates a negotiation propose message from natural-language text | Yes |
| Negotiation-T | `generateNegotiationAcceptPromptFromText` | A2A-T Client / A2A-T Server | Generates a negotiation accept message from natural-language text | Yes |
| Negotiation-T | `generateNegotiationRejectPromptFromText` | A2A-T Client / A2A-T Server | Generates a negotiation reject message from natural-language text | Yes |
| Negotiation-T | `generateNegotiationProposePromptFromData` | A2A-T Client / A2A-T Server | Deterministically generates a negotiation propose message from structured data | No |
| Negotiation-T | `generateNegotiationAcceptPromptFromData` | A2A-T Client / A2A-T Server | Deterministically generates a negotiation accept message from structured data | No |
| Negotiation-T | `generateNegotiationRejectPromptFromData` | A2A-T Client / A2A-T Server | Deterministically generates a negotiation reject message from structured data | No |
| Negotiation-T | `validateProposePromptAndDataFilling` | A2A-T Client / A2A-T Server | Validates a negotiation propose message and extracts parameters per a schema | Yes |
| Negotiation-T | `validateAcceptPromptAndDataFilling` | A2A-T Client / A2A-T Server | Validates a negotiation accept message and extracts parameters per a schema | Yes |
| Negotiation-T | `validateRejectPromptAndDataFilling` | A2A-T Client / A2A-T Server | Validates a negotiation reject message and extracts parameters per a schema | Yes |
| Task-T | `generateTaskPromptFromText` | A2A-T Client | Generates a task prompt from natural-language text with the specified Task-T template (skips scenario recognition) | Yes |
| Task-T | `generateTaskPromptFromDataWithSchema` | A2A-T Client | Generates a task prompt from structured data plus a semantic schema with the specified Task-T template | Yes |
| Task-T | `validateTaskPromptAndDataFilling` | A2A-T Server | Validates a Task-T task prompt and extracts parameters per a schema | Yes |
| Notification-T | `generateNotificationPromptFromText` | A2A-T Client | Generates a notification subscription prompt from natural-language text with the specified Notification-T template | Yes |
| Notification-T | `generateNotificationPromptFromDataWithSchema` | A2A-T Client | Generates a notification subscription prompt from structured data plus a semantic schema with the specified Notification-T template | Yes |
| Notification-T | `validateNotificationPromptAndDataFilling` | A2A-T Server | Validates a Notification-T prompt and extracts parameters per a schema | Yes |
| Authorization-T | `generateAuthPromptFromText` | A2A-T Client | Generates an authorization prompt from natural-language text with the specified Authorization-T template | Yes |
| Authorization-T | `generateAuthPromptFromDataWithSchema` | A2A-T Client | Generates an authorization prompt from structured data plus a semantic schema with the specified Authorization-T template | Yes |
| Authorization-T | `validateAuthPromptAndDataFilling` | A2A-T Server | Validates an Authorization-T prompt and extracts parameters per a schema | Yes |
| General | `generateTaskPrompt` | A2A-T Client | Generates a task prompt from natural-language or structured input via scenario recognition | Yes |
| General | `checkTaskPrompt` | A2A-T Server | Validates the scenario, template, and slot compliance of a task prompt | Yes |

**Common Data Types and Conventions**

- **Negotiation session context:** `NegotiationContext(id, round, maxRounds)` (id in UUID form, round starting at 1, default round budget `DEFAULT_MAX_ROUNDS = 5`); it travels with the message metadata and never goes through the LLM. `NegotiationContext.of(id, round)` uses the default budget, and `nextRound()` advances the round.
- **Exception hierarchy:** every SDK processing failure is a subclass of `A2ATError`; catching `A2ATError` covers all processing failures, and `getCode()` returns the machine-readable error code. All messages are rendered by the SDK from per-code message templates and follow `A2AT_LANGUAGE`. The following business exceptions extend `A2ATBusinessException` and additionally expose `getFacts()` carrying the structured fact values behind the rendered message:
  - Generation failures throw `PromptGenerationException` (Task-T / Notification-T / Authorization-T) or `NegotiationGenerationException` (Negotiation-T);
  - Validation-plus-extraction failures throw `ContentValidationException` (Task-T / Notification-T / Authorization-T) or `NegotiationParamExtractionException` (Negotiation-T).
- **SlotValidationError:** per-slot validation error details, returned with validation-failure exceptions or failure payloads (the `getErrors()` / `failedParameters()` / `errors()` mentioned in the output descriptions of the APIs all refer to this definition):

| Field | Type | Description |
| ---- | ---- | ---- |
| slotName | String | Name of the slot in error |
| code | String | Slot-level error code, taken from the 1.4 error code list, e.g. `slot.not_provided`, `content.param_missing`, `content.entry_field_missing`, `content.format_error` |
| message | String | Human-readable error description, rendered by the SDK from the message template of the error code and following `A2AT_LANGUAGE` |
| facts | Map&lt;String, String&gt; | Structured fact values behind the rendered message (e.g. `section_label`, `index`, `field_label`), may be null |

- **Template URI:** the `A2ATClient` and `A2ATServer` facades declare the template selection of every template-specific method as a plain `String templateUri` (e.g. `Task-T/network-layer/private-line-complaint/v1`). Prefer passing the `net.openan.a2at.sdk.core.model.StandardTemplates` String constants (constant name suffixed `_URI`, each with a typed `TemplateUri` twin named without the suffix); strings coming from outside the code are passed directly. The typed value type `TemplateUri` is still used by the lower-level service layer (e.g. `NegotiationContentService`), where it can be obtained with `TemplateUri.parse(String)`, which returns an `Optional<TemplateUri>` and never throws. The currently supported template URI constants (String form) are listed below:

| Constant | Description | TemplateUri |
| ---- | ---- | ---- |
| StandardTemplates.ENERGY_SAVING_URI | Task-T energy-saving task template | Task-T/network-layer/ran-energy-saving/v1 |
| StandardTemplates.PRIVATE_LINE_COMPLAINT_URI | Task-T private-line complaint task template | Task-T/network-layer/private-line-complaint/v1 |
| StandardTemplates.SUBSCRIBE_INCIDENT_URI | Notification-T incident subscription notification template | Notification-T/network-layer/subscribe-incident/v1 |
| StandardTemplates.SERVICE_RECOVERY_URI | Notification-T service recovery notification template | Notification-T/network-layer/service-recovery/v1 |
| StandardTemplates.AUTHORIZATION_POLICY_MANAGEMENT_URI | Authorization-T authorization policy management template | Authorization-T/authorization-policy-management/v1 |
| StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE_URI | Negotiation-T information negotiation propose template | Negotiation-T/information-negotiation/propose/v1 |
| StandardTemplates.INFORMATION_NEGOTIATION_ACCEPT_REJECT_URI | Negotiation-T information negotiation accept/reject template | Negotiation-T/information-negotiation/accept-reject/v1 |
| StandardTemplates.TARGET_NEGOTIATION_PROPOSE_URI | Negotiation-T target negotiation propose template | Negotiation-T/target-negotiation/propose/v1 |
| StandardTemplates.TARGET_NEGOTIATION_ACCEPT_REJECT_URI | Negotiation-T target negotiation accept/reject template | Negotiation-T/target-negotiation/accept-reject/v1 |
| StandardTemplates.FEASIBILITY_NEGOTIATION_PROPOSE_URI | Negotiation-T feasibility negotiation propose template | Negotiation-T/feasibility-negotiation/propose/v1 |
| StandardTemplates.FEASIBILITY_NEGOTIATION_ACCEPT_REJECT_URI | Negotiation-T feasibility negotiation accept/reject template | Negotiation-T/feasibility-negotiation/accept-reject/v1 |
| StandardTemplates.NEGOTIATION_ABORT_URI | Negotiation-T common negotiation abort template | Negotiation-T/common/abort/v1 |

- **Facade template URI failure policy (both `A2ATClient` and `A2ATServer`):** every facade method that takes a `templateUri` parameter (including `getPrompt`) accepts a raw URI string. A null template URI throws `NullPointerException`; a blank or malformed URI (fewer than three segments, or a segment that is not simple) throws `IllegalArgumentException` with the message `Unparseable template URI: <input>`. This replaces the previous behavior where a null typed template URI on the server task/notification/authorization validate trio threw `ContentValidationException` with the code `negotiation.invalid_input` — such programming errors are now plain JDK exceptions, not part of the `A2ATError` tree.



## 1.2 Constraints and Limitations

- Some APIs involve LLM calls. Control the call rate and concurrency of these APIs according to the concurrency capacity of the connected model service and your business time-limit requirements.
- APIs that involve LLM calls guard the length of text `text` inputs: when the input exceeds the `A2AT_INPUT_TEXT_MAX_CHARS` characters configured in `client.env` or `server.env`, the error code `input.text_too_long` is reported; the default value of this configuration item is 16384 (16×1024). Structured data that involves no LLM calls is not subject to this limit.

## 1.3 API Description

### 1.3.1 generateNegotiationProposePromptFromText

**API Definition**

```java
public MetadataContent generateNegotiationProposePromptFromText(
        String text, NegotiationContext context, String templateUri)
```

**Typical scenarios**: after receiving a Task-T task message with missing parameters, the server agent uses natural language to send a "supplement the missing information" negotiation request to the client agent; also applicable when the client agent initiates a target-clarification or feasibility-evaluation request to the server.

**Function Description**: generates a structured negotiation propose-phase message from natural-language text. Execution flow: the template is loaded first, then one LLM content-extraction step runs, and finally the template is rendered deterministically. Applicable to the initiator of information/target/feasibility negotiations.

**Input**

| Parameter | Type | Required | Description |
| ---- | ---- | ---- | ---- |
| text | String | Yes | Natural-language text describing the negotiation proposal (e.g. the list of missing information items to request); the input length is limited by the `A2AT_INPUT_TEXT_MAX_CHARS` configuration item, default 16384 |
| context | NegotiationContext | Yes | Negotiation session context, injected directly into the `negotiationContext` metadata of the generated message without going through the LLM |
| templateUri | String | Yes | Propose template, e.g. `StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE_URI` |

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
        "3dbc13b5-bd57-4c2b-b503-24e381b6c8d3", 1, NegotiationContext.DEFAULT_MAX_ROUNDS);

MetadataContent propose = client.generateNegotiationProposePromptFromText(
        "Please provide the following missing information: complaint category: private line interruption or poor private line quality. "
                + "Both parameters are required; diagnosis cannot start without them.",
        ctx,
        StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE_URI);

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

- `template.not_found` (template or prompt resource missing)

- `negotiation.content_extract_failed` (failed to extract structured content from the text, retryable)

- `llm.invocation_failed` (LLM transport failure, retryable)

- `llm.response_invalid` (LLM response violates the step contract, retryable)

- `negotiation.invalid_input` (text is blank, the extracted content contradicts the phase, or the confirm request contradicts the other sections)

- `negotiation.field_missing` (a required field is missing)

A null argument throws `NullPointerException`; a blank or malformed templateUri (see the facade failure policy in 1.1) or a templateUri whose phase segment is not `propose` throws `IllegalArgumentException`.

**Response Example**

```text
templateUri : Negotiation-T/information-negotiation/propose/v1
extensionUri: https://projects.tmforum.org/a2aproject/telecommunication/extensions/Negotiation-T/v1
promptText  :
## Information Negotiation
Please supplement the relevant content based on <Required Information Items>.

## Required Information Items
1. Complaint Category: e.g. dedicated-line quality degradation
```

### 1.3.2 generateNegotiationAcceptPromptFromText

**API Definition**

```java
public MetadataContent generateNegotiationAcceptPromptFromText(
        String text, NegotiationContext context, String templateUri)
```

**Typical scenarios**: after receiving the peer's information-negotiation request, the negotiation responder (usually the client agent) supplements/delivers the requested information in natural language and generates an accept message to return, e.g. confirming that diagnosis can start after supplementing the access port name and complaint category.

**Function Description**: generates a negotiation accept message from natural-language text. One LLM content-extraction step (the extracted conclusion must be `ACCEPT`, otherwise it is rejected with `negotiation.invalid_input`) plus deterministic rendering. Applicable to the negotiation responder supplementing/delivering information.

**Input**

| Parameter | Type | Required | Description |
| ---- | ---- | ---- | ---- |
| text | String | Yes | Natural-language text describing the acceptance (e.g. the list of supplemented/delivered information items); the input length is limited by the `A2AT_INPUT_TEXT_MAX_CHARS` configuration item, default 16384 |
| context | NegotiationContext | Yes | Negotiation session context |
| templateUri | String | Yes | Accept-reject template, e.g. `StandardTemplates.INFORMATION_NEGOTIATION_ACCEPT_REJECT_URI` |

**Request Example**

```java
MetadataContent accept = client.generateNegotiationAcceptPromptFromText(
        "I agree to supplement the following information: 1. Access port name: P533-Zhujiang Old Town-PTN3900-23-TPA1EG24-1; "
                + "2. Complaint category: dedicated-line quality degradation. "
                + "The information is complete and diagnosis can start.",
        ctx,
        StandardTemplates.INFORMATION_NEGOTIATION_ACCEPT_REJECT_URI);
```

**Output**

On success, returns `MetadataContent` (structure same as [1.3.1](#131-generatenegotiationproposepromptfromtext)).

On failure, throws `NegotiationGenerationException` (structure same as 1.3.1). Error codes:

- `template.not_found` (template or prompt resource missing)

- `negotiation.content_extract_failed` (failed to extract structured content from the text, retryable)

- `llm.invocation_failed` (LLM transport failure, retryable)

- `llm.response_invalid` (LLM response violates the step contract, retryable)

- `negotiation.invalid_input` (text is blank, or the extracted conclusion is not `ACCEPT`)

- `negotiation.field_missing` (a required field is missing)

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
        String text, NegotiationContext context, String templateUri)
```

**Typical scenarios**: when the negotiation responder (usually the client agent) cannot satisfy the peer's negotiation request, it generates a reject message in natural language to return and end the current negotiation round, e.g. the access port name cannot be provided because the site inventory is unavailable.

**Function Description**: generates a negotiation reject message from natural-language text. One LLM content-extraction step (the extracted conclusion must be `REJECT`, otherwise it is rejected with `negotiation.invalid_input`) plus deterministic rendering.

**Input**: same as [generateNegotiationAcceptPromptFromText](#132-generatenegotiationacceptpromptfromtext), where text is natural language describing the rejection reason.

**Request Example**

```java
MetadataContent reject = client.generateNegotiationRejectPromptFromText(
        "I refuse to supplement the information: the access port name cannot be provided because the site inventory is unavailable. This negotiation is over.",
        ctx,
        StandardTemplates.INFORMATION_NEGOTIATION_ACCEPT_REJECT_URI);
```

**Output**

On success, returns `MetadataContent` (structure same as [1.3.1](#131-generatenegotiationproposepromptfromtext)).

On failure, throws `NegotiationGenerationException` (structure same as 1.3.1). Error codes:

- `template.not_found` (template or prompt resource missing)

- `negotiation.content_extract_failed` (failed to extract structured content from the text, retryable)

- `llm.invocation_failed` (LLM transport failure, retryable)

- `llm.response_invalid` (LLM response violates the step contract, retryable)

- `negotiation.invalid_input` (text is blank, or the extracted conclusion is not `REJECT`)

- `negotiation.field_missing` (a required field is missing)

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
        NegotiationProposeData data, String templateUri)
```

**Typical scenarios**: the same initiation scenarios as the fromText variant, but the input is structured data constructed by the business system (e.g. the server agent automatically generates the negotiation-request items from the missing-slot list detected by `validateTaskPromptAndDataFilling`), suitable for scenarios that require deterministic message content and want to avoid the nondeterminism of LLM extraction.

**Function Description**: deterministically generates a negotiation propose message from structured-data input, **without calling the LLM**.

**Input**

| Parameter | Type | Required | Description |
| ---- | ---- | ---- | ---- |
| data | `NegotiationProposeData(context, content)` | Yes | Negotiation context plus typed propose content; the content type depends on the negotiation type |
| templateUri | String | Yes | Propose template |

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

NegotiationContext ctx = new NegotiationContext("3dbc13b5-bd57-4c2b-b503-24e381b6c8d3", 2, 5);

MetadataContent propose = client.generateNegotiationProposePromptFromData(
        new NegotiationProposeData(
                ctx,
                new InformationProposeContent(
                        List.of(
                                new NegotiationItem("Access Port Name", "e.g. P533-Zhujiang Old Town-PTN3900-23-TPA1EG24-1"),
                                new NegotiationItem("Complaint Category", "e.g. dedicated-line quality degradation"),
                                new NegotiationItem("Private Line Service Identifier", null)),
                        "OR")),
        StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE_URI);
```

**Output**

On success, returns `MetadataContent` (structure same as [1.3.1](#131-generatenegotiationproposepromptfromtext); negotiation messages always carry `negotiationContext`).

On failure, throws `NegotiationGenerationException` (structure same as 1.3.1). Error codes:

- `template.not_found` (template missing)

- `negotiation.content_invalid` (a typed content field is invalid, e.g. blank required items or a blank required description)

- `template.render_failed` (template rendering failure)

There are also two categories of programming errors (outside the `A2ATError` tree, standard JDK exceptions): a null argument or a null context within it throws `NullPointerException`; a blank or malformed templateUri (see the facade failure policy in 1.1), a content type that does not match the template's negotiation type, or a phase segment that is not `propose`, throws `IllegalArgumentException`.

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
        NegotiationEndingData data, String templateUri)
```

**Typical scenarios**: the negotiation responder (usually the client agent) programmatically fills in the parameters per the peer's requested slot list and generates an accept message from structured items to return.

**Function Description**: deterministically generates a negotiation accept message from structured-data input, **without calling the LLM**. `content.conclusion()` must be `ACCEPT`; any other conclusion (including `ABORT`) is rejected with the `negotiation.conclusion_mismatch` business error.

**Input**

| Parameter | Type | Required | Description |
| ---- | ---- | ---- | ---- |
| data | `NegotiationEndingData(context, content)` | Yes | Negotiation context plus typed accept content (conclusion is `ACCEPT`) |
| templateUri | String | Yes | Accept-reject template |

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
        StandardTemplates.INFORMATION_NEGOTIATION_ACCEPT_REJECT_URI);
```

**Output**

On success, returns `MetadataContent` (structure same as [1.3.1](#131-generatenegotiationproposepromptfromtext)).

On failure, throws `NegotiationGenerationException` (structure same as 1.3.1). Error codes:

- `template.not_found` (template missing)

- `negotiation.content_invalid` (a typed content field is invalid, e.g. blank required items or a blank required description)

- `template.render_failed` (template rendering failure)

Programming errors: a null argument or a null context within it throws `NullPointerException`; a blank or malformed templateUri (see the facade failure policy in 1.1), a mismatched content type or a phase segment that is not `accept-reject` throws `IllegalArgumentException`; a `conclusion` that is not `ACCEPT` is rejected with the `negotiation.conclusion_mismatch` business error.

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
        NegotiationEndingData data, String templateUri)
```

**Typical scenarios**: the negotiation responder, after programmatically determining that the peer's request cannot be satisfied, generates a reject message from structured items (the items that cannot be provided and the reasons) to return.

**Function Description**: deterministically generates a negotiation reject message from structured-data input, **without calling the LLM**. `content.conclusion()` must be `REJECT`; any other conclusion is rejected with the `negotiation.conclusion_mismatch` business error.

**Input**: same as [generateNegotiationAcceptPromptFromData](#135-generatenegotiationacceptpromptfromdata), but with the conclusion `REJECT`. Reject content: `InformationEndingContent(REJECT, items)` (items that cannot be provided and the reasons), `TargetEndingContent(REJECT, null, failureReason)` (rejection reason), `FeasibilityEndingContent(REJECT, feasibilitySummary)` (infeasibility conclusion summary).

**Request Example**

```java
MetadataContent reject = client.generateNegotiationRejectPromptFromData(
        new NegotiationEndingData(
                ctx,
                new InformationEndingContent(
                        NegotiationConclusion.REJECT,
                        List.of(new NegotiationItem("Access Port Name", "cannot be provided because the port inventory is temporarily unavailable on the workbench side")))),
        StandardTemplates.INFORMATION_NEGOTIATION_ACCEPT_REJECT_URI);
```

**Output**

On success, returns `MetadataContent` (structure same as [1.3.1](#131-generatenegotiationproposepromptfromtext)).

On failure, throws `NegotiationGenerationException` (structure same as 1.3.1). Error codes:

- `template.not_found` (template missing)

- `negotiation.content_invalid` (a typed content field is invalid, e.g. blank required items or a blank required description)

- `template.render_failed` (template rendering failure)

Programming errors: a null argument or a null context within it throws `NullPointerException`; a blank or malformed templateUri (see the facade failure policy in 1.1), a mismatched content type or a phase segment that is not `accept-reject` throws `IllegalArgumentException`; a `conclusion` that is not `REJECT` is rejected with the `negotiation.conclusion_mismatch` business error.

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
        String prompt, NegotiationContext context, Map<String, Object> schema, String templateUri)
```

**Typical scenarios**: an outbound self-check by the initiator (server agent) before sending a negotiation request; or the receiver (client agent) validating an inbound negotiation request and extracting the list of slots to supplement, driving the subsequent parameter filling.

**Function Description**: validates whether a negotiation propose message is a properly formatted negotiation message, and extracts parameters from it per the caller-provided JSON Schema.

**Input**

| Parameter | Type | Required | Description |
| ---- | ---- | ---- | ---- |
| prompt | String | Yes | Negotiation propose message text to validate (`MetadataContent.promptText()`); the input length is limited by the `A2AT_INPUT_TEXT_MAX_CHARS` configuration item, default 16384 |
| context | NegotiationContext | Yes | Negotiation context traveling with the message; a null value is reported as `negotiation.invalid_input` |
| schema | Map&lt;String, Object&gt; | Yes | Caller-provided parameter JSON Schema declaring the parameters to extract |
| templateUri | String | Yes | Propose template |

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
        proposePrompt, ctx, schema, StandardTemplates.INFORMATION_NEGOTIATION_PROPOSE_URI);

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

- `negotiation.invalid_input` (the message is not a negotiation message, or the context is null)

- `negotiation.rule_violation` (the negotiation context violates a rule; the nested slot error codes in `getErrors()` identify the concrete rule, e.g. `negotiation.invalid_context_id`, `negotiation.round_exceeded`)

- `negotiation.semantic_rejected` (semantic validation rejected the message; the per-slot details in `getErrors()` use the closed `negotiation.*` code set, e.g. `negotiation.conclusion_content_mismatch`, `negotiation.missing_result_content`, `negotiation.field_inconsistency`)

- `llm.invocation_failed` / `llm.response_invalid` (LLM failures, retryable)

- `template.not_found` (validation prompt resources missing)

Programming errors: a null prompt, schema or templateUri throws `NullPointerException`; a blank prompt, a blank or malformed templateUri (see the facade failure policy in 1.1), or a mismatched phase segment throws `IllegalArgumentException`.

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
        String prompt, NegotiationContext context, Map<String, Object> schema, String templateUri)
```

**Typical scenarios**: the initiator (server agent) validates the accept message returned by the peer, extracts the delivered parameter values, and cross-checks them against the expected fill values before continuing task execution.

**Function Description**: validates a negotiation accept message and extracts parameters per the schema.

**Input**: same as 1.3.7, where prompt is the accept message text and templateUri is the accept-reject template.

**Request Example**

```java
import net.openan.a2at.sdk.server.A2ATServer;

A2ATServer server = new A2ATServer(Path.of("server.env"));

// acceptPrompt is the accept message text returned by the client;
// acceptContext is its negotiation context
FilledParamData acceptParams = server.validateAcceptPromptAndDataFilling(
        acceptPrompt, acceptContext, schema, StandardTemplates.INFORMATION_NEGOTIATION_ACCEPT_REJECT_URI);
```

**Output**

On success, returns `FilledParamData` (structure same as [1.3.7](#137-validateproposepromptanddatafilling)); `data()` carries the parameters extracted from the delivered content per the schema plus the context parameters.

On failure, throws `NegotiationParamExtractionException` (structure same as 1.3.7). Error codes:

- `negotiation.invalid_input` (the message is not an accept negotiation message, or the context is null)

- `negotiation.rule_violation` (the negotiation context violates a rule; the nested slot error codes in `getErrors()` identify the concrete rule, e.g. `negotiation.invalid_context_id`, `negotiation.round_exceeded`)

- `negotiation.semantic_rejected` (the conclusion is not Accept, or the content does not satisfy the accept-phase constraints)

- `llm.invocation_failed` / `llm.response_invalid` (LLM failures, retryable)

- `template.not_found` (validation prompt resources missing)

Programming errors: a null prompt, schema or templateUri throws `NullPointerException`; a blank prompt, a blank or malformed templateUri (see the facade failure policy in 1.1), or a phase segment that is not `accept-reject` throws `IllegalArgumentException`.

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
        String prompt, NegotiationContext context, Map<String, Object> schema, String templateUri)
```

**Typical scenarios**: the initiator (server agent) validates the reject message returned by the peer, extracts the rejection reason, and terminates the task or escalates to manual handling accordingly.

**Function Description**: validates a negotiation reject message and extracts parameters per the schema.

**Input**: same as 1.3.7, where prompt is the reject message text and templateUri is the accept-reject template.

**Request Example**

```java
FilledParamData rejectParams = server.validateRejectPromptAndDataFilling(
        rejectPrompt, ctx, schema, StandardTemplates.INFORMATION_NEGOTIATION_ACCEPT_REJECT_URI);
```

**Output**

On success, returns `FilledParamData` (structure same as [1.3.7](#137-validateproposepromptanddatafilling)); `data()` carries the parameters extracted from the rejection reason per the schema plus the context parameters.

On failure, throws `NegotiationParamExtractionException` (structure same as 1.3.7). Error codes:

- `negotiation.invalid_input` (the message is not a reject negotiation message, or the context is null)

- `negotiation.rule_violation` (the negotiation context violates a rule; the nested slot error codes in `getErrors()` identify the concrete rule, e.g. `negotiation.invalid_context_id`, `negotiation.round_exceeded`)

- `negotiation.semantic_rejected` (the conclusion is not Reject, or the content does not satisfy the reject-phase constraints)

- `llm.invocation_failed` / `llm.response_invalid` (LLM failures, retryable)

- `template.not_found` (validation prompt resources missing)

Programming errors: a null prompt, schema or templateUri throws `NullPointerException`; a blank prompt, a blank or malformed templateUri (see the facade failure policy in 1.1), or a phase segment that is not `accept-reject` throws `IllegalArgumentException`.

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
public MetadataContent generateTaskPromptFromText(String text, String templateUri)
```

**Typical scenarios**: the client agent converts the user's natural-language task description (e.g. a private-line complaint diagnosis request) into a Task-T protocol message for a specified scenario; suitable when the target template is already known and scenario recognition should be skipped.

**Function Description**: generates a task prompt message from natural-language text with the specified Task-T template, **skipping scenario recognition** (the template is explicitly specified by the caller). One LLM slot-extraction step runs, then the template is rendered deterministically.

**Input**

| Parameter | Type | Required | Description |
| ---- | ---- | ---- | ---- |
| text | String | Yes | Natural-language task description; the input length is limited by the `A2AT_INPUT_TEXT_MAX_CHARS` configuration item, default 16384 |
| templateUri | String | Yes | Task-T template, e.g. `StandardTemplates.PRIVATE_LINE_COMPLAINT_URI` (`Task-T/network-layer/private-line-complaint/v1`) |

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
        StandardTemplates.PRIVATE_LINE_COMPLAINT_URI);
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
| failedParameters() | List&lt;SlotValidationError&gt; | Details of failed slot validations (non-empty on slot-domain failure codes such as `slot.not_provided`); structure defined in the common conventions |

Error codes:

- `template.not_found` (template missing)

- `template.load_failed` (prompt resource load failure)

- `slot.schema_not_found` (slot schema missing)

- `llm.invocation_failed` / `llm.response_invalid` (LLM call failures, retryable)

- `slot.not_provided` (a required slot is not provided in the input)

- `slot.constraint_violated` (a slot value is outside the allowed range)

- `slot.rule_violation` (fallback for other slot-validation rule violations)

- `template.render_failed` (template rendering failure)

- `input.text_too_long` (input longer than `A2AT_INPUT_TEXT_MAX_CHARS`)

Programming errors: a null text or templateUri throws `NullPointerException`; a blank or malformed templateUri (see the facade failure policy in 1.1) throws `IllegalArgumentException`.

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
        Map<String, Object> data, Map<String, Object> schema, String templateUri)
```

**Typical scenarios**: the client agent converts structured task parameters from an upstream system (field names may differ from the template slots; the schema describes the field semantics) into a Task-T protocol message; suitable when the task parameters are already available in structured form.

**Function Description**: generates a task prompt from structured data plus a semantic schema with the specified Task-T template, **skipping scenario recognition**. The `schema` describes the business meaning of each input field (description / examples / enum, etc.), guiding slot filling and value constraints; each key of data corresponds to one slot value.

**Input**

| Parameter | Type | Required | Description |
| ---- | ---- | ---- | ---- |
| data | Map&lt;String, Object&gt; | Yes | Structured business-field input; keys are business field names and values are field values |
| schema | Map&lt;String, Object&gt; | Yes (non-empty) | Field-semantics JSON Schema describing the meaning and constraints of each field |
| templateUri | String | Yes | Task-T template |

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
        data, semanticsSchema, StandardTemplates.PRIVATE_LINE_COMPLAINT_URI);
```

**Output**

On success, returns `MetadataContent` (structure same as [1.3.10](#1310-generatetaskpromptfromtext)).

On failure, throws `PromptGenerationException` (structure same as 1.3.10). Programming errors: a null argument throws `NullPointerException`; a blank or malformed templateUri (see the facade failure policy in 1.1) or an empty schema map throws `IllegalArgumentException`.

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
        String prompt, Map<String, Object> schema, String templateUri)
```

**Typical scenarios**: the parameter validation and extraction entry point for the server agent after receiving a Task-T message and before business execution; also the decision point for "missing-slot detection" in the negotiation flow — when a required parameter is missing, the caller decides whether to initiate a negotiation to supplement it.

**Function Description**: validates whether a Task-T task prompt message matches the template and slot constraints, and extracts parameters per the caller-provided JSON Schema.

**Input**

| Parameter | Type | Required | Description |
| ---- | ---- | ---- | ---- |
| prompt | String | Yes (non-blank) | Task prompt message text to validate (`MetadataContent.promptText()`); the input length is limited by the `A2AT_INPUT_TEXT_MAX_CHARS` configuration item, default 16384 |
| schema | Map&lt;String, Object&gt; | Yes | Caller-provided parameter JSON Schema declaring the parameters to extract/validate and the required constraints |
| templateUri | String | Yes | Task-T template (the prefix segment must be `Task-T`) |

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
                metadata.promptText(), validationSchema, StandardTemplates.PRIVATE_LINE_COMPLAINT_URI)
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
| errors() | List&lt;SlotValidationError&gt; | Per-slot error details (slot-level error codes such as `content.param_missing`, `content.entry_field_missing`, `content.format_error`); structure defined in the common conventions |
| params() | Map&lt;String, Object&gt; | Partial extraction parameters captured before the rejection (slots that could not be extracted have null values) |

Error codes:

- `negotiation.semantic_rejected` (semantic validation rejected, including missing or invalid required parameters; the per-slot details in `errors()` use the `content.*` codes, e.g. `content.param_missing`, `content.entry_field_missing`, `content.format_error`, `content.value_not_allowed`)

- `llm.invocation_failed` / `llm.response_invalid` (LLM failures, retryable)

- `template.not_found` (validation prompt resources missing)

- `input.text_too_long` (prompt longer than `A2AT_INPUT_TEXT_MAX_CHARS`)

Programming errors: a null prompt / schema / templateUri throws `NullPointerException`; a blank prompt or a blank or malformed templateUri (see the facade failure policy in 1.1) throws `IllegalArgumentException`.

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
ContentValidationException: [negotiation.semantic_rejected] ...
    slot=task_object code=content.param_missing message=... facts={section_label=任务对象}
```

### 1.3.13 generateNotificationPromptFromText

**API Definition**

```java
public MetadataContent generateNotificationPromptFromText(String text, String templateUri)
```

**Typical scenarios**: the client agent converts a natural-language subscription requirement (e.g. a service recovery event subscription) into a Notification-T subscription message for a specified scenario.

**Function Description**: generates a notification subscription prompt message from natural-language text with the specified Notification-T template. One LLM slot-extraction step runs, then the template is rendered deterministically, and the built-in slot schema is validated during generation.

**Input**

| Parameter | Type | Required | Description |
| ---- | ---- | ---- | ---- |
| text | String | Yes | Natural-language subscription description (notification topic, subscribe condition, notification data format, etc.); the input length is limited by the `A2AT_INPUT_TEXT_MAX_CHARS` configuration item, default 16384 |
| templateUri | String | Yes | Notification-T template, e.g. `StandardTemplates.SUBSCRIBE_INCIDENT_URI`, `StandardTemplates.SERVICE_RECOVERY_URI` |

**Request Example**

```java
import java.nio.file.Path;
import net.openan.a2at.sdk.client.A2ATClient;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.StandardTemplates;

A2ATClient client = new A2ATClient(Path.of("client.env"));
String templateUri = StandardTemplates.SERVICE_RECOVERY_URI;

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

On failure, throws `PromptGenerationException` (structure same as 1.3.10). Programming errors: a null text or templateUri throws `NullPointerException`; a blank or malformed templateUri (see the facade failure policy in 1.1) throws `IllegalArgumentException`.

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
        Map<String, Object> data, Map<String, Object> schema, String templateUri)
```

**Typical scenarios**: the client agent converts structured subscription parameters submitted by an upstream system or UI into a Notification-T subscription message; suitable when the subscription parameters are already available in structured form.

**Function Description**: generates a notification subscription prompt from structured data plus a semantic schema with the specified Notification-T template.

**Input**

| Parameter | Type | Required | Description |
| ---- | ---- | ---- | ---- |
| data | Map&lt;String, Object&gt; | Yes | Structured subscription input (e.g. the subscribe condition and the notification data format field list) |
| schema | Map&lt;String, Object&gt; | Yes (non-empty) | Field-semantics JSON Schema |
| templateUri | String | Yes | Notification-T template |

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

On failure, throws `PromptGenerationException` (structure same as 1.3.10). Programming errors: a null argument throws `NullPointerException`; a blank or malformed templateUri (see the facade failure policy in 1.1) or an empty schema map throws `IllegalArgumentException`.

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
        String prompt, Map<String, Object> schema, String templateUri)
```

**Typical scenarios**: the server agent validates a received Notification-T subscription message, extracts the subscription parameters (topic/condition/report format), and establishes the subscription accordingly.

**Function Description**: validates whether a Notification-T notification subscription prompt message matches the template and slot constraints, and extracts parameters per the caller's schema (subscription topic, subscribe condition, notification data format, etc.).

**Input**

| Parameter | Type | Required | Description |
| ---- | ---- | ---- | ---- |
| prompt | String | Yes (non-blank) | Notification subscription prompt message text to validate; the input length is limited by the `A2AT_INPUT_TEXT_MAX_CHARS` configuration item, default 16384 |
| schema | Map&lt;String, Object&gt; | Yes | Caller-provided parameter JSON Schema |
| templateUri | String | Yes | Notification-T template (the prefix segment must be `Notification-T`) |

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

- `negotiation.semantic_rejected` (required parameter missing or invalid value; the per-slot details in `errors()` use the `content.*` codes)

- `llm.invocation_failed` / `llm.response_invalid` (LLM failures, retryable)

- `template.not_found` (validation prompt resources missing)

- `input.text_too_long` (prompt longer than `A2AT_INPUT_TEXT_MAX_CHARS`)

Programming errors: a null prompt / schema / templateUri throws `NullPointerException`; a blank prompt or a blank or malformed templateUri (see the facade failure policy in 1.1) throws `IllegalArgumentException`.

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
public MetadataContent generateAuthPromptFromText(String text, String templateUri)
```

**Typical scenarios**: the client agent converts a natural-language authorization request (add/modify/delete/query network operation authorization policies) into an Authorization-T message.

**Function Description**: generates an authorization policy operation prompt message from natural-language text with the specified Authorization-T template. One LLM slot-extraction step runs, then the template is rendered deterministically.

**Input**

| Parameter | Type | Required | Description |
| ---- | ---- | ---- | ---- |
| text | String | Yes | Natural-language authorization description (operation type + network operation authorization policy content); the input length is limited by the `A2AT_INPUT_TEXT_MAX_CHARS` configuration item, default 16384 |
| templateUri | String | Yes | Authorization-T template: `StandardTemplates.AUTHORIZATION_POLICY_MANAGEMENT_URI` (`Authorization-T/authorization-policy-management/v1`) |

**Request Example**

```java
import java.nio.file.Path;
import net.openan.a2at.sdk.client.A2ATClient;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.StandardTemplates;

A2ATClient client = new A2ATClient(Path.of("client.env"));

MetadataContent result = client.generateAuthPromptFromText(
        "Add an authorization for the campus private network, use service recovery for handling, do a tunnel optimization, and leave the validity period to be filled in later",
        StandardTemplates.AUTHORIZATION_POLICY_MANAGEMENT_URI);
```

**Output**

On success, returns `MetadataContent` (structure same as [1.3.10](#1310-generatetaskpromptfromtext); `extensionUri` is `https://projects.tmforum.org/a2aproject/telecommunication/extensions/Authorization-T/v1`).

On failure, throws `PromptGenerationException` (structure same as 1.3.10); for example, an operation type outside the "add/modify/delete/query authorization policy" range is rejected with `slot.constraint_violated`. Programming errors: a null text or templateUri throws `NullPointerException`; a blank or malformed templateUri (see the facade failure policy in 1.1) throws `IllegalArgumentException`.

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
        Map<String, Object> data, Map<String, Object> schema, String templateUri)
```

**Typical scenarios**: the client agent converts structured authorization policy data submitted field by field by an authorization management UI or system into an Authorization-T message.

**Function Description**: generates an authorization policy operation prompt from structured data plus a semantic schema with the specified Authorization-T template, skipping scenario recognition. The semantic constraints are the same as 1.3.11.

**Input**

| Parameter | Type | Required | Description |
| ---- | ---- | ---- | ---- |
| data | Map&lt;String, Object&gt; | Yes | Structured authorization input (operation type, policy count, policy detail list, etc.) |
| schema | Map&lt;String, Object&gt; | Yes (non-empty) | Field-semantics JSON Schema |
| templateUri | String | Yes | Authorization-T template |

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
        data, schema, StandardTemplates.AUTHORIZATION_POLICY_MANAGEMENT_URI);
```

**Output**

On success, returns `MetadataContent` (structure same as [1.3.10](#1310-generatetaskpromptfromtext); `extensionUri` is `https://projects.tmforum.org/a2aproject/telecommunication/extensions/Authorization-T/v1`).

On failure, throws `PromptGenerationException` (structure same as 1.3.10). Programming errors: a null argument throws `NullPointerException`; a blank or malformed templateUri (see the facade failure policy in 1.1) or an empty schema map throws `IllegalArgumentException`.

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
        String prompt, Map<String, Object> schema, String templateUri)
```

**Typical scenarios**: the server agent validates a received Authorization-T message, extracts the operation type and policy list, and performs the authorization policy management action accordingly.

**Function Description**: validates whether an Authorization-T authorization prompt message matches the template and slot constraints, and extracts parameters per the caller's schema (operation type, policy list, etc.). Field requirements are validated differently per operation type: an add entry must carry business scenario / handling type / operation name / validity period; a modify entry must carry the policy identifier and the new validity period; a delete entry may be a policy identifier or condition fields.

**Input**

| Parameter | Type | Required | Description |
| ---- | ---- | ---- | ---- |
| prompt | String | Yes (non-blank) | Authorization prompt message text to validate; the input length is limited by the `A2AT_INPUT_TEXT_MAX_CHARS` configuration item, default 16384 |
| schema | Map&lt;String, Object&gt; | Yes | Caller-provided parameter JSON Schema (containing `operationType`, `policyList`, etc.) |
| templateUri | String | Yes | Authorization-T template (the prefix segment must be `Authorization-T`) |

**Request Example**

```java
import net.openan.a2at.sdk.server.A2ATServer;

A2ATServer server = new A2ATServer(Path.of("server.env"));

Map<String, Object> paramSchema = MAPPER.readValue(
        Path.of("param-schema.json").toFile(), new TypeReference<Map<String, Object>>() {});
// paramSchema declares: operationType (enum: add/modify/delete/query authorization policy), policyList
// (array whose entries carry policyId/businessScenario/handlingType/operationName/validityPeriod)

FilledParamData result = server.validateAuthPromptAndDataFilling(
        metadata.promptText(), paramSchema, StandardTemplates.AUTHORIZATION_POLICY_MANAGEMENT_URI);
```

**Output**

On success, returns `FilledParamData` (structure same as [1.3.12](#1312-validatetaskpromptanddatafilling)); `data()` holds the parameters extracted per the schema (operation type, policy list, etc.).

On failure, throws `ContentValidationException` (structure same as 1.3.12). Error codes:

- `negotiation.semantic_rejected` (required parameter missing or invalid value, e.g. an add entry missing required fields (`content.entry_field_missing`) or a validity period format error (`content.format_error`))

- `llm.invocation_failed` / `llm.response_invalid` (LLM failures, retryable)

- `template.not_found` (validation prompt resources missing)

- `input.text_too_long` (prompt longer than `A2AT_INPUT_TEXT_MAX_CHARS`)

Programming errors: a null prompt / schema / templateUri throws `NullPointerException`; a blank prompt or a blank or malformed templateUri (see the facade failure policy in 1.1) throws `IllegalArgumentException`.

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

**Function Description**: the general task prompt generation entry point, which locates the template via scenario recognition: the natural-language or structured input first goes through LLM scenario recognition, then slot extraction and rendering are completed with the built-in template of the recognized scenario.

**Input**

| Parameter | Type | Required | Description |
| ---- | ---- | ---- | ---- |
| userInput | Object | Yes | Task description: a `String` (natural language) or a `Map<String, Object>` (structured input); both go through LLM extraction. When the input is a `String`, its length is limited by the `A2AT_INPUT_TEXT_MAX_CHARS` configuration item, default 16384 |

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
| code | String | Machine-readable error code from the error-code list, e.g. `scenario.not_matched` (scenario recognition missed), `input.text_too_long` (input length guard), `template.load_failed` (prompt resource load failure), `template.not_found` (template missing), `slot.schema_not_found` (slot schema missing), `slot.not_provided` (required slot missing), `template.render_failed` (rendering failure), `llm.invocation_failed` / `llm.response_invalid` (LLM failures) |
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
{ code=scenario.not_matched, message=The input does not match any known scenario: <reason>, stage=scenario }
```

### 1.3.20 checkTaskPrompt

**API Definition**

```java
public PromptComplianceResult checkTaskPrompt(String processedPromptText)
```

**Typical scenarios**: the server agent performs a protocol-completeness validation (scenario/template/slot compliance) on a received task message, in scenarios where only a pass/fail conclusion is needed and no parameters must be extracted.

**Function Description**: the general task prompt compliance-check entry point (server side): it performs scenario matching, template compliance validation, and slot validation on the processed task prompt sent by the client. Difference from `validateTaskPromptAndDataFilling`: this API extracts no parameters and takes no caller-provided schema; it returns only the standardized pass/fail conclusion.

**Input**

| Parameter | Type | Required | Description |
| ---- | ---- | ---- | ---- |
| processedPromptText | String | Yes | A2A-T protocol message text sent by the client (the value keyed by the extension URI in `Message.metadata`); the input length is limited by the `A2AT_INPUT_TEXT_MAX_CHARS` configuration item, default 16384 |

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
| code | String | Machine-readable error code from the error-code list: `scenario.not_matched` (message parsing/scenario recognition failure), `template.not_found` (template missing), slot-domain codes such as `slot.not_provided` (missing required value), `slot.constraint_violated` (out-of-range value) and `slot.rule_violation` (other slot rule violations), `input.text_too_long` (input length guard) |
| message | String | Human-readable failure description |
| stage | String | Stage where the failure occurred: `prompt_parse` (message parsing), `generation` (template loading), `slot_validation` (slot validation) |

**Response Example**

On success:

```text
result.success() = true
```

On failure:

```text
result.success() = false
result.failure() =
{ code=slot.not_provided, message='Task Object' is not provided in the input., stage=slot_validation }
```

## 1.4 Error Code List

**Error code categories**: BUSINESS = expected business failures the caller can act on, carried by `A2ATBusinessException` subclasses; INFRA = infrastructure failures, carried by a plain `A2ATError`.

| Code | Category | Message (en-US) | Message (zh-CN) |
| ---- | -------- | ---------------- | --------------- |
| `template.not_found` | BUSINESS | Template '{template_uri}' does not support language '{language}'; check the template URI and language setting | 模板「{template_uri}」不支持语言「{language}」,请检查模板标识与语言配置 |
| `template.render_failed` | BUSINESS | Failed to render template '{template_uri}': {reason} | 模板「{template_uri}」渲染失败:{reason} |
| `template.load_failed` | INFRA | Failed to read template resource '{resource_path}' | 模板资源「{resource_path}」读取失败 |
| `slot.schema_not_found` | BUSINESS | Template '{template_uri}' is missing its slot schema (language '{language}') | 模板「{template_uri}」缺少参数定义文件(语言「{language}」) |
| `slot.not_provided` | BUSINESS | '{slot_label}' is not provided in the input. | 输入中未提供「{slot_label}」。 |
| `slot.constraint_violated` | BUSINESS | The value of '{slot_label}' ({actual}) is not within the allowed range | 「{slot_label}」的取值「{actual}」不在允许范围内 |
| `slot.semantic_conflict` | BUSINESS | The value of '{slot_label}' conflicts with the slot definition: {reason} | 「{slot_label}」的取值与参数定义冲突:{reason} |
| `slot.fabricated_value` | BUSINESS | The value of '{slot_label}' ({actual}) is placeholder content, not a valid value | 「{slot_label}」的取值「{actual}」是占位内容,不是有效值 |
| `slot.cross_scenario_pollution` | BUSINESS | The value of '{slot_label}' contains content from a different scenario | 「{slot_label}」的取值混入了其他场景的内容 |
| `slot.insufficient_grounding` | BUSINESS | The value of '{slot_label}' lacks sufficient grounding | 「{slot_label}」的取值缺少充分依据 |
| `slot.rule_violation` | BUSINESS | The value of '{slot_label}' violates the validation rules. | 「{slot_label}」的取值不符合校验规则。 |
| `input.text_too_long` | BUSINESS | Input text length {actual_length} exceeds the maximum of {max_chars} (A2AT_INPUT_TEXT_MAX_CHARS) | 输入文本长度 {actual_length} 超过上限 {max_chars}(A2AT_INPUT_TEXT_MAX_CHARS) |
| `content.param_missing` | BUSINESS | '{section_label}' is empty; please provide a value | 「{section_label}」未填写,请补充该参数的取值 |
| `content.entry_field_missing` | BUSINESS | Entry {index} of '{section_label}' is missing required field '{field_label}' | 「{section_label}」第 {index} 条缺少必填字段「{field_label}」 |
| `content.format_error` | BUSINESS | The format of '{section_label}' is invalid: {reason} | 「{section_label}」的取值格式不符合要求:{reason} |
| `content.value_not_allowed` | BUSINESS | The value of '{section_label}' ({actual}) is not allowed | 「{section_label}」的取值「{actual}」不在允许范围内 |
| `content.semantic_conflict` | BUSINESS | '{section_label}' has a semantic conflict: {reason} | 「{section_label}」存在语义冲突:{reason} |
| `content.rule_violation` | BUSINESS | The value of '{section_label}' violates the validation rules. | 「{section_label}」的取值不符合校验规则。 |
| `scenario.not_matched` | BUSINESS | The input does not match any known scenario: {reason} | 输入内容无法匹配任何已知场景:{reason} |
| `llm.not_configured` | BUSINESS | No LLM client is configured; check the A2AT_LLM_* settings | 未配置 LLM 客户端,无法执行该操作(请检查 A2AT_LLM_* 配置) |
| `llm.invocation_failed` | BUSINESS | LLM invocation failed (provider {provider}): {reason} | LLM 调用失败(提供方 {provider}):{reason} |
| `llm.response_invalid` | BUSINESS | The LLM response is invalid (step: {step}); please retry | LLM 返回内容不符合要求({step} 步骤),请重试 |
| `negotiation.invalid_input` | BUSINESS | The negotiation input is invalid: {reason} | 输入的协商内容无效:{reason} |
| `negotiation.invalid_context_id` | BUSINESS | The negotiation context id '{actual}' is not a valid UUID | 协商上下文标识「{actual}」不是合法的 UUID |
| `negotiation.round_exceeded` | BUSINESS | Negotiation round {round} exceeds the maximum of {max_rounds} | 协商轮次 {round} 已超过上限 {max_rounds} |
| `negotiation.type_mismatch` | BUSINESS | The message implies '{implied}' negotiation but the declared template type is '{declared}' | 报文内容属于「{implied}」协商,与声明的模板类型「{declared}」不符 |
| `negotiation.phase_mismatch` | BUSINESS | The message phase does not match the declared template phase ({implied} vs {declared}) | 报文阶段与声明的模板阶段不符({implied} vs {declared}) |
| `negotiation.conclusion_mismatch` | BUSINESS | The message conclusion is '{actual}' but '{expected}' is expected for this method | 报文结论为「{actual}」,与该方法的预期「{expected}」不符 |
| `negotiation.content_invalid` | BUSINESS | The negotiation content field '{field}' is invalid: {reason} | 协商内容字段「{field}」无效:{reason} |
| `negotiation.field_missing` | BUSINESS | The negotiation message is missing required field '{field}' | 协商报文缺少必填字段「{field}」 |
| `negotiation.content_extract_failed` | BUSINESS | Failed to extract negotiation content from text ({field}): {reason} | 无法从文本提取协商内容({field}):{reason} |
| `negotiation.conclusion_content_mismatch` | BUSINESS | The conclusion is '{conclusion}' but '{section_label}' does not state the content the conclusion requires | 结论为「{conclusion}」,但「{section_label}」未表达该结论应携带的内容 |
| `negotiation.missing_result_content` | BUSINESS | The '{section_label}' section is missing the content required by its conclusion | 「{section_label}」板块缺少结论应携带的内容 |
| `negotiation.mutually_exclusive_sections` | BUSINESS | Mutually exclusive sections appear together: {sections} | 互斥板块同时出现:{sections} |
| `negotiation.constraint_conflict` | BUSINESS | '{section_label}' conflicts with existing constraints: {reason} | 「{section_label}」与既有约束冲突:{reason} |
| `negotiation.field_inconsistency` | BUSINESS | Fields within '{section_label}' are inconsistent: {reason} | 「{section_label}」内字段取值前后不一致:{reason} |
| `negotiation.invalid_time_interval` | BUSINESS | The time interval of '{section_label}' is invalid (start must not be later than end) | 「{section_label}」的时间区间不合法(开始时间不得晚于结束时间) |
| `negotiation.semantic_rejected` | BUSINESS | The negotiation message failed semantic validation | 协商报文语义校验未通过 |
| `negotiation.rule_violation` | BUSINESS | '{section_label}' violates the negotiation message validation rules. | 「{section_label}」不符合协商报文的校验规则。 |
| `infra.config_invalid` | INFRA | Invalid configuration '{key}': {reason} | 配置项「{key}」无效:{reason} |
| `infra.resource_read_failed` | INFRA | Failed to read resource '{resource_path}' | 资源「{resource_path}」读取失败 |
| `infra.internal_error` | INFRA | SDK internal error; contact the maintainer with context | SDK 内部错误,请联系维护方并提供上下文 |





