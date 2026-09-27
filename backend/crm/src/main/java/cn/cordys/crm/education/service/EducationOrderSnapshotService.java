package cn.cordys.crm.education.service;

import cn.cordys.common.constants.PermissionConstants;
import cn.cordys.common.domain.BaseModuleFieldValue;
import cn.cordys.common.exception.GenericException;
import cn.cordys.common.permission.ResourcePermissionService;
import cn.cordys.common.response.result.CrmHttpResultCode;
import cn.cordys.common.service.DataScopeService;
import cn.cordys.common.util.JSON;
import cn.cordys.common.util.Translator;
import cn.cordys.crm.education.mapper.ExtEducationOrderMapper;
import cn.cordys.crm.order.domain.Order;
import cn.cordys.crm.order.domain.OrderSnapshot;
import cn.cordys.crm.order.dto.response.OrderGetResponse;
import cn.cordys.crm.system.dto.field.ProductSubField;
import cn.cordys.crm.system.dto.field.base.BaseField;
import cn.cordys.crm.system.dto.response.ModuleFormConfigDTO;

import jakarta.annotation.Resource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Captures a verified native product row within the caller's enrollment transaction. */
@Service
public class EducationOrderSnapshotService {
    @Resource private ExtEducationOrderMapper mapper;
    @Resource private ResourcePermissionService permissions;
    @Resource private DataScopeService dataScope;

    public record ProductRow(
            String orderId,
            String customerId,
            String contractId,
            String sellerId,
            String subFieldId,
            String rowId,
            BigDecimal amount,
            String rowJson,
            String approvalStatus) {}

    @Transactional(propagation = Propagation.MANDATORY)
    public ProductRow capture(
            String orderId,
            String customerId,
            String subFieldId,
            String rowId,
            String actor,
            String org) {
        permissions.checkPermission(PermissionConstants.ORDER_READ);
        Order order = mapper.lockOrder(orderId, org);
        if (order == null
                || order.getOwner() == null
                || order.getOwner().isBlank()
                || !Objects.equals(customerId, order.getCustomerId())
                || !dataScope.hasDataPermission(
                        actor, org, order.getOwner(), PermissionConstants.ORDER_READ)) {
            throw new GenericException(CrmHttpResultCode.FORBIDDEN);
        }
        OrderSnapshot snapshot = mapper.lockSnapshot(orderId);
        if (snapshot == null || mapper.countRow(orderId, subFieldId, rowId) == 0) invalid();
        var config = JSON.parseObject(snapshot.getOrderProp(), ModuleFormConfigDTO.class);
        var values = JSON.parseObject(snapshot.getOrderValue(), OrderGetResponse.class);
        return extract(order, config, values.getModuleFields(), subFieldId, rowId);
    }

    ProductRow extract(
            Order order,
            ModuleFormConfigDTO config,
            List<BaseModuleFieldValue> values,
            String subFieldId,
            String rowId) {
        if (config == null || config.getFields() == null || values == null) invalid();
        ProductSubField product =
                config.getFields().stream()
                        .filter(
                                field ->
                                        field instanceof ProductSubField
                                                && subFieldId.equals(field.getId()))
                        .map(ProductSubField.class::cast)
                        .findFirst()
                        .orElse(null);
        if (product == null || product.getSubFields() == null) invalid();
        String amountField =
                product.getSubFields().stream()
                        .filter(field -> "orderProductAmount".equals(field.getInternalKey()))
                        .map(BaseField::getId)
                        .findFirst()
                        .orElse(null);
        Object rows =
                values.stream()
                        .filter(value -> subFieldId.equals(value.getFieldId()))
                        .map(BaseModuleFieldValue::getFieldValue)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null);
        if (amountField == null || !(rows instanceof List<?>)) invalid();
        Map<?, ?> row =
                ((List<?>) rows)
                        .stream()
                                .filter(value -> value instanceof Map<?, ?>)
                                .map(value -> (Map<?, ?>) value)
                                .filter(value -> rowId.equals(value.get("id")))
                                .findFirst()
                                .orElse(null);
        if (row == null || row.get(amountField) == null) invalid();
        BigDecimal amount;
        try {
            amount =
                    new BigDecimal(row.get(amountField).toString())
                            .setScale(2, java.math.RoundingMode.UNNECESSARY);
        } catch (ArithmeticException | NumberFormatException ex) {
            invalid();
            return null;
        }
        if (amount.signum() < 0) invalid();
        return new ProductRow(
                order.getId(),
                order.getCustomerId(),
                order.getContractId(),
                order.getOwner(),
                subFieldId,
                rowId,
                amount,
                JSON.toJSONString(row),
                order.getApprovalStatus());
    }

    private void invalid() {
        throw new GenericException(
                CrmHttpResultCode.VALIDATE_FAILED, Translator.get("education.order.row.invalid"));
    }
}
