package cn.cordys.crm.education.service;

import cn.cordys.common.constants.PermissionConstants;
import cn.cordys.common.domain.BaseModel;
import cn.cordys.common.exception.GenericException;
import cn.cordys.common.permission.ResourcePermissionService;
import cn.cordys.common.response.result.CrmHttpResultCode;
import cn.cordys.common.uid.IDGenerator;
import cn.cordys.common.util.JSON;
import cn.cordys.common.util.Translator;
import cn.cordys.crm.education.domain.*;
import cn.cordys.crm.education.dto.request.*;
import cn.cordys.crm.education.dto.response.*;
import cn.cordys.crm.education.mapper.ExtEducationTeachingMapper;
import cn.cordys.crm.system.domain.OrganizationUser;
import cn.cordys.mybatis.BaseMapper;

import jakarta.annotation.Resource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/** Timetable writes and credit effects share an organization lock and one database transaction. */
@Service
@Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
public class EducationTeachingService {
    @Resource private BaseMapper<EducationClass> classes;
    @Resource private BaseMapper<EducationStudent> students;
    @Resource private BaseMapper<EducationFeedback> feedbacks;
    @Resource private BaseMapper<EducationMembership> memberships;
    @Resource private BaseMapper<EducationEnrollment> enrollments;
    @Resource private BaseMapper<EducationSession> sessions;
    @Resource private BaseMapper<EducationSessionStudent> sessionStudents;
    @Resource private BaseMapper<EducationAttendance> attendances;
    @Resource private BaseMapper<EducationAttendanceEvent> events;
    @Resource private BaseMapper<EducationCreditEntry> credits;
    @Resource private BaseMapper<OrganizationUser> members;
    @Resource private EducationFamilyAccessService families;
    @Resource private ResourcePermissionService permissions;
    @Resource private ExtEducationTeachingMapper queries;

    public ClassResponse createClass(ClassCreateRequest request, String actor, String org) {
        manager(actor, org);
        EducationClass group = new EducationClass();
        initialize(group, actor);
        group.setOrganizationId(org);
        group.setName(request.name().trim());
        group.setMode(request.mode());
        classes.insert(group);
        return new ClassResponse(group.getId(), group.getName(), group.getMode());
    }

    @Transactional(readOnly = true)
    public List<ClassResponse> classes(String actor, String org) {
        manager(actor, org);
        EducationClass filter = new EducationClass();
        filter.setOrganizationId(org);
        return classes.select(filter).stream()
                .filter(g -> "admin".equals(actor) || actor.equals(g.getCreateUser()))
                .map(g -> new ClassResponse(g.getId(), g.getName(), g.getMode()))
                .toList();
    }

    public void join(String classId, MembershipCreateRequest request, String actor, String org) {
        manager(actor, org);
        lock(org);
        EducationClass group = group(classId, actor, org);
        EducationEnrollment enrollment = enrollment(request.enrollmentId(), org);
        families.check(
                enrollment.getCustomerId(),
                PermissionConstants.CUSTOMER_MANAGEMENT_UPDATE,
                actor,
                org);
        if (!"ACTIVE".equals(enrollment.getStatus())
                || !group.getMode().equals(enrollment.getMode())
                || (request.effectiveTo() != null
                        && request.effectiveTo() <= request.effectiveFrom()))
            fail("education.teaching.membership");
        EducationMembership filter = new EducationMembership();
        filter.setOrganizationId(org);
        filter.setClassId(classId);
        filter.setEnrollmentId(enrollment.getId());
        List<EducationMembership> prior = memberships.select(filter);
        if (!prior.isEmpty()) {
            if (!prior.getFirst().getEffectiveFrom().equals(request.effectiveFrom())
                    || !Objects.equals(prior.getFirst().getEffectiveTo(), request.effectiveTo()))
                fail("education.request.conflict");
            return;
        }
        EducationMembership member = new EducationMembership();
        initialize(member, actor);
        member.setOrganizationId(org);
        member.setClassId(classId);
        member.setEnrollmentId(enrollment.getId());
        member.setEffectiveFrom(request.effectiveFrom());
        member.setEffectiveTo(request.effectiveTo());
        memberships.insert(member);
        // Existing dated sessions deliberately retain their original roster; joining affects newly
        // scheduled sessions.
    }

