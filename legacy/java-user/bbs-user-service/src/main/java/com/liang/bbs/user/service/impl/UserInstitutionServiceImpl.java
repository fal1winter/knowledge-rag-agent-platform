package com.liang.bbs.user.service.impl;

import com.liang.bbs.common.enums.ResponseCode;
import com.liang.bbs.common.web.exception.BusinessException;
import com.liang.bbs.user.facade.dto.UserInstitutionDTO;
import com.liang.bbs.user.facade.server.UserInstitutionService;
import com.liang.bbs.user.persistence.entity.SysUserInstitutionPo;
import com.liang.bbs.user.persistence.entity.SysUserInstitutionPoExample;
import com.liang.bbs.user.persistence.mapper.SysUserInstitutionPoMapper;
import com.liang.bbs.user.service.utils.InstitutionPermissionChecker;
import com.liang.bbs.article.facade.dto.InstitutionDTO;
import com.liang.bbs.article.facade.server.InstitutionService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.Service;
import org.apache.dubbo.config.annotation.Reference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户机构管理服务实现
 *
 */
@Slf4j
@Service
public class UserInstitutionServiceImpl implements UserInstitutionService {

    @Autowired
    private SysUserInstitutionPoMapper userInstitutionMapper;

    @Reference(check = false)
    private InstitutionService institutionService;

    @Autowired
    private InstitutionPermissionChecker permissionChecker;

    @Override
    @Transactional
    public UserInstitutionDTO addUserToInstitution(Integer userId, Long institutionId, String role, Boolean isPrimary) {
        log.info("添加用户到机构: userId={}, institutionId={}, role={}", userId, institutionId, role);

        // 验证角色
        if (!isValidRole(role)) {
            throw BusinessException.build(ResponseCode.DATA_ILLEGAL, "无效的角色类型");
        }

        // 检查机构是否存在
        InstitutionDTO institution = institutionService.getInstitutionById(institutionId);
        if (institution == null) {
            throw BusinessException.build(ResponseCode.DATA_ILLEGAL, "机构不存在");
        }

        // 检查用户是否已经在该机构中
        SysUserInstitutionPoExample example = new SysUserInstitutionPoExample();
        example.createCriteria()
                .andUserIdEqualTo(userId)
                .andInstitutionIdEqualTo(institutionId);

        List<SysUserInstitutionPo> existing = userInstitutionMapper.selectByExample(example);
        if (!existing.isEmpty()) {
            throw BusinessException.build(ResponseCode.DATA_ILLEGAL, "用户已在该机构中");
        }

        // 如果是普通用户（member），检查是否已有主机构
        if ("member".equals(role) && Boolean.TRUE.equals(isPrimary)) {
            Long existingPrimary = permissionChecker.getUserPrimaryInstitution(userId);
            if (existingPrimary != null) {
                throw BusinessException.build(ResponseCode.DATA_ILLEGAL, "普通用户只能有一个主机构");
            }
        }

        // 创建关联
        SysUserInstitutionPo po = SysUserInstitutionPo.builder()
                .userId(userId)
                .institutionId(institutionId)
                .role(role)
                .isPrimary(Boolean.TRUE.equals(isPrimary) ? (byte) 1 : (byte) 0)
                .createdAt(LocalDateTime.now())
                .build();

        userInstitutionMapper.insertSelective(po);

        return toDTO(po, institution.getName());
    }

    @Override
    @Transactional
    public Boolean removeUserFromInstitution(Integer userId, Long institutionId, Integer operatorUserId) {
        log.info("从机构移除用户: userId={}, institutionId={}, operator={}", userId, institutionId, operatorUserId);

        // 检查操作者权限（必须是管理员或所有者）
        permissionChecker.requireInstitutionAdmin(operatorUserId, institutionId);

        // 不能移除所有者
        if (permissionChecker.isInstitutionOwner(userId, institutionId)) {
            throw BusinessException.build(ResponseCode.OPERATE_FAIL, "不能移除机构所有者");
        }

        SysUserInstitutionPoExample example = new SysUserInstitutionPoExample();
        example.createCriteria()
                .andUserIdEqualTo(userId)
                .andInstitutionIdEqualTo(institutionId);

        int deleted = userInstitutionMapper.deleteByExample(example);
        return deleted > 0;
    }

    @Override
    @Transactional
    public Boolean updateUserRole(Integer userId, Long institutionId, String newRole, Integer operatorUserId) {
        log.info("更新用户角色: userId={}, institutionId={}, newRole={}, operator={}",
                userId, institutionId, newRole, operatorUserId);

        // 验证新角色
        if (!isValidRole(newRole)) {
            throw BusinessException.build(ResponseCode.DATA_ILLEGAL, "无效的角色类型");
        }

        // 检查操作者权限
        if ("owner".equals(newRole)) {
            // 只有所有者可以转让所有权
            permissionChecker.requireInstitutionOwner(operatorUserId, institutionId);
        } else {
            // 管理员可以修改其他角色
            permissionChecker.requireInstitutionAdmin(operatorUserId, institutionId);
        }

        SysUserInstitutionPoExample example = new SysUserInstitutionPoExample();
        example.createCriteria()
                .andUserIdEqualTo(userId)
                .andInstitutionIdEqualTo(institutionId);

        List<SysUserInstitutionPo> relations = userInstitutionMapper.selectByExample(example);
        if (relations.isEmpty()) {
            throw BusinessException.build(ResponseCode.DATA_ILLEGAL, "用户不在该机构中");
        }

        SysUserInstitutionPo po = relations.get(0);
        po.setRole(newRole);

        int updated = userInstitutionMapper.updateByPrimaryKeySelective(po);
        return updated > 0;
    }

