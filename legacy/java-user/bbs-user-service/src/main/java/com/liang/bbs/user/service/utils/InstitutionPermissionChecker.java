package com.liang.bbs.user.service.utils;

import com.liang.bbs.common.web.exception.BusinessException;
import com.liang.bbs.common.enums.ResponseCode;
import com.liang.bbs.user.persistence.entity.SysUserInstitutionPo;
import com.liang.bbs.user.persistence.entity.SysUserInstitutionPoExample;
import com.liang.bbs.user.persistence.entity.DeveloperUserPoExample;
import com.liang.bbs.user.persistence.mapper.SysUserInstitutionPoMapper;
import com.liang.bbs.user.persistence.mapper.DeveloperUserPoMapper;
import com.liang.bbs.article.facade.server.InstitutionService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.Reference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 机构权限检查工具类
 *
 */
@Slf4j
@Component
public class InstitutionPermissionChecker {

    @Autowired
    private SysUserInstitutionPoMapper userInstitutionMapper;

    @Autowired
    private DeveloperUserPoMapper developerUserPoMapper;

    @Reference(check = false)
    private InstitutionService institutionService;

    /**
     * 检查用户是否为系统管理员
     * 系统管理员拥有所有机构的管理权限
     *
     * @param userId 用户ID
     * @return true-是系统管理员，false-不是系统管理员
     */
    public boolean isSystemAdmin(Integer userId) {
        if (userId == null) {
            return false;
        }
        DeveloperUserPoExample example = new DeveloperUserPoExample();
        example.createCriteria().andUserIdEqualTo(userId).andStatusEqualTo(1);
        return developerUserPoMapper.countByExample(example) > 0;
    }

    /**
     * 检查用户是否是机构管理员（admin或owner）
     * 系统管理员自动拥有所有机构的管理权限
     *
     * @param userId 用户ID
     * @param institutionId 机构ID
     * @return true-是管理员，false-不是管理员
     */
    public boolean isInstitutionAdmin(Integer userId, Long institutionId) {
        // 系统管理员拥有所有机构的管理权限
        if (isSystemAdmin(userId)) {
            return true;
        }

        SysUserInstitutionPoExample example = new SysUserInstitutionPoExample();
        example.createCriteria()
                .andUserIdEqualTo(userId)
                .andInstitutionIdEqualTo(institutionId);

        List<SysUserInstitutionPo> relations = userInstitutionMapper.selectByExample(example);

        if (relations.isEmpty()) {
            return false;
        }

        String role = relations.get(0).getRole();
        return "admin".equals(role) || "owner".equals(role);
    }

    /**
     * 检查用户是否是机构所有者
     * 系统管理员自动拥有所有机构的所有者权限
     *
     * @param userId 用户ID
     * @param institutionId 机构ID
     * @return true-是所有者，false-不是所有者
     */
    public boolean isInstitutionOwner(Integer userId, Long institutionId) {
        // 系统管理员拥有所有机构的所有者权限
        if (isSystemAdmin(userId)) {
            return true;
        }

        SysUserInstitutionPoExample example = new SysUserInstitutionPoExample();
        example.createCriteria()
                .andUserIdEqualTo(userId)
                .andInstitutionIdEqualTo(institutionId);

        List<SysUserInstitutionPo> relations = userInstitutionMapper.selectByExample(example);

        if (relations.isEmpty()) {
            return false;
        }

        return "owner".equals(relations.get(0).getRole());
    }

    /**
     * 获取用户管理的所有机构ID列表
     *
     * @param userId 用户ID
     * @return 机构ID列表
     */
    public List<Long> getUserManagedInstitutions(Integer userId) {
        SysUserInstitutionPoExample example = new SysUserInstitutionPoExample();
        example.createCriteria()
                .andUserIdEqualTo(userId);

        List<SysUserInstitutionPo> relations = userInstitutionMapper.selectByExample(example);

        return relations.stream()
                .filter(r -> "admin".equals(r.getRole()) || "owner".equals(r.getRole()))
                .map(SysUserInstitutionPo::getInstitutionId)
                .collect(Collectors.toList());
    }

    /**
     * 获取用户所属的机构ID（包括普通成员）
     *
     * @param userId 用户ID
     * @return 机构ID列表
     */
    public List<Long> getUserInstitutions(Integer userId) {
        SysUserInstitutionPoExample example = new SysUserInstitutionPoExample();
        example.createCriteria()
                .andUserIdEqualTo(userId);

        List<SysUserInstitutionPo> relations = userInstitutionMapper.selectByExample(example);

        return relations.stream()
                .map(SysUserInstitutionPo::getInstitutionId)
                .collect(Collectors.toList());
    }

