package cn.cordys.crm.education.domain;

import cn.cordys.common.domain.BaseModel;

import jakarta.persistence.Table;

import lombok.Data;

@Data
@Table(name = "edu_membership")
public class EducationMembership extends BaseModel {
    private String organizationId;
    private String classId;
    private String enrollmentId;
    private Long effectiveFrom;
    private Long effectiveTo;
}
