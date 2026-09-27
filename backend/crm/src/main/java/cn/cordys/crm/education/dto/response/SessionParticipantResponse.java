package cn.cordys.crm.education.dto.response;

import java.math.BigDecimal;

/** Teaching-only projection: excludes family contacts and financial information. */
public record SessionParticipantResponse(
        String enrollmentId,
        String studentName,
        String courseTitle,
        String mode,
        BigDecimal balance,
        String attendanceStatus,
        Integer revision) {}
