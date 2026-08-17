# Design Principles

Design principles that must be followed when creating spec and impl documents. These principles are derived from the project's existing code patterns, not imposed from outside.

## Architecture Design (for spec documents)

Spec documents define capability boundaries, module interactions, and contracts. The following principles constrain how specs are designed.

### Module Boundary

A capability owns its data, its logic, and its error types. When a feature crosses module boundaries, define a clear contract: what data flows in, what data flows out, and which errors can propagate.

- Prefer **narrow contracts** — expose only what downstream modules need.
- A module should not reach into another module's internal data structures.
- Cross-module dependencies should flow from high-level (orchestrator) to low-level (utility), not sideways.

### Dependency Direction

Dependencies must flow from high-level (orchestrator/facade) to low-level (utility/common). No circular dependencies. See `architecture.md` for the concrete module dependency graph.

When adding a new capability, follow this layering: define the contract in a shared module, implement the logic in an orchestrator, expose it through the client or server facade.

### Capability Decomposition

A capability is a self-contained feature with a clear input/output boundary. When designing a spec:

- One spec = one capability. Do not merge unrelated features into one spec.
- If a feature has server-side and client-side responsibilities, they may share a spec but must clearly separate the two roles.
- Do not prematurely split a capability into sub-features. Start with one spec and split when the spec becomes too large to reason about.

### Error Hierarchy

Every module that can fail must define its own error hierarchy:

- One base exception class per module (e.g., `LLMError`, `SdkException`).
- Specific subclasses for distinct failure modes (e.g., `LLMConfigError`, `LLMRuntimeError`).
- Error types should carry enough context for callers to handle them programmatically (e.g., `ConfigFileNotFoundException` carries the file path).

### Degradation Strategy

When a dependency is optional or may fail, define a degradation strategy in the spec:

- Fall back to a safe default (e.g., `NegotiationStore` falls back to `InMemoryNegotiationStore`).
- Throw typed exceptions or return failure results — do not silently swallow errors.
- Do not degrade across security boundaries.

## Code Design (for impl documents)

Impl documents translate architecture decisions into class-level design. The following principles constrain how code is organized.

### SOLID Principles (Java-adapted)

| Principle | In this project |
|-----------|----------------|
| **Single Responsibility (SRP)** | Each class does one thing. An orchestrator coordinates, a builder wires, a renderer renders. Do not merge them. |
| **Open/Closed (OCP)** | Extend through new classes, not by modifying existing ones. Add a new negotiation type by implementing the `Negotiation` interface, not by adding branches. |
| **Liskov Substitution (LSP)** | Implementations must honor the contract of their interface. Override hook methods, not core logic methods. |
| **Interface Segregation (ISP)** | Keep interfaces narrow. `LLMClient` exposes only `structured()`, not every capability an LLM might have. |
| **Dependency Inversion (DIP)** | Depend on interfaces, not concrete classes. `LLMClientFactory` depends on `LLMClient` interface, not `OpenAIClient`. |

### Java-Specific Principles

**Composition over inheritance.** Prefer passing dependencies through constructors over inheriting behavior. Builders are the single place where wiring happens.

**Interface over abstract class.** Use Java interfaces for extension contracts. Implementers don't need to share a base class — they just need the right method signatures.

**Java records for data, classes for behavior.** Use `record` types for immutable value objects and DTOs (e.g., `A2ATConfig`, `PromptGenerationResult`). Use regular classes for objects with behavior. Lombok is available as a `provided` dependency but is minimally used — only `@NoArgsConstructor` is currently employed for utility classes.

**Constructor injection.** No global state, no service locators, no magic auto-wiring. Every dependency is passed through the constructor. Builders are the single place where wiring happens.

**No DI framework.** The project does not use Spring or any DI container. All wiring is explicit through constructors and builders.

### Project Patterns

These patterns are proven in the existing codebase. New code should follow them.

**Builder + Orchestrator.** High-level facades (`A2ATClient`, `A2ATServer`) are thin. Each capability has a `*Builder` that constructs a `*Orchestrator`. The builder is the wiring point; the orchestrator is the logic point. Do not put logic in builders or wiring in orchestrators.

**Factory + Registry.** For pluggable backends, use a registry mapping name → implementation. The factory creates instances from the registry. See `LLMClientFactory` for the canonical pattern.

**Strategy pattern for variant behavior.** When a behavior has multiple variants, define an interface and implement each variant. See `Negotiation` → `TargetNegotiation`, `InformationNegotiation`, `FeasibilityNegotiation`.

**Typed results, typed errors.** Orchestrator methods return result objects that encapsulate success/failure (e.g., `PromptGenerationResult` / `PromptGenerationFailure`, `PromptComplianceResult` / `PromptComplianceFailure`). Do not throw exceptions for expected failure paths.

### When to Abstract

| Situation | Action |
|-----------|--------|
| Single implementation, no cross-module boundary | Concrete class. No abstraction needed. |
| Single implementation, cross-module boundary | Define an interface so the caller doesn't depend on the implementation module. |
| Multiple implementations expected | Interface + Factory + Registry. |
| Behavior variant within same module | Strategy pattern (interface + implementations). |

Do not abstract for hypothetical future needs. Abstract when a second implementation is actually needed, or when a cross-module boundary demands it.

### Anti-Patterns

**Middle Man.** A class that does nothing but delegate every method call to another class without adding logic. If a class is just a pass-through, remove it and let callers depend on the real implementation directly.

**Premature Abstraction.** An interface or abstract class created for a single implementation "just in case." Wait until a second implementation exists before introducing the abstraction.