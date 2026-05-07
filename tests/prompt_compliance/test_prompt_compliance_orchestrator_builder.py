from __future__ import annotations

import sys
from pathlib import Path
import unittest


PROJECT_ROOT = Path(__file__).resolve().parents[2]
SRC_ROOT = PROJECT_ROOT / "src"

if str(SRC_ROOT) not in sys.path:
    sys.path.insert(0, str(SRC_ROOT))


from a2a_t.config.models import A2ATConfig, PromptComplianceConfig, PromptRuntimeConfig


class FakeRuntimeComponentsBuilder:
    def __init__(self, components: object) -> None:
        self.components = components
        self.calls: list[object] = []

    def build(self, *, config: A2ATConfig) -> object:
        self.calls.append(config)
        return self.components


class FakeScenarioRecognizer:
    def __init__(self, *, llm_client: object) -> None:
        self.llm_client = llm_client


class FakeScenarioResolver:
    def __init__(self, *, config: PromptRuntimeConfig, resource_registry: object, scenario_recognizer: object) -> None:
        self.config = config
        self.resource_registry = resource_registry
        self.scenario_recognizer = scenario_recognizer


class FakeSlotExtractor:
    def __init__(self, *, llm_client: object) -> None:
        self.llm_client = llm_client


class FakeOrchestrator:
    def __init__(self, **kwargs: object) -> None:
        self.kwargs = kwargs


class PromptComplianceOrchestratorBuilderTest(unittest.TestCase):
    def test_builder_uses_runtime_components_builder_and_injects_llm_client(self) -> None:
        from a2a_t.server.prompt_compliance.prompt_compliance_orchestrator_builder import PromptComplianceOrchestratorBuilder

        components = type(
            "Components",
            (),
            {
                "guardrail": object(),
                "resource_registry": object(),
                "template_loader": object(),
                "slot_schema_loader": object(),
                "slot_json_schema_loader": object(),
                "prompt_resource_loader": object(),
                "json_schema_slot_validator": object(),
            },
        )()
        runtime_builder = FakeRuntimeComponentsBuilder(components)
        llm_client = object()
        builder = PromptComplianceOrchestratorBuilder(
            runtime_components_builder=runtime_builder,
            scenario_recognizer_cls=FakeScenarioRecognizer,
            scenario_resolver_cls=FakeScenarioResolver,
            slot_extractor_cls=FakeSlotExtractor,
            orchestrator_cls=FakeOrchestrator,
        )
        config = A2ATConfig(
            prompt=PromptRuntimeConfig(local_root_dir="./default-root"),
            prompt_compliance=PromptComplianceConfig(),
        )

        orchestrator = builder.build(
            config=config,
            llm_client=llm_client,
        )

        self.assertEqual(len(runtime_builder.calls), 1)
        self.assertIs(runtime_builder.calls[0], config)
        self.assertIsInstance(orchestrator.kwargs["scenario_resolver"], FakeScenarioResolver)
        self.assertIs(orchestrator.kwargs["scenario_resolver"].config, config.prompt)
        self.assertIs(orchestrator.kwargs["scenario_resolver"].resource_registry, components.resource_registry)
        self.assertIsInstance(orchestrator.kwargs["scenario_resolver"].scenario_recognizer, FakeScenarioRecognizer)
        self.assertIs(orchestrator.kwargs["scenario_resolver"].scenario_recognizer.llm_client, llm_client)
        self.assertIsInstance(orchestrator.kwargs["extractor"], FakeSlotExtractor)
        self.assertIs(orchestrator.kwargs["extractor"].llm_client, llm_client)

    def test_builder_reuses_provided_runtime_components_without_rebuilding(self) -> None:
        from a2a_t.server.prompt_compliance.prompt_compliance_orchestrator_builder import PromptComplianceOrchestratorBuilder

        components = type(
            "Components",
            (),
            {
                "guardrail": object(),
                "resource_registry": object(),
                "template_loader": object(),
                "slot_schema_loader": object(),
                "slot_json_schema_loader": object(),
                "prompt_resource_loader": object(),
                "json_schema_slot_validator": object(),
            },
        )()
        runtime_builder = FakeRuntimeComponentsBuilder(components)
        llm_client = object()
        builder = PromptComplianceOrchestratorBuilder(
            runtime_components_builder=runtime_builder,
            scenario_recognizer_cls=FakeScenarioRecognizer,
            scenario_resolver_cls=FakeScenarioResolver,
            slot_extractor_cls=FakeSlotExtractor,
            orchestrator_cls=FakeOrchestrator,
        )
        config = A2ATConfig(
            prompt=PromptRuntimeConfig(local_root_dir="./default-root"),
            prompt_compliance=PromptComplianceConfig(),
        )

        orchestrator = builder.build(
            config=config,
            llm_client=llm_client,
            runtime_components=components,
        )

        self.assertEqual(runtime_builder.calls, [])
        self.assertIsInstance(orchestrator.kwargs["scenario_resolver"], FakeScenarioResolver)
        self.assertIs(orchestrator.kwargs["scenario_resolver"].config, config.prompt)
        self.assertIs(orchestrator.kwargs["scenario_resolver"].resource_registry, components.resource_registry)
        self.assertIs(orchestrator.kwargs["guardrail"], components.guardrail)
        self.assertIs(orchestrator.kwargs["prompt_resource_loader"], components.prompt_resource_loader)
        self.assertIs(orchestrator.kwargs["slot_json_schema_loader"], components.slot_json_schema_loader)
        self.assertIs(orchestrator.kwargs["validator"], components.json_schema_slot_validator)

if __name__ == "__main__":
    unittest.main()
