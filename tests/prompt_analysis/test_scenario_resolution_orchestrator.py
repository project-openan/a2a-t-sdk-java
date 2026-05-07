from __future__ import annotations

import sys
from pathlib import Path
import unittest


PROJECT_ROOT = Path(__file__).resolve().parents[2]
SRC_ROOT = PROJECT_ROOT / "src"

if str(SRC_ROOT) not in sys.path:
    sys.path.insert(0, str(SRC_ROOT))


from a2a_t.common.prompt_resources.errors import PromptResourceNotFoundError
from a2a_t.common.prompt_resources.models import PromptMessages, ScenarioDefinition
from a2a_t.config.models import PromptRuntimeConfig
from a2a_t.prompt.analysis.models import ScenarioRecognitionResult
from a2a_t.server.prompt_compliance.constants import (
    GENERATION_STAGE,
    PROMPT_PARSE_STAGE,
    PROMPT_RESOURCE_LOAD_ERROR,
    PROCESSED_PROMPT_PARSE_ERROR,
)


class FakeResourceRegistry:
    def __init__(self, result: object) -> None:
        self._result = result
        self.calls: list[dict[str, str]] = []

    def load_scenario_resources(self, *, version: str, language: str) -> tuple[str, list[ScenarioDefinition], PromptMessages]:
        self.calls.append({"version": version, "language": language})
        if isinstance(self._result, Exception):
            raise self._result
        return self._result


class FakeScenarioRecognizer:
    def __init__(self, result: ScenarioRecognitionResult | Exception) -> None:
        self._result = result
        self.calls: list[dict[str, object]] = []

    def recognize(self, **kwargs: object) -> ScenarioRecognitionResult:
        self.calls.append(kwargs)
        if isinstance(self._result, Exception):
            raise self._result
        return self._result


