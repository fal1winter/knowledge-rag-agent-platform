package com.liang.bbs.user.facade.server;

import com.liang.bbs.user.facade.dto.DeveloperUserDTO;
import com.liang.bbs.user.facade.dto.ResourcePermissionDTO;

import java.util.List;

/**
 * 资源权限服务接口
 * 管理房间、论文、学者等资源的所有者和管理员权限
 */
public interface ResourcePermissionService {

    // ==================== 权限检查 ====================

    /**
     * 检查用户是否为开发者（拥有所有权限）
     */
    boolean isDeveloper(Integer userId);

    /**
     * 检查用户对资源是否拥有指定权限
     *
     * @param userId       用户ID
     * @param resourceType 资源类型（chatroom/paper/scholar）
     * @param resourceId   资源ID
     * @param permission   权限编码
     * @return true-有权限 false-无权限
     */
    boolean hasPermission(Integer userId, String resourceType, Long resourceId, String permission);

    /**
     * 检查用户是否为资源的所有者
     */
    boolean isOwner(Integer userId, String resourceType, Long resourceId);

    /**
     * 检查用户是否为资源的管理员（包含所有者）
     */
    boolean isAdmin(Integer userId, String resourceType, Long resourceId);

    /**
     * 获取用户对资源的所有权限
     */
    List<String> getUserPermissions(Integer userId, String resourceType, Long resourceId);

    /**
     * 获取用户在资源中的角色类型
     *
     * @return owner/admin/member/null
     */
    String getUserRole(Integer userId, String resourceType, Long resourceId);

    // ==================== 权限授予 ====================

    /**
     * 设置资源所有者（创建资源时调用）
     *
     * @param resourceType 资源类型
     * @param resourceId   资源ID
     * @param userId       用户ID
     * @return 权限记录
     */
    ResourcePermissionDTO setOwner(String resourceType, Long resourceId, Integer userId);

    /**
     * 转让所有权
     *
     * @param resourceType  资源类型
     * @param resourceId    资源ID
     * @param newOwnerId    新所有者用户ID
     * @param operatorId    操作人用户ID（必须是当前所有者或开发者）
     * @return true-成功 false-失败
     */
    boolean transferOwnership(String resourceType, Long resourceId, Integer newOwnerId, Integer operatorId);

    /**
     * 添加管理员
     *
     * @param resourceType 资源类型
     * @param resourceId   资源ID
     * @param userId       用户ID
     * @param operatorId   操作人用户ID（必须是所有者、管理员或开发者）
     * @return 权限记录
     */
    ResourcePermissionDTO addAdmin(String resourceType, Long resourceId, Integer userId, Integer operatorId);

    /**
     * 移除管理员
     *
     * @param resourceType 资源类型
     * @param resourceId   资源ID
     * @param userId       要移除的用户ID
     * @param operatorId   操作人用户ID
     * @return true-成功 false-失败
     */
    boolean removeAdmin(String resourceType, Long resourceId, Integer userId, Integer operatorId);

    /**
     * 添加成员
     */
    ResourcePermissionDTO addMember(String resourceType, Long resourceId, Integer userId, Integer operatorId);

    /**
     * 移除成员
     */
    boolean removeMember(String resourceType, Long resourceId, Integer userId, Integer operatorId);

    /**
     * 授予特定权限
     *
     * @param resourceType 资源类型
     * @param resourceId   资源ID
     * @param userId       用户ID
     * @param permissions  要授予的权限列表
     * @param operatorId   操作人用户ID
     * @return 更新后的权限记录
     */
    ResourcePermissionDTO grantPermissions(String resourceType, Long resourceId, Integer userId,
                                           List<String> permissions, Integer operatorId);

    /**
     * 撤销特定权限
     */
    ResourcePermissionDTO revokePermissions(String resourceType, Long resourceId, Integer userId,
                                            List<String> permissions, Integer operatorId);

    // ==================== 权限查询 ====================

    /**
     * 获取资源的所有权限记录
     */
    List<ResourcePermissionDTO> getResourcePermissions(String resourceType, Long resourceId);

    /**
     * 获取资源的所有者
     */
    ResourcePermissionDTO getOwner(String resourceType, Long resourceId);

    /**
     * 获取资源的所有管理员
     */
    List<ResourcePermissionDTO> getAdmins(String resourceType, Long resourceId);

    /**
     * 获取资源的所有成员
     */
    List<ResourcePermissionDTO> getMembers(String resourceType, Long resourceId);

    /**
     * 获取用户管理的所有资源
     *
     * @param userId       用户ID
     * @param resourceType 资源类型（可选，为null则返回所有类型）
     * @return 权限记录列表
     */
    List<ResourcePermissionDTO> getUserManagedResources(Integer userId, String resourceType);

    /**
     * 获取用户拥有的所有资源（作为所有者）
     */
    List<ResourcePermissionDTO> getUserOwnedResources(Integer userId, String resourceType);

    // ==================== 开发者管理 ====================

    /**
     * 添加开发者
     *
     * @param userId      用户ID
     * @param devLevel    开发者级别
     * @param description 说明
     * @param operatorId  操作人用户ID（必须是超级管理员或已有开发者）
     * @return 开发者记录
     */
    DeveloperUserDTO addDeveloper(Integer userId, Integer devLevel, String description, Integer operatorId);

    /**
     * 移除开发者
     */
    boolean removeDeveloper(Integer userId, Integer operatorId);

    /**
     * 获取所有开发者
     */
    List<DeveloperUserDTO> getAllDevelopers();

    /**
     * 获取开发者信息
     */
    DeveloperUserDTO getDeveloper(Integer userId);

    /**
     * 更新开发者级别
     */
    DeveloperUserDTO updateDeveloperLevel(Integer userId, Integer devLevel, Integer operatorId);
}
