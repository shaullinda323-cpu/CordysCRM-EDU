package cn.cordys.crm.education.service;

import static org.junit.jupiter.api.Assertions.*;

import cn.cordys.common.domain.BaseModuleFieldValue;
import cn.cordys.common.exception.GenericException;
import cn.cordys.crm.order.domain.Order;
import cn.cordys.crm.system.dto.field.InputField;
import cn.cordys.crm.system.dto.field.ProductSubField;
import cn.cordys.crm.system.dto.response.ModuleFormConfigDTO;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

class EducationOrderSnapshotServiceTest {
    private final EducationOrderSnapshotService service = new EducationOrderSnapshotService();

    @Test
    void capturesStableBusinessRowAndNativeAmountRegardlessOfPosition() {
        var result =
                service.extract(
                        order(),
                        config(),
                        List.of(
                                new BaseModuleFieldValue(
                                        "products",
                                        List.of(
                                                Map.of("id", "other", "amount", "999.00"),
                                                Map.of(
                                                        "id",
                                                        "stable-row",
                                                        "amount",
                                                        "320.50",
                                                        "name",
                                                        "Synthetic course")))),
                        "products",
                        "stable-row");
        assertEquals(new BigDecimal("320.50"), result.amount());
        assertEquals("stable-row", result.rowId());
        assertTrue(result.rowJson().contains("Synthetic course"));
    }

    @Test
    void missingRowDoesNotFallBackToPositionOrName() {
        assertThrows(
                GenericException.class,
                () ->
                        service.extract(
                                order(),
                                config(),
                                List.of(
                                        new BaseModuleFieldValue(
                                                "products",
                                                List.of(
                                                        Map.of(
                                                                "id",
                                                                "another-row",
                                                                "amount",
                                                                "100")))),
                                "products",
                                "stable-row"));
    }

    @Test
    void refusesNegativeAndUnrepresentableMoney() {
        for (String amount : List.of("-1", "1.001", "not-a-number")) {
            assertThrows(
                    GenericException.class,
                    () ->
                            service.extract(
                                    order(),
                                    config(),
                                    List.of(
                                            new BaseModuleFieldValue(
                                                    "products",
                                                    List.of(
                                                            Map.of(
                                                                    "id",
                                                                    "stable-row",
                                                                    "amount",
                                                                    amount)))),
                                    "products",
                                    "stable-row"));
        }
    }

    private Order order() {
        Order order = new Order();
        order.setId("synthetic-order");
        order.setCustomerId("synthetic-family");
        order.setOwner("synthetic-seller");
        order.setApprovalStatus("APPROVED");
        return order;
    }

    private ModuleFormConfigDTO config() {
        InputField amount = new InputField();
        amount.setId("amount");
        amount.setInternalKey("orderProductAmount");
        ProductSubField product = new ProductSubField();
        product.setId("products");
        product.setSubFields(List.of(amount));
        ModuleFormConfigDTO config = new ModuleFormConfigDTO();
        config.setFields(List.of(product));
        return config;
    }
}
