package cn.cordys.crm.education.service;

import cn.cordys.common.constants.PermissionConstants;
import cn.cordys.common.domain.BaseModel;
import cn.cordys.common.exception.GenericException;
import cn.cordys.common.permission.ResourcePermissionService;
import cn.cordys.common.response.result.CrmHttpResultCode;
import cn.cordys.common.service.DataScopeService;
import cn.cordys.common.uid.IDGenerator;
import cn.cordys.common.util.Translator;
import cn.cordys.crm.contract.domain.Contract;
import cn.cordys.crm.contract.domain.ContractPaymentRecord;
import cn.cordys.crm.education.domain.*;
import cn.cordys.crm.education.dto.request.*;
import cn.cordys.crm.education.dto.response.EnrollmentResponse;
import cn.cordys.crm.education.mapper.*;
import cn.cordys.crm.order.domain.Order;
import cn.cordys.mybatis.BaseMapper;

import jakarta.annotation.Resource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Separate enrollment, receipt allocation and teaching release; amounts never imply approval. */
@Service
@Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
public class EducationEnrollmentService {
    @Resource private BaseMapper<EducationEnrollment> enrollments;
    @Resource private BaseMapper<EducationStudent> students;
    @Resource private BaseMapper<EducationPaymentAllocation> allocations;
    @Resource private BaseMapper<Contract> contracts;
    @Resource private BaseMapper<Order> orders;
    @Resource private BaseMapper<EducationCreditEntry> credits;
    @Resource private ExtEducationGuardianMapper studentLocks;
    @Resource private ExtEducationEnrollmentMapper locks;
    @Resource private ExtEducationOrderMapper orderLocks;
    @Resource private EducationFamilyAccessService access;
    @Resource private EducationOrderSnapshotService snapshots;
    @Resource private ResourcePermissionService permissions;
    @Resource private DataScopeService dataScope;

    @Transactional(readOnly = true)
    public List<EnrollmentResponse> list(String studentId, String actor, String org) {
        student(studentId, actor, org, PermissionConstants.CUSTOMER_MANAGEMENT_READ);
        permissions.checkPermission(PermissionConstants.ORDER_READ);
        EducationEnrollment filter = new EducationEnrollment();
        filter.setStudentId(studentId);
        filter.setOrganizationId(org);
        return enrollments.select(filter).stream()
                .filter(
                        e -> {
                            Order order = orders.selectByPrimaryKey(e.getOrderId());
                            return order != null
                                    && org.equals(order.getOrganizationId())
                                    && dataScope.hasDataPermission(
                                            actor,
                                            org,
                                            order.getOwner(),
                                            PermissionConstants.ORDER_READ);
                        })
                .map(this::response)
                .toList();
    }

    public EnrollmentResponse create(EnrollmentCreateRequest request, String actor, String org) {
        EducationStudent student =
                student(
                        request.studentId(),
                        actor,
                        org,
                        PermissionConstants.CUSTOMER_MANAGEMENT_UPDATE);
        if ("EVENT".equals(request.mode()) && request.totalUnits().compareTo(BigDecimal.ONE) != 0)
            fail("education.enrollment.event.units");
        if (request.renewalFromId() != null) {
            EducationEnrollment source = enrollments.selectByPrimaryKey(request.renewalFromId());
            if (source == null
                    || !org.equals(source.getOrganizationId())
                    || !student.getId().equals(source.getStudentId())
                    || !"ACTIVE".equals(source.getStatus())
                    || source.getOrderId().equals(request.orderId())
                    || !source.getMode().equals(request.mode())) fail("education.renewal.source");
        }
        var row =
                snapshots.capture(
                        request.orderId(),
                        student.getCustomerId(),
                        request.subFieldId(),
                        request.orderRowId(),
                        actor,
                        org);
        // The native order lock serializes all allocations of a row; one row is one child's
        // enrollment.
        if (studentLocks.lockStudent(student.getId(), org) == null) forbidden();
        EducationEnrollment prior = locks.findRequest(student.getId(), request.requestKey(), org);
        if (prior != null) {
            if (!prior.getOrderId().equals(request.orderId())
                    || !prior.getSubFieldId().equals(request.subFieldId())
                    || !prior.getOrderRowId().equals(request.orderRowId())
                    || !prior.getTitle().equals(request.title().trim())
                    || !Objects.equals(prior.getRenewalFromId(), request.renewalFromId())
                    || !prior.getMode().equals(request.mode())
                    || prior.getTotalUnits().compareTo(request.totalUnits()) != 0) {
                fail("education.request.conflict");
            }
            return response(prior);
        }
        if (locks.findRow(request.orderId(), request.subFieldId(), request.orderRowId(), org)
                != null) fail("education.enrollment.row.used");
        EducationEnrollment enrollment = new EducationEnrollment();
        initialize(enrollment, actor);
        enrollment.setOrganizationId(org);
        enrollment.setCustomerId(student.getCustomerId());
        enrollment.setStudentId(student.getId());
        enrollment.setOrderId(row.orderId());
        enrollment.setSubFieldId(row.subFieldId());
        enrollment.setOrderRowId(row.rowId());
        enrollment.setRequestKey(request.requestKey());
        enrollment.setRenewalFromId(request.renewalFromId());
        enrollment.setTitle(request.title().trim());
        enrollment.setMode(request.mode());
        enrollment.setTotalUnits(request.totalUnits());
        enrollment.setAmount(row.amount());
        enrollment.setContractId(row.contractId());
        enrollment.setSellerId(row.sellerId());
        enrollment.setRowSnapshot(row.rowJson());
        enrollment.setStatus("PENDING");
        enrollments.insert(enrollment);
        return response(enrollment);
    }

