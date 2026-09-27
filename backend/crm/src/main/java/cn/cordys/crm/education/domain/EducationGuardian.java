package cn.cordys.crm.education.domain;

import cn.cordys.common.domain.BaseModel;

import jakarta.persistence.Table;

import lombok.Data;

/** Relationship does not itself grant pickup, messaging or publication consent. */
@Data
@Table(name = "edu_guardian")
public class EducationGuardian extends BaseModel {
    private String organizationId;
    private String studentId;
    private String customerId;
    private String contactId;
    private String relationship;
}
