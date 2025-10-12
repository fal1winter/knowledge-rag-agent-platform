package com.liang.bbs.user.service.impl;

import com.liang.bbs.user.facade.dto.SysPermissionDTO;
import com.liang.bbs.user.facade.dto.SysRoleDTO;
import com.liang.bbs.user.facade.dto.UserAuthDTO;
import com.liang.bbs.user.facade.server.AuthService;
import com.liang.bbs.user.persistence.entity.*;
import com.liang.bbs.user.persistence.mapper.*;
import com.liang.bbs.user.service.mapstruct.SysPermissionMS;
import com.liang.bbs.user.service.mapstruct.SysRoleMS;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 权限服务实现类
 */
@Component
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    @Autowired
    private SysRolePoMapper sysRolePoMapper;

    @Autowired
    private SysPermissionPoMapper sysPermissionPoMapper;

    @Autowired
    private SysUserRolePoMapper sysUserRolePoMapper;

    @Autowired
    private SysRolePermissionPoMapper sysRolePermissionPoMapper;

    @Autowired
    private SysUserInstitutionPoMapper sysUserInstitutionPoMapper;

    @Autowired
    private SysInstitutionRolePoMapper sysInstitutionRolePoMapper;

    @Autowired
    private SysInstitutionPermissionPoMapper sysInstitutionPermissionPoMapper;

    // ========== 用户权限相关 ==========

    @Override
    @Cacheable(value = "userAuthCache", key = "#userId", unless = "#result == null")
    public UserAuthDTO getUserAuth(Integer userId) {
        log.info("获取用户权限信息: userId={}", userId);

        UserAuthDTO authDTO = new UserAuthDTO();
        authDTO.setUserId(userId);

        // 获取用户角色
        List<SysRoleDTO> roles = getUserRoles(userId);
        authDTO.setRoles(roles);

        // 提取角色编码
        Set<String> roleCodes = roles.stream()
                .map(SysRoleDTO::getRoleCode)
                .collect(Collectors.toSet());
        authDTO.setRoleCodes(roleCodes);

        // 获取权限编码
        Set<String> permCodes = getUserPermissionCodes(userId);
        authDTO.setPermissionCodes(permCodes);

        // 获取数据权限范围（取最大范围）
        int dataScope = roles.stream()
                .map(SysRoleDTO::getDataScope)
                .filter(Objects::nonNull)
                .min(Integer::compareTo)
                .orElse(4); // 默认仅本人
        authDTO.setDataScope(dataScope);

        // 获取机构信息
        List<Long> institutionIds = getUserInstitutionIds(userId);
        authDTO.setInstitutionIds(institutionIds);
        authDTO.setPrimaryInstitutionId(getUserPrimaryInstitutionId(userId));

        return authDTO;
    }

    @Override
    public List<SysRoleDTO> getUserRoles(Integer userId) {
        // 查询用户角色关联
        SysUserRolePoExample example = new SysUserRolePoExample();
        example.createCriteria().andUserIdEqualTo(userId);
        List<SysUserRolePo> userRoles = sysUserRolePoMapper.selectByExample(example);

        if (userRoles.isEmpty()) {
            return new ArrayList<>();
        }

        // 获取角色ID列表
        List<Long> roleIds = userRoles.stream()
                .map(SysUserRolePo::getRoleId)
                .collect(Collectors.toList());

        // 查询角色信息
        SysRolePoExample roleExample = new SysRolePoExample();
        roleExample.createCriteria()
                .andIdIn(roleIds)
                .andStatusEqualTo((byte) 1);
        List<SysRolePo> roles = sysRolePoMapper.selectByExample(roleExample);

        return SysRoleMS.INSTANCE.toDTO(roles);
    }

    @Override
    public Set<String> getUserPermissionCodes(Integer userId) {
        // 获取用户角色
        List<SysRoleDTO> roles = getUserRoles(userId);
        if (roles.isEmpty()) {
            return new HashSet<>();
        }

        List<Long> roleIds = roles.stream()
                .map(SysRoleDTO::getId)
                .collect(Collectors.toList());

        // 查询角色权限关联
        SysRolePermissionPoExample example = new SysRolePermissionPoExample();
        example.createCriteria().andRoleIdIn(roleIds);
        List<SysRolePermissionPo> rolePerms = sysRolePermissionPoMapper.selectByExample(example);

        if (rolePerms.isEmpty()) {
            return new HashSet<>();
        }

        // 获取权限ID列表
        List<Long> permIds = rolePerms.stream()
                .map(SysRolePermissionPo::getPermissionId)
                .distinct()
                .collect(Collectors.toList());

        // 查询权限信息
        SysPermissionPoExample permExample = new SysPermissionPoExample();
        permExample.createCriteria()
                .andIdIn(permIds)
                .andStatusEqualTo((byte) 1);
        List<SysPermissionPo> perms = sysPermissionPoMapper.selectByExample(permExample);

        return perms.stream()
                .map(SysPermissionPo::getPermCode)
                .collect(Collectors.toSet());
    }

    @Override
    public boolean hasRole(Integer userId, String roleCode) {
        List<SysRoleDTO> roles = getUserRoles(userId);
        return roles.stream().anyMatch(r -> roleCode.equals(r.getRoleCode()));
    }

    @Override
    public boolean hasPermission(Integer userId, String permCode) {
        Set<String> perms = getUserPermissionCodes(userId);
        return perms.contains(permCode);
    }

    // ========== 角色管理 ==========

    @Override
    public SysRoleDTO createRole(SysRoleDTO roleDTO) {
        log.info("创建角色: {}", roleDTO.getRoleCode());

        SysRolePo po = SysRoleMS.INSTANCE.toPo(roleDTO);
        po.setCreatedAt(LocalDateTime.now());
        po.setUpdatedAt(LocalDateTime.now());
        if (po.getStatus() == null) {
            po.setStatus((byte) 1);
        }
        if (po.getDataScope() == null) {
            po.setDataScope((byte) 4);
        }
        sysRolePoMapper.insertSelective(po);

        return SysRoleMS.INSTANCE.toDTO(po);
    }

    @Override
    public SysRoleDTO updateRole(SysRoleDTO roleDTO) {
        log.info("更新角色: {}", roleDTO.getId());

        SysRolePo po = sysRolePoMapper.selectByPrimaryKey(roleDTO.getId());
        if (po == null) {
            throw new IllegalArgumentException("角色不存在");
        }

        if (roleDTO.getRoleName() != null) {
            po.setRoleName(roleDTO.getRoleName());
        }
        if (roleDTO.getDescription() != null) {
            po.setDescription(roleDTO.getDescription());
        }
        if (roleDTO.getStatus() != null) {
            po.setStatus(roleDTO.getStatus().byteValue());
        }
        if (roleDTO.getDataScope() != null) {
            po.setDataScope(roleDTO.getDataScope().byteValue());
        }
        po.setUpdatedAt(LocalDateTime.now());

        sysRolePoMapper.updateByPrimaryKeySelective(po);
        return SysRoleMS.INSTANCE.toDTO(po);
    }

    @Override
    public Boolean deleteRole(Long roleId) {
        log.info("删除角色: {}", roleId);
        return sysRolePoMapper.deleteByPrimaryKey(roleId) > 0;
    }

    @Override
    public SysRoleDTO getRoleById(Long roleId) {
        SysRolePo po = sysRolePoMapper.selectByPrimaryKey(roleId);
        if (po == null) {
            return null;
        }
        SysRoleDTO dto = SysRoleMS.INSTANCE.toDTO(po);
        // 获取角色的权限
        dto.setPermissions(getRolePermissions(roleId));
        return dto;
    }

    private List<SysPermissionDTO> getRolePermissions(Long roleId) {
        SysRolePermissionPoExample example = new SysRolePermissionPoExample();
        example.createCriteria().andRoleIdEqualTo(roleId);
        List<SysRolePermissionPo> rolePerms = sysRolePermissionPoMapper.selectByExample(example);

        if (rolePerms.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> permIds = rolePerms.stream()
                .map(SysRolePermissionPo::getPermissionId)
                .collect(Collectors.toList());

        SysPermissionPoExample permExample = new SysPermissionPoExample();
        permExample.createCriteria().andIdIn(permIds);
        List<SysPermissionPo> perms = sysPermissionPoMapper.selectByExample(permExample);

        return SysPermissionMS.INSTANCE.toDTO(perms);
    }

    @Override
    // @Cacheable(value = "roleCache", key = "'all'", unless = "#result == null || #result.isEmpty()")
    public List<SysRoleDTO> getAllRoles() {
        SysRolePoExample example = new SysRolePoExample();
        example.setOrderByClause("id ASC");
        List<SysRolePo> roles = sysRolePoMapper.selectByExample(example);
        return SysRoleMS.INSTANCE.toDTO(roles);
    }

    @Override
    @Transactional
    public Boolean assignPermissionsToRole(Long roleId, List<Long> permissionIds) {
        log.info("为角色分配权限: roleId={}, permissionIds={}", roleId, permissionIds);

        // 删除原有权限
        SysRolePermissionPoExample example = new SysRolePermissionPoExample();
        example.createCriteria().andRoleIdEqualTo(roleId);
        sysRolePermissionPoMapper.deleteByExample(example);

        // 添加新权限
        for (Long permId : permissionIds) {
            SysRolePermissionPo po = new SysRolePermissionPo();
            po.setRoleId(roleId);
            po.setPermissionId(permId);
            po.setCreatedAt(LocalDateTime.now());
            sysRolePermissionPoMapper.insertSelective(po);
        }

        return true;
    }

    // ========== 权限管理 ==========

    @Override
    public SysPermissionDTO createPermission(SysPermissionDTO permissionDTO) {
        log.info("创建权限: {}", permissionDTO.getPermCode());

        SysPermissionPo po = SysPermissionMS.INSTANCE.toPo(permissionDTO);
        po.setCreatedAt(LocalDateTime.now());
        if (po.getStatus() == null) {
            po.setStatus((byte) 1);
        }
        if (po.getParentId() == null) {
            po.setParentId(0L);
        }
        sysPermissionPoMapper.insertSelective(po);

        return SysPermissionMS.INSTANCE.toDTO(po);
    }

    @Override
    public SysPermissionDTO updatePermission(SysPermissionDTO permissionDTO) {
        log.info("更新权限: {}", permissionDTO.getId());

        SysPermissionPo po = sysPermissionPoMapper.selectByPrimaryKey(permissionDTO.getId());
        if (po == null) {
            throw new IllegalArgumentException("权限不存在");
        }

        if (permissionDTO.getPermName() != null) {
            po.setPermName(permissionDTO.getPermName());
        }
        if (permissionDTO.getPermType() != null) {
            po.setPermType(permissionDTO.getPermType());
        }
        if (permissionDTO.getPath() != null) {
            po.setPath(permissionDTO.getPath());
        }
        if (permissionDTO.getIcon() != null) {
            po.setIcon(permissionDTO.getIcon());
        }
        if (permissionDTO.getSortOrder() != null) {
            po.setSortOrder(permissionDTO.getSortOrder());
        }
        if (permissionDTO.getStatus() != null) {
            po.setStatus(permissionDTO.getStatus().byteValue());
        }

        sysPermissionPoMapper.updateByPrimaryKeySelective(po);
        return SysPermissionMS.INSTANCE.toDTO(po);
    }

    @Override
    public Boolean deletePermission(Long permissionId) {
        log.info("删除权限: {}", permissionId);
        return sysPermissionPoMapper.deleteByPrimaryKey(permissionId) > 0;
    }

    @Override
    public SysPermissionDTO getPermissionById(Long permissionId) {
        SysPermissionPo po = sysPermissionPoMapper.selectByPrimaryKey(permissionId);
        return po != null ? SysPermissionMS.INSTANCE.toDTO(po) : null;
    }

    @Override
    public List<SysPermissionDTO> getAllPermissionsTree() {
        List<SysPermissionDTO> all = getAllPermissions();
        return buildTree(all, 0L);
    }

    private List<SysPermissionDTO> buildTree(List<SysPermissionDTO> all, Long parentId) {
        List<SysPermissionDTO> children = all.stream()
                .filter(p -> parentId.equals(p.getParentId()))
                .sorted(Comparator.comparing(SysPermissionDTO::getSortOrder, Comparator.nullsLast(Integer::compareTo)))
                .collect(Collectors.toList());

        for (SysPermissionDTO child : children) {
            child.setChildren(buildTree(all, child.getId()));
        }

        return children;
    }

    @Override
    // @Cacheable(value = "permissionCache", key = "'all'", unless = "#result == null || #result.isEmpty()")
    public List<SysPermissionDTO> getAllPermissions() {
        SysPermissionPoExample example = new SysPermissionPoExample();
        example.setOrderByClause("sort_order ASC");
        List<SysPermissionPo> perms = sysPermissionPoMapper.selectByExample(example);
        return SysPermissionMS.INSTANCE.toDTO(perms);
    }

    // ========== 用户角色分配 ==========

    @Override
    @Transactional
    @CacheEvict(value = "userAuthCache", key = "#userId")
    public Boolean assignRolesToUser(Integer userId, List<Long> roleIds) {
        log.info("为用户分配角色: userId={}, roleIds={}", userId, roleIds);

        // 删除原有角色
        SysUserRolePoExample example = new SysUserRolePoExample();
        example.createCriteria().andUserIdEqualTo(userId);
        sysUserRolePoMapper.deleteByExample(example);

        // 添加新角色
        for (Long roleId : roleIds) {
            SysUserRolePo po = new SysUserRolePo();
            po.setUserId(userId);
            po.setRoleId(roleId);
            po.setCreatedAt(LocalDateTime.now());
            sysUserRolePoMapper.insertSelective(po);
        }

        return true;
    }

    @Override
    @CacheEvict(value = "userAuthCache", key = "#userId")
    public Boolean removeRoleFromUser(Integer userId, Long roleId) {
        log.info("移除用户角色: userId={}, roleId={}", userId, roleId);

        SysUserRolePoExample example = new SysUserRolePoExample();
        example.createCriteria()
                .andUserIdEqualTo(userId)
                .andRoleIdEqualTo(roleId);
        return sysUserRolePoMapper.deleteByExample(example) > 0;
    }

    // ========== 用户机构关联 ==========

    @Override
    @Transactional
    public Boolean assignInstitutionsToUser(Integer userId, List<Long> institutionIds, Long primaryInstitutionId) {
        log.info("为用户分配机构: userId={}, institutionIds={}", userId, institutionIds);

        // 删除原有机构
        SysUserInstitutionPoExample example = new SysUserInstitutionPoExample();
        example.createCriteria().andUserIdEqualTo(userId);
        sysUserInstitutionPoMapper.deleteByExample(example);

        // 添加新机构
        for (Long instId : institutionIds) {
            SysUserInstitutionPo po = new SysUserInstitutionPo();
            po.setUserId(userId);
            po.setInstitutionId(instId);
            po.setIsPrimary(instId.equals(primaryInstitutionId) ? (byte) 1 : (byte) 0);
            po.setCreatedAt(LocalDateTime.now());
            sysUserInstitutionPoMapper.insertSelective(po);
        }

        return true;
    }

    @Override
    public List<Long> getUserInstitutionIds(Integer userId) {
        SysUserInstitutionPoExample example = new SysUserInstitutionPoExample();
        example.createCriteria().andUserIdEqualTo(userId);
        List<SysUserInstitutionPo> list = sysUserInstitutionPoMapper.selectByExample(example);

        return list.stream()
                .map(SysUserInstitutionPo::getInstitutionId)
                .collect(Collectors.toList());
    }

    @Override
    public Long getUserPrimaryInstitutionId(Integer userId) {
        SysUserInstitutionPoExample example = new SysUserInstitutionPoExample();
        example.createCriteria()
                .andUserIdEqualTo(userId)
                .andIsPrimaryEqualTo((byte) 1);
        List<SysUserInstitutionPo> list = sysUserInstitutionPoMapper.selectByExample(example);

        return list.isEmpty() ? null : list.get(0).getInstitutionId();
    }

    // ========== 机构权限管理 ==========

    @Override
    @Transactional
    public Boolean assignRolesToInstitution(Long institutionId, List<Long> roleIds) {
        log.info("为机构分配角色: institutionId={}, roleIds={}", institutionId, roleIds);

        // 删除原有角色
        SysInstitutionRolePoExample example = new SysInstitutionRolePoExample();
        example.createCriteria().andInstitutionIdEqualTo(institutionId);
        sysInstitutionRolePoMapper.deleteByExample(example);

        // 添加新角色
        for (Long roleId : roleIds) {
            SysInstitutionRolePo po = new SysInstitutionRolePo();
            po.setInstitutionId(institutionId);
            po.setRoleId(roleId);
            po.setCreateTime(LocalDateTime.now());
            sysInstitutionRolePoMapper.insertSelective(po);
        }

        return true;
    }

    @Override
    public Boolean removeRoleFromInstitution(Long institutionId, Long roleId) {
        log.info("移除机构角色: institutionId={}, roleId={}", institutionId, roleId);

        SysInstitutionRolePoExample example = new SysInstitutionRolePoExample();
        example.createCriteria()
                .andInstitutionIdEqualTo(institutionId)
                .andRoleIdEqualTo(roleId);
        return sysInstitutionRolePoMapper.deleteByExample(example) > 0;
    }

    @Override
    public List<SysRoleDTO> getInstitutionRoles(Long institutionId) {
        SysInstitutionRolePoExample example = new SysInstitutionRolePoExample();
        example.createCriteria().andInstitutionIdEqualTo(institutionId);
        List<SysInstitutionRolePo> instRoles = sysInstitutionRolePoMapper.selectByExample(example);

        if (instRoles.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> roleIds = instRoles.stream()
                .map(SysInstitutionRolePo::getRoleId)
                .collect(Collectors.toList());

        SysRolePoExample roleExample = new SysRolePoExample();
        roleExample.createCriteria()
                .andIdIn(roleIds)
                .andStatusEqualTo((byte) 1);
        List<SysRolePo> roles = sysRolePoMapper.selectByExample(roleExample);

        return SysRoleMS.INSTANCE.toDTO(roles);
    }

    @Override
    @Transactional
    public Boolean assignPermissionsToInstitution(Long institutionId, List<Long> permissionIds) {
        log.info("为机构分配权限: institutionId={}, permissionIds={}", institutionId, permissionIds);

        // 删除原有权限
        SysInstitutionPermissionPoExample example = new SysInstitutionPermissionPoExample();
        example.createCriteria().andInstitutionIdEqualTo(institutionId);
        sysInstitutionPermissionPoMapper.deleteByExample(example);

        // 添加新权限
        for (Long permId : permissionIds) {
            SysInstitutionPermissionPo po = new SysInstitutionPermissionPo();
            po.setInstitutionId(institutionId);
            po.setPermissionId(permId);
            po.setCreateTime(LocalDateTime.now());
            sysInstitutionPermissionPoMapper.insertSelective(po);
        }

        return true;
    }

    @Override
    public Boolean removePermissionFromInstitution(Long institutionId, Long permissionId) {
        log.info("移除机构权限: institutionId={}, permissionId={}", institutionId, permissionId);

        SysInstitutionPermissionPoExample example = new SysInstitutionPermissionPoExample();
        example.createCriteria()
                .andInstitutionIdEqualTo(institutionId)
                .andPermissionIdEqualTo(permissionId);
        return sysInstitutionPermissionPoMapper.deleteByExample(example) > 0;
    }

    @Override
    public List<SysPermissionDTO> getInstitutionPermissions(Long institutionId) {
        SysInstitutionPermissionPoExample example = new SysInstitutionPermissionPoExample();
        example.createCriteria().andInstitutionIdEqualTo(institutionId);
        List<SysInstitutionPermissionPo> instPerms = sysInstitutionPermissionPoMapper.selectByExample(example);

        if (instPerms.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> permIds = instPerms.stream()
                .map(SysInstitutionPermissionPo::getPermissionId)
                .collect(Collectors.toList());

        SysPermissionPoExample permExample = new SysPermissionPoExample();
        permExample.createCriteria()
                .andIdIn(permIds)
                .andStatusEqualTo((byte) 1);
        List<SysPermissionPo> perms = sysPermissionPoMapper.selectByExample(permExample);

        return SysPermissionMS.INSTANCE.toDTO(perms);
    }

    @Override
    public Set<String> getInstitutionPermissionCodes(Long institutionId) {
        Set<String> permCodes = new HashSet<>();

        // 1. 获取机构通过角色获得的权限
        List<SysRoleDTO> roles = getInstitutionRoles(institutionId);
        if (!roles.isEmpty()) {
            List<Long> roleIds = roles.stream()
                    .map(SysRoleDTO::getId)
                    .collect(Collectors.toList());

            SysRolePermissionPoExample rpExample = new SysRolePermissionPoExample();
            rpExample.createCriteria().andRoleIdIn(roleIds);
            List<SysRolePermissionPo> rolePerms = sysRolePermissionPoMapper.selectByExample(rpExample);

            if (!rolePerms.isEmpty()) {
                List<Long> permIds = rolePerms.stream()
                        .map(SysRolePermissionPo::getPermissionId)
                        .distinct()
                        .collect(Collectors.toList());

                SysPermissionPoExample permExample = new SysPermissionPoExample();
                permExample.createCriteria()
                        .andIdIn(permIds)
                        .andStatusEqualTo((byte) 1);
                List<SysPermissionPo> perms = sysPermissionPoMapper.selectByExample(permExample);

                permCodes.addAll(perms.stream()
                        .map(SysPermissionPo::getPermCode)
                        .collect(Collectors.toSet()));
            }
        }

        // 2. 获取机构直接分配的权限
        List<SysPermissionDTO> directPerms = getInstitutionPermissions(institutionId);
        permCodes.addAll(directPerms.stream()
                .map(SysPermissionDTO::getPermCode)
                .collect(Collectors.toSet()));

        return permCodes;
    }

    @Override
    public UserAuthDTO getUserAuthWithInstitution(Integer userId) {
        log.info("获取用户完整权限信息（含机构继承）: userId={}", userId);

        // 1. 获取用户基本权限
        UserAuthDTO authDTO = getUserAuth(userId);

        // 2. 获取用户所属机构的权限
        List<Long> institutionIds = authDTO.getInstitutionIds();
        if (institutionIds != null && !institutionIds.isEmpty()) {
            Set<String> allRoleCodes = new HashSet<>(authDTO.getRoleCodes());
            Set<String> allPermCodes = new HashSet<>(authDTO.getPermissionCodes());

            for (Long instId : institutionIds) {
                // 获取机构角色
                List<SysRoleDTO> instRoles = getInstitutionRoles(instId);
                allRoleCodes.addAll(instRoles.stream()
                        .map(SysRoleDTO::getRoleCode)
                        .collect(Collectors.toSet()));

                // 获取机构权限
                Set<String> instPermCodes = getInstitutionPermissionCodes(instId);
                allPermCodes.addAll(instPermCodes);
            }

            authDTO.setRoleCodes(allRoleCodes);
            authDTO.setPermissionCodes(allPermCodes);
        }

        return authDTO;
    }
}
