package cn.cordys.crm.education.service;

import cn.cordys.common.constants.PermissionConstants;
import cn.cordys.common.exception.GenericException;
import cn.cordys.common.permission.ResourcePermissionService;
import cn.cordys.common.response.result.CrmHttpResultCode;
import cn.cordys.common.service.DataScopeService;
import cn.cordys.common.util.JSON;
import cn.cordys.crm.contract.domain.ContractPaymentRecord;
import cn.cordys.crm.education.domain.EducationEnrollment;
import cn.cordys.crm.education.dto.response.*;
import cn.cordys.crm.education.mapper.ExtEducationEnrollmentMapper;
import cn.cordys.crm.order.domain.Order;
import cn.cordys.crm.order.domain.OrderSnapshot;
import cn.cordys.crm.order.dto.response.OrderGetResponse;
import cn.cordys.crm.system.dto.field.ProductSubField;
import cn.cordys.crm.system.dto.response.ModuleFormConfigDTO;
import cn.cordys.mybatis.BaseMapper;

import jakarta.annotation.Resource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional(readOnly = true)
public class EducationOptionsService {
    @Resource private BaseMapper<Order> orders;
    @Resource private BaseMapper<cn.cordys.crm.system.domain.OrganizationUser> members;
    @Resource private BaseMapper<cn.cordys.crm.system.domain.User> users;
    @Resource private BaseMapper<cn.cordys.crm.education.domain.EducationGuardian> guardians;
    @Resource private BaseMapper<cn.cordys.crm.customer.domain.CustomerContact> contacts;
    @Resource private BaseMapper<OrderSnapshot> snapshots;
    @Resource private BaseMapper<EducationEnrollment> enrollments;
    @Resource private BaseMapper<ContractPaymentRecord> receipts;
    @Resource private EducationOrderSnapshotService adapter;
    @Resource private EducationFamilyAccessService families;
    @Resource private ResourcePermissionService permissions;
    @Resource private DataScopeService scope;
    @Resource private ExtEducationEnrollmentMapper allocation;

    public List<EducationPersonOption> teachers(String org) {
        permissions.checkPermission(PermissionConstants.CUSTOMER_MANAGEMENT_UPDATE);
        var filter = new cn.cordys.crm.system.domain.OrganizationUser();
        filter.setOrganizationId(org);
        filter.setEnable(true);
        return members.select(filter).stream()
                .map(m -> users.selectByPrimaryKey(m.getUserId()))
                .filter(Objects::nonNull)
                .map(u -> new EducationPersonOption(u.getId(), u.getName()))
                .distinct()
                .toList();
    }

    public List<EducationPersonOption> recipients(String enrollmentId, String actor, String org) {
        EducationEnrollment enrollment = enrollments.selectByPrimaryKey(enrollmentId);
        if (enrollment == null || !Objects.equals(org, enrollment.getOrganizationId()))
            throw new GenericException(CrmHttpResultCode.FORBIDDEN);
        families.check(
                enrollment.getCustomerId(),
                PermissionConstants.CUSTOMER_MANAGEMENT_UPDATE,
                actor,
                org);
        var filter = new cn.cordys.crm.education.domain.EducationGuardian();
        filter.setOrganizationId(org);
        filter.setStudentId(enrollment.getStudentId());
        return guardians.select(filter).stream()
                .map(g -> contacts.selectByPrimaryKey(g.getContactId()))
                .filter(
                        c ->
                                c != null
                                        && Boolean.TRUE.equals(c.getEnable())
                                        && org.equals(c.getOrganizationId())
                                        && enrollment.getCustomerId().equals(c.getCustomerId()))
                .map(c -> new EducationPersonOption(c.getId(), c.getName()))
                .toList();
    }

