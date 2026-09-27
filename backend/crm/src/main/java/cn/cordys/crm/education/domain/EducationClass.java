package cn.cordys.crm.education.domain;

import cn.cordys.common.domain.BaseModel;

import jakarta.persistence.Table;

import lombok.Data;

@Data
@Table(name = "edu_class")
public class EducationClass extends BaseModel {
    private String organizationId;
    private String name;
    private String mode;
}
