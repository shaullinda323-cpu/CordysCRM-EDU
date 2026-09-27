package cn.cordys.crm.education.domain;

import cn.cordys.common.domain.BaseModel;

import jakarta.persistence.Table;

import lombok.Data;

@Data
@Table(name = "edu_session_student")
public class EducationSessionStudent extends BaseModel {
    private String organizationId;
    private String sessionId;
    private String enrollmentId;
    private String studentId;
}
