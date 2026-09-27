package cn.cordys.crm.education.domain;

import cn.cordys.common.domain.BaseModel;

import jakarta.persistence.Table;

import lombok.Data;

@Data
@Table(name = "edu_payment_allocation")
public class EducationPaymentAllocation extends BaseModel {
    private String organizationId;
    private String enrollmentId;
    private String receiptId;
    private String contractId;
    private java.math.BigDecimal receiptTotal;
    private java.math.BigDecimal amount;
}
