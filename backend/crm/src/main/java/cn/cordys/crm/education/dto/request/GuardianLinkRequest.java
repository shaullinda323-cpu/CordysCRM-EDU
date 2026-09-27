package cn.cordys.crm.education.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GuardianLinkRequest(
        @NotBlank @Size(max = 32) String studentId,
        @NotBlank @Size(max = 32) String contactId,
        @NotBlank @Size(max = 30) String relationship) {}
