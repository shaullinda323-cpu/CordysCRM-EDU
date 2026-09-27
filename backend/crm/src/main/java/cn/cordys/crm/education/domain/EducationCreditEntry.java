package cn.cordys.crm.education.domain;

import cn.cordys.common.domain.BaseModel;

import jakarta.persistence.Table;

import lombok.Data;

@Data
@Table(name = "edu_credit_entry")
public class EducationCreditEntry extends BaseModel {
    private String organizationId;
    private String enrollmentId;
    private String sessionId;
    private String operationKey;
    private String kind;
    private java.math.BigDecimal units;
}
