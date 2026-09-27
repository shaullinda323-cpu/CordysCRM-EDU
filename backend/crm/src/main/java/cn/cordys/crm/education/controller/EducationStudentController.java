package cn.cordys.crm.education.controller;

import cn.cordys.context.OrganizationContext;
import cn.cordys.crm.education.dto.request.GuardianLinkRequest;
import cn.cordys.crm.education.dto.request.StudentCreateRequest;
import cn.cordys.crm.education.dto.response.GuardianResponse;
import cn.cordys.crm.education.dto.response.StudentResponse;
import cn.cordys.crm.education.service.EducationStudentService;
import cn.cordys.security.SessionUtils;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Staff-facing family records. The service applies CRM data scope to every operation. */
@RestController
@Validated
@RequestMapping("/education/students")
public class EducationStudentController {
    @Resource private EducationStudentService students;

    @GetMapping
    public List<StudentResponse> list(@RequestParam @NotBlank @Size(max = 32) String customerId) {
        return students.list(
                customerId, SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }

    @PostMapping
    public StudentResponse create(@Valid @RequestBody StudentCreateRequest request) {
        return students.create(
                request, SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }

    @GetMapping("/{studentId}/guardians")
    public List<GuardianResponse> guardians(
            @PathVariable @NotBlank @Size(max = 32) String studentId) {
        return students.guardians(
                studentId, SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }

    @PostMapping("/guardians")
    public GuardianResponse linkGuardian(@Valid @RequestBody GuardianLinkRequest request) {
        return students.linkGuardian(
                request, SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }
}
