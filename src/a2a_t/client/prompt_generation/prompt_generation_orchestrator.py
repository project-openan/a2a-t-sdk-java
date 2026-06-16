from __future__ import annotations

import logging
from typing import Any

from a2a_t.config.models import PromptRuntimeConfig
from a2a_t.common.prompt_resources import (
    PromptResourceNotFoundError,
    PromptResourceParseError,
)
from a2a_t.prompt.analysis import ScenarioResolutionOrchestrator
from a2a_t.prompt.analysis.errors import PromptAnalysisError
from a2a_t.prompt.analysis.scenario_resolution_orchestrator import PREPARATION_STAGE
from a2a_t.prompt.common.errors import PromptSourceError
from a2a_t.prompt.task_rendering import TaskPromptRenderError, TaskPromptRenderer

from .generation_constants import (
    GENERATION_STAGE,
    INVALID_LLM_OUTPUT,
    LLM_EXECUTION_FAILED,
    PROMPT_NOT_FOUND,
    PROMPT_RESOURCE_ACCESS_ERROR,
    PROMPT_RESOURCE_PARSE_ERROR,
    RENDER_FAILED,
    RENDER_STAGE,
    SCENARIO_PARSE_FAILED,
    SCENARIO_STAGE,
    SLOT_SCHEMA_NOT_FOUND,
    TEMPLATE_NOT_FOUND,
)
from a2a_t.prompt.common.models import PromptReference
from .input_normalizer import InputNormalizer
from .models import PromptGenerationFailure, PromptGenerationResult


logger = logging.getLogger(__name__)


