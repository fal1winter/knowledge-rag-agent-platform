package com.liang.bbs.user.facade.server;

import com.liang.bbs.user.facade.dto.SysPermissionDTO;
import com.liang.bbs.user.facade.dto.SysRoleDTO;
import com.liang.bbs.user.facade.dto.UserAuthDTO;

import java.util.List;
import java.util.Set;

/**
 * 权限服务接口
 */
public interface AuthService {

    // ========== 用户权限相关 ==========

    /**
     * 获取用户的完整权限信息
     */
    UserAuthDTO getUserAuth(Integer userId);

    /**
     * 获取用户的角色列表
     */
    List<SysRoleDTO> getUserRoles(Integer userId);

    /**
     * 获取用户的权限编码集合
     */
    Set<String> getUserPermissionCodes(Integer userId);

    /**
     * 检查用户是否拥有指定角色
     */
    boolean hasRole(Integer userId, String roleCode);

    /**
     * 检查用户是否拥有指定权限
     */
    boolean hasPermission(Integer userId, String permCode);

    // ========== 角色管理 ==========

    /**
     * 创建角色
     */
    SysRoleDTO createRole(SysRoleDTO roleDTO);

    /**
     * 更新角色
     */
    SysRoleDTO updateRole(SysRoleDTO roleDTO);

    /**
     * 删除角色
     */
    Boolean deleteRole(Long roleId);

    /**
     * 获取角色详情
     */
    SysRoleDTO getRoleById(Long roleId);

    /**
     * 获取所有角色
     */
    List<SysRoleDTO> getAllRoles();

    /**
     * 为角色分配权限
     */
    Boolean assignPermissionsToRole(Long roleId, List<Long> permissionIds);

    // ========== 权限管理 ==========

    /**
     * 创建权限
     */
    SysPermissionDTO createPermission(SysPermissionDTO permissionDTO);

    /**
     * 更新权限
     */
    SysPermissionDTO updatePermission(SysPermissionDTO permissionDTO);

    /**
     * 删除权限
     */
    Boolean deletePermission(Long permissionId);

    /**
     * 获取权限详情
     */
    SysPermissionDTO getPermissionById(Long permissionId);

    /**
     * 获取所有权限（树形结构）
     */
    List<SysPermissionDTO> getAllPermissionsTree();

    /**
     * 获取所有权限（平铺）
     */
    List<SysPermissionDTO> getAllPermissions();

    // ========== 用户角色分配 ==========

    /**
     * 为用户分配角色
     */
    Boolean assignRolesToUser(Integer userId, List<Long> roleIds);

    /**
     * 移除用户的角色
     */
    Boolean removeRoleFromUser(Integer userId, Long roleId);

    // ========== 用户机构关联 ==========

    /**
     * 为用户分配机构
     */
    Boolean assignInstitutionsToUser(Integer userId, List<Long> institutionIds, Long primaryInstitutionId);

    /**
     * 获取用户的机构ID列表
     */
    List<Long> getUserInstitutionIds(Integer userId);

    /**
     * 获取用户的主机构ID
     */
    Long getUserPrimaryInstitutionId(Integer userId);

    // ========== 机构权限管理 ==========

    /**
     * 为机构分配角色
     */
    Boolean assignRolesToInstitution(Long institutionId, List<Long> roleIds);

    /**
     * 移除机构的角色
     */
    Boolean removeRoleFromInstitution(Long institutionId, Long roleId);

    /**
     * 获取机构的角色列表
     */
    List<SysRoleDTO> getInstitutionRoles(Long institutionId);

    /**
     * 为机构分配权限（直接分配，不通过角色）
     */
    Boolean assignPermissionsToInstitution(Long institutionId, List<Long> permissionIds);

    /**
     * 移除机构的权限
     */
    Boolean removePermissionFromInstitution(Long institutionId, Long permissionId);

    /**
     * 获取机构的直接权限列表
     */
    List<SysPermissionDTO> getInstitutionPermissions(Long institutionId);

    /**
     * 获取机构的所有权限编码（包括通过角色获得的权限）
     */
    Set<String> getInstitutionPermissionCodes(Long institutionId);

    /**
     * 获取用户的完整权限（包含机构继承的权限）
     * 用户权限 = 用户直接角色权限 + 所属机构角色权限 + 所属机构直接权限
     */
    UserAuthDTO getUserAuthWithInstitution(Integer userId);
}
