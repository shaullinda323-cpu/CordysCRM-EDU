package cn.cordys.crm.education.dto.response;

public record CreditEntryResponse(
        String id,
        String sessionId,
        String kind,
        java.math.BigDecimal units,
        Long createTime,
        String createUser) {}
