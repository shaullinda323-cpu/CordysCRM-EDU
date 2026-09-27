package cn.cordys.crm.education.service;

import cn.cordys.common.constants.PermissionConstants;
import cn.cordys.common.exception.GenericException;
import cn.cordys.common.response.result.CrmHttpResultCode;
import cn.cordys.common.uid.IDGenerator;
import cn.cordys.common.util.Translator;
import cn.cordys.crm.customer.domain.CustomerContact;
import cn.cordys.crm.education.domain.*;
import cn.cordys.crm.education.dto.request.*;
import cn.cordys.crm.education.dto.response.FeedbackResponse;
import cn.cordys.crm.education.mapper.ExtEducationTeachingMapper;
import cn.cordys.mybatis.BaseMapper;

import jakarta.annotation.Resource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
public class EducationFeedbackService {
    @Resource private BaseMapper<EducationFeedback> feedbacks;
    @Resource private BaseMapper<EducationAttendance> attendances;
    @Resource private BaseMapper<EducationEnrollment> enrollments;
    @Resource private BaseMapper<EducationGuardian> guardians;
    @Resource private BaseMapper<CustomerContact> contacts;
    @Resource private EducationTeachingService teaching;
    @Resource private EducationFamilyAccessService families;
    @Resource private ExtEducationTeachingMapper locks;

    @Transactional(readOnly = true)
    public List<FeedbackResponse> list(
            String session, String enrollment, String actor, String org) {
        authorize(session, enrollment, actor, org);
        return history(session, enrollment, org).stream()
                .sorted(Comparator.comparing(EducationFeedback::getRevision).reversed())
                .map(this::response)
                .toList();
    }

    public FeedbackResponse draft(FeedbackCreateRequest request, String actor, String org) {
        authorize(request.sessionId(), request.enrollmentId(), actor, org);
        lock(org);
        EducationAttendance attendance = new EducationAttendance();
        attendance.setOrganizationId(org);
        attendance.setSessionId(request.sessionId());
        attendance.setEnrollmentId(request.enrollmentId());
        if (attendances.select(attendance).isEmpty()) fail("education.feedback.attendance");
        EducationFeedback key = new EducationFeedback();
        key.setOrganizationId(org);
        key.setRequestKey(request.requestKey());
        List<EducationFeedback> retry = feedbacks.select(key);
        if (!retry.isEmpty()) {
            EducationFeedback prior = retry.getFirst();
            if (!prior.getSessionId().equals(request.sessionId())
                    || !prior.getEnrollmentId().equals(request.enrollmentId())
                    || !Objects.equals(prior.getPreviousId(), request.previousId())
                    || !prior.getContent().equals(request.content().trim()))
                fail("education.request.conflict");
            return response(prior);
        }
        EducationFeedback latest = latest(request.sessionId(), request.enrollmentId(), org);
        if (!Objects.equals(latest == null ? null : latest.getId(), request.previousId()))
            fail("education.request.conflict");
        EducationFeedback feedback = new EducationFeedback();
        feedback.setId(IDGenerator.nextStr());
        feedback.setOrganizationId(org);
        feedback.setSessionId(request.sessionId());
        feedback.setEnrollmentId(request.enrollmentId());
        feedback.setRequestKey(request.requestKey());
        feedback.setPreviousId(request.previousId());
        feedback.setRevision(latest == null ? 1 : latest.getRevision() + 1);
        feedback.setContent(request.content().trim());
        feedback.setStatus("DRAFT");
        feedback.setCreateUser(actor);
        feedback.setUpdateUser(actor);
        feedback.setCreateTime(System.currentTimeMillis());
        feedback.setUpdateTime(feedback.getCreateTime());
        feedbacks.insert(feedback);
        return response(feedback);
    }

    public FeedbackResponse confirm(String id, String actor, String org) {
        EducationFeedback feedback = get(id, actor, org);
        lock(org);
        feedback = feedbacks.selectByPrimaryKey(id);
        if (!latest(feedback.getSessionId(), feedback.getEnrollmentId(), org).getId().equals(id))
            fail("education.request.conflict");
        if (!"DRAFT".equals(feedback.getStatus())) return response(feedback);
        feedback.setStatus("CONFIRMED");
        feedback.setConfirmedUser(actor);
        feedback.setConfirmedTime(System.currentTimeMillis());
        feedback.setUpdateUser(actor);
        feedback.setUpdateTime(feedback.getConfirmedTime());
        feedbacks.update(feedback);
        return response(feedback);
    }

