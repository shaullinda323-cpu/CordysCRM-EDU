package cn.cordys.crm.education.dto.request;

import jakarta.validation.constraints.*;

public record SessionCreateRequest(
        @NotBlank @Size(max = 32) String classId,
        @NotBlank @Size(max = 64) String requestKey,
        @NotBlank @Size(max = 32) String teacherId,
        @NotBlank @Pattern(regexp = "[a-z0-9_-]{1,32}") String roomKey,
        @NotNull @Positive Long startsAt,
        @NotNull @Positive Long endsAt,
        @Size(max = 32) String makeupForSessionId) {}
