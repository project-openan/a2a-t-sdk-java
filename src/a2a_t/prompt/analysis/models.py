from __future__ import annotations

from dataclasses import dataclass

from a2a_t.prompt.common.models import PromptReference
from a2a_t.common.prompt_resources.models import ScenarioDefinition
from a2a_t.prompt.validation.models import SlotValidationError


@dataclass(slots=True)
class ScenarioRecognitionResult:
    """Represent the normalized result returned by scenario recognition."""

    matched: bool
    scenario_code: str | None
    error_message: str | None


@dataclass(slots=True)
class SlotExtractionResult:
    """Represent the normalized result returned by slot extraction."""

    slots: dict[str, str | None]
    slot_errors: list[SlotValidationError]


@dataclass(slots=True)
class ScenarioResolutionFailure:
    """Represent a standardized failure emitted by scenario resolution."""

    code: str
    message: str
    stage: str


@dataclass(slots=True)
class ScenarioResolutionResult:
    """Represent the normalized result returned by shared scenario resolution."""

    success: bool
    reference: PromptReference | None = None
    scenario: ScenarioDefinition | None = None
    failure: ScenarioResolutionFailure | None = None
