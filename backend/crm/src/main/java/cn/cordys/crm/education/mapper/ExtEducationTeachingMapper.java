package cn.cordys.crm.education.mapper;

import cn.cordys.crm.education.domain.EducationEnrollment;

import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

public interface ExtEducationTeachingMapper {
    int organizationExists(@Param("org") String org);

    void ensureLock(@Param("org") String org);

    String lockOrganization(@Param("org") String org);

    List<EducationEnrollment> roster(
            @Param("org") String org, @Param("classId") String classId, @Param("time") long time);

    int conflicts(
            @Param("org") String org,
            @Param("teacher") String teacher,
            @Param("room") String room,
            @Param("start") long start,
            @Param("end") long end,
            @Param("students") List<String> students);

    int makeupClaims(
            @Param("org") String org,
            @Param("source") String source,
            @Param("enrollment") String enrollment);

    BigDecimal balance(@Param("org") String org, @Param("enrollment") String enrollment);
}
