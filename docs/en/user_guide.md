# 1 a2a-t-sdk-java User Guide

## 1.1 Feature Introduction
### 1.1.1 What is A2A-T?
A2A-T (Agent-to-Agent Telecom) is a multi-agent interconnection protocol for the telecom domain based on the A2A protocol, designed specifically for complex collaboration scenarios in the telecom domain.
Industry-generic agent interconnection protocols primarily focus on agent interconnection and interaction frameworks, with insufficient attention to business scenarios and specific interaction content, resulting in low task completion success rates. Business scenarios in the telecom domain are complex and demanding; operations agent interconnection and collaboration require dedicated protocol support. The A2A-T solution is based on the A2A protocol, with focused application extensions for telecom domain business flow-related information models, task negotiation, and collaboration security enhancement capabilities.

### 1.1.2 Relationship Between A2A-T SDK and A2A SDK
 
The A2A-T protocol is an extension based on the A2A protocol. The A2A-T SDK is provided for the protocol extension content, supporting rapid construction of agents for complex collaboration scenarios in the telecom domain. The A2A-T SDK is independent of the A2A SDK; by integrating both the A2A-T SDK and the A2A SDK simultaneously, you can build agents that support the A2A-T protocol, enabling deterministic, highly reliable, efficient, and secure collaboration among multiple agents in the telecom domain.

### 1.1.3 Capability Introduction

a2a-t-sdk-java is the Java SDK implementation of the A2A-T protocol, providing client prompt generation, server prompt validation, and negotiation runtime capabilities. 

Main capabilities include:

- **Client prompt generation**: Convert natural language or structured input into processed task prompt via `A2ATClient`.
- **Server prompt validation**: Validate the scenario, slot, and semantic consistency of processed task prompt via `A2ATServer`.
- **Negotiation flow**: Support negotiation type: `information`.
- **Modular Maven project**: Organized by layers: core, resources, llm, prompt, negotiation, client, server, sample.
- **A2A Java integration sample**: `a2a-t-sample` demonstrates real HTTP+JSON/REST links based on `a2a-java v1.0.0.Beta1`.

## 1.2 Application Scenarios

The Java SDK is typically used in the following scenarios:

1. A Java client Agent receives user intent and generates an A2A-T processed task prompt.
2. A Java server Agent receives an A2A request, validates the prompt, and proceeds to business execution.
3. Client and server perform multi-round information supplementation or task feasibility confirmation through negotiation context.
4. Work with the registry-center to implement AgentCard registration, query, and A2A invocation address resolution.

## 1.3 Environment Requirements

| Item | Requirement |
| --- | --- |
| JDK | 17+ |
| Build Tool | Maven 3.8+ |
| LLM | Accessible OpenAI-compatible service and API Key |
| Registry Center | registry-center must be started before running Java sample |
| A2A Java | sample based on `a2a-java v1.0.0.Beta1` |

## 1.4 Build and Install

Execute in the repository root directory:

```bash
cd {project_path}/a2a-t-sdk-java
mvn -DskipTests package
```

To use a local Maven repository directory:

```bash
mvn "-Dmaven.repo.local=.mvn/repository" -DskipTests package
```

Run tests:

```bash
mvn test
```

## 1.5 Prepare Configuration

The repository root directory provides `client.env` and `server.env`, which can be modified according to your local environment.

Client configuration example:

```properties
A2AT_LANGUAGE=zh-CN
A2AT_PROMPT_SOURCE_TYPE=classpath
A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR=
A2AT_LLM_PROVIDER=openai_compatible
A2AT_LLM_MODEL=deepseek-chat
A2AT_LLM_BASE_URL=https://api.deepseek.com
A2AT_LLM_API_KEY={your_api_key}
A2AT_NEGOTIATION_STATE_STORE_TYPE=in_memory
A2AT_SAMPLE_HOST=127.0.0.1
A2AT_SAMPLE_PORT=26335
REGISTRY_CENTER_HOST=127.0.0.1
REGISTRY_CENTER_PORT=5001
```

Server configuration example:

