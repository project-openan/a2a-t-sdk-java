# Implementation Design

This directory contains code-level implementation design documents. An implementation document translates a spec into package, class, and method-level design, plus pseudo-code for key logic. Its purpose is to verify that the code architecture is clear and reasonable before writing code — **it is not the final code itself**.

Every spec in `../spec/` must have a corresponding implementation document. An implementation document must fully conform to its spec. Design facts are in `../spec/`, architectural rationale in `../decision/`.

## When to Create or Update an Impl

Create or update an implementation document when:

- A corresponding spec is created or updated.
- Class design, method signatures, or internal data structures change.
- Pseudo-code or key algorithms change.

Do NOT create or update an impl for:

- Local refactoring that does not change class design or method signatures.
- Code style, formatting, or comment adjustments.

When modifying code that touches the scope above, always update the spec first, then the impl, then change the code.
If implementation conflicts with the implementation document, verify against the spec first, then determine which is correct and update the implementation document if it is outdated, or fix the implementation if it deviates from the implementation document.
When unsure whether an update is needed, state the judgment basis and residual risk in the delivery notes.

All impl documents must follow the constraints in `../guide/architecture.md` and `../guide/coding-style.md`.

## Template
When creating a new impl, use the following template.
**Overall constraint**:
- This is a design document, not code. Do not write method bodies, field declarations, constructors, or any code implementation.
- Do not write utility classes, helper functions, or object definitions (fields, constructors, data classes).
- Do not write detailed implementation logic — one-line responsibility description is sufficient for non-core methods.
- Do not write annotations, imports, copyright notices, or Javadoc.
- Do not include design facts or architectural decisions.
- For sections that are not applicable, mark 'Not applicable' and state the reason.

```markdown
# Title

- Impact Scope: concise description of the impact scope. Do not reference actual code
- Related Spec: references to the corresponding spec document
- Last Updated: YYYY-MM-DD

## Design Patterns & Trade-offs

Why this pattern was chosen. Alternatives considered and why they were rejected.

## Package Structure

Package layout and module responsibilities.

## Class Design

### ClassName

- Responsibility:
- Dependencies:
- Method signatures (use lookup tables for mappings and configuration items when appropriate):

  - `ReturnType methodName(ParamType param)`

## Key Algorithms

Pseudo-code for core logic. Describe steps and constraints, not language syntax.

## Unit Test Scenarios

What scenarios to test. Describe test intent, not test implementation.

## Design Constraints

Key constraints and invariants that the implementation must satisfy.
```

## Naming
Use the same name as the corresponding spec, ending with `-impl.md`.
**examples**:
```text
negotiation-impl.md
```

## Index
When creating a new impl, add a row to this table. When updating an existing impl, update the `Last Updated` column. When an impl is no longer relevant, remove its row.

| Impl | Last Updated |
| --- | --- |

*(No implementation documents yet.)*