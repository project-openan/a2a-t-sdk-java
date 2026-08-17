# Design

This directory contains design documents, architectural decisions, and development guides for the project. It serves as the source of truth for design facts, architectural rationale, and engineering standards.

Documents in this directory support code modification, review, regression judgment, and new-contributor understanding. Do not put one-off task plans, meeting notes, or implementation logs here.

## Directory Structure

| Directory | Purpose | When to load |
| --- | --- | --- |
| `guide/` | Development conventions and guides | Design, coding, and submission stages |
| `spec/` | Feature specifications and protocol definitions | Design and coding stages — when designing new capabilities or modifying existing ones |
| `decision/` | Architecture Decision Records (ADR) | Design and coding stages — when creating or understanding architectural decisions |
| `impl/` | Code-level implementation design (package structure, class design, method signatures, pseudo-code) | Coding stage — when implementing features with a corresponding spec |

## Reading Rules

Before entering any subdirectory, read that subdirectory's `AGENTS.md` first for navigation and indexing.

## Loading Order

1. Start with `guide/architecture.md` for project structure and key patterns.
2. Read `guide/design-principles.md` for design constraints before writing spec or impl documents.
3. Read `guide/coding-style.md` and `guide/testing.md` before writing code.
4. Consult `spec/` and `decision/` when designing new features or questioning existing design.
5. Check `guide/security.md` for secrets handling, `guide/git-workflow.md` for CI/CD.
6. Consult `impl/` when implementing features to understand class-level design.

## Work-Stage Triggers

Before starting work, confirm the required reading/actions for the stage you are in.

### Design Stage

Read `guide/architecture.md` + `guide/design-principles.md` + `guide/security.md`.
Consult `spec/` and `decision/` for existing design decisions.

### Coding Stage

Ensure a corresponding spec and impl document exist before writing code.
Read `guide/design-principles.md` + `guide/coding-style.md` + `guide/testing.md` + `guide/security.md`.
Consult `impl/` for class-level design.

### Submission Stage

Read `guide/git-workflow.md`. Follow its submission checklist.

## Update Rules

- Spec and impl documents describe current repository facts. When implementation conflicts with an impl or a spec document, determine whether the implementation deviates from design or the design document is outdated.
- ADRs are not rewritten in place. An ADR with no "Superseded by" field is the latest decision for its scope. When decisions change, create a new ADR and update the supersession fields.
- Guide documents describe current engineering practices. When practices deviate from the guide, either fix the practice or update the guide.
- When adding a new document, update the corresponding subdirectory `AGENTS.md` index.
- When unsure whether a document update is needed, state the judgment basis and residual risk in the delivery notes.