package cn.cordys.crm.education.mapper;

import cn.cordys.crm.contract.domain.ContractPaymentRecord;
import cn.cordys.crm.education.domain.EducationEnrollment;

import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

public interface ExtEducationEnrollmentMapper {
    EducationEnrollment lock(@Param("id") String id, @Param("org") String org);

    EducationEnrollment findRequest(
            @Param("student") String student, @Param("key") String key, @Param("org") String org);

    EducationEnrollment findRow(
            @Param("order") String order,
            @Param("field") String field,
            @Param("row") String row,
            @Param("org") String org);

    ContractPaymentRecord lockReceipt(@Param("id") String id, @Param("org") String org);

    BigDecimal allocated(@Param("enrollment") String enrollment);

    BigDecimal receiptAllocated(@Param("receipt") String receipt);
}
