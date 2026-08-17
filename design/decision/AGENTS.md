# Architecture Decision Records

This directory records major architecture-level decisions. An ADR explains why a particular architectural choice was made, and what constraints and consequences that choice brings.

## When to Create or Update an ADR

Create a new ADR when:

- Significant module boundary or responsibility assignment changes.
- Key technology choices change.
- Protocols, communication methods, middleware, or storage solutions change.
- Runtime architecture, deployment topology, or dependency relationships change.
- Significant data model, persistence format, or compatibility strategy changes.
- Public API breaking changes or versioning strategy changes.
- Authentication, authorization, key management, audit, or isolation security architecture changes.
- Performance, reliability, or observability architecture-level constraints change.

Do NOT create or update an ADR for:

- Minor design changes — capture them in the corresponding spec only.
- Documentation governance or AI workflows.
- General coding conventions.
- Test commands, CI flows, or publishing steps.
- Task plans, meeting notes, or implementation steps.
- Ordinary bug fixes, local refactoring, or code style adjustments.

When a decision changes, create a new ADR — do not rewrite the existing one. Set the new ADR's "Supersedes" field to the old ADR, and update the old ADR's "Superseded by" field. Supersession is at the whole-ADR level — the new ADR replaces the entire scope of the old one, not a subset of it.
When the decision itself has not changed but the document needs clarification, correction, or supplementary details, update the existing ADR directly.
An ADR with no "Superseded by" field is the latest decision for its scope. No separate status field is needed; validity is derived from the supersede fields above.
When unsure whether an ADR is needed, state the judgment basis and residual risk in the delivery notes.

## Template
When creating a new ADR, use the following template.
**Overall constraint**:
- This is a design document, not code or implementation. Do not write code implementations, class names, method signatures, or object definitions.
- Do not write task plans, meeting notes, or implementation steps.
- Do not write architecture descriptions or system design documents — focus on the decision and its rationale.
- For sections that are not applicable, mark 'Not applicable' and state the reason.

```markdown
# Title

- Impact Scope: concise description of the impact scope. Do not reference actual code
- Related Spec: references to the corresponding spec document
- Last Updated: YYYY-MM-DD
- Supersedes:
- Superseded by:

## Context

The background, constraints, and problem that led to this architectural decision.

## Decision

The chosen architectural approach.

## Rationale

The primary reasons for choosing this approach.

## Alternatives

### Option 1

Why it was not chosen.

### Option 2

Why it was not chosen.

## Consequences

Positive impacts, costs, migration effort, and long-term constraints.

## Verification

How to verify that this decision still holds.
```

## Naming
ADR files use four-digit sequential numbering and a descriptive title. Numbers are never reused.
**examples**:
```text
0001-negotiation-state-storage.md
```

## Index
When creating a new ADR, add a row to this table. When updating an existing ADR directly, update the `Last Updated` column. When an ADR is superseded, update the `Superseded by` column. When an ADR is no longer relevant, remove its row.

| Number | Title | Last Updated | Superseded by |
| --- | --- | --- | --- |

*(No ADRs yet.)*