from __future__ import annotations

from a2a_t.prompt.analysis import SlotExtractor
from a2a_t.prompt.analysis.errors import PromptAnalysisError
from a2a_t.common.prompt_resources import (
    PromptResourceLoader,
    PromptResourceNotFoundError,
    PromptResourceParseError,
    SlotJsonSchemaLoader,
    SlotSchemaLoader,
    TemplateLoader,
)
from a2a_t.prompt.common.task_prompt_format import TaskPromptFormatError, parse_task_prompt_metadata
from a2a_t.prompt.common.errors import PromptSourceError
from a2a_t.prompt.common.models import PromptReference
from a2a_t.prompt.validation.errors import GuardrailExecutionError
from a2a_t.prompt.validation.guardrails import SafetyGuardrail
from a2a_t.prompt.validation.models import SlotValidationResult
from a2a_t.prompt.validation.json_schema_slot_validator import JsonSchemaSlotValidator
from a2a_t.server.prompt_compliance.constants import (
    GUARDRAIL_EXECUTION_ERROR,
    GENERATION_STAGE,
    PROMPT_RESOURCE_ACCESS_ERROR,
    PROMPT_RESOURCE_PARSE_ERROR,
    GUARDRAIL_REJECTED,
    GUARDRAIL_STAGE,
    PASSED_STAGE,
    PROCESSED_PROMPT_PARSE_ERROR,
    PROMPT_PARSE_STAGE,
    PROMPT_RESOURCE_LOAD_ERROR,
    SLOT_EXTRACTION_ERROR,
    SLOT_EXTRACTION_STAGE,
    SLOT_SCHEMA_LOAD_ERROR,
    SLOT_VALIDATION_ERROR,
    SLOT_VALIDATION_STAGE,
    TEMPLATE_LOAD_ERROR,
)
from a2a_t.server.prompt_compliance.result import PromptComplianceResult


