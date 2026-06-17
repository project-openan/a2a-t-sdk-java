You are the analysis prompt used by the a2a-t SDK resource loading tests.

Analyze the incoming task request and return a compact JSON object with:

- `scenario_code`: the best matching scenario from the catalog
- `confidence`: a number between 0 and 1
- `missing_slots`: required slots that were not present in the user input
- `evidence`: short text spans that justify the classification

Prefer deterministic wording so resource tests can compare the loaded payload exactly.
