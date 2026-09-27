package cn.cordys.crm.education.service;

import cn.cordys.common.constants.PermissionConstants;
import cn.cordys.common.exception.GenericException;
import cn.cordys.common.response.result.CrmHttpResultCode;
import cn.cordys.common.uid.IDGenerator;
import cn.cordys.common.util.Translator;
import cn.cordys.crm.education.domain.*;
import cn.cordys.crm.education.dto.request.*;
import cn.cordys.crm.education.dto.response.TrialResponse;
import cn.cordys.crm.education.mapper.*;
import cn.cordys.mybatis.BaseMapper;

import jakarta.annotation.Resource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@Transactional(rollbackFor = Exception.class)
public class EducationTrialService {
    @Resource private BaseMapper<EducationTrial> trialMapper;
    @Resource private BaseMapper<EducationStudent> studentMapper;
    @Resource private EducationFamilyAccessService access;
    @Resource private ExtEducationGuardianMapper studentLocks;
    @Resource private ExtEducationTrialMapper trialLocks;

    @Transactional(readOnly = true)
    public List<TrialResponse> list(String studentId, String actor, String org) {
        checkStudent(studentId, actor, org, PermissionConstants.CUSTOMER_MANAGEMENT_READ);
        EducationTrial filter = new EducationTrial();
        filter.setOrganizationId(org);
        filter.setStudentId(studentId);
        return trialMapper.select(filter).stream().map(this::response).toList();
    }

    public TrialResponse book(TrialCreateRequest request, String actor, String org) {
        checkStudent(
                request.studentId(), actor, org, PermissionConstants.CUSTOMER_MANAGEMENT_UPDATE);
        if (request.endsAt() <= request.startsAt()) fail("education.trial.invalid.time");
        if (studentLocks.lockStudent(request.studentId(), org) == null) forbidden();
        EducationTrial existing =
                trialLocks.findRequest(request.studentId(), request.requestKey(), org);
        if (existing != null) {
            if (!existing.getTopic().equals(request.topic().trim())
                    || !existing.getStartsAt().equals(request.startsAt())
                    || !existing.getEndsAt().equals(request.endsAt())) {
                fail("education.request.conflict");
            }
            return response(existing);
        }
        EducationTrial trial = new EducationTrial();
        trial.setId(IDGenerator.nextStr());
        trial.setOrganizationId(org);
        trial.setStudentId(request.studentId());
        trial.setRequestKey(request.requestKey());
        trial.setTopic(request.topic().trim());
        trial.setStartsAt(request.startsAt());
        trial.setEndsAt(request.endsAt());
        trial.setStatus("BOOKED");
        trial.setCreateUser(actor);
        trial.setUpdateUser(actor);
        trial.setCreateTime(System.currentTimeMillis());
        trial.setUpdateTime(trial.getCreateTime());
        trialMapper.insert(trial);
        return response(trial);
    }

    public TrialResponse result(String id, TrialResultRequest request, String actor, String org) {
        EducationTrial trial = trialLocks.lock(id, org);
        if (trial == null) forbidden();
        checkStudent(
                trial.getStudentId(), actor, org, PermissionConstants.CUSTOMER_MANAGEMENT_UPDATE);
        if (!"BOOKED".equals(trial.getStatus())) {
            if (!trial.getStatus().equals(request.status())
                    || !Objects.equals(trial.getOutcome(), request.outcome().trim())) {
                fail("education.request.conflict");
            }
            return response(trial);
        }
        if ("ATTENDED".equals(request.status())
                && trial.getStartsAt() > System.currentTimeMillis()) {
            fail("education.trial.not.started");
        }
        trial.setStatus(request.status());
        trial.setOutcome(request.outcome().trim());
        trial.setUpdateUser(actor);
        trial.setUpdateTime(System.currentTimeMillis());
        trialMapper.update(trial);
        return response(trial);
    }

    private void checkStudent(String id, String actor, String org, String permission) {
        EducationStudent student = studentMapper.selectByPrimaryKey(id);
        if (student == null || org == null || !org.equals(student.getOrganizationId())) forbidden();
        access.check(student.getCustomerId(), permission, actor, org);
    }

    private TrialResponse response(EducationTrial trial) {
        return new TrialResponse(
                trial.getId(),
                trial.getStudentId(),
                trial.getTopic(),
                trial.getStartsAt(),
                trial.getEndsAt(),
                trial.getStatus(),
                trial.getOutcome());
    }

    private void forbidden() {
        throw new GenericException(CrmHttpResultCode.FORBIDDEN);
    }

    private void fail(String key) {
        throw new GenericException(CrmHttpResultCode.VALIDATE_FAILED, Translator.get(key));
    }
}
