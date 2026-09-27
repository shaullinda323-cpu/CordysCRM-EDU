package cn.cordys.crm.education.controller;

import cn.cordys.context.OrganizationContext;
import cn.cordys.crm.education.dto.request.*;
import cn.cordys.crm.education.dto.response.TrialResponse;
import cn.cordys.crm.education.service.EducationTrialService;
import cn.cordys.security.SessionUtils;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Validated
@RequestMapping("/education/trials")
public class EducationTrialController {
    @Resource private EducationTrialService service;

    @GetMapping
    public List<TrialResponse> list(@RequestParam @NotBlank @Size(max = 32) String studentId) {
        return service.list(
                studentId, SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }

    @PostMapping
    public TrialResponse book(@Valid @RequestBody TrialCreateRequest request) {
        return service.book(
                request, SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }

    @PostMapping("/{id}/result")
    public TrialResponse result(
            @PathVariable @NotBlank @Size(max = 32) String id,
            @Valid @RequestBody TrialResultRequest request) {
        return service.result(
                id, request, SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }
}
