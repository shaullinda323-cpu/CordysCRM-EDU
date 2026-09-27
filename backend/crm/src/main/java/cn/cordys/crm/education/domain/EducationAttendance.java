package cn.cordys.crm.education.domain;

import cn.cordys.common.domain.BaseModel;

import jakarta.persistence.Table;

import lombok.Data;

@Data
@Table(name = "edu_attendance")
public class EducationAttendance extends BaseModel {
    private String organizationId;
    private String sessionId;
    private String enrollmentId;
    private String status;
    private java.math.BigDecimal chargedUnits;
    private Integer revision;
}
