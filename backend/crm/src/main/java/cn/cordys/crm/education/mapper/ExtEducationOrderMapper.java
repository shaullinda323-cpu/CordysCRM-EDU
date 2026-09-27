package cn.cordys.crm.education.mapper;

import cn.cordys.crm.order.domain.Order;
import cn.cordys.crm.order.domain.OrderSnapshot;

import org.apache.ibatis.annotations.Param;

public interface ExtEducationOrderMapper {
    Order lockOrder(@Param("id") String id, @Param("orgId") String orgId);

    OrderSnapshot lockSnapshot(@Param("id") String id);

    int countRow(
            @Param("orderId") String orderId,
            @Param("subFieldId") String subFieldId,
            @Param("rowId") String rowId);
}
