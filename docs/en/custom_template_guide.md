# 1 Custom Template Guide

This guide walks through an end-to-end runnable example that uses a **custom business template** (office equipment repair) with the `local_file` resource-loading mode to run the full A2A-T Task-T flow: client natural-language input -> slot extraction -> template rendering to produce a prompt -> server validation and parameter extraction.

## 1.1 Overview

### 1.1.1 What This Example Demonstrates

Example business: reporting a fault on office equipment (printer, computer, monitor, projector, and so on). Given a one-sentence user description, the client SDK extracts slots against an **explicitly specified template** and renders an A2A-T task message; the server SDK then semantically validates that message and extracts structured parameters according to the caller-provided schema.

The client uses `generateTaskPromptFromText(String, TemplateUri)`: it requires an explicit `TemplateUri`, therefore **skips scenario recognition** (an LLM call), performs slot extraction and rendering against the specified template directly, and returns `MetadataContent`, from which you read the rendered message via `promptText()`. The server uses `validateTaskPromptAndDataFilling(String, Map, TemplateUri)`: it validates the task message and extracts parameters against the provided schema, returning `FilledParamData`, from which you read the extracted `Map` via `data()`.

### 1.1.2 Supported Versions and Prerequisites

| Item | Requirement |
| --- | --- |
| JDK | 17+ |
| Build tool | Maven 3.8+ |
| SDK | `net.openan.a2a-t.sdk:a2a-t-client` and `net.openan.a2a-t.sdk:a2a-t-server`, version `1.0.0` |
| LLM | An accessible OpenAI-compatible service and API key |
| Resource mode | `local_file` (business content loaded from a local directory) |

> This example only demonstrates the A2A-T SDK itself; it does not bring in a2a-java, a registry center, or an HTTP pipeline. For a complete A2A HTTP+JSON/REST integration, see the Developer Guide and `a2a-t-sample`.

## 1.2 Local Resource Directory

