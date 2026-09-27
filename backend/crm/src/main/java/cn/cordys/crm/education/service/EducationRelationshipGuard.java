package cn.cordys.crm.education.service;

import cn.cordys.common.exception.GenericException;
import cn.cordys.common.response.result.CrmHttpResultCode;
import cn.cordys.common.util.Translator;
import cn.cordys.crm.education.domain.EducationGuardian;
import cn.cordys.crm.education.domain.EducationStudent;
import cn.cordys.mybatis.BaseMapper;

import jakarta.annotation.Resource;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Native CRM mutations must preserve stable child identities; SQL foreign keys close concurrent
 * races.
 */
@Service
public class EducationRelationshipGuard {
    @Resource private BaseMapper<EducationStudent> studentMapper;
    @Resource private BaseMapper<EducationGuardian> guardianMapper;

    public void requireNoStudents(List<String> customerIds) {
        for (String id : customerIds) {
            EducationStudent filter = new EducationStudent();
            filter.setCustomerId(id);
            if (!studentMapper.select(filter).isEmpty()) {
                throw new GenericException(
                        CrmHttpResultCode.VALIDATE_FAILED,
                        Translator.get("education.family.in.use"));
            }
        }
    }

    public void requireUnlinkedContact(String contactId) {
        EducationGuardian filter = new EducationGuardian();
        filter.setContactId(contactId);
        if (!guardianMapper.select(filter).isEmpty()) {
            throw new GenericException(
                    CrmHttpResultCode.VALIDATE_FAILED, Translator.get("education.contact.in.use"));
        }
    }
}