    /**
     * 获取用户的主机构ID
     *
     * @param userId 用户ID
     * @return 主机构ID，如果没有则返回null
     */
    public Long getUserPrimaryInstitution(Integer userId) {
        SysUserInstitutionPoExample example = new SysUserInstitutionPoExample();
        example.createCriteria()
                .andUserIdEqualTo(userId)
                .andIsPrimaryEqualTo((byte) 1);

        List<SysUserInstitutionPo> relations = userInstitutionMapper.selectByExample(example);

        return relations.isEmpty() ? null : relations.get(0).getInstitutionId();
    }

    /**
     * 检查并要求用户是机构管理员，否则抛出异常
     *
     * @param userId 用户ID
     * @param institutionId 机构ID
     * @throws BusinessException 如果用户不是管理员
     */
    public void requireInstitutionAdmin(Integer userId, Long institutionId) {
        if (!isInstitutionAdmin(userId, institutionId)) {
            log.warn("用户 {} 尝试访问机构 {} 的管理功能，但没有权限", userId, institutionId);
            throw BusinessException.build(ResponseCode.NO_PERMISSION, "您没有该机构的管理权限");
        }
    }

    /**
     * 检查并要求用户是机构所有者，否则抛出异常
     *
     * @param userId 用户ID
     * @param institutionId 机构ID
     * @throws BusinessException 如果用户不是所有者
     */
    public void requireInstitutionOwner(Integer userId, Long institutionId) {
        if (!isInstitutionOwner(userId, institutionId)) {
            log.warn("用户 {} 尝试访问机构 {} 的所有者功能，但没有权限", userId, institutionId);
            throw BusinessException.build(ResponseCode.NO_PERMISSION, "只有机构所有者才能执行此操作");
        }
    }

    /**
     * 检查用户是否对某个机构有管理权限（包括层级管理）
     * 如果用户是系统管理员，或者是该机构的管理员，或者是该机构任何父机构的管理员，则返回true
     *
     * @param userId 用户ID
     * @param institutionId 机构ID
     * @return true-有管理权限，false-无管理权限
     */
    public boolean hasHierarchicalAdminPermission(Integer userId, Long institutionId) {
        // 系统管理员拥有所有机构的管理权限
        if (isSystemAdmin(userId)) {
            return true;
        }

        // 先检查是否是该机构的直接管理员
        if (isInstitutionAdmin(userId, institutionId)) {
            return true;
        }

        // 获取用户管理的所有机构
        List<Long> managedInstitutions = getUserManagedInstitutions(userId);
        if (managedInstitutions.isEmpty()) {
            return false;
        }

        // 检查用户管理的机构中，是否有任何一个是目标机构的父机构
        try {
            for (Long managedInstitutionId : managedInstitutions) {
                List<Long> subInstitutions = institutionService.getAllSubInstitutionIds(managedInstitutionId);
                if (subInstitutions.contains(institutionId)) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.error("检查层级管理权限时出错: userId={}, institutionId={}", userId, institutionId, e);
        }

        return false;
    }

    /**
     * 获取用户有管理权限的所有机构（包括层级管理）
     * 系统管理员返回所有机构，普通用户返回直接管理的机构及其所有子机构
     *
     * @param userId 用户ID
     * @return 所有有管理权限的机构ID列表
     */
    public List<Long> getAllManagedInstitutionIds(Integer userId) {
        // 系统管理员拥有所有机构的管理权限
        if (isSystemAdmin(userId)) {
            try {
                List<com.liang.bbs.article.facade.dto.InstitutionDTO> allInstitutions = institutionService.getAllInstitutions();
                return allInstitutions.stream()
                        .map(com.liang.bbs.article.facade.dto.InstitutionDTO::getId)
                        .collect(Collectors.toList());
            } catch (Exception e) {
                log.error("系统管理员获取所有机构时出错: userId={}", userId, e);
                return new java.util.ArrayList<>();
            }
        }

        List<Long> result = new java.util.ArrayList<>();
        List<Long> directlyManaged = getUserManagedInstitutions(userId);

        try {
            for (Long institutionId : directlyManaged) {
                List<Long> subInstitutions = institutionService.getAllSubInstitutionIds(institutionId);
                result.addAll(subInstitutions);
            }
        } catch (Exception e) {
            log.error("获取所有管理机构时出错: userId={}", userId, e);
            return directlyManaged; // 出错时返回直接管理的机构
        }

        return result.stream().distinct().collect(Collectors.toList());
    }

    /**
     * 检查并要求用户对机构有管理权限（支持层级管理），否则抛出异常
     *
     * @param userId 用户ID
     * @param institutionId 机构ID
     * @throws BusinessException 如果用户没有管理权限
     */
    public void requireHierarchicalAdminPermission(Integer userId, Long institutionId) {
        if (!hasHierarchicalAdminPermission(userId, institutionId)) {
            log.warn("用户 {} 尝试访问机构 {} 的管理功能（包括层级），但没有权限", userId, institutionId);
            throw BusinessException.build(ResponseCode.NO_PERMISSION, "您没有该机构的管理权限");
        }
    }
}