In `local_file` mode, business content is loaded from a local root directory. The root directory is specified by `A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR` (see [1.4](#14-env-configuration)). The directory structure is:

```
prompt-resources/
├── templates/
│   └── Task-T/
│       └── office-it/
│           └── printer-repair/
│               └── v1/
│                   └── en-US/
│                       └── template.md
├── slots/
│   └── Task-T/
│       └── office-it/
│           └── printer-repair/
│               └── v1/
│                   └── en-US/
│                       └── slot.json
└── scenarios/
    └── en-US/
        └── scenarios.json
```

Key points:

- **The three kinds of business content — `templates` / `slots` / `scenarios` — can be localized, and are local-first with built-in fallback.** `prompts` (LLM prompts) and negotiation (Negotiation-T) resources are always loaded from the classpath. Placing them in a local directory has no effect; the SDK only ignores them and logs a WARN message.
- The directory levels for `templates` and `slots` are fixed as `{extension}/{business-domain}/{business-name}/{version}/{language}` — here `Task-T/office-it/printer-repair/v1/en-US/`.
- `scenarios` has no `Task-T` segment; it is directly `scenarios/{language}/scenarios.json`. This file **may be omitted**: when it is absent, assembly falls back to the built-in scenario catalog.
- The language directory (here `en-US`) must match `A2AT_LANGUAGE` in `.env`, otherwise the corresponding template/slot/scenario resources cannot be found.
- Note: although `generateTaskPromptFromText` itself skips scenario recognition, in `local_file` mode `new A2ATClient(...)` still calls `loadScenarios` during **assembly**. `scenarios/{language}/scenarios.json` may be omitted — a missing local file falls back to the built-in scenario catalog and assembly does not fail. This example only overrides a custom template, so no local scenario directory is required (see [1.3.3](#133-scenariosjson)).

## 1.3 Resource Files

### 1.3.1 template.md (template)

The template defines the skeleton of the task message. `{{slot-name}}` is a slot placeholder that is replaced by the actual value extracted from the user input at render time; the placeholder name must match the slot name in `slot.json`. The sectioning, the `(required)/(optional)` markers, the `Requirements:` numbered list, and the `<angle-bracket>` cross-references mirror the built-in Task-T templates (e.g. `ran-energy-saving`).

> The `(required)/(optional)` and `Requirements:` text is authoring guidance: it is stripped at render time and never appears in the final message (see the rendered output in [1.6](#16-running-and-expected-output)). Together with the `description`/`x-a2at-value-constraint` of `slot.json`, it drives slot extraction and semantic validation.

````markdown
## Operation Type

{{operation_type}}(required)

Requirements:

1. Provide the operation type; allowed values: create, modify

## Task Type

Office equipment repair

## Task Description

{{task_description}}(required)

Requirements:

1. Provide the task description dynamically based on the <operation_type> (create/modify):
   - When the type is [create]: Based on the <task_object> and <task_context>, issue an office equipment repair request, achieve the repair objective defined in <task_target>, and return the repair result in the format defined in <expected_output>.
   - When the type is [modify]: Based on the <task_object> and <task_context>, update the office equipment repair request and achieve the repair objective defined in <task_target>.

## Task Object

{{task_object}}(required)

Requirements:

1. Reporter:(required)
   - Name of the person reporting the fault
   - Example: reporter: "Zhang Wei"
2. Department:(required)
   - Department name of the reporter
   - Example: department: "Marketing"
3. Faulty equipment:(required)
   - Name, brand and location of the faulty office equipment
   - Example: faulty equipment: "The HP printer beside the meeting room on the east side of the third floor"

## Task Target

{{task_target}}(optional)

Requirements:

1. Define the quantitative or qualitative objective for the repair task; quantitative and qualitative objectives cannot be selected at the same time, defaulting to a qualitative objective. Details:
   - Quantitative objective: expected completion time of the repair, in the form of a specific time or time range; example: expected completion time: before 3 p.m.
   - Qualitative objective: process and complete the repair as soon as possible and restore the equipment to a working state

## Task Context

{{task_context}}(optional)

Requirements:

1. Fault symptom: description of the specific fault; example: fault symptom: cannot power on, the power indicator keeps flashing
2. Contact: phone number of the reporter; example: contact: 13800001234

## Expected Output

{{expected_output}}(optional when creating, not needed when modifying)

Requirements:

1. Output the repair result of the office equipment repair task; the repair result must contain the following information:

### Repair result basic information

1. Repair acceptance status
2. Repair task unique identifier

### Repair result detailed information

1. Faulty equipment information
2. Repair handling requirements
````

### 1.3.2 slot.json (slot schema)

The slot schema is a JSON Schema that declares each slot's type, business meaning, and value constraint. Keys under `properties` are the slot names and must correspond to the `{{placeholders}}` in `template.md`; the `description`/`examples`/`x-a2at-value-constraint` triple on each slot is the primary source the LLM slot extraction reads. The `required` array declares required slots (this example mirrors the built-in template by leaving it empty; requiredness is primarily expressed through the value constraint and description).

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "type": "object",
  "additionalProperties": false,
  "properties": {
    "operation_type": {
      "type": "string",
      "description": "Provide the operation type; allowed values: create, modify.",
      "examples": [
        "create"
      ],
      "x-a2at-value-constraint": "Allowed values: create, modify"
    },
    "task_description": {
      "type": "string",
      "description": "Provide the task description dynamically based on the operation type (create/modify).",
      "examples": [
        "Based on the <task_object> and <task_context>, issue an office equipment repair request, achieve the repair objective defined in <task_target>, and return the repair result in the format defined in <expected_output>."
      ],
      "x-a2at-value-constraint": "When the type is [create]: based on the <task_object> and <task_context>, issue an office equipment repair request, achieve the repair objective defined in <task_target>, and return the repair result in the format defined in <expected_output>. When the type is [modify]: based on the <task_object> and <task_context>, update the office equipment repair request and achieve the repair objective defined in <task_target>."
    },
    "task_object": {
      "type": "string",
      "description": "Specify the repair object, including the reporter, department and faulty equipment.",
      "examples": [
        "Reporter: Zhang Wei, department: Marketing, faulty equipment: the HP printer beside the meeting room on the east side of the third floor."
      ],
      "x-a2at-value-constraint": "Reporter, department and faulty equipment are all required. Reporter is the name of the person reporting the fault; department is the department name of the reporter; faulty equipment is the name, brand and location of the faulty office equipment."
    },
    "task_target": {
      "type": "string",
      "description": "Define the quantitative or qualitative objective for the repair task.",
      "examples": [
        "Expected completion time: before 3 p.m."
      ],
      "x-a2at-value-constraint": "1. Quantitative objective (expected completion time): the required time to complete the repair, in the form of a specific time or time range. 2. Qualitative objective: process and complete the repair as soon as possible and restore the equipment to a working state. Quantitative and qualitative objectives cannot be selected at the same time, defaulting to a qualitative objective."
    },
    "task_context": {
      "type": "string",
      "description": "Provide the background and context of the repair task.",
      "examples": [
        "Fault symptom: cannot power on, the power indicator keeps flashing; contact: 13800001234."
      ],
      "x-a2at-value-constraint": "1. Fault symptom: description of the specific fault. 2. Contact: phone number of the reporter."
    },
    "expected_output": {
      "type": "string",
      "description": "Output the repair result of the office equipment repair task.",
      "examples": [
        ""
      ],
      "x-a2at-value-constraint": "Repair result basic information: repair acceptance status, repair task unique identifier. Repair result detailed information: faulty equipment information, repair handling requirements."
    }
  },
  "required": []
}
```

### 1.3.3 scenarios.json (scenarios)

The scenario list is loaded during client assembly. **This example uses the `generateTaskPromptFromText` path and performs no scenario recognition**, so there is no need to register scenarios — `scenarios/{language}/scenarios.json` may be omitted entirely; when it is absent, assembly falls back to the built-in scenario catalog without failing. If local scenario recognition is needed, place this file, where `scenario_code` must match the template directory path: `Task-T/office-it/printer-repair/v1`.

```json
{
  "scenarios": [
    {
      "scenario_code": "Task-T/office-it/printer-repair/v1",
      "scenario_name": "Office equipment repair",
      "description": "Generates an A2A-T task request for reporting/repairing a fault on office equipment (printer, computer, monitor, projector, etc.), describing the reporter, department, faulty equipment, fault symptom, expected completion time and contact information.",
      "example": "Help me create an office equipment repair task. Operation type: create. Task object: the reporter is Zhang Wei. Task target: the expected completion time is before 3 p.m."
    }
  ]
}
```

## 1.4 .env Configuration

Save the following as `.env` (placed in the process working directory, or pass a full path via a command-line argument):

```properties
A2AT_LANGUAGE=en-US
A2AT_PROMPT_SOURCE_TYPE=local_file
A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR=./prompt-resources
A2AT_PROMPT_COMPLIANCE_ENABLED=false
A2AT_LLM_PROVIDER=openai
A2AT_LLM_MODEL=your-model-name
A2AT_LLM_BASE_URL=https://your-llm-gateway.example.com/v1
A2AT_LLM_API_KEY=your-api-key
A2AT_LLM_MAX_TOKENS=8000
A2AT_LLM_TIMEOUT_SECONDS=180
```

Key points:

- `A2AT_PROMPT_SOURCE_TYPE=local_file`: enable local-file resource loading.
- `A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR`: the local resource root directory. **A relative path is resolved against the directory of the `.env` file** (not the process working directory). The `./prompt-resources` above means the resource directory sits next to `.env`.
- This directory is read and validated at assembly time of `new A2ATClient(...)` / `new A2ATServer(...)`: **a missing directory fails fast at construction without any LLM call**.
- `A2AT_PROMPT_COMPLIANCE_ENABLED=false`: this example disables the generation-stage compliance pre-check to focus on the "generate -> server validation/extraction" mainline.
- `A2AT_LLM_MAX_TOKENS`: use a larger value for reasoning models; a value that is too small causes the structured output to be empty.
- `A2AT_LLM_PROVIDER` / `A2AT_LLM_MODEL` / `A2AT_LLM_BASE_URL` / `A2AT_LLM_API_KEY` should be filled in per the actual LLM service; all of them are shown as placeholders here.

## 1.5 Project and Code

### 1.5.1 pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>

    <groupId>demo</groupId>
    <artifactId>task-t-demo</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.release>17</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>net.openan.a2a-t.sdk</groupId>
            <artifactId>a2a-t-client</artifactId>
            <version>1.0.0</version>
        </dependency>
        <dependency>
            <groupId>net.openan.a2a-t.sdk</groupId>
            <artifactId>a2a-t-server</artifactId>
            <version>1.0.0</version>
        </dependency>
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-simple</artifactId>
            <version>2.0.17</version>
            <scope>runtime</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.0</version>
                <configuration>
                    <mainClass>demo.TaskTDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### 1.5.2 TaskTDemo.java

```java
package demo;

import java.nio.file.Path;
import java.util.Map;
import net.openan.a2at.sdk.client.A2ATClient;
import net.openan.a2at.sdk.core.model.MetadataContent;
import net.openan.a2at.sdk.core.model.TemplateUri;
import net.openan.a2at.sdk.server.A2ATServer;

public final class TaskTDemo {
    public static void main(String[] args) throws Exception {
        Path env = Path.of(args.length > 0 ? args[0] : ".env");
        String text = args.length > 1 ? args[1]
                : "Help me create an office equipment repair task. Operation type: create. Task description: based on the reporter, department and faulty equipment information, issue an office equipment repair request, achieve the repair objective defined in the task target, and return the repair handling result in the format defined in the expected output. Task object: the reporter is Zhang Wei, the department is Marketing, and the faulty equipment is the HP printer beside the meeting room on the east side of the third floor. Task target: the expected completion time is before 3 p.m. Task context: the fault symptom is that it cannot power on and the power indicator keeps flashing, and the contact number is 13800001234. Expected output: output the repair result containing the repair acceptance status, the repair task unique identifier, the faulty equipment information and the repair handling requirements.";
        TemplateUri uri = TemplateUri.of("Task-T", "office-it", "printer-repair");
        Map<String, Object> schema = Map.of(
                "type", "object", "properties", Map.of(
                        "reporter", slot("Name of the person reporting the fault"),
                        "department", slot("Department name of the reporter"),
                        "device", slot("Name, brand and location of the faulty office equipment"),
                        "symptom", slot("Description of the specific fault"),
                        "deadline", slot("Expected completion time of the repair"),
                        "contact", slot("Phone number of the reporter")));

        A2ATClient client = new A2ATClient(env);
        MetadataContent content = client.generateTaskPromptFromText(text, uri);
        System.out.println(content.promptText());
        A2ATServer server = new A2ATServer(env);
        System.out.println(server.validateTaskPromptAndDataFilling(content.promptText(), schema, uri).data());
    }

    private static Map<String, Object> slot(String description) {
        return Map.of("type", "string", "description", description);
    }
}
```

Two key API choices worth calling out:

- **The client uses `generateTaskPromptFromText(String, TemplateUri)`**: it takes an explicit template URI, skips scenario recognition, runs one LLM slot-extraction step against that template and renders the result, returning `MetadataContent` (`promptText()` is the rendered message). Compared with the scenario-recognition path of `generateTaskPrompt(Object)`, it saves the recognition LLM call, and the result carries an explicit `templateUri`.
- **The server uses `validateTaskPromptAndDataFilling(String, Map, TemplateUri)`**: it validates the task message and extracts parameters against the caller-provided schema, returning `FilledParamData` (`data()` is the extracted `Map`). It needs an explicit `TemplateUri`; this example builds it segment-by-segment with `TemplateUri.of("Task-T", "office-it", "printer-repair")`, mirroring the resource directory path.
- The server-side `schema` is the caller-provided JSON parameter schema (here declared inline with `Map.of`, six business fields). Its keys (`reporter`/`department`/`device` and so on) **intentionally differ** from the client template slot names (`operation_type`/`task_object` and so on): the client renders the business fields into template sections, and the server re-extracts them under its own key names from the rendered message — demonstrating the SDK's cross-key adaptation.

## 1.6 Running and Expected Output

Run in the project root directory:

```bash
mvn compile exec:java
```

> On first run, make sure the `net.openan.a2a-t.sdk` dependencies can be resolved over the network and that the LLM configuration (such as `A2AT_LLM_API_KEY`) is filled in correctly.

The expected output has two parts: first the prompt text rendered by the client, followed immediately by the `data` map extracted by the server (the stringified `FilledParamData.data()`).

```text
## Operation Type

create

## Task Type

Office equipment repair

## Task Description

based on the reporter, department and faulty equipment information, issue an office equipment repair request, achieve the repair objective defined in the task target, and return the repair handling result in the format defined in the expected output.

## Task Object

the reporter is Zhang Wei, the department is Marketing, and the faulty equipment is the HP printer beside the meeting room on the east side of the third floor.

## Task Target

the expected completion time is before 3 p.m.

## Task Context

the fault symptom is that it cannot power on and the power indicator keeps flashing, and the contact number is 13800001234.

## Expected Output

output the repair result containing the repair acceptance status, the repair task unique identifier, the faulty equipment information and the repair handling requirements.

{symptom=it cannot power on and the power indicator keeps flashing, department=Marketing, device=the HP printer beside the meeting room on the east side of the third floor, deadline=before 3 p.m., reporter=Zhang Wei, contact=13800001234}
```

Timing note: this example's complete chain triggers **2 LLM calls in total** — one client slot extraction and one server semantic validation/extraction (the `FromText` path has no scenario-recognition call). Actual time depends on the response speed of the connected model.

## 1.7 FAQ

- **Resource directory missing; construction fails fast (zero LLM calls).** When the directory pointed to by `A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR` does not exist or is invalid, `new A2ATClient(...)` / `new A2ATServer(...)` throws at assembly time before any LLM call. In `local_file` mode, business content is local-first with built-in fallback and `scenarios/{language}/scenarios.json` may be omitted (a missing file falls back to the built-in scenario catalog; see [1.3.3](#133-scenariosjson)).
- **Oversized input (`input_text_too_long`).** Both FromText generation and server validation gates are bounded by `A2AT_INPUT_TEXT_MAX_CHARS` (default 16384); oversized free text fails fast with `input_text_too_long` before reaching the LLM.
- **`slot_validation_error` (`missing_required`) or `validation_semantic_rejected`.** When `slot.json` declares a required slot that the input does not provide, the client throws `slot_validation_error` (`missing_required`); when the message contradicts itself, the server rejects it with `validation_semantic_rejected`. Both require the "template `Requirements:` prose <-> `slot.json` `description`/`x-a2at-value-constraint` <-> input content" wording to stay consistent. A typical counter-example: the task description states "restore the equipment as soon as possible" (a qualitative objective) while the task target states "before 3 p.m." (a quantitative objective) — since quantitative and qualitative objectives cannot be selected at the same time, the server rejects the content with `semantic_mismatch`.
- **Windows console encoding.** On Windows the default console encoding may not be UTF-8, which can garble output. Run `set JAVA_TOOL_OPTIONS=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Dfile.encoding=UTF-8` (or `chcp 65001`) first so the output is rendered as UTF-8. The language directory must match `A2AT_LANGUAGE` (here `en-US`).