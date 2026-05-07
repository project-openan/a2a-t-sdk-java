from __future__ import annotations

from a2a_t.common.prompt_resources import (
    LocalPromptResourceSource,
    PromptResourceLoader,
    PromptResourceRegistry,
    ScenarioLoader,
    SlotJsonSchemaLoader,
    SlotSchemaLoader,
    TemplateLoader,
)
from a2a_t.config.models import A2ATConfig
from a2a_t.prompt.validation import JsonSchemaSlotValidator, SafetyGuardrailFactory, SlotValidator

from .prompt_runtime_components import PromptRuntimeComponents


class PromptRuntimeComponentsBuilder:
    """Build the shared prompt runtime services used by client and server flows."""

    def build(self, *, config: A2ATConfig) -> PromptRuntimeComponents:
        """Create loaders, validators, and guardrails from the resolved config."""
        prompt_config = config.prompt
        if prompt_config.source_type != "local_file":
            raise ValueError(f"Unsupported prompt resource source_type: {prompt_config.source_type}")

        # All prompt resources currently resolve from the same local root so downstream
        # components observe identical fallback and error behavior.
        resource_source = LocalPromptResourceSource(
            root_dir=prompt_config.local_root_dir,
            cache=None,
        )
        scenario_loader = ScenarioLoader(source=resource_source)
        template_loader = TemplateLoader(source=resource_source)
        slot_schema_loader = SlotSchemaLoader(source=resource_source)
        slot_json_schema_loader = SlotJsonSchemaLoader(source=resource_source)
        prompt_resource_loader = PromptResourceLoader(source=resource_source)
        resource_registry = PromptResourceRegistry(
            source=resource_source,
            scenario_loader=scenario_loader,
            prompt_resource_loader=prompt_resource_loader,
            template_loader=template_loader,
            slot_schema_loader=slot_schema_loader,
        )
        slot_validator = SlotValidator()
        json_schema_slot_validator = JsonSchemaSlotValidator()
        guardrail = SafetyGuardrailFactory.create(config.prompt_compliance.guardrail)

        return PromptRuntimeComponents(
            resource_source=resource_source,
            resource_registry=resource_registry,
            scenario_loader=scenario_loader,
            template_loader=template_loader,
            slot_schema_loader=slot_schema_loader,
            slot_json_schema_loader=slot_json_schema_loader,
            prompt_resource_loader=prompt_resource_loader,
            slot_validator=slot_validator,
            json_schema_slot_validator=json_schema_slot_validator,
            guardrail=guardrail,
        )
