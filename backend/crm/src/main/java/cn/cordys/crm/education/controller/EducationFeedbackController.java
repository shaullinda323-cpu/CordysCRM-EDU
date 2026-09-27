package cn.cordys.crm.education.controller;

import cn.cordys.context.OrganizationContext;
import cn.cordys.crm.education.dto.request.*;
import cn.cordys.crm.education.dto.response.FeedbackResponse;
import cn.cordys.crm.education.service.EducationFeedbackService;
import cn.cordys.security.SessionUtils;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Validated
@RequestMapping("/education/feedback")
public class EducationFeedbackController {
    @Resource private EducationFeedbackService service;

    @GetMapping
    public List<FeedbackResponse> list(
            @RequestParam @NotBlank @Size(max = 32) String sessionId,
            @RequestParam @NotBlank @Size(max = 32) String enrollmentId) {
        return service.list(
                sessionId,
                enrollmentId,
                SessionUtils.getUserId(),
                OrganizationContext.getOrganizationId());
    }

    @PostMapping
    public FeedbackResponse draft(@Valid @RequestBody FeedbackCreateRequest request) {
        return service.draft(
                request, SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }

    @PostMapping("/{id}/confirm")
    public FeedbackResponse confirm(@PathVariable @NotBlank @Size(max = 32) String id) {
        return service.confirm(
                id, SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }

    @PostMapping("/{id}/report-sent")
    public FeedbackResponse report(
            @PathVariable @NotBlank @Size(max = 32) String id,
            @Valid @RequestBody FeedbackSendRequest request) {
        return service.reportSent(
                id, request, SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }
}