class PromptComplianceOrchestrator:
    """Coordinate prompt compliance validation flow on the server side."""

    def __init__(
        self,
        *,
        guardrail: SafetyGuardrail,
        template_loader: TemplateLoader,
        slot_schema_loader: SlotSchemaLoader,
        slot_json_schema_loader: SlotJsonSchemaLoader,
        prompt_resource_loader: PromptResourceLoader,
        extractor: SlotExtractor,
        validator: JsonSchemaSlotValidator,
    ) -> None:
        self._guardrail = guardrail
        self._template_loader = template_loader
        self._slot_schema_loader = slot_schema_loader
        self._slot_json_schema_loader = slot_json_schema_loader
        self._prompt_resource_loader = prompt_resource_loader
        self._extractor = extractor
        self._validator = validator

    def check(
        self,
        *,
        processed_prompt_text: str,
        request_metadata: dict[str, object] | None = None,
    ) -> PromptComplianceResult:
        """Validate a processed task prompt and derive follow-up negotiation hints when needed."""
        try:
            guardrail_result = self._guardrail.check(processed_prompt_text, request_metadata)
        except GuardrailExecutionError as error:
            return self._error_result(
                stage=GUARDRAIL_STAGE,
                error_code=GUARDRAIL_EXECUTION_ERROR,
                error_message=str(error),
            )
        except Exception as error:
            return self._error_result(
                stage=GUARDRAIL_STAGE,
                error_code=GUARDRAIL_EXECUTION_ERROR,
                error_message=str(error),
            )
        if not guardrail_result.passed:
            return self._error_result(
                stage=GUARDRAIL_STAGE,
                error_code=guardrail_result.error_code or GUARDRAIL_REJECTED,
                error_message=guardrail_result.error_message or "Guardrail rejected the processed prompt.",
            )

        try:
            reference = self._parse_reference(processed_prompt_text)
        except TaskPromptFormatError as error:
            return self._error_result(PROMPT_PARSE_STAGE, PROCESSED_PROMPT_PARSE_ERROR, str(error))

        try:
            template_text = self._template_loader.load(reference=reference)
        except PromptResourceNotFoundError as error:
            return self._error_result(GENERATION_STAGE, TEMPLATE_LOAD_ERROR, str(error))
        except PromptResourceParseError as error:
            return self._error_result(GENERATION_STAGE, PROMPT_RESOURCE_PARSE_ERROR, str(error))
        except PromptSourceError as error:
            return self._error_result(GENERATION_STAGE, PROMPT_RESOURCE_ACCESS_ERROR, str(error))

        try:
            slot_json_schema = self._slot_json_schema_loader.load(reference=reference)
        except PromptResourceNotFoundError as error:
            return self._error_result(GENERATION_STAGE, SLOT_SCHEMA_LOAD_ERROR, str(error))
        except PromptResourceParseError as error:
            return self._error_result(GENERATION_STAGE, PROMPT_RESOURCE_PARSE_ERROR, str(error))
        except PromptSourceError as error:
            return self._error_result(GENERATION_STAGE, PROMPT_RESOURCE_ACCESS_ERROR, str(error))

        try:
            slot_schema = self._slot_schema_loader.load(reference=reference)
        except PromptResourceNotFoundError as error:
            return self._error_result(GENERATION_STAGE, SLOT_SCHEMA_LOAD_ERROR, str(error))
        except PromptResourceParseError as error:
            return self._error_result(GENERATION_STAGE, PROMPT_RESOURCE_PARSE_ERROR, str(error))
        except PromptSourceError as error:
            return self._error_result(GENERATION_STAGE, PROMPT_RESOURCE_ACCESS_ERROR, str(error))

        try:
            slot_prompts = self._prompt_resource_loader.load(
                analysis_action="slot_extraction",
                version=reference.version,
                language=reference.language,
            )
        except PromptResourceNotFoundError as error:
            return self._error_result(GENERATION_STAGE, PROMPT_RESOURCE_LOAD_ERROR, str(error))
        except PromptResourceParseError as error:
            return self._error_result(GENERATION_STAGE, PROMPT_RESOURCE_PARSE_ERROR, str(error))
        except PromptSourceError as error:
            return self._error_result(GENERATION_STAGE, PROMPT_RESOURCE_ACCESS_ERROR, str(error))

        try:
            extraction_result = self._extractor.extract(
                normalized_input=processed_prompt_text,
                reference=reference,
                template_text=template_text,
                slot_schema=slot_schema,
                system_prompt=slot_prompts.system_prompt,
                user_prompt=slot_prompts.user_prompt,
            )
        except PromptAnalysisError as error:
            return self._error_result(SLOT_EXTRACTION_STAGE, SLOT_EXTRACTION_ERROR, str(error))
        except Exception as error:
            return self._error_result(SLOT_EXTRACTION_STAGE, SLOT_EXTRACTION_ERROR, str(error))

        validation_result: SlotValidationResult = self._validator.validate(
            slots=extraction_result.slots,
            slot_errors=extraction_result.slot_errors,
            slot_json_schema=slot_json_schema,
        )
        if not validation_result.passed:
            error_message = self._aggregate_slot_errors(validation_result)
            return PromptComplianceResult(
                success=False,
                failure={
                    "code": SLOT_VALIDATION_ERROR,
                    "message": error_message,
                    "stage": SLOT_VALIDATION_STAGE,
                },
            )

        return PromptComplianceResult(
            success=True,
        )

    @staticmethod
    def _parse_reference(processed_prompt_text: str) -> PromptReference:
        """Extract the prompt reference embedded in task prompt front matter."""
        return parse_task_prompt_metadata(processed_prompt_text).to_prompt_reference()

    def _aggregate_slot_errors(self, validation_result: SlotValidationResult) -> str:
        """Collapse slot validation messages into the single message exposed to callers."""
        messages = [slot_error.message for slot_error in validation_result.slot_errors if slot_error.message]
        return "; ".join(messages) if messages else "Slot validation failed."

    @staticmethod
    def _error_result(stage: str, error_code: str, error_message: str) -> PromptComplianceResult:
        """Build a standardized compliance failure result."""
        return PromptComplianceResult(
            success=False,
            failure={
                "code": error_code,
                "message": error_message,
                "stage": stage,
            },
        )