    public SessionResponse schedule(SessionCreateRequest request, String actor, String org) {
        manager(actor, org);
        lock(org);
        EducationClass group = group(request.classId(), actor, org);
        activeMember(request.teacherId(), org);
        long duration = request.endsAt() - request.startsAt();
        if (duration <= 0 || duration > 86400000 || duration % 900000 != 0)
            fail("education.teaching.time");
        EducationSession requestFilter = new EducationSession();
        requestFilter.setOrganizationId(org);
        requestFilter.setRequestKey(request.requestKey());
        List<EducationSession> existing = sessions.select(requestFilter);
        if (!existing.isEmpty()) {
            EducationSession s = existing.getFirst();
            if (!s.getClassId().equals(request.classId())
                    || !s.getTeacherId().equals(request.teacherId())
                    || !s.getRoomKey().equals(request.roomKey())
                    || !s.getStartsAt().equals(request.startsAt())
                    || !s.getEndsAt().equals(request.endsAt())
                    || !Objects.equals(s.getMakeupForSessionId(), request.makeupForSessionId()))
                fail("education.request.conflict");
            return sessionResponse(s);
        }
        List<EducationEnrollment> roster = queries.roster(org, group.getId(), request.startsAt());
        if (request.makeupForSessionId() != null) {
            EducationSession source = session(request.makeupForSessionId(), org);
            if (!source.getClassId().equals(group.getId())
                    || !"SCHEDULED".equals(source.getStatus())
                    || request.startsAt() < source.getEndsAt()) fail("education.teaching.makeup");
            roster =
                    roster.stream()
                            .filter(
                                    e -> {
                                        EducationAttendance attendance =
                                                attendance(source.getId(), e.getId(), org);
                                        return attendance != null
                                                && !"PRESENT".equals(attendance.getStatus())
                                                && queries.makeupClaims(
                                                                org, source.getId(), e.getId())
                                                        == 0;
                                    })
                            .toList();
        }
        if (roster.isEmpty()) fail("education.teaching.empty");
        for (EducationEnrollment e : roster) {
            families.check(
                    e.getCustomerId(), PermissionConstants.CUSTOMER_MANAGEMENT_UPDATE, actor, org);
            if (!"ACTIVE".equals(e.getStatus())) fail("education.teaching.membership");
        }
        List<String> students = roster.stream().map(EducationEnrollment::getStudentId).toList();
        if (new HashSet<>(students).size() != students.size()
                || queries.conflicts(
                                org,
                                request.teacherId(),
                                request.roomKey(),
                                request.startsAt(),
                                request.endsAt(),
                                students)
                        > 0) fail("education.teaching.conflict");
        EducationSession session = new EducationSession();
        initialize(session, actor);
        session.setOrganizationId(org);
        session.setClassId(group.getId());
        session.setRequestKey(request.requestKey());
        session.setTeacherId(request.teacherId());
        session.setRoomKey(request.roomKey());
        session.setStartsAt(request.startsAt());
        session.setEndsAt(request.endsAt());
        session.setMakeupForSessionId(request.makeupForSessionId());
        session.setStatus("SCHEDULED");
        sessions.insert(session);
        for (EducationEnrollment e : roster) {
            EducationSessionStudent attendee = new EducationSessionStudent();
            initialize(attendee, actor);
            attendee.setOrganizationId(org);
            attendee.setSessionId(session.getId());
            attendee.setEnrollmentId(e.getId());
            attendee.setStudentId(e.getStudentId());
            sessionStudents.insert(attendee);
        }
        return sessionResponse(session);
    }

