package cn.cordys.crm.education.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import cn.cordys.common.exception.GenericException;
import cn.cordys.crm.base.BaseTest;
import cn.cordys.crm.customer.service.CustomerContactService;
import cn.cordys.crm.customer.service.CustomerService;
import cn.cordys.crm.education.dto.request.GuardianLinkRequest;
import cn.cordys.crm.education.dto.request.StudentCreateRequest;

import com.jayway.jsonpath.JsonPath;

import jakarta.annotation.Resource;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Real HTTP authentication, MySQL migration and transactional linking; all fixture data is
 * synthetic.
 */
@Sql(scripts = {"/education/cleanup_family_test.sql", "/education/init_family_test.sql"})
@Sql(
        scripts = "/education/cleanup_family_test.sql",
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class EducationStudentControllerTest extends BaseTest {
    private static final String PATH = "/education/students";
    @Resource private JdbcTemplate jdbc;
    @Resource private CustomerService customers;
    @Resource private CustomerContactService contacts;

    @Test
    void familyCanHaveTwoChildrenWithTheSameName() throws Exception {
        String first = createChild();
        String second = createChild();
        assertNotEquals(first, second);
        requestGetWithOk(PATH + "?customerId=edu-test-family")
                .andExpect(jsonPath("$.data.length()").value(2));
        assertEquals(
                "admin",
                jdbc.queryForObject(
                        "SELECT create_user FROM edu_student WHERE id = ?", String.class, first));
    }

    @Test
    void foreignAndPoolFamiliesAreDeniedEvenToAnAdministrator() throws Exception {
        for (String family : new String[] {"edu-test-foreign", "edu-test-pool"}) {
            requestPost(PATH, new StudentCreateRequest(family, "Synthetic Child", null))
                    .andExpect(status().isForbidden());
            mockMvc.perform(getRequestBuilder(PATH + "?customerId=" + family))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void blankStudentNameIsRejectedAtTheHttpBoundary() throws Exception {
        requestPost(PATH, new StudentCreateRequest("edu-test-family", "   ", null))
                .andExpect(status().is4xxClientError());
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM edu_student WHERE customer_id = 'edu-test-family'",
                        Integer.class));
    }

    @Test
    void concurrentGuardianRetriesReturnOnePersistedIdentity() throws Exception {
        String student = createChild();
        var request = new GuardianLinkRequest(student, "edu-test-parent", "Parent");
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(4)) {
            var requests = new ArrayList<Future<String>>();
            for (int i = 0; i < 4; i++) {
                requests.add(
                        executor.submit(
                                () -> {
                                    assertTrue(start.await(10, TimeUnit.SECONDS));
                                    var result =
                                            requestPostWithOk(PATH + "/guardians", request)
                                                    .andReturn();
                                    return JsonPath.read(
                                            result.getResponse().getContentAsString(), "$.data.id");
                                }));
            }
            start.countDown();
            String identity = requests.getFirst().get(30, TimeUnit.SECONDS);
            for (var result : requests) assertEquals(identity, result.get(30, TimeUnit.SECONDS));
        }
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM edu_guardian WHERE student_id = ?",
                        Integer.class,
                        student));
        requestGetWithOk(PATH + "/" + student + "/guardians")
                .andExpect(jsonPath("$.data[0].contactId").value("edu-test-parent"));
    }

    @Test
    void nativeMutationsCannotBreakFamilyIdentity() throws Exception {
        String student = createChild();
        requestPostWithOk(
                PATH + "/guardians", new GuardianLinkRequest(student, "edu-test-parent", "Parent"));
        jdbc.update(
                "UPDATE customer_contact SET name = 'Renamed Synthetic Guardian' WHERE id ="
                    + " 'edu-test-parent'");
        requestGetWithOk(PATH + "/" + student + "/guardians")
                .andExpect(jsonPath("$.data[0].contactId").value("edu-test-parent"));
        assertThrows(GenericException.class, () -> contacts.delete("edu-test-parent"));
        assertThrows(
                GenericException.class,
                () -> customers.deleteCustomerResource(java.util.List.of("edu-test-family")));
        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        jdbc.update(
                                "UPDATE customer_contact SET customer_id = 'edu-test-pool' WHERE id"
                                    + " = 'edu-test-parent'"));
        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        jdbc.update(
                                "UPDATE customer SET organization_id = 'edu-test-other-org' WHERE"
                                    + " id = 'edu-test-family'"));
        assertEquals(
                "edu-test-family",
                jdbc.queryForObject(
                        "SELECT customer_id FROM customer_contact WHERE id = 'edu-test-parent'",
                        String.class));
    }

    @Test
    void trialRetriesDoNotCreateSalesAndFinalResultsAreImmutable() throws Exception {
        String student = createChild();
        long start = System.currentTimeMillis() - 3600000;
        var booking =
                new cn.cordys.crm.education.dto.request.TrialCreateRequest(
                        student,
                        "synthetic-trial-key",
                        "Synthetic AI Trial",
                        start,
                        start + 1800000);
        var first = requestPostWithOk("/education/trials", booking).andReturn();
        String id = JsonPath.read(first.getResponse().getContentAsString(), "$.data.id");
        requestPostWithOk("/education/trials", booking).andExpect(jsonPath("$.data.id").value(id));
        requestPost(
                        "/education/trials",
                        new cn.cordys.crm.education.dto.request.TrialCreateRequest(
                                student,
                                "synthetic-trial-key",
                                "Different content",
                                start,
                                start + 1800000))
                .andExpect(status().is4xxClientError());
        var outcome =
                new cn.cordys.crm.education.dto.request.TrialResultRequest(
                        "ATTENDED", "Completed synthetic exercise");
        requestPostWithOk("/education/trials/" + id + "/result", outcome)
                .andExpect(jsonPath("$.data.status").value("ATTENDED"));
        requestPostWithOk("/education/trials/" + id + "/result", outcome);
        requestPost(
                        "/education/trials/" + id + "/result",
                        new cn.cordys.crm.education.dto.request.TrialResultRequest(
                                "NO_SHOW", "Changed"))
                .andExpect(status().is4xxClientError());
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM edu_trial WHERE student_id = ?",
                        Integer.class,
                        student));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM sales_order WHERE customer_id = 'edu-test-family'",
                        Integer.class));
    }

    private String createChild() throws Exception {
        var result =
                requestPostWithOk(
                                PATH,
                                new StudentCreateRequest(
                                        "edu-test-family", "Synthetic Child", "Grade 5"))
                        .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
    }
}
