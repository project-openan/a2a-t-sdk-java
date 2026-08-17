# Testing

## Framework

JUnit Jupiter 5.10.2 with no mocking framework. Tests use real in-memory implementations.

## Test Style

- JUnit 5 with `@Test` annotation.
- Assertions via static imports `assertEquals`, `assertThrows`, `assertTrue`, `assertNotNull`,
  `assertFalse`, `assertNull`, `assertInstanceOf`, `assertSame`, `assertDoesNotThrow`, `fail`.
- Prefer fakes and in-memory implementations over mocks. Unit tests must not hit the network.
- Focus tests on **behavior**, not implementation details.
- No `Mockito` or other mocking frameworks — use `InMemoryNegotiationStore`, `@TempDir` or `Files.createTempDirectory()` for temp file testing, and manual test doubles.

## Test Naming

Test methods should describe expected behavior and conditions. Use descriptive method names such as:

```java
@Test
void should_buildUnifiedConfig_When_envFileContainsAllKeys() { ... }

@Test
void availableProvidersIncludesDefaultOpenAiProvider() { ... }

@Test
void receiveAllowsFirstRoundWithoutExistingRecord() { ... }

@Test
void checkTaskPromptReturnsSuccessWhenMetadataExtractionAndValidationPass() { ... }
```

The `should_<expectedBehavior>_When_<condition>()` pattern is used in `a2a-t-core`. Other modules use descriptive names without a strict format.

## Test Structure

- Tests mirror the main package structure under `src/test/java/`.
- One or more test classes per production class. Splitting by concern (e.g., state machine, limits, strict state) is acceptable.
- Test class visibility: package-private.
- Test method visibility: package-private (standard JUnit 5 convention).

## Test Categories

| Category | Approach |
| --- | --- |
| API contract tests | Verify constructor signatures, public API surface, no static helpers |
| Configuration tests | `@TempDir` or `Files.createTempDirectory()` for temp env files |
| State machine tests | Verify state transitions, round validation, invalid transitions |
| Package structure tests | Verify module's public export surface |

## Conventions

- When adding a feature, add a test file under the matching `src/test/java/` subdirectory in the relevant module.
- Prefer real in-memory implementations over test doubles when they exist (e.g., `InMemoryNegotiationStore`).
- Tests should not depend on external services, file system state outside temp directories, or network access.

## Test Doubles

When creating manual test doubles, define them as package-private inner classes within the test class. Use consistent naming prefixes:

- `RecordingXxx` — a test double that captures calls and arguments for later assertion (e.g., `RecordingClient` in `LLMClientFactoryTest`).
- `FakeXxx` — a lightweight working implementation with simplified behavior (e.g., `FakeTemplateLoader` in `DefaultClientPromptGenerationOrchestratorTest`).

## Running Tests

```bash
mvn test                              # run all tests
mvn -pl a2a-t-core -am test           # run core module tests with dependencies
mvn -pl a2a-t-resources -am test      # run resources module tests with dependencies
mvn -pl a2a-t-llm -am test            # run llm module tests with dependencies
mvn -pl a2a-t-prompt -am test         # run prompt module tests with dependencies
mvn -pl a2a-t-negotiation -am test    # run negotiation module tests with dependencies
mvn -pl a2a-t-client -am test         # run client module tests with dependencies
mvn -pl a2a-t-server -am test         # run server module tests with dependencies
mvn -pl a2a-t-sample -am test         # run sample module tests with dependencies
```