package cn.cordys.crm.education.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cn.cordys.common.exception.GenericException;
import cn.cordys.common.permission.ResourcePermissionService;
import cn.cordys.common.service.DataScopeService;
import cn.cordys.crm.customer.domain.Customer;
import cn.cordys.mybatis.BaseMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class EducationFamilyAccessServiceTest {
    private final EducationFamilyAccessService access = new EducationFamilyAccessService();

    @SuppressWarnings("unchecked")
    private final BaseMapper<Customer> customers = mock(BaseMapper.class);

    private final ResourcePermissionService permissions = mock(ResourcePermissionService.class);
    private final DataScopeService scope = mock(DataScopeService.class);
    private final Customer customer = new Customer();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(access, "customerMapper", customers);
        ReflectionTestUtils.setField(access, "permissions", permissions);
        ReflectionTestUtils.setField(access, "dataScopeService", scope);
        customer.setOrganizationId("org");
        customer.setOwner("owner");
        when(customers.selectByPrimaryKey("family")).thenReturn(customer);
        when(scope.hasDataPermission("actor", "org", "owner", "permission")).thenReturn(true);
    }

    @Test
    void reusesCrmRoleAndDataScope() {
        assertDoesNotThrow(() -> access.check("family", "permission", "actor", "org"));
        verify(permissions).checkPermission("permission");
        verify(scope).hasDataPermission("actor", "org", "owner", "permission");
    }

    @Test
    void foreignOrganizationDoesNotReachDataScope() {
        customer.setOrganizationId("other");
        assertThrows(
                GenericException.class, () -> access.check("family", "permission", "actor", "org"));
        verifyNoInteractions(scope);
    }

    @Test
    void publicPoolDoesNotExposeChildrenEvenWithBroadCrmScope() {
        customer.setInSharedPool(true);
        assertThrows(
                GenericException.class, () -> access.check("family", "permission", "actor", "org"));
        verifyNoInteractions(scope);
    }

    @Test
    void deniedDataScopeCannotBeBypassed() {
        when(scope.hasDataPermission("actor", "org", "owner", "permission")).thenReturn(false);
        assertThrows(
                GenericException.class, () -> access.check("family", "permission", "actor", "org"));
    }

    @Test
    void missingIdentityCustomerOrOwnerIsDenied() {
        assertThrows(
                GenericException.class, () -> access.check("family", "permission", "actor", null));
        assertThrows(
                GenericException.class, () -> access.check("family", "permission", null, "org"));
        assertThrows(
                GenericException.class,
                () -> access.check("missing", "permission", "actor", "org"));
        customer.setOwner(null);
        assertThrows(
                GenericException.class, () -> access.check("family", "permission", "actor", "org"));
    }
}
