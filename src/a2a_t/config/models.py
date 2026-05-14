"""Configuration data models for a2a_t."""

from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Mapping

from a2a_t.common.resource_roots import resolve_prompt_resource_root
from a2a_t.config.source import DotEnvConfigSource


def _parse_bool(raw_value: str | None, default: bool) -> bool:
    """Parse a boolean-like environment value with a fallback default."""
    if raw_value is None or not raw_value.strip():
        return default
    return raw_value.strip().lower() in {"1", "true", "yes", "on"}


def _parse_float(raw_value: str | None, default: float) -> float:
    """Parse a float-like environment value with a fallback default."""
    if raw_value is None or not raw_value.strip():
        return default
    return float(raw_value)


def _default_prompt_resource_root_dir() -> str:
    """Return the packaged prompt resource root directory."""
    return str(
        resolve_prompt_resource_root(
            module_file=__file__,
            source_parent_depth=3,
        ).resolve()
    )


def _resolve_prompt_resource_root_dir(raw_value: str | None, *, base_dir: Path | None = None) -> str:
    """Resolve prompt resource roots relative to the config file when needed."""
    if raw_value is None or not raw_value.strip():
        return _default_prompt_resource_root_dir()

    candidate = Path(raw_value)
    if candidate.is_absolute():
        return str(candidate.resolve())

    resolved_base_dir = base_dir.resolve() if base_dir is not None else Path.cwd().resolve()
    return str((resolved_base_dir / candidate).resolve())

@dataclass(slots=True)
class PromptRuntimeConfig:
    """Prompt runtime configuration owned by the config package."""

    language: str = "en-US"
    source_type: str = "local_file"
    local_root_dir: str = field(default_factory=_default_prompt_resource_root_dir)

    @classmethod
    def from_mapping(cls, values: Mapping[str, str], *, base_dir: Path | None = None) -> "PromptRuntimeConfig":
        """Build prompt runtime config from raw environment values."""
        return cls(
            language=values.get("A2AT_LANGUAGE", "en-US") or "en-US",
            source_type=values.get("A2AT_PROMPT_SOURCE_TYPE", "local_file") or "local_file",
            local_root_dir=_resolve_prompt_resource_root_dir(
                values.get("A2AT_PROMPT_RESOURCE_LOCAL_ROOT_DIR"),
                base_dir=base_dir,
            ),
        )


@dataclass(slots=True)
class GuardrailProviderConfig:
    """Provider configuration for safety guardrail adapters."""

    provider: str = "noop"
    timeout: float = 10.0
    policy_id: str = ""
    endpoint: str = ""
    region: str = ""
    credentials_ref: str = ""
    config: dict[str, Any] = field(default_factory=dict)


@dataclass(slots=True)
class PromptComplianceConfig:
    """Top-level configuration for prompt compliance."""

    enabled: bool = False
    guardrail: GuardrailProviderConfig = field(default_factory=GuardrailProviderConfig)
    providers: dict[str, dict[str, Any]] = field(default_factory=dict)

    @classmethod
    def from_mapping(cls, values: Mapping[str, str]) -> "PromptComplianceConfig":
        """Build prompt compliance config from raw environment values."""
        return cls(
            enabled=_parse_bool(values.get("A2AT_PROMPT_COMPLIANCE_ENABLED"), False),
            guardrail=GuardrailProviderConfig(
                provider=values.get("A2AT_PROMPT_COMPLIANCE_GUARDRAIL_PROVIDER", "noop") or "noop",
                timeout=_parse_float(
                    values.get("A2AT_PROMPT_COMPLIANCE_GUARDRAIL_TIMEOUT_SECONDS"),
                    10.0,
                ),
                policy_id=values.get("A2AT_PROMPT_COMPLIANCE_GUARDRAIL_POLICY_ID", "") or "",
                endpoint=values.get("A2AT_PROMPT_COMPLIANCE_GUARDRAIL_ENDPOINT", "") or "",
                region=values.get("A2AT_PROMPT_COMPLIANCE_GUARDRAIL_REGION", "") or "",
                credentials_ref=values.get("A2AT_PROMPT_COMPLIANCE_GUARDRAIL_CREDENTIALS_REF", "") or "",
            ),
        )

@dataclass
class A2ATConfig:
    """Global A2A-T configuration entry point."""

    prompt: PromptRuntimeConfig
    prompt_compliance: PromptComplianceConfig

    @classmethod
    def load(cls, env_path: Path) -> A2ATConfig:
        """Load the complete runtime configuration from a .env file."""
        values = DotEnvConfigSource.load(env_path)
        return cls(
            prompt=PromptRuntimeConfig.from_mapping(values, base_dir=env_path.parent),
            prompt_compliance=PromptComplianceConfig.from_mapping(values),
        )
