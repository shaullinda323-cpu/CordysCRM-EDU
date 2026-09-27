package cn.cordys.crm.order.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cn.cordys.crm.order.domain.Order;
import cn.cordys.mybatis.BaseMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

class OrderResourceAccessContextProviderTest {
    private final OrderResourceAccessContextProvider provider =
            new OrderResourceAccessContextProvider();

    @SuppressWarnings("unchecked")
    private final BaseMapper<Order> orders = mock(BaseMapper.class);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(provider, "orderMapper", orders);
    }

    @Test
    void sameOrganizationRetainsOwnerAndApprovalStatus() {
        Order order = order("own", "org", "sales");
        order.setApprovalStatus("NONE");
        when(orders.selectByPrimaryKey("own")).thenReturn(order);

        var context = provider.getAccessContext("own", "org");
        assertNotNull(context);
        assertEquals("sales", context.getOwnerId());
        assertEquals("NONE", context.getApprovalStatus());
    }

    @Test
    void foreignOrderIsDeniedEvenWhenOwnerIsTheSameUser() {
        when(orders.selectByPrimaryKey("foreign")).thenReturn(order("foreign", "other", "sales"));
        assertNull(provider.getAccessContext("foreign", "org"));
    }

    @Test
    void missingOrganizationAndMissingResourceFailClosed() {
        when(orders.selectByPrimaryKey("own")).thenReturn(order("own", "org", "sales"));
        assertNull(provider.getAccessContext("own", null));
        assertNull(provider.getAccessContext("missing", "org"));
    }

    @Test
    void mixedBatchOmitsForeignAndOwnerlessOrdersSoCallerCanRejectTheBatch() {
        var ids = List.of("own", "foreign", "unassigned");
        when(orders.selectByIds(ids))
                .thenReturn(
                        List.of(
                                order("own", "org", "sales"),
                                order("foreign", "other", "sales"),
                                order("unassigned", "org", null)));

        assertEquals(Map.of("own", "sales"), provider.batchGetOwnerIds(ids, "org"));
        assertEquals(Map.of(), provider.batchGetOwnerIds(ids, null));
    }

    @Test
    void emptyBatchDoesNotQueryOrders() {
        assertEquals(Map.of(), provider.batchGetOwnerIds(List.of(), "org"));
        assertEquals(Map.of(), provider.batchGetOwnerIds(null, "org"));
        verifyNoInteractions(orders);
    }

    private Order order(String id, String organizationId, String owner) {
        Order order = new Order();
        order.setId(id);
        order.setOrganizationId(organizationId);
        order.setOwner(owner);
        return order;
    }
}
