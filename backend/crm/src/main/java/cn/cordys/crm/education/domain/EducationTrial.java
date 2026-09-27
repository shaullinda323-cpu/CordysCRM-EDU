package cn.cordys.crm.education.domain;

import cn.cordys.common.domain.BaseModel;

import jakarta.persistence.Table;

import lombok.Data;

/** A trial is an appointment, never evidence of a sale or paid entitlement. */
@Data
@Table(name = "edu_trial")
public class EducationTrial extends BaseModel {
    private String organizationId;
    private String studentId;
    private String requestKey;
    private String topic;
    private Long startsAt;
    private Long endsAt;
    private String status;
    private String outcome;
}
