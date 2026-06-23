<!--
Copyright (c) 2026 Huawei Technologies Co., Ltd.
All Rights Reserved.

SPDX-License-Identifier: Apache-2.0

   Licensed under the Apache License, Version 2.0 (the "License"); you may
   not use this file except in compliance with the License. You may obtain
   a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
   WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
   License for the specific language governing permissions and limitations
   under the License.
-->

# a2a-t-sdk-java

<p align="center">
  <a href="https://dev.java/"><img src="https://img.shields.io/badge/java-17+-orange.svg" alt="Java"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-Apache%202.0-green.svg" alt="License"></a>
</p>

<p align="center">
  <strong>Java SDK used to generate task prompts and handle task negotiation flows based on the A2A-T protocol.</strong>
  <br>
  基于A2A-T协议用于生成任务提示词并处理任务协商流程的Java SDK。
</p>

<p align="center">
  <a href="./README_zh.md">中文</a>
</p>

---

## Project Overview

`a2a-t-sdk-java` is a Java SDK targeting telecom scenarios, used to generate task prompts and handle task negotiation flows.

This SDK is primarily aimed at two types of users:

- Client: Generates task prompts based on user input, and initiates, receives, and advances negotiation flows.
- Server: Validates A2A-T protocol messages that conform to the SDK format, and initiates, receives, and advances negotiation flows.

## Core Capabilities

- Task prompt generation pipeline: Covers input normalization, scenario recognition, slot extraction, and task prompt rendering.
- Client API: Provides a task prompt generation result stream, along with negotiation entry points such as `start_negotiation`, `receive_negotiation`, and `continue_negotiation`.
- Server validation API: Targets A2A-T protocol messages that conform to the SDK format, performing metadata parsing, slot extraction.
- Negotiation types: Includes four built-in negotiation types: `information`.
- Resource organization: Built-in prompt resources are located in `a2a-t-resources/src/main/resources/prompt_resources`, containing `prompts`, `scenarios`, `slots`, and `templates`.
- Built-in example scenario: Currently, the package provides scenarios such as`subscribe_incident`.

## Project Structure

The repository is organized as a Maven multi-module reactor, with core code under each module's `src/main/java`. The main modules are:

- `a2a-t-bom`: Bill of materials (BOM) that aligns library module versions.
- `a2a-t-core`: Shared `.env`-driven configuration loading, value types, JSON parsing abstractions, and the exception processing.
- `a2a-t-resources`: Packaging and classpath loading of built-in prompt resources.
- `a2a-t-llm`: LLM adapter layer, LLMs can be customized and connected. By default, OpenAIClient is provided, and LLMs compatible with OpenAI specifications can be connected.
- `a2a-t-prompt`: Prompt resource model and loading, scenario recognition, slot extraction, and template rendering.
- `a2a-t-negotiation`: Negotiation types, runtime state machine, and state storage.
- `a2a-t-client`: Client facade providing task prompt generation and negotiation entry points.
- `a2a-t-server`: Server facade providing `processed task prompt` validation and negotiation entry points.
- `a2a-t-sample`: Runnable client/server sample.

## Installation and Environment Requirements

- Java requirement: `>=17`
- Build tool: Maven (no wrapper is bundled; install it locally)
- Group ID: `net.openan.a2a-t.sdk`
- License: `Apache-2.0`
- It is recommended to import `a2a-t-bom` to align module versions:

Before starting, edit `client.env` / `server.env` at the repo root (or refer to `env.example`) and fill in a valid `A2AT_LLM_API_KEY` and other settings.

## Development and Testing

The project uses a Maven multi-module reactor build, with code formatting unified by `spotless-maven-plugin` (Palantir Java Format) and tests based on JUnit 5 (Jupiter).

Each module's `src/test/java` mirrors the main package structure, covering the negotiation state machine, the four negotiation type handlers, classpath and local-file loaders, prompt rendering, and client/server orchestration. For a runnable end-to-end sample, see `a2a-t-sample` and [a2a-t-sample/README.zh-CN.md](a2a-t-sample/README.zh-CN.md).

## Current Scope of Support

Before use, it is recommended to confirm the following limitations:

- The built-in LLM invocation chain is unified externally as an OpenAI-compatible adaptation layer.
- Prompt resources currently only support local files.
- Negotiation state storage currently only provides an in-memory implementation and does not guarantee persistence.
- The bundled resources and language coverage are limited, and do not include remote resource loading capabilities such as `registry-center`.
- This document primarily introduces the SDK itself, and does not cover the CLI, hosted services, deployment processes, or ready-to-use application solutions.

## License

This project is licensed under the [Apache-2.0](LICENSE) license.