class PromptGenerationOrchestrator:
    """Coordinate the full client-side prompt generation pipeline."""

    def __init__(
        self,
        *,
        config: PromptRuntimeConfig,
        scenario_loader: Any,
        prompt_resource_loader: Any,
        template_loader: Any,
        slot_schema_loader: Any,
        scenario_resolver: ScenarioResolutionOrchestrator,
        slot_extractor: Any,
        input_normalizer: InputNormalizer | None = None,
        renderer: TaskPromptRenderer | None = None,
        logger: Any | None = None,
    ) -> None:
        if not isinstance(config, PromptRuntimeConfig):
            raise TypeError("config must be a PromptRuntimeConfig instance.")
        self._config = config
        self._prompt_resource_loader = prompt_resource_loader
        self._template_loader = template_loader
        self._slot_schema_loader = slot_schema_loader
        self._scenario_resolver = scenario_resolver
        self._slot_extractor = slot_extractor
        self._input_normalizer = input_normalizer or InputNormalizer()
        self._renderer = renderer or TaskPromptRenderer()
        self._logger = logger or globals()["logger"]

    def generate(self, user_input: str | dict[str, object]) -> PromptGenerationResult:
        """Run prompt generation from input normalization through prompt rendering."""
        self._log_info("prompt_generation_started")
        if self._is_debug_enabled():
            self._log_debug("prompt_generation_raw_user_input raw_user_input=%s", user_input)
        normalized_input = self._input_normalizer.normalize(user_input)
        language = self._config.language
        self._log_info(
            "prompt_generation_input_normalized input_kind=%s requested_language=%s",
            normalized_input.input_kind,
            language,
        )

        scenario_resolution = self._scenario_resolver.resolve(normalized_input.normalized_input)
        self._log_debug_if_available(
            "prompt_generation_scenario_raw_output scenario_raw_output=%s",
            self._scenario_resolver,
        )
        if not scenario_resolution.success or scenario_resolution.reference is None or scenario_resolution.scenario is None:
            failure = scenario_resolution.failure
            return self._failure_result(
                code=failure.code if failure is not None else SCENARIO_PARSE_FAILED,
                message=failure.message if failure is not None else "Scenario recognition failed.",
                stage=failure.stage if failure is not None else SCENARIO_STAGE,
            )
        reference = scenario_resolution.reference
        scenario = scenario_resolution.scenario
        scenario_code = reference.scenario_code
        resolved_language = reference.language
        self._log_info(
            "prompt_generation_scenario_recognized scenario_code=%s language=%s",
            scenario_code,
            resolved_language,
        )
        try:
            resolved_language, template_text, slot_schema, slot_prompts = self._load_generation_resources(
                reference=reference,
            )
            reference = PromptReference(scenario_code=scenario_code, language=resolved_language)
        except _PromptGenerationResourceFailure as error:
            # At this point the scenario is known, so preserve it in the failure payload for callers.
            return self._finalize_result(
                PromptGenerationResult(
                    success=False,
                    prompt_text=None,
                    failure=PromptGenerationFailure(code=error.code, message=error.message, stage=error.stage),
                )
            )

        try:
            extraction_result = self._slot_extractor.extract(
                normalized_input=normalized_input.normalized_input,
                reference=reference,
                template_text=template_text,
                slot_schema=slot_schema,
                system_prompt=slot_prompts.system_prompt,
                user_prompt=slot_prompts.user_prompt,
            )
        except PromptAnalysisError as error:
            return self._finalize_result(
                PromptGenerationResult(
                    success=False,
                    prompt_text=None,
                    failure=PromptGenerationFailure(code=INVALID_LLM_OUTPUT, message=str(error), stage=GENERATION_STAGE),
                )
            )
        except Exception as error:
            return self._finalize_result(
                PromptGenerationResult(
                    success=False,
                    prompt_text=None,
                    failure=PromptGenerationFailure(code=LLM_EXECUTION_FAILED, message=str(error), stage=GENERATION_STAGE),
                )
            )
        self._log_debug_if_available(
            "prompt_generation_slot_raw_output slot_raw_output=%s",
            self._slot_extractor,
        )
        rendered_prompt_text, render_error_message = self._render_prompt_text(
            template_text=template_text,
            slots=extraction_result.slots,
            scenario_code=scenario_code,
            language=resolved_language,
            description=scenario.description,
        )
        self._log_info(
            "prompt_generation_slots_extracted slots=%s slot_errors=%s",
            extraction_result.slots,
            extraction_result.slot_errors,
        )
        if rendered_prompt_text is None:
            return self._finalize_result(
                PromptGenerationResult(
                    success=False,
                    prompt_text=None,
                    failure=PromptGenerationFailure(
                        code=RENDER_FAILED,
                        message=render_error_message or "Task prompt rendering failed.",
                        stage=RENDER_STAGE,
                    ),
                )
            )

        return self._finalize_result(
            PromptGenerationResult(
                success=True,
                prompt_text=rendered_prompt_text,
                failure=None,
            )
        )

    def _load_generation_resources(
        self,
        *,
        reference: PromptReference,
    ) -> tuple[str, Any, Any, Any]:
        """Load generation resources and specialize missing-resource failures by artifact type."""
        try:
            template_text = self._template_loader.load(
                reference=reference,
            )
            slot_schema = self._slot_schema_loader.load(
                reference=reference,
            )
            slot_prompts = self._prompt_resource_loader.load(
                analysis_action="slot_extraction",
                language=reference.language,
            )
            return reference.language, template_text, slot_schema, slot_prompts
        except _PromptGenerationResourceFailure:
            raise
        except PromptResourceNotFoundError as error:
            resource_path = str(error.context.get("path", ""))
            # Different missing artifacts produce different public error codes even though loaders share one exception type.
            if resource_path.endswith("template.md"):
                raise _PromptGenerationResourceFailure(
                    code=TEMPLATE_NOT_FOUND,
                    message=str(error),
                    stage=PREPARATION_STAGE,
                ) from error
            if resource_path.endswith("slot.json"):
                raise _PromptGenerationResourceFailure(
                    code=SLOT_SCHEMA_NOT_FOUND,
                    message=str(error),
                    stage=PREPARATION_STAGE,
                ) from error
            raise _PromptGenerationResourceFailure(
                code=PROMPT_NOT_FOUND,
                message=str(error),
                stage=PREPARATION_STAGE,
            ) from error
        except PromptResourceParseError as error:
            raise _PromptGenerationResourceFailure(
                code=PROMPT_RESOURCE_PARSE_ERROR,
                message=str(error),
                stage=PREPARATION_STAGE,
            ) from error
        except PromptSourceError as error:
            raise _PromptGenerationResourceFailure(
                code=PROMPT_RESOURCE_ACCESS_ERROR,
                message=str(error),
                stage=PREPARATION_STAGE,
            ) from error

    def _render_prompt_text(
        self,
        *,
        template_text: str,
        slots: dict[str, str | None],
        scenario_code: str,
        language: str,
        description: str,
    ) -> tuple[str | None, str | None]:
        """Render the final prompt text while preserving renderer failures as data."""
        try:
            return (
                self._renderer.render(
                template_text=template_text,
                slots=slots,
                scenario_code=scenario_code,
                language=language,
                description=description,
                ),
                None,
            )
        except TaskPromptRenderError as error:
            return None, str(error)

    def _failure_result(
        self,
        *,
        code: str,
        message: str,
        stage: str,
    ) -> PromptGenerationResult:
        """Build a standardized generation failure result without scenario context."""
        return self._finalize_result(
            PromptGenerationResult(
                success=False,
                prompt_text=None,
                failure=PromptGenerationFailure(code=code, message=message, stage=stage),
            )
        )

    def _finalize_result(self, result: PromptGenerationResult) -> PromptGenerationResult:
        """Emit completion logs and return the final generation result unchanged."""
        failure_stage = result.failure.stage if result.failure is not None else None
        failure_code = result.failure.code if result.failure is not None else None
        self._log_info(
            "prompt_generation_completed success=%s stage=%s code=%s",
            result.success,
            failure_stage,
            failure_code,
        )
        return result

    def _log_info(self, message: str, *args: object) -> None:
        """Write an info log through the configured logger."""
        self._logger.info(message, *args)

    def _log_debug(self, message: str, *args: object) -> None:
        """Write a debug log only when prompt-generation debug mode is enabled."""
        if self._is_debug_enabled():
            self._logger.debug(message, *args)

    def _log_debug_if_available(self, message: str, source: Any) -> None:
        """Log captured raw model output when the dependency exposes it."""
        raw_content = getattr(source, "last_raw_response_content", None)
        if raw_content is not None:
            self._log_debug(message, raw_content)

    def _is_debug_enabled(self) -> bool:
        """Return whether prompt-generation debug logging is enabled."""
        return bool(getattr(self._config, "prompt_generation_debug", False))


class _PromptGenerationResourceFailure(Exception):
    """Carry resource failure details before they are converted into API results."""

    def __init__(self, *, code: str, message: str, stage: str) -> None:
        super().__init__(message)
        self.code = code
        self.message = message
        self.stage = stage
