package cn.cordys.crm.education.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import cn.cordys.common.domain.BaseModel;
import cn.cordys.crm.base.BaseTest;
import cn.cordys.crm.contract.domain.Contract;
import cn.cordys.crm.contract.domain.ContractPaymentRecord;
import cn.cordys.crm.education.dto.request.*;
import cn.cordys.crm.order.domain.Order;
import cn.cordys.crm.order.domain.OrderField;
import cn.cordys.crm.order.domain.OrderSnapshot;
import cn.cordys.mybatis.BaseMapper;

import com.jayway.jsonpath.JsonPath;

import jakarta.annotation.Resource;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.*;

/** Synthetic native CRM records exercise actual enrollment transactions and receipt constraints. */
@Sql(scripts = {"/education/cleanup_family_test.sql", "/education/init_family_test.sql"})
@Sql(
        scripts = "/education/cleanup_family_test.sql",
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class EducationEnrollmentControllerTest extends BaseTest {
    @Resource private BaseMapper<Contract> contracts;
    @Resource private BaseMapper<Order> orders;
    @Resource private BaseMapper<OrderField> fields;
    @Resource private BaseMapper<OrderSnapshot> snapshots;
    @Resource private BaseMapper<ContractPaymentRecord> receipts;
    @Resource private JdbcTemplate jdbc;
    private static final String PATH = "/education/enrollments";

    @Test
    void approvalAndReceiptAreSeparateHandoffRequirements() throws Exception {
        seedOrder();
        String student = child();
        var create = request(student, "row-1");
        String id =
                id(requestPostWithOk(PATH, create).andReturn().getResponse().getContentAsString());
        requestPostWithOk(PATH, create).andExpect(jsonPath("$.data.id").value(id));
        requestPost(PATH + "/" + id + "/handoff", Map.of()).andExpect(status().isBadRequest());
        jdbc.update("UPDATE sales_order SET approval_status='APPROVED' WHERE id='edu-test-order'");
        requestPost(PATH + "/" + id + "/handoff", Map.of()).andExpect(status().isBadRequest());
        var allocation = new PaymentAllocationRequest("edu-test-receipt", new BigDecimal("100.00"));
        requestPostWithOk(PATH + "/" + id + "/payments", allocation)
                .andExpect(jsonPath("$.data.allocatedAmount").value(100.0));
        requestPostWithOk(PATH + "/" + id + "/payments", allocation);
        jdbc.update("UPDATE sales_order SET approval_status='APPROVING' WHERE id='edu-test-order'");
        requestPost(PATH + "/" + id + "/handoff", Map.of()).andExpect(status().isBadRequest());
        jdbc.update("UPDATE sales_order SET approval_status='APPROVED' WHERE id='edu-test-order'");
        requestPostWithOk(PATH + "/" + id + "/handoff", Map.of())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        requestPostWithOk(PATH + "/" + id + "/handoff", Map.of())
                .andExpect(jsonPath("$.data.handoffUser").value("admin"));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM edu_payment_allocation WHERE enrollment_id=?",
                        Integer.class,
                        id));
        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        jdbc.update(
                                "UPDATE contract_payment_record SET record_amount=1 WHERE"
                                    + " id='edu-test-receipt'"));
        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        jdbc.update(
                                "DELETE FROM contract_payment_record WHERE id='edu-test-receipt'"));
    }

    @Test
    void concurrentAllocationsCannotSpendOneReceiptTwice() throws Exception {
        seedOrder();
        String student = child();
        String first =
                id(
                        requestPostWithOk(PATH, request(student, "row-1"))
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        String second =
                id(
                        requestPostWithOk(PATH, request(student, "row-2"))
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> one =
                    () -> {
                        start.await();
                        return requestPost(
                                        PATH + "/" + first + "/payments",
                                        new PaymentAllocationRequest(
                                                "edu-test-receipt", new BigDecimal("100")))
                                .andReturn()
                                .getResponse()
                                .getStatus();
                    };
            Callable<Integer> two =
                    () -> {
                        start.await();
                        return requestPost(
                                        PATH + "/" + second + "/payments",
                                        new PaymentAllocationRequest(
                                                "edu-test-receipt", new BigDecimal("100")))
                                .andReturn()
                                .getResponse()
                                .getStatus();
                    };
            Future<Integer> a = executor.submit(one), b = executor.submit(two);
            start.countDown();
            int statusA = a.get(30, TimeUnit.SECONDS), statusB = b.get(30, TimeUnit.SECONDS);
            assertTrue((statusA == 200 && statusB == 400) || (statusA == 400 && statusB == 200));
        }
        assertEquals(
                0,
                new BigDecimal("100")
                        .compareTo(
                                jdbc.queryForObject(
                                        "SELECT SUM(amount) FROM edu_payment_allocation WHERE"
                                            + " receipt_id='edu-test-receipt'",
                                        BigDecimal.class)));
    }

    @Test
    void oneNativeRowCannotSilentlyEnrollAnotherChild() throws Exception {
        seedOrder();
        requestPostWithOk(PATH, request(child(), "row-1"));
        requestPost(PATH, request(child(), "row-1")).andExpect(status().isBadRequest());
    }

    @Test
    void attendanceCorrectionAndMakeupPreserveAnAppendOnlyLedger() throws Exception {
        String enrollment = activeEnrollment("HOURS", new BigDecimal("6"));
        String group = createGroup(enrollment, "HOURS");
        long start = System.currentTimeMillis() - 14400000;
        String session = schedule(group, "lesson-1", start, null);
        String attendancePath =
                "/education/teaching/sessions/" + session + "/attendance/" + enrollment;
        var present =
                new AttendanceRequest("present-1", 0, "PRESENT", "Synthetic initial attendance");
        requestPostWithOk(attendancePath, present).andExpect(jsonPath("$.data.balance").value(5.0));
        requestPostWithOk(attendancePath, present).andExpect(jsonPath("$.data.balance").value(5.0));
        requestPost(attendancePath, new AttendanceRequest("stale", 0, "LEAVE", "Stale correction"))
                .andExpect(status().isBadRequest());
        requestPostWithOk(
                        attendancePath,
                        new AttendanceRequest(
                                "correct-1", 1, "LEAVE", "Synthetic correction after review"))
                .andExpect(jsonPath("$.data.balance").value(6.0));
        String makeup = schedule(group, "makeup-1", start + 7200000, session);
        requestPost(
                        attendancePath,
                        new AttendanceRequest(
                                "bad-correction", 2, "PRESENT", "Cannot charge source and makeup"))
                .andExpect(status().isBadRequest());
        requestPostWithOk(
                        "/education/teaching/sessions/" + makeup + "/attendance/" + enrollment,
                        new AttendanceRequest(
                                "makeup-present", 0, "PRESENT", "Synthetic makeup completed"))
                .andExpect(jsonPath("$.data.balance").value(5.0));
        assertEquals(
                4,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM edu_credit_entry WHERE enrollment_id=?",
                        Integer.class,
                        enrollment));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM edu_credit_entry WHERE enrollment_id=? AND"
                            + " kind='REVERSAL'",
                        Integer.class,
                        enrollment));
    }

    @Test
    void eventEntitlementCannotBeConsumedTwice() throws Exception {
        String enrollment = activeEnrollment("EVENT", BigDecimal.ONE);
        String group = createGroup(enrollment, "EVENT");
        long start = System.currentTimeMillis() - 14400000;
        String first = schedule(group, "event-1", start, null);
        String second = schedule(group, "event-2", start + 3600000, null);
        requestPostWithOk(
                        "/education/teaching/sessions/" + first + "/attendance/" + enrollment,
                        new AttendanceRequest(
                                "event-present", 0, "PRESENT", "Synthetic event participation"))
                .andExpect(jsonPath("$.data.balance").value(0.0));
        requestPost(
                        "/education/teaching/sessions/" + second + "/attendance/" + enrollment,
                        new AttendanceRequest(
                                "event-again", 0, "PRESENT", "Rejected duplicate entitlement use"))
                .andExpect(status().isBadRequest());
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM edu_attendance WHERE session_id=?",
                        Integer.class,
                        second));
    }

    @Test
    void concurrentSchedulesCannotOverlapForOneStudent() throws Exception {
        String enrollment = activeEnrollment("HOURS", new BigDecimal("6"));
        String first = createGroup(enrollment, "HOURS"), second = createGroup(enrollment, "HOURS");
        long start = System.currentTimeMillis() + 86400000;
        CountDownLatch gate = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> a =
                    executor.submit(
                            () -> {
                                gate.await();
                                return requestPost(
                                                "/education/teaching/sessions",
                                                new SessionCreateRequest(
                                                        first,
                                                        "conflict-1",
                                                        "permission_test",
                                                        "room-1",
                                                        start,
                                                        start + 3600000,
                                                        null))
                                        .andReturn()
                                        .getResponse()
                                        .getStatus();
                            });
            Future<Integer> b =
                    executor.submit(
                            () -> {
                                gate.await();
                                return requestPost(
                                                "/education/teaching/sessions",
                                                new SessionCreateRequest(
                                                        second,
                                                        "conflict-2",
                                                        "permission_test",
                                                        "room-2",
                                                        start,
                                                        start + 3600000,
                                                        null))
                                        .andReturn()
                                        .getResponse()
                                        .getStatus();
                            });
            gate.countDown();
            int statusA = a.get(30, TimeUnit.SECONDS), statusB = b.get(30, TimeUnit.SECONDS);
            assertTrue((statusA == 200 && statusB == 400) || (statusA == 400 && statusB == 200));
        }
    }

    @Test
    void feedbackNeedsHumanConfirmationAndManualReportDoesNotClaimDelivery() throws Exception {
        String enrollment = activeEnrollment("HOURS", new BigDecimal("6"));
        String group = createGroup(enrollment, "HOURS");
        String session =
                schedule(group, "feedback-lesson", System.currentTimeMillis() - 7200000, null);
        requestPostWithOk(
                "/education/teaching/sessions/" + session + "/attendance/" + enrollment,
                new AttendanceRequest("feedback-attendance", 0, "PRESENT", "Synthetic attendance"));
        String student =
                jdbc.queryForObject(
                        "SELECT student_id FROM edu_enrollment WHERE id=?",
                        String.class,
                        enrollment);
        requestPostWithOk(
                "/education/students/guardians",
                new GuardianLinkRequest(student, "edu-test-parent", "Parent"));
        String feedback =
                id(
                        requestPostWithOk(
                                        "/education/feedback",
                                        new FeedbackCreateRequest(
                                                session,
                                                enrollment,
                                                "feedback-draft",
                                                null,
                                                "Synthetic learning feedback"))
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        requestPost(
                        "/education/feedback/" + feedback + "/report-sent",
                        new FeedbackSendRequest(
                                "edu-test-parent",
                                System.currentTimeMillis(),
                                "Synthetic manual record",
                                "Synthetic consent reference"))
                .andExpect(status().isBadRequest());
        requestPostWithOk("/education/feedback/" + feedback + "/confirm", Map.of());
        var sent =
                new FeedbackSendRequest(
                        "edu-test-parent",
                        System.currentTimeMillis(),
                        "Synthetic manual record",
                        "Synthetic consent reference");
        requestPostWithOk("/education/feedback/" + feedback + "/report-sent", sent)
                .andExpect(jsonPath("$.data.status").value("MANUAL_REPORTED"))
                .andExpect(jsonPath("$.data.deliveryStatus").value("UNVERIFIED"));
        requestPostWithOk("/education/feedback/" + feedback + "/report-sent", sent);
        requestPostWithOk(
                "/education/feedback",
                new FeedbackCreateRequest(
                        session,
                        enrollment,
                        "feedback-revision",
                        feedback,
                        "Revised synthetic learning feedback"));
        requestPost("/education/feedback/" + feedback + "/confirm", Map.of())
                .andExpect(status().isBadRequest());
        assertEquals(
                2,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM edu_feedback WHERE enrollment_id=?",
                        Integer.class,
                        enrollment));
    }

    @Test
    void renewalCreatesNewCreditsAndPreservesOriginalHistory() throws Exception {
        String original = activeEnrollment("HOURS", new BigDecimal("6"));
        String student =
                jdbc.queryForObject(
                        "SELECT student_id FROM edu_enrollment WHERE id=?", String.class, original);
        requestPost(
                        PATH,
                        new EnrollmentCreateRequest(
                                student,
                                "edu-test-order",
                                "products",
                                "row-2",
                                "bad-renewal",
                                "Synthetic Renewal",
                                "HOURS",
                                new BigDecimal("6"),
                                original))
                .andExpect(status().isBadRequest());
        Order renewalOrder = orders.selectByPrimaryKey("edu-test-order");
        renewalOrder.setId("edu-test-renewal-order");
        renewalOrder.setNumber("EDU-SYNTHETIC-RENEWAL");
        orders.insert(renewalOrder);
        OrderField row = fields.selectByPrimaryKey("edu-test-row-1");
        row.setId("edu-test-renewal-row");
        row.setResourceId(renewalOrder.getId());
        fields.insert(row);
        OrderSnapshot snapshot = snapshots.selectByPrimaryKey("edu-test-snapshot");
        snapshot.setId("edu-test-renewal-snapshot");
        snapshot.setOrderId(renewalOrder.getId());
        snapshots.insert(snapshot);
        ContractPaymentRecord receipt = receipts.selectByPrimaryKey("edu-test-receipt");
        receipt.setId("edu-test-renewal-receipt");
        receipts.insert(receipt);
        String renewal =
                id(
                        requestPostWithOk(
                                        PATH,
                                        new EnrollmentCreateRequest(
                                                student,
                                                renewalOrder.getId(),
                                                "products",
                                                "row-1",
                                                "renewal",
                                                "Synthetic Renewal",
                                                "HOURS",
                                                new BigDecimal("6"),
                                                original))
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        requestPostWithOk(
                PATH + "/" + renewal + "/payments",
                new PaymentAllocationRequest(receipt.getId(), new BigDecimal("100")));
        requestPostWithOk(PATH + "/" + renewal + "/handoff", Map.of())
                .andExpect(jsonPath("$.data.renewalFromId").value(original));
        for (String enrollment : java.util.List.of(original, renewal)) {
            assertEquals(
                    0,
                    new BigDecimal("6")
                            .compareTo(
                                    jdbc.queryForObject(
                                            "SELECT SUM(units) FROM edu_credit_entry WHERE"
                                                + " enrollment_id=?",
                                            BigDecimal.class,
                                            enrollment)));
        }
        assertNotEquals(original, renewal);
    }

    @Test
    void assignedTeacherCanTeachWithoutAccessToFamilyFinancialRecords() throws Exception {
        String enrollment = activeEnrollment("HOURS", new BigDecimal("6"));
        String group = createGroup(enrollment, "HOURS");
        String session =
                schedule(group, "teacher-scope", System.currentTimeMillis() - 7200000, null);
        var teacherRequest =
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                                "/education/teaching/sessions/" + session + "/participants")
                        .header(
                                cn.cordys.security.SessionConstants.HEADER_TOKEN,
                                permissionAuthInfo.getSessionId())
                        .header(
                                cn.cordys.security.SessionConstants.CSRF_TOKEN,
                                permissionAuthInfo.getCsrfToken())
                        .header("Organization-Id", DEFAULT_ORGANIZATION_ID);
        mockMvc.perform(teacherRequest)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].studentName").value("Synthetic Learner"))
                .andExpect(jsonPath("$.data[0].amount").doesNotExist())
                .andExpect(jsonPath("$.data[0].phone").doesNotExist());
        String student =
                jdbc.queryForObject(
                        "SELECT student_id FROM edu_enrollment WHERE id=?",
                        String.class,
                        enrollment);
        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                                        PATH)
                                .param("studentId", student)
                                .header(
                                        cn.cordys.security.SessionConstants.HEADER_TOKEN,
                                        permissionAuthInfo.getSessionId())
                                .header(
                                        cn.cordys.security.SessionConstants.CSRF_TOKEN,
                                        permissionAuthInfo.getCsrfToken())
                                .header("Organization-Id", DEFAULT_ORGANIZATION_ID))
                .andExpect(status().isForbidden());
    }

    private String activeEnrollment(String mode, BigDecimal units) throws Exception {
        seedOrder();
        String enrollment =
                id(
                        requestPostWithOk(
                                        PATH,
                                        new EnrollmentCreateRequest(
                                                child(),
                                                "edu-test-order",
                                                "products",
                                                "row-1",
                                                "teaching-enrollment",
                                                "Synthetic Course",
                                                mode,
                                                units))
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        jdbc.update("UPDATE sales_order SET approval_status='APPROVED' WHERE id='edu-test-order'");
        requestPostWithOk(
                PATH + "/" + enrollment + "/payments",
                new PaymentAllocationRequest("edu-test-receipt", new BigDecimal("100")));
        requestPostWithOk(PATH + "/" + enrollment + "/handoff", Map.of());
        return enrollment;
    }

    private String createGroup(String enrollment, String mode) throws Exception {
        String group =
                id(
                        requestPostWithOk(
                                        "/education/teaching/classes",
                                        new ClassCreateRequest("Synthetic Class", mode))
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        requestPostWithOk(
                "/education/teaching/classes/" + group + "/members",
                new MembershipCreateRequest(enrollment, 1L, null));
        return group;
    }

    private String schedule(String group, String key, long start, String makeup) throws Exception {
        return id(
                requestPostWithOk(
                                "/education/teaching/sessions",
                                new SessionCreateRequest(
                                        group,
                                        key,
                                        "permission_test",
                                        "room-1",
                                        start,
                                        start + 3600000,
                                        makeup))
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
    }

    private EnrollmentCreateRequest request(String student, String row) {
        return new EnrollmentCreateRequest(
                student,
                "edu-test-order",
                "products",
                row,
                "key-" + row,
                "Synthetic Course",
                "HOURS",
                new BigDecimal("6"));
    }

    private String child() throws Exception {
        return id(
                requestPostWithOk(
                                "/education/students",
                                new StudentCreateRequest(
                                        "edu-test-family", "Synthetic Learner", null))
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
    }

    private String id(String response) {
        return JsonPath.read(response, "$.data.id");
    }

    private <T extends BaseModel> T initialize(T model, String id) {
        model.setId(id);
        model.setCreateTime(1L);
        model.setUpdateTime(1L);
        model.setCreateUser("admin");
        model.setUpdateUser("admin");
        return model;
    }

    private void seedOrder() {
        Contract contract = initialize(new Contract(), "edu-test-contract");
        contract.setName("Synthetic Contract");
        contract.setNumber("EDU-SYNTHETIC");
        contract.setCustomerId("edu-test-family");
        contract.setOwner("admin");
        contract.setOrganizationId(DEFAULT_ORGANIZATION_ID);
        contract.setStage("synthetic");
        contracts.insert(contract);
        Order order = initialize(new Order(), "edu-test-order");
        order.setName("Synthetic Order");
        order.setNumber("EDU-SYNTHETIC");
        order.setCustomerId("edu-test-family");
        order.setContractId(contract.getId());
        order.setOwner("admin");
        order.setOrganizationId(DEFAULT_ORGANIZATION_ID);
        order.setStage("synthetic");
        order.setApprovalStatus("APPROVING");
        order.setAmount(new BigDecimal("200"));
        orders.insert(order);
        for (String row : java.util.List.of("row-1", "row-2")) {
            OrderField field = new OrderField();
            field.setId("edu-test-" + row);
            field.setResourceId(order.getId());
            field.setFieldId("amount");
            field.setFieldValue("100.00");
            field.setRefSubId("products");
            field.setRowId(row);
            field.setBizId(row);
            fields.insert(field);
        }
        OrderSnapshot snapshot = new OrderSnapshot();
        snapshot.setId("edu-test-snapshot");
        snapshot.setOrderId(order.getId());
        snapshot.setOrderProp(
                "{\"fields\":[{\"id\":\"products\",\"type\":\"SUB_PRODUCT\",\"subFields\":[{\"id\":\"amount\",\"type\":\"INPUT_NUMBER\",\"internalKey\":\"orderProductAmount\"}]}]}");
        snapshot.setOrderValue(
                "{\"moduleFields\":[{\"fieldId\":\"products\",\"fieldValue\":[{\"id\":\"row-1\",\"amount\":\"100.00\"},{\"id\":\"row-2\",\"amount\":\"100.00\"}]}]}");
        snapshots.insert(snapshot);
        ContractPaymentRecord receipt = initialize(new ContractPaymentRecord(), "edu-test-receipt");
        receipt.setName("Synthetic Receipt");
        receipt.setOwner("admin");
        receipt.setContractId(contract.getId());
        receipt.setOrganizationId(DEFAULT_ORGANIZATION_ID);
        receipt.setRecordAmount(new BigDecimal("100"));
        receipt.setRecordEndTime(1L);
        receipts.insert(receipt);
    }
}
