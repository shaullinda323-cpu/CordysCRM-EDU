package cn.cordys.crm.education.service;

import cn.cordys.common.constants.PermissionConstants;
import cn.cordys.common.domain.BaseModel;
import cn.cordys.common.exception.GenericException;
import cn.cordys.common.response.result.CrmHttpResultCode;
import cn.cordys.common.uid.IDGenerator;
import cn.cordys.common.util.Translator;
import cn.cordys.crm.customer.domain.CustomerContact;
import cn.cordys.crm.education.domain.EducationGuardian;
import cn.cordys.crm.education.domain.EducationStudent;
import cn.cordys.crm.education.dto.request.GuardianLinkRequest;
import cn.cordys.crm.education.dto.request.StudentCreateRequest;
import cn.cordys.crm.education.dto.response.GuardianResponse;
import cn.cordys.crm.education.dto.response.StudentResponse;
import cn.cordys.crm.education.mapper.ExtEducationGuardianMapper;
import cn.cordys.mybatis.BaseMapper;

import jakarta.annotation.Resource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(rollbackFor = Exception.class)
public class EducationStudentService {
    @Resource private BaseMapper<EducationStudent> studentMapper;
    @Resource private BaseMapper<EducationGuardian> guardianMapper;
    @Resource private BaseMapper<CustomerContact> contactMapper;
    @Resource private EducationFamilyAccessService familyAccess;
    @Resource private ExtEducationGuardianMapper guardianLockMapper;

    @Transactional(readOnly = true)
    public List<StudentResponse> list(String customerId, String actorId, String orgId) {
        familyAccess.check(
                customerId, PermissionConstants.CUSTOMER_MANAGEMENT_READ, actorId, orgId);
        EducationStudent filter = new EducationStudent();
        filter.setOrganizationId(orgId);
        filter.setCustomerId(customerId);
        return studentMapper.select(filter).stream().map(this::response).toList();
    }

    public StudentResponse create(StudentCreateRequest request, String actorId, String orgId) {
        familyAccess.check(
                request.customerId(),
                PermissionConstants.CUSTOMER_MANAGEMENT_UPDATE,
                actorId,
                orgId);
        EducationStudent student = new EducationStudent();
        initialize(student, actorId);
        student.setOrganizationId(orgId);
        student.setCustomerId(request.customerId());
        student.setName(request.name().trim());
        student.setGrade(request.grade() == null ? null : request.grade().trim());
        // Same names are allowed: a name is neither an identity nor a safe merge key.
        studentMapper.insert(student);
        return response(student);
    }

    @Transactional(readOnly = true)
    public List<GuardianResponse> guardians(String studentId, String actorId, String orgId) {
        requireStudent(studentId, PermissionConstants.CUSTOMER_MANAGEMENT_READ, actorId, orgId);
        EducationGuardian filter = new EducationGuardian();
        filter.setStudentId(studentId);
        filter.setOrganizationId(orgId);
        return guardianMapper.select(filter).stream().map(this::response).toList();
    }

    public GuardianResponse linkGuardian(
            GuardianLinkRequest request, String actorId, String orgId) {
        EducationStudent student =
                requireStudent(
                        request.studentId(),
                        PermissionConstants.CUSTOMER_MANAGEMENT_UPDATE,
                        actorId,
                        orgId);
        CustomerContact contact = contactMapper.selectByPrimaryKey(request.contactId());
        if (contact == null
                || !Boolean.TRUE.equals(contact.getEnable())
                || !orgId.equals(contact.getOrganizationId())
                || !student.getCustomerId().equals(contact.getCustomerId())) {
            throw new GenericException(CrmHttpResultCode.FORBIDDEN);
        }

        // Concurrent retries must observe the winning transaction rather than fail the unique
        // constraint.
        if (guardianLockMapper.lockStudent(student.getId(), orgId) == null) {
            throw new GenericException(CrmHttpResultCode.FORBIDDEN);
        }
        List<EducationGuardian> existing =
                guardianLockMapper.selectLinkForUpdate(student.getId(), contact.getId(), orgId);
        if (!existing.isEmpty()) {
            // Retrying a link is safe; changing a relationship needs a separate audited operation.
            EducationGuardian guardian = existing.getFirst();
            if (!guardian.getRelationship().equals(request.relationship().trim())) {
                throw new GenericException(
                        CrmHttpResultCode.VALIDATE_FAILED,
                        Translator.get("education.guardian.relationship.conflict"));
            }
            return response(guardian);
        }

        EducationGuardian guardian = new EducationGuardian();
        initialize(guardian, actorId);
        guardian.setOrganizationId(orgId);
        guardian.setStudentId(student.getId());
        guardian.setCustomerId(student.getCustomerId());
        guardian.setContactId(contact.getId());
        guardian.setRelationship(request.relationship().trim());
        guardianMapper.insert(guardian);
        return response(guardian);
    }

    private EducationStudent requireStudent(
            String id, String permission, String actorId, String orgId) {
        EducationStudent student = studentMapper.selectByPrimaryKey(id);
        if (student == null || orgId == null || !orgId.equals(student.getOrganizationId())) {
            throw new GenericException(CrmHttpResultCode.FORBIDDEN);
        }
        familyAccess.check(student.getCustomerId(), permission, actorId, orgId);
        return student;
    }

    private void initialize(BaseModel record, String actorId) {
        record.setId(nextId());
        long now = System.currentTimeMillis();
        record.setCreateTime(now);
        record.setUpdateTime(now);
        record.setCreateUser(actorId);
        record.setUpdateUser(actorId);
    }

    protected String nextId() {
        return IDGenerator.nextStr();
    }

    private StudentResponse response(EducationStudent student) {
        return new StudentResponse(
                student.getId(), student.getCustomerId(), student.getName(), student.getGrade());
    }

    private GuardianResponse response(EducationGuardian guardian) {
        return new GuardianResponse(
                guardian.getId(),
                guardian.getStudentId(),
                guardian.getContactId(),
                guardian.getRelationship());
    }
}
