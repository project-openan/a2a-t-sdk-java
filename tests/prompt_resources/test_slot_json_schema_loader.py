from __future__ import annotations

import json
import sys
from pathlib import Path
import unittest


PROJECT_ROOT = Path(__file__).resolve().parents[2]
SRC_ROOT = PROJECT_ROOT / "src"

if str(SRC_ROOT) not in sys.path:
    sys.path.insert(0, str(SRC_ROOT))


from a2a_t.prompt.common.models import PromptReference
from tests.test_support import ManagedTempDirTestCase


class FakePromptResourceSource:
    source_type = "fake"

    def __init__(self) -> None:
        self.json_values: dict[str, dict[str, object]] = {}
        self.json_reads: list[str] = []

    def read_json(self, *, relative_path: str) -> dict[str, object]:
        self.json_reads.append(relative_path)
        return self.json_values[relative_path]

    def exists(self, *, relative_path: str) -> bool:
        return relative_path in self.json_values


class SlotJsonSchemaLoaderTest(ManagedTempDirTestCase):
    def setUp(self) -> None:
        super().setUp()
        self.root = self.make_temp_dir("prompt_resources")

    def _write_json(self, relative_path: str, payload: dict[str, object]) -> None:
        path = self.root / relative_path
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(payload, ensure_ascii=True), encoding="utf-8")

    def test_loader_reads_raw_json_schema_object(self) -> None:
        self._write_json(
            "slots/energy_saving/0.0.1/en-US/slot.json",
            {
                "$schema": "https://json-schema.org/draft/2020-12/schema",
                "type": "object",
                "additionalProperties": False,
                "properties": {"site": {"type": "string", "minLength": 1}},
                "required": ["site"],
            },
        )

        from a2a_t.common.prompt_resources.slot_json_schema_loader import SlotJsonSchemaLoader

        loader = SlotJsonSchemaLoader(root_dir=self.root)
        slot_schema = loader.load(
            reference=PromptReference(scenario_code="energy_saving", version="0.0.1", language="en-US")
        )

        self.assertEqual(slot_schema["type"], "object")
        self.assertEqual(slot_schema["required"], ["site"])
        self.assertEqual(slot_schema["properties"]["site"]["minLength"], 1)

    def test_loader_reads_via_prompt_resource_source(self) -> None:
        source = FakePromptResourceSource()
        source.json_values["slots/energy_saving/0.0.1/en-US/slot.json"] = {
            "$schema": "https://json-schema.org/draft/2020-12/schema",
            "type": "object",
            "properties": {"site": {"type": "string", "minLength": 1}},
            "required": ["site"],
        }

        from a2a_t.common.prompt_resources.slot_json_schema_loader import SlotJsonSchemaLoader

        loader = SlotJsonSchemaLoader(source=source)
        slot_schema = loader.load(
            reference=PromptReference(scenario_code="energy_saving", version="0.0.1", language="en-US")
        )

        self.assertEqual(source.json_reads, ["slots/energy_saving/0.0.1/en-US/slot.json"])
        self.assertEqual(slot_schema["required"], ["site"])

    def test_loader_translates_legacy_slot_schema_to_string_json_schema(self) -> None:
        self._write_json(
            "slots/energy_saving/0.0.1/en-US/slot.json",
            {
                "scenario_code": "energy_saving",
                "version": "0.0.1",
                "slots": [
                    {
                        "name": "site",
                        "required": True,
                        "description": "Site name",
                        "example": "Site A",
                        "value_constraint": "Must be a concrete site name.",
                        "type": "string",
                        "allowed_values": None,
                        "range": None,
                        "pattern": None,
                    },
                    {
                        "name": "incident_level",
                        "required": False,
                        "description": "Incident level",
                        "example": "critical",
                        "value_constraint": "Must be one of the supported levels.",
                        "type": "string",
                        "allowed_values": ["critical", "major"],
                        "range": None,
                        "pattern": None,
                    },
                ],
            },
        )

        from a2a_t.common.prompt_resources.slot_json_schema_loader import SlotJsonSchemaLoader

        loader = SlotJsonSchemaLoader(root_dir=self.root)
        slot_schema = loader.load(
            reference=PromptReference(scenario_code="energy_saving", version="0.0.1", language="en-US")
        )

        self.assertEqual(slot_schema["type"], "object")
        self.assertEqual(slot_schema["required"], ["site"])
        self.assertEqual(slot_schema["properties"]["site"]["type"], "string")
        self.assertEqual(slot_schema["properties"]["site"]["minLength"], 1)
        self.assertEqual(slot_schema["properties"]["incident_level"]["enum"], ["critical", "major"])


if __name__ == "__main__":
    unittest.main()
