package cn.cordys.crm.education.service;

import cn.cordys.common.exception.GenericException;
import cn.cordys.common.permission.ResourcePermissionService;
import cn.cordys.common.response.result.CrmHttpResultCode;
import cn.cordys.common.service.DataScopeService;
import cn.cordys.crm.customer.domain.Customer;
import cn.cordys.mybatis.BaseMapper;

import jakarta.annotation.Resource;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/** Reuses CRM ownership and data scope without exposing child records through the public pool. */
@Service
public class EducationFamilyAccessService {
    @Resource private BaseMapper<Customer> customerMapper;
    @Resource private ResourcePermissionService permissions;
    @Resource private DataScopeService dataScopeService;

    public void check(String customerId, String permission, String actorId, String organizationId) {
        permissions.checkPermission(permission);
        Customer customer = customerMapper.selectByPrimaryKey(customerId);
        if (StringUtils.isBlank(organizationId)
                || StringUtils.isBlank(actorId)
                || customer == null
                || !organizationId.equals(customer.getOrganizationId())
                || Boolean.TRUE.equals(customer.getInSharedPool())
                || StringUtils.isBlank(customer.getOwner())
                || !dataScopeService.hasDataPermission(
                        actorId, organizationId, customer.getOwner(), permission)) {
            throw new GenericException(CrmHttpResultCode.FORBIDDEN);
        }
    }
}
