package cn.cordys.crm.education.controller;

import cn.cordys.context.OrganizationContext;
import cn.cordys.crm.education.dto.request.*;
import cn.cordys.crm.education.dto.response.EnrollmentResponse;
import cn.cordys.crm.education.service.EducationEnrollmentService;
import cn.cordys.security.SessionUtils;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Validated
@RequestMapping("/education/enrollments")
public class EducationEnrollmentController {
    @Resource private EducationEnrollmentService service;

    @GetMapping
    public List<EnrollmentResponse> list(@RequestParam @NotBlank @Size(max = 32) String studentId) {
        return service.list(
                studentId, SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }

    @PostMapping
    public EnrollmentResponse create(@Valid @RequestBody EnrollmentCreateRequest request) {
        return service.create(
                request, SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }

    @PostMapping("/{id}/payments")
    public EnrollmentResponse allocate(
            @PathVariable @NotBlank @Size(max = 32) String id,
            @Valid @RequestBody PaymentAllocationRequest request) {
        return service.allocate(
                id, request, SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }

    @PostMapping("/{id}/handoff")
    public EnrollmentResponse handoff(@PathVariable @NotBlank @Size(max = 32) String id) {
        return service.handoff(
                id, SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }
}