```properties
A2AT_LANGUAGE=zh-CN
A2AT_PROMPT_SOURCE_TYPE=classpath
A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR=
A2AT_LLM_PROVIDER=openai_compatible
A2AT_LLM_MODEL=deepseek-chat
A2AT_LLM_BASE_URL=https://api.deepseek.com
A2AT_LLM_API_KEY={your_api_key}
A2AT_NEGOTIATION_STATE_STORE_TYPE=in_memory
A2AT_SAMPLE_HOST=127.0.0.1
A2AT_SAMPLE_PORT=26335
REGISTRY_CENTER_HOST=127.0.0.1
REGISTRY_CENTER_PORT=5001
```

## 1.6 Quick Run Example

The Java sample module is located at `a2a-t-sdk-java/a2a-t-sample`.

### 1.6.1 Prerequisites

1. Fill in `A2AT_LLM_API_KEY` in `client.env` and `server.env`.
2. Start the sample server before running the sample client.
3. To query AgentCard directly from the sample server, make sure `A2AT_SAMPLE_HOST` and `A2AT_SAMPLE_PORT` in `client.env` match the server listen address.
4. To query AgentCard through registry-center instead, start registry-center and confirm that `REGISTRY_CENTER_HOST` and `REGISTRY_CENTER_PORT` in `client.env` and `server.env` point to the same registry-center.

### 1.6.2 Build sample

```bash
cd {project_path}/a2a-t-sdk-java
mvn "-Dmaven.repo.local=.mvn/repository" -pl a2a-t-sample -am -DskipTests package
```

### 1.6.3 Start Server

```bash
java @a2a-t-sample/target/server.javaargs.txt
```

After the server starts, it will:

- Construct a sample AgentCard.
- Register the AgentCard with the registry-center.
- Start the local A2A HTTP service.
- Wait for client requests and validate the A2A-T prompt.

### 1.6.4 Start Client

Open another terminal and execute:

```bash
java @a2a-t-sample/target/client.javaargs.txt
```

The client will:

1. Read sample input from `sample/client/scenario.json`.
2. Query the target AgentCard directly from the sample server root path `GET /` when `A2AT_SAMPLE_HOST` and `A2AT_SAMPLE_PORT` are configured; otherwise query it from registry-center.
3. Generate processed task prompt using `A2ATClient`.
4. Construct an A2A HTTP+JSON/REST request.
5. Receive server task status, message, and artifact events.


## 1.7 Configuration File Quick Reference

| Configuration Item | Description |
| --- | --- |
| `A2AT_LANGUAGE` | Prompt resource language, commonly `zh-CN` |
| `A2AT_PROMPT_SOURCE_TYPE` | Prompt resource source, supports `classpath` and `local_file`; default is `classpath` |
| `A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR` | Local prompt resource root directory |
| `A2AT_LLM_PROVIDER` | LLM provider, sample uses `openai_compatible` |
| `A2AT_LLM_MODEL` | Model name |
| `A2AT_LLM_BASE_URL` | LLM service address |
| `A2AT_LLM_API_KEY` | LLM API Key |
| `A2AT_NEGOTIATION_STATE_STORE_TYPE` | Negotiation state storage, currently supports `in_memory` |
| `A2AT_SAMPLE_HOST` | Sample server listen address; the client also uses it to query AgentCard directly from the server root |
| `A2AT_SAMPLE_PORT` | Sample server listen port; the client also uses it to query AgentCard directly from the server root |
| `REGISTRY_CENTER_HOST` | Registry-center address |
| `REGISTRY_CENTER_PORT` | Registry-center port |

## 1.8 Constraints and Limitations

1. The current version is Alpha; interfaces and module organization may change with version evolution.
2. Negotiation state currently uses in-memory storage; cross-process persistence is not provided.
3. `A2ATClient` and `A2ATServer` do not automatically discover `.env`; the caller must explicitly pass in the configuration file path.
4. The SDK does not provide authentication, key management, or AgentCard registry-center service capabilities.
5. The sample client can query AgentCard directly from the sample server root path when `A2AT_SAMPLE_HOST` and `A2AT_SAMPLE_PORT` are configured. Registry-center is only required when using registry-center AgentCard query mode.
