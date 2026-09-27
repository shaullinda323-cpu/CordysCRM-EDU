package cn.cordys.crm.education.domain;

import cn.cordys.common.domain.BaseModel;

import jakarta.persistence.Table;

import lombok.Data;

/** A child is a learning subject, separate from the existing family customer. */
@Data
@Table(name = "edu_student")
public class EducationStudent extends BaseModel {
    private String organizationId;
    private String customerId;
    private String name;
    private String grade;
}
