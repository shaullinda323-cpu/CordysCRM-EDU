package cn.cordys.crm.education.mapper;

import cn.cordys.crm.education.domain.EducationGuardian;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ExtEducationGuardianMapper {
    String lockStudent(@Param("studentId") String studentId, @Param("orgId") String orgId);

    List<EducationGuardian> selectLinkForUpdate(
            @Param("studentId") String studentId,
            @Param("contactId") String contactId,
            @Param("orgId") String orgId);
}
