package cn.cordys.crm.education.dto.response;

public record SessionResponse(
        String id,
        String classId,
        String teacherId,
        String roomKey,
        Long startsAt,
        Long endsAt,
        String status,
        String makeupForSessionId,
        java.util.List<String> enrollmentIds) {}
