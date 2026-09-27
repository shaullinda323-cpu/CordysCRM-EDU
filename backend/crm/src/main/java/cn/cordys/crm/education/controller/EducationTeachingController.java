package cn.cordys.crm.education.controller;

import cn.cordys.context.OrganizationContext;
import cn.cordys.crm.education.dto.request.*;
import cn.cordys.crm.education.dto.response.*;
import cn.cordys.crm.education.service.EducationTeachingService;
import cn.cordys.security.SessionUtils;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Validated
@RequestMapping("/education/teaching")
public class EducationTeachingController {
    @Resource private EducationTeachingService service;

    @GetMapping("/queue")
    public List<TeachingQueueResponse> queue() {
        return service.queue(SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }

    @GetMapping("/classes")
    public List<ClassResponse> classes() {
        return service.classes(SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }

    @PostMapping("/classes")
    public ClassResponse create(@Valid @RequestBody ClassCreateRequest request) {
        return service.createClass(
                request, SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }

    @PostMapping("/classes/{id}/members")
    public void join(
            @PathVariable @NotBlank @Size(max = 32) String id,
            @Valid @RequestBody MembershipCreateRequest request) {
        service.join(
                id, request, SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }

    @GetMapping("/sessions")
    public List<SessionResponse> sessions() {
        return service.sessions(SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }

    @PostMapping("/sessions")
    public SessionResponse schedule(@Valid @RequestBody SessionCreateRequest request) {
        return service.schedule(
                request, SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }

    @PostMapping("/sessions/{id}/cancel")
    public void cancel(@PathVariable @NotBlank @Size(max = 32) String id) {
        service.cancel(id, SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }

    @PostMapping("/sessions/{sessionId}/attendance/{enrollmentId}")
    public AttendanceResponse record(
            @PathVariable @NotBlank @Size(max = 32) String sessionId,
            @PathVariable @NotBlank @Size(max = 32) String enrollmentId,
            @Valid @RequestBody AttendanceRequest request) {
        return service.record(
                sessionId,
                enrollmentId,
                request,
                SessionUtils.getUserId(),
                OrganizationContext.getOrganizationId());
    }

    @GetMapping("/enrollments/{id}/ledger")
    public List<CreditEntryResponse> ledger(@PathVariable @NotBlank @Size(max = 32) String id) {
        return service.ledger(
                id, SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }

    @GetMapping("/sessions/{id}/participants")
    public List<SessionParticipantResponse> participants(
            @PathVariable @NotBlank @Size(max = 32) String id) {
        return service.participants(
                id, SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }
}