    public List<OrderRowOption> rows(String customer, String actor, String org) {
        families.check(customer, PermissionConstants.CUSTOMER_MANAGEMENT_READ, actor, org);
        permissions.checkPermission(PermissionConstants.ORDER_READ);
        Order filter = new Order();
        filter.setOrganizationId(org);
        filter.setCustomerId(customer);
        List<OrderRowOption> result = new ArrayList<>();
        for (Order order : orders.select(filter)) {
            if (!scope.hasDataPermission(
                    actor, org, order.getOwner(), PermissionConstants.ORDER_READ)) continue;
            OrderSnapshot sf = new OrderSnapshot();
            sf.setOrderId(order.getId());
            OrderSnapshot snapshot = snapshots.select(sf).stream().findFirst().orElse(null);
            if (snapshot == null) continue;
            ModuleFormConfigDTO config =
                    JSON.parseObject(snapshot.getOrderProp(), ModuleFormConfigDTO.class);
            OrderGetResponse values =
                    JSON.parseObject(snapshot.getOrderValue(), OrderGetResponse.class);
            if (config == null
                    || config.getFields() == null
                    || values == null
                    || values.getModuleFields() == null) continue;
            for (var field : config.getFields()) {
                if (!(field instanceof ProductSubField)) continue;
                Object rows =
                        values.getModuleFields().stream()
                                .filter(v -> field.getId().equals(v.getFieldId()))
                                .map(v -> v.getFieldValue())
                                .filter(Objects::nonNull)
                                .findFirst()
                                .orElse(null);
                if (!(rows instanceof List<?> list)) continue;
                int number = 0;
                for (Object value : list) {
                    number++;
                    if (!(value instanceof Map<?, ?> row)
                            || !(row.get("id") instanceof String rowId)) continue;
                    EducationEnrollment used = new EducationEnrollment();
                    used.setOrganizationId(org);
                    used.setOrderId(order.getId());
                    used.setSubFieldId(field.getId());
                    used.setOrderRowId(rowId);
                    if (!enrollments.select(used).isEmpty()) continue;
                    try {
                        var verified =
                                adapter.extract(
                                        order,
                                        config,
                                        values.getModuleFields(),
                                        field.getId(),
                                        rowId);
                        result.add(
                                new OrderRowOption(
                                        order.getId(),
                                        order.getName(),
                                        field.getId(),
                                        rowId,
                                        number,
                                        verified.amount()));
                    } catch (GenericException invalidRow) {
                        // Unusable product rows are not selectable; create validates the persisted
                        // row again.
                    }
                }
            }
        }
        return result;
    }

    public List<ReceiptOption> receipts(String enrollmentId, String actor, String org) {
        EducationEnrollment enrollment = enrollments.selectByPrimaryKey(enrollmentId);
        if (enrollment == null || !Objects.equals(org, enrollment.getOrganizationId()))
            throw new GenericException(CrmHttpResultCode.FORBIDDEN);
        families.check(
                enrollment.getCustomerId(),
                PermissionConstants.CUSTOMER_MANAGEMENT_UPDATE,
                actor,
                org);
        permissions.checkPermission(PermissionConstants.CONTRACT_PAYMENT_RECORD_READ);
        if (enrollment.getContractId() == null) return List.of();
        ContractPaymentRecord filter = new ContractPaymentRecord();
        filter.setOrganizationId(org);
        filter.setContractId(enrollment.getContractId());
        return receipts.select(filter).stream()
                .filter(
                        r ->
                                r.getRecordAmount() != null
                                        && r.getRecordEndTime() != null
                                        && r.getRecordEndTime() <= System.currentTimeMillis())
                .filter(
                        r ->
                                scope.hasDataPermission(
                                        actor,
                                        org,
                                        r.getOwner(),
                                        PermissionConstants.CONTRACT_PAYMENT_RECORD_READ))
                .map(
                        r ->
                                new ReceiptOption(
                                        r.getId(),
                                        r.getName(),
                                        r.getRecordAmount()
                                                .subtract(allocation.receiptAllocated(r.getId()))))
                .filter(r -> r.availableAmount().signum() > 0)
                .toList();
    }
}
