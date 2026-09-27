package cn.cordys.crm.education.dto.response;

public record AttendanceResponse(
        String id,
        String enrollmentId,
        String sessionId,
        String status,
        Integer revision,
        java.math.BigDecimal chargedUnits,
        java.math.BigDecimal balance) {}
