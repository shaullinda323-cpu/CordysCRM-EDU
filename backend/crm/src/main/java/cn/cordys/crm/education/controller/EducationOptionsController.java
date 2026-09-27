package cn.cordys.crm.education.controller;

import cn.cordys.context.OrganizationContext;
import cn.cordys.crm.education.dto.response.*;
import cn.cordys.crm.education.service.EducationOptionsService;
import cn.cordys.security.SessionUtils;

import jakarta.annotation.Resource;
import jakarta.validation.constraints.*;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Validated
@RequestMapping("/education/options")
public class EducationOptionsController {
    @Resource private EducationOptionsService service;

    @GetMapping("/teachers")
    public List<EducationPersonOption> teachers() {
        return service.teachers(OrganizationContext.getOrganizationId());
    }

    @GetMapping("/recipients")
    public List<EducationPersonOption> recipients(
            @RequestParam @NotBlank @Size(max = 32) String enrollmentId) {
        return service.recipients(
                enrollmentId, SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }

    @GetMapping("/order-rows")
    public List<OrderRowOption> rows(@RequestParam @NotBlank @Size(max = 32) String customerId) {
        return service.rows(
                customerId, SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }

    @GetMapping("/receipts")
    public List<ReceiptOption> receipts(
            @RequestParam @NotBlank @Size(max = 32) String enrollmentId) {
        return service.receipts(
                enrollmentId, SessionUtils.getUserId(), OrganizationContext.getOrganizationId());
    }
}
