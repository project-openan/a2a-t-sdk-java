from __future__ import annotations

from a2a_t.prompt.common.models import PromptReference

from ._base import BasePromptResourceLoader
from .models import SlotDefinition, SlotRange, SlotSchema


class SlotSchemaLoader(BasePromptResourceLoader):
    """Load slot schemas used by extraction and validation flows."""

    def load(self, *, reference: PromptReference) -> SlotSchema:
        """Return the slot schema for the referenced scenario resource."""
        path = f"slots/{reference.scenario_code}/{reference.version}/{reference.language}/slot.json"
        data = self._read_json(path)
        if self._looks_like_json_schema(data):
            return self._build_from_json_schema(data, reference=reference)
        raw_slots = data.get("slots") or []

        return SlotSchema(
            scenario_code=str(data["scenario_code"]),
            version=str(data["version"]),
            slots=[self._build_slot_definition(item) for item in raw_slots],
        )

    @staticmethod
    def _looks_like_json_schema(data: dict[str, object]) -> bool:
        """Return whether the loaded payload already looks like a JSON Schema object."""
        return "slots" not in data and data.get("type") == "object" and "properties" in data

    def _build_from_json_schema(self, data: dict[str, object], *, reference: PromptReference) -> SlotSchema:
        """Project a standard JSON Schema back into the SlotSchema view used by generation flows."""
        properties = data.get("properties")
        if not isinstance(properties, dict):
            properties = {}
        required_names = set(data.get("required") or [])
        slots = [
            self._build_slot_definition_from_property(
                name=str(name),
                property_schema=property_schema,
                required=bool(name in required_names),
            )
            for name, property_schema in properties.items()
        ]
        return SlotSchema(
            scenario_code=reference.scenario_code,
            version=reference.version,
            slots=slots,
        )

    def _build_slot_definition(self, raw_slot: dict[str, object]) -> SlotDefinition:
        """Normalize a raw slot payload into the shared slot definition model."""
        raw_range = raw_slot.get("range")
        slot_range = None
        if isinstance(raw_range, dict):
            slot_range = SlotRange(
                min=raw_range.get("min"),  # type: ignore[arg-type]
                max=raw_range.get("max"),  # type: ignore[arg-type]
            )

        allowed_values = raw_slot.get("allowed_values")
        if allowed_values is not None and not isinstance(allowed_values, list):
            allowed_values = [allowed_values]

        return SlotDefinition(
            name=str(raw_slot["name"]),
            required=bool(raw_slot["required"]),
            description=str(raw_slot["description"]),
            example=str(raw_slot["example"]),
            value_constraint=str(raw_slot["value_constraint"]),
            type=str(raw_slot["type"]) if raw_slot.get("type") is not None else None,
            allowed_values=allowed_values,
            range=slot_range,
            pattern=str(raw_slot["pattern"]) if raw_slot.get("pattern") is not None else None,
        )

    def _build_slot_definition_from_property(
        self,
        *,
        name: str,
        property_schema: object,
        required: bool,
    ) -> SlotDefinition:
        """Normalize one JSON Schema property into the shared slot definition model."""
        if not isinstance(property_schema, dict):
            property_schema = {}

        examples = property_schema.get("examples")
        example = ""
        if isinstance(examples, list) and examples:
            example = str(examples[0])

        allowed_values = property_schema.get("x-a2at-allowed-values")
        if allowed_values is None:
            allowed_values = property_schema.get("enum")
        if allowed_values is not None and not isinstance(allowed_values, list):
            allowed_values = [allowed_values]

        slot_range = None
        minimum = property_schema.get("minimum")
        maximum = property_schema.get("maximum")
        if minimum is not None or maximum is not None:
            slot_range = SlotRange(min=minimum, max=maximum)  # type: ignore[arg-type]

        slot_type = property_schema.get("x-a2at-slot-type")
        if slot_type is None:
            slot_type = property_schema.get("type")

        return SlotDefinition(
            name=name,
            required=required,
            description=str(property_schema.get("description") or ""),
            example=example,
            value_constraint=str(property_schema.get("x-a2at-value-constraint") or ""),
            type=str(slot_type) if slot_type is not None else None,
            allowed_values=allowed_values,
            range=slot_range,
            pattern=str(property_schema["pattern"]) if property_schema.get("pattern") is not None else None,
        )