    @Override
    @Transactional
    public Boolean setPrimaryInstitution(Integer userId, Long institutionId) {
        log.info("设置主机构: userId={}, institutionId={}", userId, institutionId);

        // 检查用户是否在该机构中
        SysUserInstitutionPoExample checkExample = new SysUserInstitutionPoExample();
        checkExample.createCriteria()
                .andUserIdEqualTo(userId)
                .andInstitutionIdEqualTo(institutionId);

        List<SysUserInstitutionPo> relations = userInstitutionMapper.selectByExample(checkExample);
        if (relations.isEmpty()) {
            throw BusinessException.build(ResponseCode.DATA_ILLEGAL, "用户不在该机构中");
        }

        // 检查用户角色，如果是member，需要先取消其他主机构
        String role = relations.get(0).getRole();
        if ("member".equals(role)) {
            // 取消其他主机构
            SysUserInstitutionPoExample clearExample = new SysUserInstitutionPoExample();
            clearExample.createCriteria()
                    .andUserIdEqualTo(userId)
                    .andIsPrimaryEqualTo((byte) 1);

            List<SysUserInstitutionPo> primaryRelations = userInstitutionMapper.selectByExample(clearExample);
            for (SysUserInstitutionPo pr : primaryRelations) {
                pr.setIsPrimary((byte) 0);
                userInstitutionMapper.updateByPrimaryKeySelective(pr);
            }
        }

        // 设置新的主机构
        SysUserInstitutionPo po = relations.get(0);
        po.setIsPrimary((byte) 1);
        int updated = userInstitutionMapper.updateByPrimaryKeySelective(po);

        return updated > 0;
    }

    @Override
    public List<UserInstitutionDTO> getUserInstitutions(Integer userId) {
        log.info("获取用户的所有机构: userId={}", userId);

        SysUserInstitutionPoExample example = new SysUserInstitutionPoExample();
        example.createCriteria().andUserIdEqualTo(userId);

        List<SysUserInstitutionPo> relations = userInstitutionMapper.selectByExample(example);

        return relations.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<UserInstitutionDTO> getUserManagedInstitutions(Integer userId) {
        log.info("获取用户管理的机构: userId={}", userId);

        SysUserInstitutionPoExample example = new SysUserInstitutionPoExample();
        example.createCriteria().andUserIdEqualTo(userId);

        List<SysUserInstitutionPo> relations = userInstitutionMapper.selectByExample(example);

        return relations.stream()
                .filter(r -> "admin".equals(r.getRole()) || "owner".equals(r.getRole()))
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<UserInstitutionDTO> getInstitutionMembers(Long institutionId, Integer operatorUserId) {
        log.info("获取机构成员: institutionId={}, operator={}", institutionId, operatorUserId);

        // 检查操作者权限
        permissionChecker.requireInstitutionAdmin(operatorUserId, institutionId);

        SysUserInstitutionPoExample example = new SysUserInstitutionPoExample();
        example.createCriteria().andInstitutionIdEqualTo(institutionId);

        List<SysUserInstitutionPo> relations = userInstitutionMapper.selectByExample(example);

        return relations.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public UserInstitutionDTO getUserPrimaryInstitution(Integer userId) {
        log.info("获取用户主机构: userId={}", userId);

        SysUserInstitutionPoExample example = new SysUserInstitutionPoExample();
        example.createCriteria()
                .andUserIdEqualTo(userId)
                .andIsPrimaryEqualTo((byte) 1);

        List<SysUserInstitutionPo> relations = userInstitutionMapper.selectByExample(example);

        return relations.isEmpty() ? null : toDTO(relations.get(0));
    }

    // ==================== 私有方法 ====================

    private boolean isValidRole(String role) {
        return "owner".equals(role) || "admin".equals(role) || "member".equals(role);
    }

    private UserInstitutionDTO toDTO(SysUserInstitutionPo po) {
        if (po == null) {
            return null;
        }

        UserInstitutionDTO dto = new UserInstitutionDTO();
        dto.setId(po.getId());
        dto.setUserId(po.getUserId());
        dto.setInstitutionId(po.getInstitutionId());
        dto.setIsPrimary(po.getIsPrimary());
        dto.setRole(po.getRole());
        dto.setCreatedAt(po.getCreatedAt());

        // 通过 Dubbo 服务查询机构名称
        try {
            InstitutionDTO institution = institutionService.getInstitutionById(po.getInstitutionId());
            if (institution != null) {
                dto.setInstitutionName(institution.getName());
            }
        } catch (Exception e) {
            log.warn("查询机构名称失败: institutionId={}", po.getInstitutionId(), e);
        }

        return dto;
    }

    private UserInstitutionDTO toDTO(SysUserInstitutionPo po, String institutionName) {
        UserInstitutionDTO dto = toDTO(po);
        if (dto != null) {
            dto.setInstitutionName(institutionName);
        }
        return dto;
    }
}
