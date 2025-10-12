package com.liang.bbs.user.facade.server;

import com.liang.bbs.user.facade.dto.UserInstitutionDTO;

import java.util.List;

/**
 * 用户机构管理服务接口
 *
 */
public interface UserInstitutionService {

    /**
     * 添加用户到机构
     *
     * @param userId 用户ID
     * @param institutionId 机构ID
     * @param role 角色: owner/admin/member
     * @param isPrimary 是否为主机构
     * @return 关联信息
     */
    UserInstitutionDTO addUserToInstitution(Integer userId, Long institutionId, String role, Boolean isPrimary);

    /**
     * 从机构移除用户
     *
     * @param userId 用户ID
     * @param institutionId 机构ID
     * @param operatorUserId 操作者用户ID
     * @return 是否成功
     */
    Boolean removeUserFromInstitution(Integer userId, Long institutionId, Integer operatorUserId);

    /**
     * 更新用户在机构中的角色
     *
     * @param userId 用户ID
     * @param institutionId 机构ID
     * @param newRole 新角色
     * @param operatorUserId 操作者用户ID
     * @return 是否成功
     */
    Boolean updateUserRole(Integer userId, Long institutionId, String newRole, Integer operatorUserId);

    /**
     * 设置用户的主机构
     *
     * @param userId 用户ID
     * @param institutionId 机构ID
     * @return 是否成功
     */
    Boolean setPrimaryInstitution(Integer userId, Long institutionId);

    /**
     * 获取用户的所有机构
     *
     * @param userId 用户ID
     * @return 机构列表
     */
    List<UserInstitutionDTO> getUserInstitutions(Integer userId);

    /**
     * 获取用户管理的机构（admin或owner）
     *
     * @param userId 用户ID
     * @return 机构列表
     */
    List<UserInstitutionDTO> getUserManagedInstitutions(Integer userId);

    /**
     * 获取机构的所有成员
     *
     * @param institutionId 机构ID
     * @param operatorUserId 操作者用户ID（需要是管理员）
     * @return 成员列表
     */
    List<UserInstitutionDTO> getInstitutionMembers(Long institutionId, Integer operatorUserId);

    /**
     * 获取用户的主机构
     *
     * @param userId 用户ID
     * @return 主机构信息，如果没有则返回null
     */
    UserInstitutionDTO getUserPrimaryInstitution(Integer userId);
}
