package cn.cordys.crm.education.dto.response;

public record TrialResponse(
        String id,
        String studentId,
        String topic,
        Long startsAt,
        Long endsAt,
        String status,
        String outcome) {}
