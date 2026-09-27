package cn.cordys.crm.education.dto.request;

import jakarta.validation.constraints.*;

public record TrialResultRequest(
        @NotBlank @Pattern(regexp = "ATTENDED|NO_SHOW|CANCELLED") String status,
        @NotBlank @Size(max = 2000) String outcome) {}
