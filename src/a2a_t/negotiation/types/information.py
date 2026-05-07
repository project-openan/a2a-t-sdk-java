from __future__ import annotations

from a2a_t.negotiation.common.enums import NegotiationStatus
from a2a_t.negotiation.common.models import ContinueResult, NegotiationContext, NegotiationRecord, ReceiveResult

from .base import BaseNegotiationType


class InformationNegotiationType(BaseNegotiationType):
    """Handle information negotiations that may end in a completed task prompt."""

    _COMPLETE_MESSAGE = "Task prompt is complete."

    def __init__(self, *, prompt_renderer, prompt_checker=None) -> None:
        super().__init__(prompt_renderer=prompt_renderer)
        self._prompt_checker = prompt_checker

    def process_received_message(
        self,
        *,
        message: str,
        context: NegotiationContext,
        record: NegotiationRecord | None,
    ) -> ReceiveResult:
        """Validate received prompt content and decide whether more information is needed."""
        if context.status in {NegotiationStatus.AGREED, NegotiationStatus.REJECTED}:
            return ReceiveResult(
                need_response=False,
                facts={},
                message=message,
            )

        if self._prompt_checker is None:
            return super().process_received_message(
                message=message,
                context=context,
                record=None,
        )

        compliance_result = self._prompt_checker.check(
            processed_prompt_text=message,
            request_metadata=None,
        )
        if compliance_result.success:
            return ReceiveResult(
                need_response=True,
                facts={},
                message=self._COMPLETE_MESSAGE,
            )

        failure = compliance_result.failure or {}
        failure_stage = str(failure.get("stage") or "")
        failure_message = str(failure.get("message") or "").strip()

        if failure_stage == "slot_validation":
            return ReceiveResult(
                need_response=True,
                facts={},
                message=failure_message or message,
            )

        return ReceiveResult(
            need_response=True,
            facts={},
            message=failure_message or "Task prompt validation failed.",
        )

    def render_continue_prompt(
        self,
        *,
        record: NegotiationRecord,
        context: NegotiationContext,
        status: NegotiationStatus,
        content_text: str,
    ) -> ContinueResult:
        """Render the next information negotiation prompt and finalize agreed task prompts."""
        prompt_text = self._prompt_renderer.render_continue(
            negotiation_type=context.negotiation_type,
            message=content_text,
        )
        final_task_prompt = content_text if status == NegotiationStatus.AGREED else None
        return ContinueResult(
            prompt_text=prompt_text,
            final_task_prompt=final_task_prompt,
        )