    public FeedbackResponse reportSent(
            String id, FeedbackSendRequest request, String actor, String org) {
        EducationFeedback feedback = get(id, actor, org);
        lock(org);
        feedback = feedbacks.selectByPrimaryKey(id);
        EducationEnrollment enrollment = enrollments.selectByPrimaryKey(feedback.getEnrollmentId());
        families.check(
                enrollment.getCustomerId(),
                PermissionConstants.CUSTOMER_MANAGEMENT_UPDATE,
                actor,
                org);
        if ("MANUAL_REPORTED".equals(feedback.getStatus())) {
            if (!Objects.equals(feedback.getRecipientContactId(), request.recipientContactId())
                    || !Objects.equals(feedback.getSentTime(), request.sentTime())
                    || !Objects.equals(feedback.getSendEvidence(), request.sendEvidence().trim())
                    || !Objects.equals(
                            feedback.getConsentReference(), request.consentReference().trim()))
                fail("education.request.conflict");
            return response(feedback);
        }
        if (!"CONFIRMED".equals(feedback.getStatus())
                || !latest(feedback.getSessionId(), feedback.getEnrollmentId(), org)
                        .getId()
                        .equals(id)
                || request.sentTime() > System.currentTimeMillis()
                || request.sentTime() < feedback.getConfirmedTime())
            fail("education.feedback.send");
        CustomerContact contact = contacts.selectByPrimaryKey(request.recipientContactId());
        if (contact == null
                || !org.equals(contact.getOrganizationId())
                || !enrollment.getCustomerId().equals(contact.getCustomerId())
                || !Boolean.TRUE.equals(contact.getEnable())) forbidden();
        EducationGuardian guardian = new EducationGuardian();
        guardian.setOrganizationId(org);
        guardian.setStudentId(enrollment.getStudentId());
        guardian.setContactId(contact.getId());
        if (guardians.select(guardian).isEmpty()) forbidden();
        feedback.setStatus("MANUAL_REPORTED");
        feedback.setRecipientContactId(contact.getId());
        feedback.setSentTime(request.sentTime());
        feedback.setSendEvidence(request.sendEvidence().trim());
        feedback.setConsentReference(request.consentReference().trim());
        feedback.setUpdateUser(actor);
        feedback.setUpdateTime(System.currentTimeMillis());
        feedbacks.update(feedback);
        return response(feedback);
    }

    private EducationFeedback get(String id, String actor, String org) {
        EducationFeedback feedback = feedbacks.selectByPrimaryKey(id);
        if (feedback == null || !Objects.equals(org, feedback.getOrganizationId())) forbidden();
        authorize(feedback.getSessionId(), feedback.getEnrollmentId(), actor, org);
        return feedback;
    }

    private void authorize(String session, String enrollment, String actor, String org) {
        if (teaching.participants(session, actor, org).stream()
                .noneMatch(p -> p.enrollmentId().equals(enrollment))) forbidden();
    }

    private List<EducationFeedback> history(String session, String enrollment, String org) {
        EducationFeedback filter = new EducationFeedback();
        filter.setOrganizationId(org);
        filter.setSessionId(session);
        filter.setEnrollmentId(enrollment);
        return feedbacks.select(filter);
    }

    private EducationFeedback latest(String session, String enrollment, String org) {
        return history(session, enrollment, org).stream()
                .max(Comparator.comparing(EducationFeedback::getRevision))
                .orElse(null);
    }

    private FeedbackResponse response(EducationFeedback f) {
        return new FeedbackResponse(
                f.getId(),
                f.getSessionId(),
                f.getEnrollmentId(),
                f.getRevision(),
                f.getContent(),
                f.getStatus(),
                f.getConfirmedUser(),
                f.getConfirmedTime(),
                f.getRecipientContactId(),
                f.getSentTime(),
                f.getSendEvidence(),
                "UNVERIFIED");
    }

    private void lock(String org) {
        locks.ensureLock(org);
        locks.lockOrganization(org);
    }

    private void forbidden() {
        throw new GenericException(CrmHttpResultCode.FORBIDDEN);
    }

    private void fail(String key) {
        throw new GenericException(CrmHttpResultCode.VALIDATE_FAILED, Translator.get(key));
    }
}
