package com.liang.bbs.user.service.impl;

import com.alibaba.fastjson.JSON;
import com.liang.bbs.user.facade.dto.DeveloperUserDTO;
import com.liang.bbs.user.facade.dto.ResourcePermissionDTO;
import com.liang.bbs.user.facade.enums.ResourceRoleEnum;
import com.liang.bbs.user.facade.server.ResourcePermissionService;
import com.liang.bbs.user.persistence.entity.*;
import com.liang.bbs.user.persistence.mapper.DeveloperUserPoMapper;
import com.liang.bbs.user.persistence.mapper.ResourcePermissionPoMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.Service;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 资源权限服务实现类
 */
@Component
@Slf4j
@Service
public class ResourcePermissionServiceImpl implements ResourcePermissionService {

    @Autowired
    private ResourcePermissionPoMapper resourcePermissionPoMapper;

    @Autowired
    private DeveloperUserPoMapper developerUserPoMapper;

    // ==================== 权限检查 ====================

    @Override
    public boolean isDeveloper(Integer userId) {
        if (userId == null) {
            return false;
        }
        DeveloperUserPoExample example = new DeveloperUserPoExample();
        example.createCriteria().andUserIdEqualTo(userId).andStatusEqualTo(1);
        return developerUserPoMapper.countByExample(example) > 0;
    }

    @Override
    public boolean hasPermission(Integer userId, String resourceType, Long resourceId, String permission) {
        // 开发者拥有所有权限
        if (isDeveloper(userId)) {
            return true;
        }

        List<String> permissions = getUserPermissions(userId, resourceType, resourceId);
        return permissions.contains(permission);
    }

    @Override
    public boolean isOwner(Integer userId, String resourceType, Long resourceId) {
        if (isDeveloper(userId)) {
            return true;
        }
        ResourcePermissionPo po = getPermissionRecord(userId, resourceType, resourceId);
        return po != null && "owner".equals(po.getRoleType());
    }

    @Override
    public boolean isAdmin(Integer userId, String resourceType, Long resourceId) {
        if (isDeveloper(userId)) {
            return true;
        }
        ResourcePermissionPo po = getPermissionRecord(userId, resourceType, resourceId);
        return po != null && ("owner".equals(po.getRoleType()) || "admin".equals(po.getRoleType()));
    }

    @Override
    public List<String> getUserPermissions(Integer userId, String resourceType, Long resourceId) {
        // 开发者拥有所有权限
        if (isDeveloper(userId)) {
            return ResourceRoleEnum.OWNER.getDefaultPermissions();
        }

        ResourcePermissionPo po = getPermissionRecord(userId, resourceType, resourceId);
        if (po == null) {
            return Collections.emptyList();
        }

        // 如果有自定义权限，返回自定义权限
        if (StringUtils.hasText(po.getPermissions())) {
            return JSON.parseArray(po.getPermissions(), String.class);
        }

        // 否则返回角色默认权限
        ResourceRoleEnum role = ResourceRoleEnum.fromCode(po.getRoleType());
        return role != null ? role.getDefaultPermissions() : Collections.emptyList();
    }

    @Override
    public String getUserRole(Integer userId, String resourceType, Long resourceId) {
        if (isDeveloper(userId)) {
            return "developer";
        }
        ResourcePermissionPo po = getPermissionRecord(userId, resourceType, resourceId);
        return po != null ? po.getRoleType() : null;
    }

    // ==================== 权限授予 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResourcePermissionDTO setOwner(String resourceType, Long resourceId, Integer userId) {
        log.info("设置资源所有者: resourceType={}, resourceId={}, userId={}", resourceType, resourceId, userId);

        // 检查是否已有所有者
        ResourcePermissionDTO existingOwner = getOwner(resourceType, resourceId);
        if (existingOwner != null) {
            throw new RuntimeException("资源已有所有者");
        }

        ResourcePermissionPo po = new ResourcePermissionPo();
        po.setResourceType(resourceType);
        po.setResourceId(resourceId);
        po.setUserId(userId);
        po.setRoleType("owner");
        po.setCreateTime(LocalDateTime.now());
        po.setUpdateTime(LocalDateTime.now());

