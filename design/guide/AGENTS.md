# Development Guides

This directory contains project development conventions and guides. Guides describe engineering practices: how to write code, how to test, how to extend the system, and how to work with the toolchain.

Design facts are in `../spec/`, architectural rationale in `../decision/`, and implementation details in `../impl/`.

## When to Load

Guides should be loaded in both design and coding stages:

- **Design stage**: Read `architecture.md` for project structure and key patterns; read `design-principles.md` for design constraints and pattern selection.
- **Coding stage**: Read `coding-style.md` and `testing.md` before writing or changing code.
- **Submission stage**: Read `git-workflow.md` for CI/CD and submission checklist.

## Directory

| File | Content |
| --- | --- |
| [architecture.md](architecture.md) | Repository layout, Builder+Orchestrator, Factory+Registry, interface-based design, error hierarchy |
| [design-principles.md](design-principles.md) | Design constraints for spec and impl documents, pattern selection, SOLID principles |
| [coding-style.md](coding-style.md) | Code conventions, Lombok usage, imports, naming, error handling |
| [testing.md](testing.md) | Test framework, style, conventions, naming |
| [security.md](security.md) | Secrets handling, env file management |
| [git-workflow.md](git-workflow.md) | CI/CD, submission checklist, publishing |

## Update Rules

- Guides describe current practices. When practices deviate from the guide, either fix the practice or update the guide.
- When adding a new guide, update this index.
- When changing a guide's scope or content, ensure the `design/AGENTS.md` Work-Stage Triggers still reference the correct file.
- Do not put one-off task plans, meeting notes, or implementation logs here.