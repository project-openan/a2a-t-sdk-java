# Coding Style

Enforced by Spotless with Palantir Java Format. Do not fight the formatter.

## General Principles

- Follow Java best practices and idiomatic patterns.
- Follow software principles such as DRY and YAGNI.
- Keep diffs as minimal as possible.
- Do not add comments unless a non-obvious invariant requires explanation.

## Lombok

- Lombok scope is `provided` — it is a compile-time only dependency.
- Use Java `record` types for immutable value objects and DTOs (e.g., `A2ATConfig`, `PromptGenerationResult`).
- Use regular classes for objects with behavior.
- Builders are hand-written (e.g., `DefaultA2ATClientBuilder`, `NegotiationHandler.Builder`), not generated via `@Builder`.
- Lombok is minimally used — only `@NoArgsConstructor` for utility classes.

## Imports & Module Structure

- No wildcard imports. Spotless `removeUnusedImports` is enabled.
- Package structure mirrors module responsibilities: `net.openan.a2at.sdk.<module>.*`.
- Static imports for test assertions: `import static org.junit.jupiter.api.Assertions.*`.

## Formatting

- Palantir Java Format handles all formatting decisions (indentation, line breaks, braces, etc.).
- Javadoc formatting is also enforced by Palantir (`formatJavadoc: true`).
- Run `mvn spotless:apply` before committing to ensure compliance.

## Naming

- Classes: `PascalCase` (e.g., `A2ATClient`, `LLMClientFactory`).
- Methods and variables: `camelCase` (e.g., `generateTaskPrompt`, `promptText`).
- Constants: `UPPER_SNAKE` (e.g., `DEFAULT_TIMEOUT_SECONDS`).
- Packages: lowercase, single word preferred (e.g., `net.openan.a2at.sdk.client`).
- Test methods: use descriptive names describing expected behavior (e.g., `should_buildUnifiedConfig_When_envFileContainsAllKeys` in `a2a-t-core`, `availableProvidersIncludesDefaultOpenAiProvider` in `a2a-t-llm`).

## Error Handling

- SDK exceptions are unchecked (`RuntimeException` subclasses). Some extend `SdkException` (in `a2a-t-core`), others extend `RuntimeException` directly (e.g., `LLMError`, `PromptComplianceCheckException`).
- Wrap provider errors into `LLMRuntimeError`.
- Throw the most specific subclass available.
- Use checked exceptions sparingly — prefer unchecked `SdkException` subclasses for SDK errors.

## Method Visibility

- Public API methods: `public`.
- Internal methods not part of the public contract: `private`.
- Test methods: package-private (no `public` modifier), per JUnit 5 conventions.

## Logging

- Use SLF4J via `@Slf4j` annotation.
- Log at appropriate levels: `log.debug()` for internal details, `log.info()` for lifecycle events, `log.warn()` for recoverable issues, `log.error()` for failures.
- Do not log secrets, API keys, or personally identifiable information.