package cn.cordys.crm.education.dto.response;

public record FeedbackResponse(
        String id,
        String sessionId,
        String enrollmentId,
        Integer revision,
        String content,
        String status,
        String confirmedUser,
        Long confirmedTime,
        String recipientContactId,
        Long sentTime,
        String sendEvidence,
        String deliveryStatus) {}
