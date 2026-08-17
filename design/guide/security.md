# Security

## Secrets

- Do not put real API keys or secrets in `client.env`, `server.env`, or any committed file. Use placeholder values.
- Before running the SDK locally, fill in the `A2AT_LLM_*` values in the appropriate `.env` file. Use `env.example` as a template reference.
- Do not commit `.env` files containing real secrets. Verify they are in `.gitignore` before committing.

## Logging

- Do not log secrets, API keys, tokens, or personally identifiable information.
- Review `log.debug()` and `log.info()` calls to ensure they do not leak configuration values.
- The `@Slf4j` logger should never include `A2AT_LLM_API_KEY` or similar values in log messages.
- When logging structured payloads, use a safe formatter that redacts sensitive fields (see `SampleLoggingFormatter` in `a2a-t-sample` for the canonical pattern).

## Configuration

- All runtime config is read from `.env` files explicitly passed by the caller. The SDK does not auto-discover configuration files.
- Validate configuration values at load time and fail fast with typed errors.
- Default values for sensitive configuration (like `A2AT_LLM_API_KEY`) should be empty or placeholder, never a real key.
- `DotEnvConfigSource` enforces a maximum entry limit (`MAX_ENTRIES = 200`) to prevent DoS via oversized config files.