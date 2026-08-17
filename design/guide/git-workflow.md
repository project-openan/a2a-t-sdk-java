# Git Workflow & CI/CD

## Commands

The project uses Maven as its build system. JDK 17+ is required.

```bash
mvn -DskipTests package              # build without tests
mvn test                             # run all tests
mvn spotless:check                   # check code formatting
mvn spotless:apply                   # auto-format code
mvn -pl <module> -am test            # run tests for a specific module with dependencies
```

## Submission Checklist

Before committing or creating a PR:

```bash
mvn spotless:apply
mvn verify
```

Use `git commit -s` to sign off every commit.

Do not commit planning artifacts (`findings.md`, `progress.md`, `task_plan.md`, `reference/`).

## CI

`.github/workflows/ci.yml`: runs on push to `main` and PRs to `main`.

- Single job: `mvn -B clean verify` on JDK 17 (temurin) on `ubuntu-latest`.
- `verify` includes compilation, spotless:check, and tests.

## Publishing

`.github/workflows/release.yml`: triggered by `v*` tags or manual dispatch (`workflow_dispatch`).

- Validates required secrets (`MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`, `GPG_KEY_CONTENTS`, `SIGNING_KEY_ID`, `SIGNING_PASSWORD`).
- Builds with JDK 21 (temurin).
- Publishes with `mvn -B -Prelease deploy -Dgpg.executable=gpg -Dgpg.keyname="${SIGNING_KEY_ID}"` with GPG signing via `central-publishing-maven-plugin`.

## Code Owners

`@project-openan/maintainers-a2a-t-sdk` (see `CODEOWNERS`, `MAINTAINERS.md`).