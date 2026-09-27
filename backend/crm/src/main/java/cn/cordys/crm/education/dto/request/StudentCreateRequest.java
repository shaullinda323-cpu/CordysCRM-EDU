package cn.cordys.crm.education.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StudentCreateRequest(
        @NotBlank @Size(max = 32) String customerId,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 50) String grade) {}
