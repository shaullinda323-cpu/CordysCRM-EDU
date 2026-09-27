package cn.cordys.crm.education.dto.request;

import jakarta.validation.constraints.*;

public record TrialCreateRequest(
        @NotBlank @Size(max = 32) String studentId,
        @NotBlank @Size(max = 64) String requestKey,
        @NotBlank @Size(max = 100) String topic,
        @NotNull @Positive Long startsAt,
        @NotNull @Positive Long endsAt) {}
