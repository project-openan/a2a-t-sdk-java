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
  <strong>基于A2A-T协议用于生成任务提示词并处理任务协商流程的Java SDK。</strong>
  <br>
  Java SDK used to generate task prompts and handle task negotiation flows based on the A2A-T protocol.
</p>

<p align="center">
  <a href="./README.md">English</a>
</p>

---

## 项目简介

`a2a-t-sdk-java` 是一个面向电信场景的 Java SDK，用于生成任务提示词并处理任务协商流程。

这个 SDK 主要面向两类使用方：

- 客户端：根据用户输入生成任务提示词，并发起、接收和推进协商流程。
- 服务端：校验符合 SDK 格式的 A2A-T协议报文（处理后的任务提示词），并发起、接收和推进协商流程。

## 核心能力

- 任务提示词生成链路：覆盖输入归一化、场景识别、槽位提取、任务提示词渲染。
- 客户端 API：提供任务提示词生成结果流，以及 `start_negotiation`、`receive_negotiation`、`continue_negotiation` 等协商入口。
- 服务端校验 API：面向符合 SDK 格式的 `processed task prompt`，执行元数据解析、槽位提取和槽位校验。
- 协商类型：内置 `information` 协商类型。
- 资源组织：内置提示词资源位于 `a2a-t-resources/src/main/resources/prompt_resources`，包含 `prompts`、`scenarios`、`slots`、`templates`。
- 内置示例场景：当前随包提供 `subscribe_incident`等场景。

## 项目结构

仓库采用 Maven 多模块组织，核心代码位于各模块的 `src/main/java`，主要模块如下：

- `a2a-t-bom`：物料清单（BOM），统一管理各库模块版本。
- `a2a-t-core`：共享的 `.env` 配置加载、值类型、JSON 解析抽象与异常处理。
- `a2a-t-resources`：内置提示词资源的打包与 classpath 加载。
- `a2a-t-llm`：LLM 适配层，支持自定义LLM接入，默认提供OpenAIClient，支持接入兼容OpenAI规范的LLM。
- `a2a-t-prompt`：提示词资源模型与加载、场景识别、槽位提取与模板渲染。
- `a2a-t-negotiation`：协商类型、运行时状态机与状态存储。
- `a2a-t-client`：客户端封装，提供任务提示词生成与协商入口。
- `a2a-t-server`：服务端封装，提供A2A-T协议报文的校验与协商入口。
- `a2a-t-sample`：可运行的客户端/服务端示例。

## 安装与环境要求

- Java 要求：`>=17`
- 构建工具：Maven（仓库未内置 wrapper，需本地安装）
- Group ID：`net.openan.a2a-t.sdk`
- 许可证：`Apache-2.0`

开始前，建议先编辑仓库根目录的 `client.env` / `server.env`（或参考 `env.example`），补充可用的 `A2AT_LLM_API_KEY` 等配置。


## 开发与测试

项目使用 Maven 多模块 reactor 构建，代码格式化由 `spotless-maven-plugin`（Palantir Java Format）统一管理，测试基于 JUnit 5（Jupiter）。

各模块的 `src/test/java` 与 main 包结构对应，覆盖协商状态机、四类协商处理器、classpath 与本地文件加载器、提示词渲染以及客户端/服务端编排等测试用例。可运行的端到端示例见 `a2a-t-sample`，详见 [a2a-t-sample/README.zh-CN.md](a2a-t-sample/README.zh-CN.md)。

## 当前支持范围

使用前建议先确认以下限制：

- 内置 LLM 调用链对外统一为 OpenAI-compatible 适配层。
- 提示词资源目前仅支持本地文件。
- 协商状态存储目前仅提供内存实现，不保证持久化。
- 随包资源与语言覆盖有限，不包含 `registry-center`（注册中心）等远程资源加载能力。
- 本文档主要介绍 SDK 本身，不涉及 CLI、托管服务、部署流程或可直接使用的应用方案。

## 许可证

本项目采用 [Apache-2.0](LICENSE) 许可证。