class ScenarioResolutionOrchestratorTest(unittest.TestCase):
    def _build_orchestrator(
        self,
        *,
        resource_result: object,
        recognition_result: ScenarioRecognitionResult | Exception,
        language: str = "zh-CN",
        version: str = "0.0.1",
    ):
        from a2a_t.prompt.analysis.scenario_resolution_orchestrator import ScenarioResolutionOrchestrator

        self.resource_registry = FakeResourceRegistry(resource_result)
        self.scenario_recognizer = FakeScenarioRecognizer(recognition_result)

        return ScenarioResolutionOrchestrator(
            config=PromptRuntimeConfig(language=language, prompt_resource_version=version),
            resource_registry=self.resource_registry,
            scenario_recognizer=self.scenario_recognizer,
        )

    def test_resolve_returns_reference_and_scenario_when_recognition_succeeds(self) -> None:
        orchestrator = self._build_orchestrator(
            resource_result=(
                "en-US",
                [
                    ScenarioDefinition(
                        scenario_code="energy_saving",
                        scenario_name="Energy Saving",
                        description="Energy saving analysis tasks.",
                        example="Analyze site power usage and suggest optimization.",
                    )
                ],
                PromptMessages(system_prompt="Identify scenario.", user_prompt="Choose scenario."),
            ),
            recognition_result=ScenarioRecognitionResult(
                matched=True,
                scenario_code="energy_saving",
                error_message=None,
            ),
        )

        result = orchestrator.resolve("Please analyze site A energy usage.")

        self.assertTrue(result.success)
        self.assertIsNone(result.failure)
        self.assertEqual(result.reference.scenario_code, "energy_saving")
        self.assertEqual(result.reference.language, "en-US")
        self.assertEqual(result.reference.version, "0.0.1")
        self.assertEqual(result.scenario.scenario_code, "energy_saving")
        self.assertEqual(
            self.resource_registry.calls,
            [{"version": "0.0.1", "language": "zh-CN"}],
        )
        self.assertEqual(self.scenario_recognizer.calls[0]["language"], "en-US")

    def test_resolve_returns_prompt_parse_failure_when_recognition_reports_unmatched(self) -> None:
        orchestrator = self._build_orchestrator(
            resource_result=(
                "en-US",
                [
                    ScenarioDefinition(
                        scenario_code="energy_saving",
                        scenario_name="Energy Saving",
                        description="Energy saving analysis tasks.",
                        example="Analyze site power usage and suggest optimization.",
                    )
                ],
                PromptMessages(system_prompt="Identify scenario.", user_prompt="Choose scenario."),
            ),
            recognition_result=ScenarioRecognitionResult(
                matched=False,
                scenario_code=None,
                error_message="No matching scenario.",
            ),
        )

        result = orchestrator.resolve("Please analyze site A energy usage.")

        self.assertFalse(result.success)
        self.assertIsNone(result.reference)
        self.assertIsNone(result.scenario)
        self.assertEqual(result.failure.stage, PROMPT_PARSE_STAGE)
        self.assertEqual(result.failure.code, PROCESSED_PROMPT_PARSE_ERROR)
        self.assertEqual(result.failure.message, "No matching scenario.")

    def test_resolve_returns_prompt_parse_failure_when_scenario_code_is_not_supported(self) -> None:
        orchestrator = self._build_orchestrator(
            resource_result=(
                "en-US",
                [
                    ScenarioDefinition(
                        scenario_code="energy_saving",
                        scenario_name="Energy Saving",
                        description="Energy saving analysis tasks.",
                        example="Analyze site power usage and suggest optimization.",
                    )
                ],
                PromptMessages(system_prompt="Identify scenario.", user_prompt="Choose scenario."),
            ),
            recognition_result=ScenarioRecognitionResult(
                matched=True,
                scenario_code="unknown_scenario",
                error_message=None,
            ),
        )

        result = orchestrator.resolve("Please analyze site A energy usage.")

        self.assertFalse(result.success)
        self.assertEqual(result.failure.stage, PROMPT_PARSE_STAGE)
        self.assertEqual(result.failure.code, PROCESSED_PROMPT_PARSE_ERROR)
        self.assertEqual(
            result.failure.message,
            "Scenario recognition returned unsupported scenario_code: unknown_scenario",
        )

    def test_resolve_returns_generation_failure_when_scenario_resources_are_missing(self) -> None:
        orchestrator = self._build_orchestrator(
            resource_result=PromptResourceNotFoundError("Scenario recognition prompt resources are missing."),
            recognition_result=ScenarioRecognitionResult(
                matched=True,
                scenario_code="energy_saving",
                error_message=None,
            ),
        )

        result = orchestrator.resolve("Please analyze site A energy usage.")

        self.assertFalse(result.success)
        self.assertEqual(result.failure.stage, GENERATION_STAGE)
        self.assertEqual(result.failure.code, PROMPT_RESOURCE_LOAD_ERROR)
        self.assertEqual(result.failure.message, "Scenario recognition prompt resources are missing.")

    def test_resolve_returns_prompt_parse_failure_when_recognizer_raises_runtime_error(self) -> None:
        orchestrator = self._build_orchestrator(
            resource_result=(
                "en-US",
                [
                    ScenarioDefinition(
                        scenario_code="energy_saving",
                        scenario_name="Energy Saving",
                        description="Energy saving analysis tasks.",
                        example="Analyze site power usage and suggest optimization.",
                    )
                ],
                PromptMessages(system_prompt="Identify scenario.", user_prompt="Choose scenario."),
            ),
            recognition_result=RuntimeError("llm transport down"),
        )

        result = orchestrator.resolve("Please analyze site A energy usage.")

        self.assertFalse(result.success)
        self.assertEqual(result.failure.stage, PROMPT_PARSE_STAGE)
        self.assertEqual(result.failure.code, PROCESSED_PROMPT_PARSE_ERROR)
        self.assertEqual(result.failure.message, "llm transport down")


if __name__ == "__main__":
    unittest.main()
