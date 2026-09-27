package cn.cordys.crm.education.dto.response;

import java.math.BigDecimal;

public record OrderRowOption(
        String orderId,
        String orderName,
        String subFieldId,
        String rowId,
        int rowNumber,
        BigDecimal amount) {}
