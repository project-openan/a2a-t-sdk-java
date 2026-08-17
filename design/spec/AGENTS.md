# Specifications

This directory contains feature-level design specifications. A spec describes the current design facts: what problem this capability solves, what the boundaries are, what the call chain looks like, and what interfaces and data it depends on.

## When to Create or Update a Spec

Create or update a spec when:

- Adding a new capability or changing capability boundaries.
- Changing call chains, module responsibilities, or cross-module dependencies.
- Changing public interfaces, events, configuration, data models, or state machines.
- Changing authentication, authorization, permission boundaries, exception handling, or degradation strategies.
- Changing critical flows, failure modes, observability, or testing strategies.

Do NOT create or update a spec for:

- Bug fixes that do not change design facts.
- Local refactoring that does not change responsibility boundaries.
- Code style, formatting, or comment adjustments.
- One-off task plans, meeting notes, or implementation logs.

Every spec must have a corresponding implementation document. Before writing code, ensure both spec and impl documents exist.

When modifying code that touches the scope above, always update the spec first, then the impl, then change the code.
If implementation conflicts with the spec, determine which is correct and update the spec if it is outdated, or fix the implementation if it deviates from the spec.
If the change introduces or overturns an architectural-level choice, also check whether an ADR is needed in `../decision/`.
When unsure whether a spec update is needed, state the judgment basis and residual risk in the delivery notes.

## Template
When creating a new spec, use the following template.
**Overall constraint**:
- This is a design document, not code or implementation. Do not write code implementations, class names, method signatures, or object definitions.
- Do not describe class-level or code-level interactions — only describe business logic level interactions.
- Do not reference specific code files, classes, or implementation details.
- Keep descriptions concise — use diagrams and bullet points; do not use large blocks of prose.
- For sections that are not applicable, mark 'Not applicable' and state the reason.

```markdown
# Title

- Impact Scope: concise description of the impact scope. Do not reference actual code
- Related ADR: references to related ADR documents
- Related Impl: references to related impl documents
- Last Updated: YYYY-MM-DD

## Background
Explain why this capability exists, what problem it solves, and key constraints. Keep it concise.

## Goals
List the business and engineering goals this design must achieve. Goals should be verifiable.

## Non-Goals
Explicitly state what this design does NOT cover, to prevent scope creep.

## Core Concepts
Define domain objects, key terminology, state machines, and invariants. Keep it at the conceptual level.

## Boundaries
Define what this capability owns and does not own at the functional and module level. Describe the boundaries with upstream/downstream modules. Cover:
- Input boundary: what data/events it receives from which upstream module
- Output boundary: what results it produces for which downstream module
- Dependency boundary: what external modules/services it depends on

## Key Interactions
Describe the high-level interactions between modules. Use diagrams wherever possible.

## Feature Design
Organize by sub-scenario, sub-feature, or sub-flow. Each subsection should cover the following aspects.

### Sub-scenario A

#### Flow
Describe the business process logic. Use diagrams wherever possible.

#### Interfaces & Data
Describe the external interfaces, input/output/internal data models, and database design involved in this sub-scenario. Only describe the contract.

#### Exceptions
Describe failure modes and error handling strategies. Merge common exception branches into unified handling when possible. For extreme edge cases, list them as known-unhandled. Keep it concise — do not write complex flows attempting to resolve extreme scenarios.

#### Security
Describe security considerations to cover: authentication, authorization, secret management, log redaction, data protection.

#### Observability
Describe what to instrument: log fields, metrics, traces, and alerts.

#### Testing
Describe business scenarios to cover and key regression scenarios. Provide a test matrix when necessary.

#### Performance
Describe performance considerations and constraints for this sub-scenario.

### Sub-scenario B
...

## Open Questions
Only record genuine unresolved questions. Do not write empty placeholder content.
```

## Naming
Use the capability or feature name, ending with `-spec.md`.
**examples**:
```text
negotiation-spec.md
prompt-generation-spec.md
```

## Index
When creating a new spec, add a row to this table. When updating an existing spec, update the `Last Updated` column. When a spec is no longer relevant, remove its row.

| Spec | Last Updated |
| --- | --- |
*(No specs yet.)*