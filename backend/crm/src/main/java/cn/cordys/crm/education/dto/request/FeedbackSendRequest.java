package cn.cordys.crm.education.dto.request;

import jakarta.validation.constraints.*;

/** Records the operator's evidence; this request does not send a message or prove delivery. */
public record FeedbackSendRequest(
        @NotBlank @Size(max = 32) String recipientContactId,
        @NotNull @Positive Long sentTime,
        @NotBlank @Size(max = 1000) String sendEvidence,
        @NotBlank @Size(max = 1000) String consentReference) {}
