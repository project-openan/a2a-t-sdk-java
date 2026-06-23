# 1 A2A-T Java SDK Developer Guide

## 1.1 Feature Introduction

See the [User Guide 1.1 Feature Introduction](https://github.com/project-openan/a2a-t-sdk-java/blob/main/docs/en/user_guide.md#11-feature-introduction) section.

## 1.2 Constraints and Limitations

1. JDK requirement is 17+.
2. Currently supports `classpath` and `local_file` prompt resources. The default source is the built-in resources packaged in the `a2a-t-resources` jar.
3. By default supports `local_rule` and `openai_compatible` LLM provider.
4. Negotiation state storage currently supports `in_memory`.
5. The SDK is not responsible for starting business HTTP services, user authentication, key management, or registry center deployment.

## 1.3 Environment Setup

### 1.3.1 Get Source Code

```bash
git clone git@github.com:project-openan/a2a-t-sdk-java.git
cd a2a-t-sdk-java
```

### 1.3.2 Build Project

```bash
mvn -DskipTests package
```

### 1.3.3 Run Tests

```bash
mvn test
```

### 1.3.4 Format Check

The project uses Spotless to manage Java formatting:

```bash
mvn spotless:check
```

For auto-formatting:

```bash
mvn spotless:apply
```

## 1.4 Maven Dependencies

Business projects can use BOM to manage versions:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>net.openan.a2a-t.sdk</groupId>
            <artifactId>a2a-t-bom</artifactId>
            <version>1.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

Client Agent dependency:

```xml
<dependency>
    <groupId>net.openan.a2a-t.sdk</groupId>
    <artifactId>a2a-t-client</artifactId>
</dependency>
```

Server Agent dependency:

```xml
<dependency>
    <groupId>net.openan.a2a-t.sdk</groupId>
    <artifactId>a2a-t-server</artifactId>
</dependency>
```

If the business system needs to directly read SDK built-in resources, or wants to use `A2AT_PROMPT_SOURCE_TYPE=classpath` without relying on transitive dependencies, add the following dependency:

```xml
<dependency>
    <groupId>net.openan.a2a-t.sdk</groupId>
    <artifactId>a2a-t-resources</artifactId>
</dependency>
```

## 1.5 Configuration Loading

The Java SDK does not auto-discover `.env`; the caller must explicitly pass the path:

```java
import java.nio.file.Path;
import net.openan.a2at.sdk.client.A2ATClient;

A2ATClient client = new A2ATClient(Path.of("client.env"));
```

Basic configuration example:

```properties
A2AT_LANGUAGE=zh-CN
A2AT_PROMPT_SOURCE_TYPE=classpath
A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR=
A2AT_LLM_PROVIDER=openai_compatible
A2AT_LLM_MODEL=deepseek-chat
A2AT_LLM_BASE_URL=https://api.deepseek.com
A2AT_LLM_API_KEY={your_api_key}
A2AT_NEGOTIATION_STATE_STORE_TYPE=in_memory
```


## 1.6 SDK Basic Usage

### 1.6.1 Client Generates Task Prompt

```java
import java.nio.file.Path;
import net.openan.a2at.sdk.client.A2ATClient;
import net.openan.a2at.sdk.client.model.PromptGenerationResult;

A2ATClient client = new A2ATClient(Path.of("client.env"));
PromptGenerationResult result = client.generateTaskPrompt(
        "Notification topic is Incident, subscription condition: ETH-LOS fault with subscription level critical, notification data format for reporting: DataPart");

if (result.success()) {
    System.out.println(result.promptText());
} else {
    System.out.println(result.failure().message());
}
```

### 1.6.2 Server Validates Task Prompt

```java
import java.nio.file.Path;
import net.openan.a2at.sdk.server.A2ATServer;
import net.openan.a2at.sdk.server.model.PromptComplianceResult;

A2ATServer server = new A2ATServer(Path.of("server.env"));
PromptComplianceResult result = server.checkTaskPrompt(processedPromptText);

if (result.success()) {
    System.out.println("prompt check passed");
} else {
    System.out.println(result.failure().message());
}
```

### 1.6.3 Negotiation Interface

```java
import java.nio.file.Path;
import java.util.Map;
import net.openan.a2at.sdk.client.A2ATClient;
import net.openan.a2at.sdk.negotiation.types.model.NegotiationType;

A2ATClient client = new A2ATClient(Path.of("client.env"));
Map<String, Object> payload = client.startNegotiation(
        NegotiationType.INFORMATION,
        "Please provide incident level.",
        Map.of("missingFields", java.util.List.of("subscription_condition_incident_level")));
```

The business system needs to pass the text and context in the negotiation payload to the peer via A2A messages, and subsequently advance the state through `receiveNegotiation` and `continueNegotiation`.


## 1.7 Full Integration Development Flow

### 1.7.1 Client Agent

Recommended Client Agent flow:

1. Read `.env` and create `A2ATClient`.
2. Call `generateTaskPrompt` based on user input.
3. Place the prompt into the A2A message body or extension fields.
4. Query the target AgentCard directly from the server root path `GET /`, or through the registry center when registry lookup is required.
5. Send an A2A request based on the interface address in the AgentCard.
6. If negotiation context is received, continue the interaction through `receiveNegotiation` and `continueNegotiation`.

`ClientSampleFlow` in `a2a-t-sample` demonstrates the full flow: reading scenarios, querying AgentCard directly from the sample server or from registry-center, generating prompt, constructing A2A requests, and handling streaming events.

### 1.7.2 Server Agent

Recommended Server Agent flow:

1. Create a business HTTP service or integrate with an existing A2A Java service framework.
2. Construct AgentCard at startup, expose it from the service root path `GET /`, and register it with the registry center when registry lookup is used.
3. After receiving an A2A request, extract the processed task prompt.
4. Call `A2ATServer.checkTaskPrompt`.
5. After validation passes, execute business logic.
6. When validation fails or information is insufficient, return supplementary information requirements through the negotiation interface.

`ServerSampleMain` and `ServerSampleFlow` in `a2a-t-sample` demonstrate AgentCard exposure at the HTTP root path, optional registry-center registration, HTTP service startup, prompt extraction, validation, and task event pushing.

## 1.8 Prompt Resource Extension
### 1.8.1 Resource Source

Prompt resources can be loaded from two sources:

| Source Type | Description |
|--|--|
| `classpath` | Loads built-in prompt resources from the runtime classpath, typically from the `a2a-t-resources` jar downloaded by Maven. This is the default and is used by `a2a-t-sample` when `a2a-t-resources` is present in the sample `pom.xml` and runtime classpath. |
| `local_file` | Loads prompt resources from a local directory specified by `A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR`. Use this when developing or overriding scenarios, slots, templates, or LLM prompt files. |

To use the built-in resources from the jar, keep the source type as `classpath`:

```properties
A2AT_PROMPT_SOURCE_TYPE=classpath
A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR=
A2AT_LANGUAGE=zh-CN
```

### 1.8.2 Local File Extension
When customizing scenarios, prepare local resources with the following structure:

```text
prompt_resources/
  scenarios/zh-CN/scenarios.json
  slots/{scenario_code}/zh-CN/slot.json
  templates/{scenario_code}/zh-CN/template.md
  prompts/scenario_recognition/zh-CN/system.md
  prompts/scenario_recognition/zh-CN/user.md
  prompts/slot_extraction/zh-CN/system.md
  prompts/slot_extraction/zh-CN/user.md
  prompts/semantic_validation/zh-CN/system.md
  prompts/semantic_validation/zh-CN/user.md
```

Then specify in `.env`:

```properties
A2AT_PROMPT_SOURCE_TYPE=local_file
A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR={your_prompt_resources_root}
A2AT_LANGUAGE=zh-CN
```

It is recommended to add the following tests for new resources:

1. Scenario loading tests.
2. Slot Schema loading and validation tests.
3. Client prompt generation tests.
4. Server prompt compliance validation tests.
5. Sample-level end-to-end verification.

### 1.8.3 How to Define Prompt Templates
#### 1.8.3.1 Core Value
A2A-T Structured Prompt provides a reusable structured approach for providing clear and consistent prompts to LLMs. By separating core logic from variable data, it makes interactions between agents more reliable, efficient, and scalable. The main benefits of using Structured Prompt include:
- 	Consistency: Ensures prompts follow a standardized format, making agent output more predictable.
-	Efficiency: Avoids writing each Prompt from scratch, saving time and effort. Also avoids repeating complex instructions.
-	Scalability: Makes it easier to generate prompts for various business scenarios.
-	Optimization: Allows templates to be refined and optimized for better results.

#### 1.8.3.2 Classification of A2A-T Prompt Templates
For agent communication in the telecom domain, to ensure completeness of request content and improve reasoning efficiency and accuracy, A2A-T defines Structured Prompt templates for each AN high-value scenario, and published the industry A2A-T protocol standards at TMF:
《IG1453A_Structured_Prompt_of_Agent_to_Agent_Protocol_for_Telecoms_A2AT_v1.0.0》
《IG1453_Agent_to_Agent_Protocol_for_Telecoms_A2AT_v2.0.0》

Structured Prompt template definitions are divided into two layers:
- L0 Basic Templates:
Define the foundational framework of Structured Prompt for ICT domain tasks, without specifying variables and ontology for specific scenarios.
L0 template list:

	| Template Name | Description   |
	|--|--|
	|Task-T  | Defines the basic structure of ICT domain tasks, but does not specify commonly used variables and general ontology specifications for specific scenarios. Parsing of basic templates relies on the LLM's reasoning ability and the Agent's context processing ability. |
	|Notification-T | Defines a structured prompt-based network event subscription and reporting mechanism for the ICT domain. This mechanism ensures real-time perception of network events, and through structured prompts, provides consistent task descriptions across different levels and domains. |

- L1 Value Scenario Prompt Templates:
Building on L0 templates, commonly used "variables" are defined for different high-value scenario tasks, so that during task generation, agents can input corresponding content based on these variables, and during task execution, identify related content to improve reasoning efficiency and accuracy.

#### 1.8.3.3 Core Composition Elements
A complete A2A-T Prompt template generally contains the following two parts:
1. Instructions
	- Definition: Core directives or context.
	- Role: Provides the basic requirements and framework of the task.
	- Syntax: Use ## to mark instruction names directly (e.g., ## 任务描述).
2. Variables
	- Definition: Dynamic slots, filled with specific data each time they are used.
	- Role: Provides more specific information, improving reasoning efficiency.
	- Syntax: Use double curly braces {{}} to mark variable names (e.g., {{故障发生时间}}).

##### 1.8.3.3.1 Instructions

1. Instruction syntax requirements: When declaring "instructions", use "##" for marking, followed by the name of the "instruction", so that the Agent can recognize it and thereby implement content input or corresponding reasoning and execution.
2. Instruction set: The Structured Prompt defined by A2A-T has established the foundational framework for ICT task Prompt templates, deconstructing typical ICT task information into the following instructions.


| Instruction Name | Required/Optional | Description & Example |
|--|--|--|
| 任务描述 |	Required 	| Describes the basic requirements of the task. Example:<br> `## 任务描述` <br> `Please analyze the root cause of the fault based on "目标对象", "任务上下文", and "约束条件", and provide repair suggestions. Please respond to the task according to the structure defined in "预期输出".`
|任务类型|Optional  |Identifies the task type (e.g., fault diagnosis, energy efficiency optimization). Example:<br>`## 任务类型`<br>`Fault diagnosis `
|目标对象|Optional|  Describes the direct object of the task operation. Example:<br> `## 目标对象`<br>`Fault identifier (fault-csn) is "OSS-FAULT-20250405-001".`
|任务上下文|Optional  |Provides background information for task execution. Example:<br>`## 任务上下文`<br>`Fault occurrence time (occur-time) is "2025-04-05T14:30:00Z". `
| 预期输出|Optional  | Defines the format of expected results. Example:<br>`## 预期输出`<br> `Fault diagnosis results should include the following information: 1. Diagnosis status: success or failure 2. Fault diagnosis analysis results 3. Repair suggestions 4. List of root causes of the fault 5. Domain-specific information`

##### 1.8.3.3.2 Variables
1. Variable syntax requirements: When using "variables", use double curly braces "{{}}" for marking, and place the variable name inside the double curly braces, so that the agent can recognize it and thereby implement content input or corresponding reasoning and execution.
2. Variable instantiation methods: Variables need to be correctly instantiated to provide value. Two recommended methods
	- Natural language subject-verb-object structure: The fault occurred at 2026-01-08 16:38:18
	- key-value concise format: 故障发生时间：2026/1/8/16:38:18
3. Common variable set:
A2A-T has summarized commonly used variables in AN L4 high-value scenarios based on best practices. These variables help effectively describe tasks in AN L4 high-value scenarios:
	| Variable Name | Required/Optional | Description & Example |
	|--|--|--|
	| 标识符 |	Required 	| Used to specify the target identifier associated with the task.<br> Example:<br>`## 目标对象`<br> `{{标识符}}` <br>Instance example: <br>`## 目标对象`<br>`Fault identifier (fault-csn) is "OSS-FAULT-20250405-001".`
	|受影响对象|Optional  |Used to specify the network resource object affected by the fault.<br>Example:<br>`## 任务上下文`<br>`{{受影响对象}}`<br>Instance example:<br>`## 任务上下文 `<br>`The ID of the affected object is "BTS-001", type is "Base Station Transceiver", name is "Base Station 001", location is "Chaoyang District, Beijing".`
	|相关信息|Optional| Its general ontology can be a list of events or alarms related to the fault.<br> Example:<br>`## 任务上下文 `<br>`{{相关信息}} `<br>Instance example:<br>`## 任务上下文`<br>`The associated alarm list is as follows: - Alarm identifier (alarm-csn) is "ALM-20250405-001", alarm ID (alarm-id) is "ALM-001", alarm name is "Base Station Signal Loss", network element name is "BTS-001", alarm location is "Chaoyang District, Beijing", alarm occurrence time (alarm-create-time) is "2025-04-05T14:28:00Z". - Alarm identifier (alarm-csn) is "ALM-20250405-002", alarm ID (alarm-id) is "ALM-002", alarm name is "Transmission Link Interruption", network element name is "TRX-002", alarm location is "Haidian District, Beijing", alarm occurrence time (alarm-create-time) is "2025-04-05T14:29:00Z".`
	|故障发生时间| Required | Its general ontology is the time when the fault occurred.<br>Example:<br>`## 任务上下文 `<br>`{{相关信息}}`<br>Instance example:<br>`## 任务上下文 `<br>`Fault occurrence time is "2025-04-05T14:30:00Z".`
	| 故障上下文对象 | Required  | Its general ontology can be fault pre-processing information from OSS, or alarm reporting information from EMS.<br>Example:<br>`## 任务上下文`<br>`{{故障上下文对象}}`<br>Instance example:<br>`## 任务上下文 `<br>`Fault context object is: "Alarm Management System: FMC, alarm location: Beijing, alarm name: Base Station Signal Loss, alarm time: 2025-04-05T14:28:00Z, alarm network element: BTS-001".`
    
#### 1.8.3.4 Format and Specification
A2A-T commonly used text format syntax specification requirements, including paragraphs, lists, links, using Markdown format to ensure structured and readable output:
- Paragraphs: Separate text blocks with blank lines
- Ordered lists: Number plus period (1. Item one)
- Unordered lists: Dash at the beginning (- Item one)
- Links: Square brackets plus parentheses ([text](link))

##### 1.8.3.4.1 Paragraphs
To create a paragraph, use blank lines to separate one or more lines of text. Example:
```
## 任务描述
Handle 5G service fault in Community A

Complete service restoration

Identify the root cause of the fault and perform repair
```

##### 1.8.3.4.2 Ordered Lists
To create an ordered list, add items represented by numbers plus periods. The numbers do not need to be in sequence, but the list should start with number 1. Example:
```
## 预期输出 
1. Bar
2. Foo
```

##### 1.8.3.4.3 Unordered Lists
To create an unordered list, add a dash (-) before each item. Indent one or more items to create a nested list. Example:
```
## 预期输出
- Item
  - Item 1
- Bar2
- Foo
```
##### 1.8.3.4.4 Links
To create a link, enter the link text in square brackets, followed immediately by the URL in parentheses. Example:
```
## 任务描述
Handle the issue of [TM Forum AN](Autonomous Network project homepage) failing to load.
```


#### 1.8.3.5 Steps for Template Definition
It is recommended to define prompt templates following these steps:
1.	Determine task type: Clarify the task type and collaboration mode for the current business scenario, and locate the corresponding template category from the A2A-T task classification system (e.g., Task-T);
2.	Write required instructions: Select key instructions, and use structured format to declare task goals, execution conditions, input parameters, expected output, etc.;
3.	Fill in commonly used variables: Declare the specific parameters involved in this task instance; variable references must follow A2A-T variable syntax specifications;
4.	Bind context information: Supplement the context information needed by the agent to complete the task;
5.	Set output definition: Clearly define the output format, acceptance criteria, and exception handling rules;
6.	Verify template completeness: Conduct thorough testing in actual cross-model environments to verify syntax compliance and cross-LLM compatibility;
7.	Version iteration and optimization: Incorporate validated templates into version management for continuous governance, iteration, and evolution.

## 1.9 Testing Recommendations

Common test commands:

```bash
mvn test
```

When modifying client prompt generation:

```bash
mvn -pl a2a-t-client -am test
```

When modifying server validation:

```bash
mvn -pl a2a-t-server -am test
```

When modifying negotiation state machine:

```bash
mvn -pl a2a-t-negotiation -am test
```

When modifying sample:

```bash
mvn -pl a2a-t-sample -am -DskipTests package
```