    public void cancel(String id, String actor, String org) {
        manager(actor, org);
        lock(org);
        EducationSession session = session(id, org);
        group(session.getClassId(), actor, org);
        if ("CANCELLED".equals(session.getStatus())) return;
        EducationAttendance filter = new EducationAttendance();
        filter.setOrganizationId(org);
        filter.setSessionId(id);
        if (!attendances.select(filter).isEmpty()) fail("education.teaching.recorded");
        session.setStatus("CANCELLED");
        session.setUpdateUser(actor);
        session.setUpdateTime(System.currentTimeMillis());
        sessions.update(session);
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> sessions(String actor, String org) {
        organizationActor(actor, org);
        EducationSession filter = new EducationSession();
        filter.setOrganizationId(org);
        return sessions.select(filter).stream()
                .filter(
                        s ->
                                "admin".equals(actor)
                                        || actor.equals(s.getTeacherId())
                                        || actor.equals(s.getCreateUser()))
                .map(this::sessionResponse)
                .toList();
    }

    public AttendanceResponse record(
            String sessionId,
            String enrollmentId,
            AttendanceRequest request,
            String actor,
            String org) {
        organizationActor(actor, org);
        lock(org);
        EducationSession session = session(sessionId, org);
        EducationEnrollment enrollment = enrollment(enrollmentId, org);
        if (!"SCHEDULED".equals(session.getStatus())
                || session.getStartsAt() > System.currentTimeMillis())
            fail("education.teaching.not.started");
        if (!actor.equals(session.getTeacherId())) {
            manager(actor, org);
            group(session.getClassId(), actor, org);
            families.check(
                    enrollment.getCustomerId(),
                    PermissionConstants.CUSTOMER_MANAGEMENT_UPDATE,
                    actor,
                    org);
        }
        if (roster(sessionId, org).stream()
                .noneMatch(s -> enrollmentId.equals(s.getEnrollmentId()))) forbidden();
        String payload = JSON.toJSONString(List.of(sessionId, enrollmentId, request));
        EducationAttendanceEvent eventFilter = new EducationAttendanceEvent();
        eventFilter.setOrganizationId(org);
        eventFilter.setRequestKey(request.requestKey());
        List<EducationAttendanceEvent> prior = events.select(eventFilter);
        EducationAttendance attendance = attendance(sessionId, enrollmentId, org);
        if (!prior.isEmpty()) {
            if (!prior.getFirst().getRequestPayload().equals(payload))
                fail("education.request.conflict");
            return attendanceResponse(attendance);
        }
        int revision = attendance == null ? 0 : attendance.getRevision();
        if (request.expectedRevision() != revision) fail("education.request.conflict");
        if ("PRESENT".equals(request.status())
                && queries.makeupClaims(org, sessionId, enrollmentId) > 0)
            fail("education.teaching.makeup");
        BigDecimal previous = attendance == null ? BigDecimal.ZERO : attendance.getChargedUnits();
        BigDecimal charge = BigDecimal.ZERO;
        if ("PRESENT".equals(request.status())) {
            charge =
                    "EVENT".equals(enrollment.getMode())
                            ? BigDecimal.ONE
                            : BigDecimal.valueOf(session.getEndsAt() - session.getStartsAt())
                                    .divide(BigDecimal.valueOf(3600000));
        }
        if (queries.balance(org, enrollmentId).add(previous).subtract(charge).signum() < 0)
            fail("education.teaching.balance");
        String previousStatus = attendance == null ? null : attendance.getStatus();
        if (attendance == null) {
            attendance = new EducationAttendance();
            initialize(attendance, actor);
            attendance.setOrganizationId(org);
            attendance.setSessionId(sessionId);
            attendance.setEnrollmentId(enrollmentId);
        }
        attendance.setStatus(request.status());
        attendance.setChargedUnits(charge);
        attendance.setRevision(revision + 1);
        attendance.setUpdateUser(actor);
        attendance.setUpdateTime(System.currentTimeMillis());
        if (revision == 0) attendances.insert(attendance);
        else attendances.update(attendance);
        EducationAttendanceEvent event = new EducationAttendanceEvent();
        initialize(event, actor);
        event.setOrganizationId(org);
        event.setAttendanceId(attendance.getId());
        event.setRequestKey(request.requestKey());
        event.setRequestPayload(payload);
        event.setRevision(revision + 1);
        event.setPreviousStatus(previousStatus);
        event.setStatus(request.status());
        event.setReason(request.reason().trim());
        events.insert(event);
        // Corrections append a reversal and a new debit; no historical credit entry is edited or
        // deleted.
        if (previous.signum() > 0)
            entry(
                    enrollmentId,
                    sessionId,
                    event.getId() + ":reverse",
                    "REVERSAL",
                    previous,
                    actor,
                    org);
        if (charge.signum() > 0)
            entry(
                    enrollmentId,
                    sessionId,
                    event.getId() + ":debit",
                    "DEBIT",
                    charge.negate(),
                    actor,
                    org);
        return attendanceResponse(attendance);
    }

    @Transactional(readOnly = true)
    public List<CreditEntryResponse> ledger(String enrollmentId, String actor, String org) {
        EducationEnrollment enrollment = enrollment(enrollmentId, org);
        families.check(
                enrollment.getCustomerId(),
                PermissionConstants.CUSTOMER_MANAGEMENT_READ,
                actor,
                org);
        EducationCreditEntry filter = new EducationCreditEntry();
        filter.setOrganizationId(org);
        filter.setEnrollmentId(enrollmentId);
        return credits.select(filter).stream()
                .map(
                        e ->
                                new CreditEntryResponse(
                                        e.getId(),
                                        e.getSessionId(),
                                        e.getKind(),
                                        e.getUnits(),
                                        e.getCreateTime(),
                                        e.getCreateUser()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SessionParticipantResponse> participants(String id, String actor, String org) {
        organizationActor(actor, org);
        EducationSession session = session(id, org);
        if (!actor.equals(session.getTeacherId())) {
            manager(actor, org);
            group(session.getClassId(), actor, org);
        }
        return roster(id, org).stream()
                .map(
                        member -> {
                            EducationEnrollment enrollment =
                                    enrollment(member.getEnrollmentId(), org);
                            if (!actor.equals(session.getTeacherId()))
                                families.check(
                                        enrollment.getCustomerId(),
                                        PermissionConstants.CUSTOMER_MANAGEMENT_READ,
                                        actor,
                                        org);
                            EducationStudent student =
                                    students.selectByPrimaryKey(enrollment.getStudentId());
                            EducationAttendance attendance =
                                    attendance(id, enrollment.getId(), org);
                            return new SessionParticipantResponse(
                                    enrollment.getId(),
                                    student.getName(),
                                    enrollment.getTitle(),
                                    enrollment.getMode(),
                                    queries.balance(org, enrollment.getId()),
                                    attendance == null ? null : attendance.getStatus(),
                                    attendance == null ? 0 : attendance.getRevision());
                        })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TeachingQueueResponse> queue(String actor, String org) {
        List<TeachingQueueResponse> result = new ArrayList<>();
        for (SessionResponse session : sessions(actor, org)) {
            if (!"SCHEDULED".equals(session.status())) continue;
            int missingAttendance = 0, missingFeedback = 0, pendingSend = 0;
            for (String enrollment : session.enrollmentIds()) {
                EducationAttendance attendance = attendance(session.id(), enrollment, org);
                if (attendance == null) {
                    if (session.startsAt() <= System.currentTimeMillis()) missingAttendance++;
                    continue;
                }
                EducationFeedback filter = new EducationFeedback();
                filter.setOrganizationId(org);
                filter.setSessionId(session.id());
                filter.setEnrollmentId(enrollment);
                EducationFeedback latest =
                        feedbacks.select(filter).stream()
                                .max(Comparator.comparing(EducationFeedback::getRevision))
                                .orElse(null);
                if (latest == null || "DRAFT".equals(latest.getStatus())) missingFeedback++;
                else if ("CONFIRMED".equals(latest.getStatus())) pendingSend++;
            }
            if (session.endsAt() > System.currentTimeMillis()
                    || missingAttendance + missingFeedback + pendingSend > 0) {
                EducationClass group = classes.selectByPrimaryKey(session.classId());
                result.add(
                        new TeachingQueueResponse(
                                session.id(),
                                group.getName(),
                                session.startsAt(),
                                session.endsAt(),
                                session.enrollmentIds().size(),
                                missingAttendance,
                                missingFeedback,
                                pendingSend));
            }
        }
        return result.stream()
                .sorted(Comparator.comparing(TeachingQueueResponse::startsAt))
                .toList();
    }

    private void entry(
            String enrollment,
            String session,
            String operation,
            String kind,
            BigDecimal units,
            String actor,
            String org) {
        EducationCreditEntry entry = new EducationCreditEntry();
        initialize(entry, actor);
        entry.setOrganizationId(org);
        entry.setEnrollmentId(enrollment);
        entry.setSessionId(session);
        entry.setOperationKey(operation);
        entry.setKind(kind);
        entry.setUnits(units);
        credits.insert(entry);
    }

    private EducationAttendance attendance(String session, String enrollment, String org) {
        EducationAttendance filter = new EducationAttendance();
        filter.setOrganizationId(org);
        filter.setSessionId(session);
        filter.setEnrollmentId(enrollment);
        return attendances.select(filter).stream().findFirst().orElse(null);
    }

    private AttendanceResponse attendanceResponse(EducationAttendance a) {
        return new AttendanceResponse(
                a.getId(),
                a.getEnrollmentId(),
                a.getSessionId(),
                a.getStatus(),
                a.getRevision(),
                a.getChargedUnits(),
                queries.balance(a.getOrganizationId(), a.getEnrollmentId()));
    }

    private SessionResponse sessionResponse(EducationSession s) {
        return new SessionResponse(
                s.getId(),
                s.getClassId(),
                s.getTeacherId(),
                s.getRoomKey(),
                s.getStartsAt(),
                s.getEndsAt(),
                s.getStatus(),
                s.getMakeupForSessionId(),
                roster(s.getId(), s.getOrganizationId()).stream()
                        .map(EducationSessionStudent::getEnrollmentId)
                        .toList());
    }

    private List<EducationSessionStudent> roster(String session, String org) {
        EducationSessionStudent filter = new EducationSessionStudent();
        filter.setOrganizationId(org);
        filter.setSessionId(session);
        return sessionStudents.select(filter);
    }

    private EducationClass group(String id, String actor, String org) {
        EducationClass group = classes.selectByPrimaryKey(id);
        if (group == null
                || !Objects.equals(org, group.getOrganizationId())
                || (!"admin".equals(actor) && !actor.equals(group.getCreateUser()))) forbidden();
        return group;
    }

    private EducationSession session(String id, String org) {
        EducationSession session = sessions.selectByPrimaryKey(id);
        if (session == null || !Objects.equals(org, session.getOrganizationId())) forbidden();
        return session;
    }

    private EducationEnrollment enrollment(String id, String org) {
        EducationEnrollment enrollment = enrollments.selectByPrimaryKey(id);
        if (enrollment == null || !Objects.equals(org, enrollment.getOrganizationId())) forbidden();
        return enrollment;
    }

    private void manager(String actor, String org) {
        organizationActor(actor, org);
        permissions.checkPermission(PermissionConstants.CUSTOMER_MANAGEMENT_UPDATE);
    }

    private void organizationActor(String actor, String org) {
        if (org == null || actor == null || queries.organizationExists(org) == 0) forbidden();
        if (!"admin".equals(actor)) activeMember(actor, org);
    }

    private void activeMember(String user, String org) {
        OrganizationUser filter = new OrganizationUser();
        filter.setUserId(user);
        filter.setOrganizationId(org);
        filter.setEnable(true);
        if (members.select(filter).isEmpty()) forbidden();
    }

    private void lock(String org) {
        queries.ensureLock(org);
        queries.lockOrganization(org);
    }

    private void initialize(BaseModel record, String actor) {
        record.setId(IDGenerator.nextStr());
        record.setCreateUser(actor);
        record.setUpdateUser(actor);
        record.setCreateTime(System.currentTimeMillis());
        record.setUpdateTime(record.getCreateTime());
    }

    private void forbidden() {
        throw new GenericException(CrmHttpResultCode.FORBIDDEN);
    }

    private void fail(String key) {
        throw new GenericException(CrmHttpResultCode.VALIDATE_FAILED, Translator.get(key));
    }
}
