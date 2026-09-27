package cn.cordys.crm.education.dto.response;

import java.math.BigDecimal;

public record EnrollmentResponse(
        String id,
        String studentId,
        String orderId,
        String title,
        String mode,
        BigDecimal totalUnits,
        BigDecimal amount,
        BigDecimal allocatedAmount,
        String status,
        String handoffUser,
        Long handoffTime,
        String renewalFromId) {}
