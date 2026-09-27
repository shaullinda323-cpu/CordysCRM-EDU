package cn.cordys.crm.education.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import cn.cordys.common.constants.PermissionConstants;
import cn.cordys.common.exception.GenericException;
import cn.cordys.crm.customer.domain.CustomerContact;
import cn.cordys.crm.education.domain.EducationGuardian;
import cn.cordys.crm.education.domain.EducationStudent;
import cn.cordys.crm.education.dto.request.GuardianLinkRequest;
import cn.cordys.crm.education.dto.request.StudentCreateRequest;
import cn.cordys.crm.education.mapper.ExtEducationGuardianMapper;
import cn.cordys.mybatis.BaseMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

class EducationStudentServiceTest {
    private final EducationStudentService service = spy(new EducationStudentService());

    @SuppressWarnings("unchecked")
    private final BaseMapper<EducationStudent> students = mock(BaseMapper.class);

    @SuppressWarnings("unchecked")
    private final BaseMapper<EducationGuardian> guardians = mock(BaseMapper.class);

    @SuppressWarnings("unchecked")
    private final BaseMapper<CustomerContact> contacts = mock(BaseMapper.class);

    private final EducationFamilyAccessService access = mock(EducationFamilyAccessService.class);
    private final ExtEducationGuardianMapper locks = mock(ExtEducationGuardianMapper.class);
    private final EducationStudent student = new EducationStudent();
    private final CustomerContact contact = new CustomerContact();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "studentMapper", students);
        ReflectionTestUtils.setField(service, "guardianMapper", guardians);
        ReflectionTestUtils.setField(service, "contactMapper", contacts);
        ReflectionTestUtils.setField(service, "familyAccess", access);
        ReflectionTestUtils.setField(service, "guardianLockMapper", locks);
        doReturn("generated-id").when(service).nextId();
        student.setId("child");
        student.setCustomerId("family");
        student.setOrganizationId("org");
        contact.setId("parent");
        contact.setCustomerId("family");
        contact.setOrganizationId("org");
        contact.setEnable(true);
        when(students.selectByPrimaryKey("child")).thenReturn(student);
        when(contacts.selectByPrimaryKey("parent")).thenReturn(contact);
        when(locks.lockStudent("child", "org")).thenReturn("child");
        when(locks.selectLinkForUpdate("child", "parent", "org")).thenReturn(List.of());
    }

    @Test
    void childIsLinkedToExistingCustomerAndCreatedByAuthenticatedActor() {
        var response =
                service.create(
                        new StudentCreateRequest("family", " Synthetic Child ", " Grade 5 "),
                        "actor",
                        "org");
        verify(access)
                .check("family", PermissionConstants.CUSTOMER_MANAGEMENT_UPDATE, "actor", "org");
        var captor = ArgumentCaptor.forClass(EducationStudent.class);
        verify(students).insert(captor.capture());
        assertEquals("org", captor.getValue().getOrganizationId());
        assertEquals("actor", captor.getValue().getCreateUser());
        assertEquals("family", response.customerId());
        assertEquals("Synthetic Child", response.name());
        verifyNoInteractions(contacts);
    }

    @Test
    void listUsesFamilyAndOrganizationTogether() {
        when(students.select(any(EducationStudent.class))).thenReturn(List.of(student));
        assertEquals(1, service.list("family", "actor", "org").size());
        var captor = ArgumentCaptor.forClass(EducationStudent.class);
        verify(students).select(captor.capture());
        assertEquals("family", captor.getValue().getCustomerId());
        assertEquals("org", captor.getValue().getOrganizationId());
    }

    @Test
    void deniedCustomerCannotCreateStudent() {
        doThrow(new GenericException("denied")).when(access).check(any(), any(), any(), any());
        assertThrows(
                GenericException.class,
                () ->
                        service.create(
                                new StudentCreateRequest("family", "Child", null), "actor", "org"));
        verify(students, never()).insert(any());
    }

    @Test
    void guardianMustBelongToTheSameFamilyAndOrganization() {
        contact.setCustomerId("other-family");
        assertThrows(GenericException.class, () -> link());
        contact.setCustomerId("family");
        contact.setOrganizationId("other-org");
        assertThrows(GenericException.class, () -> link());
        verify(guardians, never()).insert(any());
    }

    @Test
    void foreignStudentCannotBeReadOrLinked() {
        student.setOrganizationId("other");
        assertThrows(GenericException.class, () -> service.guardians("child", "actor", "org"));
        assertThrows(GenericException.class, () -> link());
        verifyNoInteractions(guardians, contacts, access);
    }

    @Test
    void guardianRetryReturnsTheSameRecordWithoutAnotherInsert() {
        EducationGuardian existing = new EducationGuardian();
        existing.setId("existing");
        existing.setStudentId("child");
        existing.setContactId("parent");
        existing.setRelationship("Mother");
        when(locks.selectLinkForUpdate("child", "parent", "org")).thenReturn(List.of(existing));
        assertEquals("existing", service.linkGuardian(request(), "actor", "org").id());
        verify(guardians, never()).insert(any());
    }

    @Test
    void guardianLinkPreservesChildIdentity() {
        var result = service.linkGuardian(request(), "actor", "org");
        assertEquals("child", result.studentId());
        assertEquals("parent", result.contactId());
        verify(guardians).insert(any(EducationGuardian.class));
    }

    @Test
    void disabledContactCannotBeLinked() {
        contact.setEnable(false);
        assertThrows(GenericException.class, this::link);
        verifyNoInteractions(locks, guardians);
    }

    @Test
    void deletedStudentCannotReceiveAGuardian() {
        when(locks.lockStudent("child", "org")).thenReturn(null);
        assertThrows(GenericException.class, this::link);
        verify(guardians, never()).insert(any());
    }

    private GuardianLinkRequest request() {
        return new GuardianLinkRequest("child", "parent", "Mother");
    }

    private void link() {
        service.linkGuardian(request(), "actor", "org");
    }
}
