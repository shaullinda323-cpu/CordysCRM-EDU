package cn.cordys.crm.education.dto.request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * Course units are explicit teaching terms; the payable amount comes only from the native product
 * row.
 */
public record EnrollmentCreateRequest(
        @NotBlank @Size(max = 32) String studentId,
        @NotBlank @Size(max = 32) String orderId,
        @NotBlank @Size(max = 32) String subFieldId,
        @NotBlank @Size(max = 32) String orderRowId,
        @NotBlank @Size(max = 64) String requestKey,
        @NotBlank @Size(max = 100) String title,
        @NotBlank @Pattern(regexp = "HOURS|EVENT") String mode,
        @NotNull @DecimalMin("0.01") @DecimalMax("10000") @Digits(integer = 5, fraction = 2)
                BigDecimal totalUnits,
        @Size(max = 32) String renewalFromId) {
    public EnrollmentCreateRequest(
            String studentId,
            String orderId,
            String subFieldId,
            String orderRowId,
            String requestKey,
            String title,
            String mode,
            BigDecimal totalUnits) {
        this(studentId, orderId, subFieldId, orderRowId, requestKey, title, mode, totalUnits, null);
    }
}
