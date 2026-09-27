package cn.cordys.crm.education.dto.request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record PaymentAllocationRequest(
        @NotBlank @Size(max = 32) String receiptId,
        @NotNull @DecimalMin("0.01") @Digits(integer = 14, fraction = 2) BigDecimal amount) {}
