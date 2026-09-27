package cn.cordys.crm.education.domain;

import cn.cordys.common.domain.BaseModel;

import jakarta.persistence.Table;

import lombok.Data;

@Data
@Table(name = "edu_attendance_event")
public class EducationAttendanceEvent extends BaseModel {
    private String organizationId;
    private String attendanceId;
    private String requestKey;
    private String requestPayload;
    private Integer revision;
    private String previousStatus;
    private String status;
    private String reason;
}
