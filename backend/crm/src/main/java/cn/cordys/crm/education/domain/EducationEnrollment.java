package cn.cordys.crm.education.domain;

import cn.cordys.common.domain.BaseModel;

import jakarta.persistence.Table;

import lombok.Data;

@Data
@Table(name = "edu_enrollment")
public class EducationEnrollment extends BaseModel {
    private String organizationId;
    private String customerId;
    private String studentId;
    private String orderId;
    private String subFieldId;
    private String orderRowId;
    private String requestKey;
    private String renewalFromId;
    private String title;
    private String mode;
    private java.math.BigDecimal totalUnits;
    private java.math.BigDecimal amount;
    private String contractId;
    private String sellerId;
    private String rowSnapshot;
    private String status;
    private String handoffUser;
    private Long handoffTime;
}
