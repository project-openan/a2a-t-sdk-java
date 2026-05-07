from __future__ import annotations

from typing import Any

from a2a_t.config.models import PromptRuntimeConfig
from a2a_t.common.prompt_resources.errors import PromptResourceNotFoundError, PromptResourceParseError
from a2a_t.prompt.common.errors import PromptSourceError
from a2a_t.prompt.common.models import PromptReference

from .errors import PromptAnalysisError
from .models import ScenarioResolutionFailure, ScenarioResolutionResult

GENERATION_STAGE = "generation"
PROMPT_PARSE_STAGE = "prompt_parse"
PROMPT_RESOURCE_ACCESS_ERROR = "prompt_resource_access_error"
PROMPT_RESOURCE_LOAD_ERROR = "prompt_resource_load_error"
PROMPT_RESOURCE_PARSE_ERROR = "prompt_resource_parse_error"
PROCESSED_PROMPT_PARSE_ERROR = "processed_prompt_parse_error"


class ScenarioResolutionOrchestrator:
    """Resolve a prompt reference from scenario recognition."""

    def __init__(
        self,
        *,
        config: PromptRuntimeConfig,
        resource_registry: Any,
        scenario_recognizer: Any,
    ) -> None:
        if not isinstance(config, PromptRuntimeConfig):
            raise TypeError("config must be a PromptRuntimeConfig instance.")
        self._config = config
        self._resource_registry = resource_registry
        self._scenario_recognizer = scenario_recognizer

    def resolve(self, normalized_input: str) -> ScenarioResolutionResult:
        """Return a resolved prompt reference or a standardized failure."""
        try:
            resolved_language, scenarios, scenario_prompts = self._resource_registry.load_scenario_resources(
                version=self._config.prompt_resource_version,
                language=self._config.language,
            )
        except PromptResourceNotFoundError as error:
            return self._failure(GENERATION_STAGE, PROMPT_RESOURCE_LOAD_ERROR, str(error))
        except PromptResourceParseError as error:
            return self._failure(GENERATION_STAGE, PROMPT_RESOURCE_PARSE_ERROR, str(error))
        except PromptSourceError as error:
            return self._failure(GENERATION_STAGE, PROMPT_RESOURCE_ACCESS_ERROR, str(error))

        try:
            recognition_result = self._scenario_recognizer.recognize(
                normalized_input=normalized_input,
                scenarios=scenarios,
                language=resolved_language,
                system_prompt=scenario_prompts.system_prompt,
                user_prompt=scenario_prompts.user_prompt,
            )
        except PromptAnalysisError as error:
            return self._failure(PROMPT_PARSE_STAGE, PROCESSED_PROMPT_PARSE_ERROR, str(error))
        except Exception as error:
            return self._failure(PROMPT_PARSE_STAGE, PROCESSED_PROMPT_PARSE_ERROR, str(error))

        if not recognition_result.matched or not recognition_result.scenario_code:
            return self._failure(
                PROMPT_PARSE_STAGE,
                PROCESSED_PROMPT_PARSE_ERROR,
                recognition_result.error_message or "Scenario recognition failed.",
            )

        for scenario in scenarios:
            if scenario.scenario_code == recognition_result.scenario_code:
                return ScenarioResolutionResult(
                    success=True,
                    reference=PromptReference(
                        scenario_code=scenario.scenario_code,
                        language=resolved_language,
                        version=self._config.prompt_resource_version,
                    ),
                    scenario=scenario,
                )

        return self._failure(
            PROMPT_PARSE_STAGE,
            PROCESSED_PROMPT_PARSE_ERROR,
            f"Scenario recognition returned unsupported scenario_code: {recognition_result.scenario_code}",
        )

    @staticmethod
    def _failure(stage: str, code: str, message: str) -> ScenarioResolutionResult:
        return ScenarioResolutionResult(
            success=False,
            failure=ScenarioResolutionFailure(code=code, message=message, stage=stage),
        )
