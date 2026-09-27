package cn.cordys.crm.education.dto.request;

import jakarta.validation.constraints.*;

public record FeedbackCreateRequest(
        @NotBlank @Size(max = 32) String sessionId,
        @NotBlank @Size(max = 32) String enrollmentId,
        @NotBlank @Size(max = 64) String requestKey,
        @Size(max = 32) String previousId,
        @NotBlank @Size(max = 4000) String content) {}
