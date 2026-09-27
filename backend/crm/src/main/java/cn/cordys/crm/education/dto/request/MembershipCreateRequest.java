package cn.cordys.crm.education.dto.request;

import jakarta.validation.constraints.*;

public record MembershipCreateRequest(
        @NotBlank @Size(max = 32) String enrollmentId,
        @NotNull @Positive Long effectiveFrom,
        @Positive Long effectiveTo) {}
