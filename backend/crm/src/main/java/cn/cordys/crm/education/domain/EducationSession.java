package cn.cordys.crm.education.domain;

import cn.cordys.common.domain.BaseModel;

import jakarta.persistence.Table;

import lombok.Data;

@Data
@Table(name = "edu_session")
public class EducationSession extends BaseModel {
    private String organizationId;
    private String classId;
    private String requestKey;
    private String teacherId;
    private String roomKey;
    private Long startsAt;
    private Long endsAt;
    private String status;
    private String makeupForSessionId;
}
