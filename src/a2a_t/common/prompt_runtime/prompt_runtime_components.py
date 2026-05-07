from __future__ import annotations

from dataclasses import dataclass

from a2a_t.common.prompt_resources import (
    PromptResourceLoader,
    PromptResourceRegistry,
    PromptResourceSource,
    ScenarioLoader,
    SlotJsonSchemaLoader,
    SlotSchemaLoader,
    TemplateLoader,
)
from a2a_t.prompt.validation.guardrails import SafetyGuardrail
from a2a_t.prompt.validation.json_schema_slot_validator import JsonSchemaSlotValidator
from a2a_t.prompt.validation.slot_validator import SlotValidator


@dataclass(slots=True)
class PromptRuntimeComponents:
    """Group the shared prompt runtime services built from configuration."""

    resource_source: PromptResourceSource
    resource_registry: PromptResourceRegistry
    scenario_loader: ScenarioLoader
    template_loader: TemplateLoader
    slot_schema_loader: SlotSchemaLoader
    slot_json_schema_loader: SlotJsonSchemaLoader
    prompt_resource_loader: PromptResourceLoader
    slot_validator: SlotValidator
    json_schema_slot_validator: JsonSchemaSlotValidator
    guardrail: SafetyGuardrail
