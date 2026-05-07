from __future__ import annotations

from a2a_t.prompt.common.models import PromptReference

from ._base import BasePromptResourceLoader


class SlotJsonSchemaLoader(BasePromptResourceLoader):
    """Load slot validation schemas used by server-side compliance validation."""

    def load(self, *, reference: PromptReference) -> dict[str, object]:
        """Return a JSON Schema object for the referenced scenario resource."""
        path = f"slots/{reference.scenario_code}/{reference.version}/{reference.language}/slot.json"
        data = self._read_json(path)
        if self._looks_like_json_schema(data):
            return dict(data)
        return self._translate_legacy_schema(data)

    @staticmethod
    def _looks_like_json_schema(data: dict[str, object]) -> bool:
        """Return whether the loaded payload already looks like a JSON Schema object."""
        return "slots" not in data and data.get("type") == "object" and "properties" in data

    def _translate_legacy_schema(self, data: dict[str, object]) -> dict[str, object]:
        """Translate the legacy slot DSL into a string-slot JSON Schema."""
        raw_slots = data.get("slots") or []
        properties: dict[str, object] = {}
        required: list[str] = []

        for raw_slot in raw_slots:
            if not isinstance(raw_slot, dict):
                continue
            name = str(raw_slot["name"])
            property_schema: dict[str, object] = {"type": "string"}
            if bool(raw_slot.get("required")):
                required.append(name)
                # Preserve existing behavior: blank required strings should fail local validation.
                property_schema["minLength"] = 1
            pattern = raw_slot.get("pattern")
            if pattern is not None:
                property_schema["pattern"] = str(pattern)
            allowed_values = raw_slot.get("allowed_values")
            if raw_slot.get("type") != "list" and isinstance(allowed_values, list) and allowed_values:
                property_schema["enum"] = [str(value) for value in allowed_values]
            properties[name] = property_schema

        return {
            "$schema": "https://json-schema.org/draft/2020-12/schema",
            "type": "object",
            "additionalProperties": False,
            "properties": properties,
            "required": required,
        }