        resourcePermissionPoMapper.insertSelective(po);
        return toDTO(po);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean transferOwnership(String resourceType, Long resourceId, Integer newOwnerId, Integer operatorId) {
        log.info("转让所有权: resourceType={}, resourceId={}, newOwnerId={}, operatorId={}",
                resourceType, resourceId, newOwnerId, operatorId);

        // 检查操作权限（必须是当前所有者或开发者）
        if (!isDeveloper(operatorId) && !isOwner(operatorId, resourceType, resourceId)) {
            throw new RuntimeException("无权转让所有权");
        }

        // 获取当前所有者
        ResourcePermissionDTO currentOwner = getOwner(resourceType, resourceId);
        if (currentOwner == null) {
            throw new RuntimeException("资源没有所有者");
        }

        // 如果新所有者和当前所有者相同，直接返回
        if (currentOwner.getUserId().equals(newOwnerId)) {
            return true;
        }

        // 将当前所有者降为管理员
        ResourcePermissionPo currentOwnerPo = new ResourcePermissionPo();
        currentOwnerPo.setId(currentOwner.getId());
        currentOwnerPo.setRoleType("admin");
        currentOwnerPo.setUpdateTime(LocalDateTime.now());
        resourcePermissionPoMapper.updateByPrimaryKeySelective(currentOwnerPo);

        // 检查新所有者是否已有权限记录
        ResourcePermissionPo newOwnerPo = getPermissionRecord(newOwnerId, resourceType, resourceId);
        if (newOwnerPo != null) {
            // 升级为所有者
            newOwnerPo.setRoleType("owner");
            newOwnerPo.setUpdateTime(LocalDateTime.now());
            resourcePermissionPoMapper.updateByPrimaryKeySelective(newOwnerPo);
        } else {
            // 创建新的所有者记录
            newOwnerPo = new ResourcePermissionPo();
            newOwnerPo.setResourceType(resourceType);
            newOwnerPo.setResourceId(resourceId);
            newOwnerPo.setUserId(newOwnerId);
            newOwnerPo.setRoleType("owner");
            newOwnerPo.setGrantedBy(operatorId);
            newOwnerPo.setCreateTime(LocalDateTime.now());
            newOwnerPo.setUpdateTime(LocalDateTime.now());
            resourcePermissionPoMapper.insertSelective(newOwnerPo);
        }

        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResourcePermissionDTO addAdmin(String resourceType, Long resourceId, Integer userId, Integer operatorId) {
        log.info("添加管理员: resourceType={}, resourceId={}, userId={}, operatorId={}",
                resourceType, resourceId, userId, operatorId);

        // 检查操作权限
        if (!isDeveloper(operatorId) && !isAdmin(operatorId, resourceType, resourceId)) {
            throw new RuntimeException("无权添加管理员");
        }

        // 检查是否已有权限记录
        ResourcePermissionPo existing = getPermissionRecord(userId, resourceType, resourceId);
        if (existing != null) {
            if ("owner".equals(existing.getRoleType())) {
                throw new RuntimeException("该用户已是所有者");
            }
            if ("admin".equals(existing.getRoleType())) {
                return toDTO(existing);
            }
            // 升级为管理员
            existing.setRoleType("admin");
            existing.setGrantedBy(operatorId);
            existing.setUpdateTime(LocalDateTime.now());
            resourcePermissionPoMapper.updateByPrimaryKeySelective(existing);
            return toDTO(existing);
        }

        // 创建管理员记录
        ResourcePermissionPo po = new ResourcePermissionPo();
        po.setResourceType(resourceType);
        po.setResourceId(resourceId);
        po.setUserId(userId);
        po.setRoleType("admin");
        po.setGrantedBy(operatorId);
        po.setCreateTime(LocalDateTime.now());
        po.setUpdateTime(LocalDateTime.now());
        resourcePermissionPoMapper.insertSelective(po);

        return toDTO(po);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeAdmin(String resourceType, Long resourceId, Integer userId, Integer operatorId) {
        log.info("移除管理员: resourceType={}, resourceId={}, userId={}, operatorId={}",
                resourceType, resourceId, userId, operatorId);

        // 检查操作权限（必须是所有者或开发者）
        if (!isDeveloper(operatorId) && !isOwner(operatorId, resourceType, resourceId)) {
            throw new RuntimeException("无权移除管理员");
        }

        ResourcePermissionPo po = getPermissionRecord(userId, resourceType, resourceId);
        if (po == null || !"admin".equals(po.getRoleType())) {
            return false;
        }

        // 降级为成员或直接删除
        resourcePermissionPoMapper.deleteByPrimaryKey(po.getId());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResourcePermissionDTO addMember(String resourceType, Long resourceId, Integer userId, Integer operatorId) {
        log.info("添加成员: resourceType={}, resourceId={}, userId={}, operatorId={}",
                resourceType, resourceId, userId, operatorId);

        // 检查操作权限
        if (!isDeveloper(operatorId) && !hasPermission(operatorId, resourceType, resourceId, "invite")) {
            throw new RuntimeException("无权邀请成员");
        }

        // 检查是否已有权限记录
        ResourcePermissionPo existing = getPermissionRecord(userId, resourceType, resourceId);
        if (existing != null) {
            return toDTO(existing);
        }

        // 创建成员记录
        ResourcePermissionPo po = new ResourcePermissionPo();
        po.setResourceType(resourceType);
        po.setResourceId(resourceId);
        po.setUserId(userId);
        po.setRoleType("member");
        po.setGrantedBy(operatorId);
        po.setCreateTime(LocalDateTime.now());
        po.setUpdateTime(LocalDateTime.now());
        resourcePermissionPoMapper.insertSelective(po);

        return toDTO(po);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeMember(String resourceType, Long resourceId, Integer userId, Integer operatorId) {
        log.info("移除成员: resourceType={}, resourceId={}, userId={}, operatorId={}",
                resourceType, resourceId, userId, operatorId);

        // 检查操作权限
        if (!isDeveloper(operatorId) && !hasPermission(operatorId, resourceType, resourceId, "kick")) {
            throw new RuntimeException("无权移除成员");
        }

        ResourcePermissionPo po = getPermissionRecord(userId, resourceType, resourceId);
        if (po == null) {
            return false;
        }

        // 不能移除所有者和管理员
        if ("owner".equals(po.getRoleType()) || "admin".equals(po.getRoleType())) {
            throw new RuntimeException("不能移除所有者或管理员");
        }

        resourcePermissionPoMapper.deleteByPrimaryKey(po.getId());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResourcePermissionDTO grantPermissions(String resourceType, Long resourceId, Integer userId,
                                                   List<String> permissions, Integer operatorId) {
        log.info("授予权限: resourceType={}, resourceId={}, userId={}, permissions={}, operatorId={}",
                resourceType, resourceId, userId, permissions, operatorId);

        // 检查操作权限
        if (!isDeveloper(operatorId) && !isAdmin(operatorId, resourceType, resourceId)) {
            throw new RuntimeException("无权授予权限");
        }

        ResourcePermissionPo po = getPermissionRecord(userId, resourceType, resourceId);
        if (po == null) {
            throw new RuntimeException("用户没有该资源的权限记录");
        }

        // 合并权限
        Set<String> currentPermissions = new HashSet<>();
        if (StringUtils.hasText(po.getPermissions())) {
            currentPermissions.addAll(JSON.parseArray(po.getPermissions(), String.class));
        } else {
            ResourceRoleEnum role = ResourceRoleEnum.fromCode(po.getRoleType());
            if (role != null) {
                currentPermissions.addAll(role.getDefaultPermissions());
            }
        }
        currentPermissions.addAll(permissions);

        po.setPermissions(JSON.toJSONString(new ArrayList<>(currentPermissions)));
        po.setUpdateTime(LocalDateTime.now());
        resourcePermissionPoMapper.updateByPrimaryKeySelective(po);

        return toDTO(po);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResourcePermissionDTO revokePermissions(String resourceType, Long resourceId, Integer userId,
                                                    List<String> permissions, Integer operatorId) {
        log.info("撤销权限: resourceType={}, resourceId={}, userId={}, permissions={}, operatorId={}",
                resourceType, resourceId, userId, permissions, operatorId);

        // 检查操作权限
        if (!isDeveloper(operatorId) && !isAdmin(operatorId, resourceType, resourceId)) {
            throw new RuntimeException("无权撤销权限");
        }

        ResourcePermissionPo po = getPermissionRecord(userId, resourceType, resourceId);
        if (po == null) {
            throw new RuntimeException("用户没有该资源的权限记录");
        }

        // 移除权限
        Set<String> currentPermissions = new HashSet<>();
        if (StringUtils.hasText(po.getPermissions())) {
            currentPermissions.addAll(JSON.parseArray(po.getPermissions(), String.class));
        } else {
            ResourceRoleEnum role = ResourceRoleEnum.fromCode(po.getRoleType());
            if (role != null) {
                currentPermissions.addAll(role.getDefaultPermissions());
            }
        }
        currentPermissions.removeAll(permissions);

        po.setPermissions(JSON.toJSONString(new ArrayList<>(currentPermissions)));
        po.setUpdateTime(LocalDateTime.now());
        resourcePermissionPoMapper.updateByPrimaryKeySelective(po);

        return toDTO(po);
    }

    // ==================== 权限查询 ====================

    @Override
    public List<ResourcePermissionDTO> getResourcePermissions(String resourceType, Long resourceId) {
        ResourcePermissionPoExample example = new ResourcePermissionPoExample();
        example.createCriteria()
                .andResourceTypeEqualTo(resourceType)
                .andResourceIdEqualTo(resourceId);
        example.setOrderByClause("role_type ASC, create_time ASC");

        List<ResourcePermissionPo> poList = resourcePermissionPoMapper.selectByExample(example);
        return poList.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public ResourcePermissionDTO getOwner(String resourceType, Long resourceId) {
        ResourcePermissionPoExample example = new ResourcePermissionPoExample();
        example.createCriteria()
                .andResourceTypeEqualTo(resourceType)
                .andResourceIdEqualTo(resourceId)
                .andRoleTypeEqualTo("owner");

        List<ResourcePermissionPo> poList = resourcePermissionPoMapper.selectByExample(example);
        return poList.isEmpty() ? null : toDTO(poList.get(0));
    }

    @Override
    public List<ResourcePermissionDTO> getAdmins(String resourceType, Long resourceId) {
        ResourcePermissionPoExample example = new ResourcePermissionPoExample();
        example.createCriteria()
                .andResourceTypeEqualTo(resourceType)
                .andResourceIdEqualTo(resourceId)
                .andRoleTypeIn(Arrays.asList("owner", "admin"));

        List<ResourcePermissionPo> poList = resourcePermissionPoMapper.selectByExample(example);
        return poList.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public List<ResourcePermissionDTO> getMembers(String resourceType, Long resourceId) {
        ResourcePermissionPoExample example = new ResourcePermissionPoExample();
        example.createCriteria()
                .andResourceTypeEqualTo(resourceType)
                .andResourceIdEqualTo(resourceId)
                .andRoleTypeEqualTo("member");

        List<ResourcePermissionPo> poList = resourcePermissionPoMapper.selectByExample(example);
        return poList.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public List<ResourcePermissionDTO> getUserManagedResources(Integer userId, String resourceType) {
        ResourcePermissionPoExample example = new ResourcePermissionPoExample();
        ResourcePermissionPoExample.Criteria criteria = example.createCriteria()
                .andUserIdEqualTo(userId)
                .andRoleTypeIn(Arrays.asList("owner", "admin"));

        if (StringUtils.hasText(resourceType)) {
            criteria.andResourceTypeEqualTo(resourceType);
        }

        List<ResourcePermissionPo> poList = resourcePermissionPoMapper.selectByExample(example);
        return poList.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public List<ResourcePermissionDTO> getUserOwnedResources(Integer userId, String resourceType) {
        ResourcePermissionPoExample example = new ResourcePermissionPoExample();
        ResourcePermissionPoExample.Criteria criteria = example.createCriteria()
                .andUserIdEqualTo(userId)
                .andRoleTypeEqualTo("owner");

        if (StringUtils.hasText(resourceType)) {
            criteria.andResourceTypeEqualTo(resourceType);
        }

        List<ResourcePermissionPo> poList = resourcePermissionPoMapper.selectByExample(example);
        return poList.stream().map(this::toDTO).collect(Collectors.toList());
    }

    // ==================== 开发者管理 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeveloperUserDTO addDeveloper(Integer userId, Integer devLevel, String description, Integer operatorId) {
        log.info("添加开发者: userId={}, devLevel={}, operatorId={}", userId, devLevel, operatorId);

        // 检查操作权限（必须是已有开发者或第一个开发者）
        DeveloperUserPoExample checkExample = new DeveloperUserPoExample();
        checkExample.createCriteria().andStatusEqualTo(1);
        long count = developerUserPoMapper.countByExample(checkExample);

        if (count > 0 && !isDeveloper(operatorId)) {
            throw new RuntimeException("无权添加开发者");
        }

        // 检查是否已是开发者
        DeveloperUserPoExample existExample = new DeveloperUserPoExample();
        existExample.createCriteria().andUserIdEqualTo(userId);
        List<DeveloperUserPo> existList = developerUserPoMapper.selectByExample(existExample);

        if (!existList.isEmpty()) {
            DeveloperUserPo existing = existList.get(0);
            if (existing.getStatus() == 1) {
                return toDeveloperDTO(existing);
            }
            // 重新启用
            existing.setStatus(1);
            existing.setDevLevel(devLevel);
            existing.setDescription(description);
            existing.setUpdateTime(LocalDateTime.now());
            developerUserPoMapper.updateByPrimaryKeySelective(existing);
            return toDeveloperDTO(existing);
        }

        // 创建新开发者
        DeveloperUserPo po = new DeveloperUserPo();
        po.setUserId(userId);
        po.setDevLevel(devLevel != null ? devLevel : 1);
        po.setDescription(description);
        po.setStatus(1);
        po.setCreateTime(LocalDateTime.now());
        po.setUpdateTime(LocalDateTime.now());
        developerUserPoMapper.insertSelective(po);

        return toDeveloperDTO(po);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeDeveloper(Integer userId, Integer operatorId) {
        log.info("移除开发者: userId={}, operatorId={}", userId, operatorId);

        // 检查操作权限
        if (!isDeveloper(operatorId)) {
            throw new RuntimeException("无权移除开发者");
        }

        // 不能移除自己
        if (userId.equals(operatorId)) {
            throw new RuntimeException("不能移除自己的开发者权限");
        }

        DeveloperUserPoExample example = new DeveloperUserPoExample();
        example.createCriteria().andUserIdEqualTo(userId);
        List<DeveloperUserPo> poList = developerUserPoMapper.selectByExample(example);

        if (poList.isEmpty()) {
            return false;
        }

        DeveloperUserPo po = poList.get(0);
        po.setStatus(0);
        po.setUpdateTime(LocalDateTime.now());
        developerUserPoMapper.updateByPrimaryKeySelective(po);

        return true;
    }

    @Override
    public List<DeveloperUserDTO> getAllDevelopers() {
        DeveloperUserPoExample example = new DeveloperUserPoExample();
        example.createCriteria().andStatusEqualTo(1);
        example.setOrderByClause("dev_level DESC, create_time ASC");

        List<DeveloperUserPo> poList = developerUserPoMapper.selectByExample(example);
        return poList.stream().map(this::toDeveloperDTO).collect(Collectors.toList());
    }

    @Override
    public DeveloperUserDTO getDeveloper(Integer userId) {
        DeveloperUserPoExample example = new DeveloperUserPoExample();
        example.createCriteria().andUserIdEqualTo(userId).andStatusEqualTo(1);

        List<DeveloperUserPo> poList = developerUserPoMapper.selectByExample(example);
        return poList.isEmpty() ? null : toDeveloperDTO(poList.get(0));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeveloperUserDTO updateDeveloperLevel(Integer userId, Integer devLevel, Integer operatorId) {
        log.info("更新开发者级别: userId={}, devLevel={}, operatorId={}", userId, devLevel, operatorId);

        if (!isDeveloper(operatorId)) {
            throw new RuntimeException("无权更新开发者级别");
        }

        DeveloperUserPoExample example = new DeveloperUserPoExample();
        example.createCriteria().andUserIdEqualTo(userId).andStatusEqualTo(1);

        List<DeveloperUserPo> poList = developerUserPoMapper.selectByExample(example);
        if (poList.isEmpty()) {
            throw new RuntimeException("用户不是开发者");
        }

        DeveloperUserPo po = poList.get(0);
        po.setDevLevel(devLevel);
        po.setUpdateTime(LocalDateTime.now());
        developerUserPoMapper.updateByPrimaryKeySelective(po);

        return toDeveloperDTO(po);
    }

    // ==================== 私有方法 ====================

    private ResourcePermissionPo getPermissionRecord(Integer userId, String resourceType, Long resourceId) {
        ResourcePermissionPoExample example = new ResourcePermissionPoExample();
        example.createCriteria()
                .andUserIdEqualTo(userId)
                .andResourceTypeEqualTo(resourceType)
                .andResourceIdEqualTo(resourceId);

        List<ResourcePermissionPo> poList = resourcePermissionPoMapper.selectByExample(example);
        return poList.isEmpty() ? null : poList.get(0);
    }

    private ResourcePermissionDTO toDTO(ResourcePermissionPo po) {
        if (po == null) {
            return null;
        }
        ResourcePermissionDTO dto = new ResourcePermissionDTO();
        BeanUtils.copyProperties(po, dto);

        // 解析权限列表
        if (StringUtils.hasText(po.getPermissions())) {
            dto.setPermissions(JSON.parseArray(po.getPermissions(), String.class));
        } else {
            ResourceRoleEnum role = ResourceRoleEnum.fromCode(po.getRoleType());
            if (role != null) {
                dto.setPermissions(role.getDefaultPermissions());
            }
        }

        return dto;
    }

    private DeveloperUserDTO toDeveloperDTO(DeveloperUserPo po) {
        if (po == null) {
            return null;
        }
        DeveloperUserDTO dto = new DeveloperUserDTO();
        BeanUtils.copyProperties(po, dto);
        return dto;
    }
}