    public EnrollmentResponse allocate(
            String id, PaymentAllocationRequest request, String actor, String org) {
        EducationEnrollment enrollment = lock(id, actor, org);
        permissions.checkPermission(PermissionConstants.CONTRACT_PAYMENT_RECORD_READ);
        ContractPaymentRecord receipt = locks.lockReceipt(request.receiptId(), org);
        if (receipt == null
                || !Objects.equals(enrollment.getContractId(), receipt.getContractId())
                || receipt.getContractId() == null
                || receipt.getRecordAmount() == null
                || receipt.getRecordEndTime() == null
                || receipt.getRecordEndTime() > System.currentTimeMillis()
                || !dataScope.hasDataPermission(
                        actor,
                        org,
                        receipt.getOwner(),
                        PermissionConstants.CONTRACT_PAYMENT_RECORD_READ)) {
            forbidden();
        }
        Contract contract = contracts.selectByPrimaryKey(receipt.getContractId());
        if (contract == null
                || !org.equals(contract.getOrganizationId())
                || !enrollment.getCustomerId().equals(contract.getCustomerId())) forbidden();
        EducationPaymentAllocation filter = new EducationPaymentAllocation();
        filter.setEnrollmentId(id);
        filter.setReceiptId(receipt.getId());
        List<EducationPaymentAllocation> existing = allocations.select(filter);
        if (!existing.isEmpty()) {
            if (existing.getFirst().getAmount().compareTo(request.amount()) != 0)
                fail("education.request.conflict");
            return response(enrollment);
        }
        if (!"PENDING".equals(enrollment.getStatus())) fail("education.enrollment.active");
        // READ_COMMITTED sees preceding allocations after the receipt lock, including those for
        // another order.
        if (locks.receiptAllocated(receipt.getId())
                                .add(request.amount())
                                .compareTo(receipt.getRecordAmount())
                        > 0
                || locks.allocated(id).add(request.amount()).compareTo(enrollment.getAmount()) > 0)
            fail("education.payment.excess");
        EducationPaymentAllocation allocation = new EducationPaymentAllocation();
        initialize(allocation, actor);
        allocation.setOrganizationId(org);
        allocation.setEnrollmentId(id);
        allocation.setReceiptId(receipt.getId());
        allocation.setContractId(receipt.getContractId());
        allocation.setReceiptTotal(receipt.getRecordAmount());
        allocation.setAmount(request.amount());
        allocations.insert(allocation);
        return response(enrollment);
    }

    public EnrollmentResponse handoff(String id, String actor, String org) {
        EducationEnrollment enrollment = lock(id, actor, org);
        if ("ACTIVE".equals(enrollment.getStatus())) return response(enrollment);
        var row =
                snapshots.capture(
                        enrollment.getOrderId(),
                        enrollment.getCustomerId(),
                        enrollment.getSubFieldId(),
                        enrollment.getOrderRowId(),
                        actor,
                        org);
        if (!"APPROVED".equals(row.approvalStatus())
                && !"AUTO_APPROVED".equals(row.approvalStatus()))
            fail("education.order.not.approved");
        if (row.amount().compareTo(enrollment.getAmount()) != 0
                || !Objects.equals(row.contractId(), enrollment.getContractId())
                || !Objects.equals(row.rowJson(), enrollment.getRowSnapshot()))
            fail("education.order.changed");
        if (locks.allocated(id).compareTo(enrollment.getAmount()) < 0)
            fail("education.payment.required");
        enrollment.setStatus("ACTIVE");
        enrollment.setHandoffUser(actor);
        enrollment.setHandoffTime(System.currentTimeMillis());
        enrollment.setUpdateUser(actor);
        enrollment.setUpdateTime(enrollment.getHandoffTime());
        enrollments.update(enrollment);
        EducationCreditEntry grant = new EducationCreditEntry();
        initialize(grant, actor);
        grant.setOrganizationId(org);
        grant.setEnrollmentId(id);
        grant.setOperationKey("grant:" + id);
        grant.setKind("GRANT");
        grant.setUnits(enrollment.getTotalUnits());
        credits.insert(grant);
        return response(enrollment);
    }

    private EducationEnrollment lock(String id, String actor, String org) {
        EducationEnrollment existing = enrollments.selectByPrimaryKey(id);
        if (existing == null || !Objects.equals(org, existing.getOrganizationId())) forbidden();
        access.check(
                existing.getCustomerId(),
                PermissionConstants.CUSTOMER_MANAGEMENT_UPDATE,
                actor,
                org);
        // Always order before enrollment, matching create/capture and avoiding reverse-order
        // deadlocks.
        Order order = orderLocks.lockOrder(existing.getOrderId(), org);
        if (order == null || !existing.getCustomerId().equals(order.getCustomerId())) forbidden();
        return locks.lock(id, org);
    }

    private EducationStudent student(String id, String actor, String org, String permission) {
        EducationStudent student = students.selectByPrimaryKey(id);
        if (student == null || org == null || !org.equals(student.getOrganizationId())) forbidden();
        access.check(student.getCustomerId(), permission, actor, org);
        return student;
    }

    private EnrollmentResponse response(EducationEnrollment e) {
        return new EnrollmentResponse(
                e.getId(),
                e.getStudentId(),
                e.getOrderId(),
                e.getTitle(),
                e.getMode(),
                e.getTotalUnits(),
                e.getAmount(),
                locks.allocated(e.getId()),
                e.getStatus(),
                e.getHandoffUser(),
                e.getHandoffTime(),
                e.getRenewalFromId());
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
