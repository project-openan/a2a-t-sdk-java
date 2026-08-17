# AGENTS.md

Guidance for AI coding assistants working in this repository. Read this before making changes.

## Project Overview

`a2a-t-sdk-java` is a Java SDK for the A2A-T Extension, based on the A2A protocol.
It supports `Task-T`, `Notification-T`, `Negotiation-T`, `Authorization-T` extensions.
Main features include A2A-T message generation, prompt compliance validation, negotiation flow management.

## Key Guidelines

- Before any design or development task, read `design/AGENTS.md` for the full workflow, stage-specific triggers, and design directory structure.
- Base fixes, reviews, and technical judgments on source code, call sites, tests, and dependency contracts; do not conclude from memory.
- Always update design spec and documents before changing the code. Keep them in sync. If implementation conflicts with the design, determine which is correct.
- Always use the test-driven-development workflow to develop code.
- Follow Java best practices and idiomatic patterns. Follow software principles such as DRY and YAGNI.
- Maintain existing code structure and organization.
- Keep diffs as minimal as possible.
- Document public APIs and complex logic.
- Write unit tests for new functionality focusing on behavior and not implementation.