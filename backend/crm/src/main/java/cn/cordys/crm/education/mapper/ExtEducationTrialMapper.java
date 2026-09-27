package cn.cordys.crm.education.mapper;

import cn.cordys.crm.education.domain.EducationTrial;

import org.apache.ibatis.annotations.Param;

public interface ExtEducationTrialMapper {
    EducationTrial lock(@Param("id") String id, @Param("orgId") String orgId);

    EducationTrial findRequest(
            @Param("studentId") String studentId,
            @Param("requestKey") String requestKey,
            @Param("orgId") String orgId);
}
