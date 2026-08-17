# Architecture & Key Patterns

## Repository Layout

```
a2a-t-core/           # Shared configuration, value types, JSON parsing, exceptions
a2a-t-resources/      # Classpath-loaded prompt resources (scenarios, slots, templates, prompts)
a2a-t-llm/            # LLMClient interface, factory, config loader, providers/
a2a-t-prompt/         # Prompt resource model & loading, scenario recognition, slot extraction, template rendering
a2a-t-negotiation/    # Negotiation types, runtime state machine, state storage
a2a-t-client/         # A2ATClient facade + prompt generation orchestration
a2a-t-server/         # A2ATServer facade + prompt compliance validation orchestration
a2a-t-bom/            # Bill of materials POM for version alignment
a2a-t-sample/         # Runnable sample with A2A Java HTTP integration
docs/                 # Developer guide and user guide (en/zh)
```

### Module Dependency Graph

```
a2a-t-core ─────────────────────────────────────────────────────────────┐
                                                                         │
a2a-t-resources ──► a2a-t-core                                          │
a2a-t-llm        ──► a2a-t-core                                          │
a2a-t-prompt     ──► a2a-t-core, a2a-t-resources, a2a-t-llm            │
a2a-t-negotiation ──► a2a-t-core                                         │
                                                                         │
a2a-t-client ──► a2a-t-core, a2a-t-resources, a2a-t-llm,               │
                 a2a-t-prompt, a2a-t-negotiation                         │
                                                                         │
a2a-t-server ──► a2a-t-core, a2a-t-llm, a2a-t-prompt,                  │
                 a2a-t-negotiation, a2a-t-resources                      │
                                                                         │
a2a-t-sample ──► a2a-t-client, a2a-t-server                             │
```

## Builder + Orchestrator

High-level `A2ATClient` / `A2ATServer` are thin facades. Each capability is assembled by a `*Builder` that constructs a `*Orchestrator`, which does the real work. The facade delegates every public call.

- `a2a-t-client/.../prompt/assembly/DefaultA2ATClientBuilder` → `DefaultClientPromptGenerationOrchestrator`
- `a2a-t-server/.../assembly/DefaultA2ATServerBuilder` → `DefaultServerPromptComplianceOrchestrator`
- `a2a-t-negotiation/.../runtime/NegotiationHandler` wraps the negotiation runtime

## Factory + Registry

`a2a-t-llm/.../LLMClientFactory` keeps a registry mapping provider name → client implementation. New providers are registered and created through the factory. The negotiation state store follows the same pattern (`NegotiationStore` interface + `InMemoryNegotiationStore`).

## Interface-based Contracts

`LLMClient` is a Java interface with a single `structured(...)` method. Provider clients (e.g. `OpenAIClient`) implement it. `NegotiationStore` is an interface for state storage pluggability.

## Configuration from .env

All runtime config is read from an `.env` file via `DotEnvConfigSource`. Configuration is loaded into typed value objects:

- `A2ATConfig` — prompt runtime config, compliance config, negotiation config
- `LLMConfigLoader` — LLM client config

Both validate and throw typed errors (`ConfigFileNotFoundException`, `LLMConfigError`) on bad input. `A2ATClient` / `A2ATServer` accept `Path envFile` at construction time.

## Error Hierarchy

- `a2a-t-core`: `SdkException` → `ConfigFileNotFoundException`, `ResourceNotFoundException`
- `a2a-t-llm`: `LLMError` (extends `RuntimeException`) → `LLMConfigError`, `LLMRuntimeError`
- `a2a-t-prompt`: `TaskPromptRenderException` (extends `SdkException`)
- `a2a-t-negotiation`: `NegotiationStateException` (extends `SdkException`)
- `a2a-t-server`: `PromptComplianceCheckException` (extends `RuntimeException`)

## Negotiation Types

`Negotiation` interface defines the contract with a single `processReceivedMessage` method. `NegotiationHandler` provides the high-level orchestration with `start`, `receive`, and `continue` methods. `NegotiationHandler` is built via its nested `Builder` inner class. Concrete implementations: `TargetNegotiation`, `InformationNegotiation`, `FeasibilityNegotiation`. State is managed through `InMemoryNegotiationStore`.