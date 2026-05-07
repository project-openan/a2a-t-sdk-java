from __future__ import annotations

from dataclasses import dataclass


@dataclass
class PromptComplianceResult:
    """Unified compliance execution result."""

    success: bool
    failure: dict[str, str] | None = None
