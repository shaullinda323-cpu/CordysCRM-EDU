package cn.cordys.crm.education.dto.request;

import jakarta.validation.constraints.*;

public record AttendanceRequest(
        @NotBlank @Size(max = 64) String requestKey,
        @NotNull @Min(0) Integer expectedRevision,
        @NotBlank @Pattern(regexp = "PRESENT|ABSENT|LEAVE") String status,
        @NotBlank @Size(max = 1000) String reason) {}
