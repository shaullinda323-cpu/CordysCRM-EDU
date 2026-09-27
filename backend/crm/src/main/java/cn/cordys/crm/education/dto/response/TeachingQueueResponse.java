package cn.cordys.crm.education.dto.response;

public record TeachingQueueResponse(
        String sessionId,
        String className,
        Long startsAt,
        Long endsAt,
        int learners,
        int pendingAttendance,
        int pendingFeedback,
        int pendingSend) {}
