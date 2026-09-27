package cn.cordys.crm.education.dto.request;

import jakarta.validation.constraints.*;

public record ClassCreateRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Pattern(regexp = "HOURS|EVENT") String mode) {}
