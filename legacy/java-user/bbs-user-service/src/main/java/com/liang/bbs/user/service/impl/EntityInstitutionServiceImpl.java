package com.liang.bbs.user.service.impl;

import com.liang.bbs.article.facade.dto.InstitutionDTO;
import com.liang.bbs.article.facade.server.InstitutionService;
import com.liang.bbs.common.enums.ResponseCode;
import com.liang.bbs.common.web.exception.BusinessException;
import com.liang.bbs.user.facade.dto.EntityInstitutionDTO;
import com.liang.bbs.user.facade.server.EntityInstitutionService;
import com.liang.bbs.user.persistence.entity.EntityInstitutionPo;
import com.liang.bbs.user.persistence.mapper.EntityInstitutionMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.Reference;
import org.apache.dubbo.config.annotation.Service;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 通用实体机构关联服务实现类
 *
 */
@Slf4j
@Service
public class EntityInstitutionServiceImpl implements EntityInstitutionService {

    @Autowired
    private EntityInstitutionMapper entityInstitutionMapper;

    @Reference(check = false)
    private InstitutionService institutionService;

    @Override
    @Transactional
    public EntityInstitutionDTO addRelation(EntityInstitutionDTO dto) {
        log.info("添加实体机构关联: entityType={}, entityId={}, institutionId={}",
                dto.getEntityType(), dto.getEntityId(), dto.getInstitutionId());

        // 验证机构是否存在
        InstitutionDTO institution = institutionService.getInstitutionById(dto.getInstitutionId());
        if (institution == null) {
            throw BusinessException.build(ResponseCode.DATA_ILLEGAL, "机构不存在");
        }

        // 检查是否已存在关联
        EntityInstitutionPo existing = entityInstitutionMapper.selectByEntityAndInstitution(
                dto.getEntityType(), dto.getEntityId(), dto.getInstitutionId());
        if (existing != null) {
            throw BusinessException.build(ResponseCode.DATA_ILLEGAL, "该关联已存在");
        }

        // 如果设置为主要关联，先取消其他主要关联
        if (dto.getIsPrimary() != null && dto.getIsPrimary() == 1) {
            clearPrimaryRelation(dto.getEntityType(), dto.getEntityId());
        }

        EntityInstitutionPo po = toEntity(dto);
        po.setCreatedAt(LocalDateTime.now());
        po.setUpdatedAt(LocalDateTime.now());

        entityInstitutionMapper.insertSelective(po);

        EntityInstitutionDTO result = toDTO(po);
        result.setInstitutionName(institution.getName());
        return result;
    }

    @Override
    @Transactional
    public Integer batchAddRelations(List<EntityInstitutionDTO> dtoList) {
        log.info("批量添加实体机构关联: count={}", dtoList.size());

        List<EntityInstitutionPo> poList = dtoList.stream()
                .map(dto -> {
                    EntityInstitutionPo po = toEntity(dto);
                    po.setCreatedAt(LocalDateTime.now());
                    po.setUpdatedAt(LocalDateTime.now());
                    return po;
                })
                .collect(Collectors.toList());

        return entityInstitutionMapper.batchInsert(poList);
    }

    @Override
    @Transactional
    public Boolean updateRelation(EntityInstitutionDTO dto) {
        log.info("更新实体机构关联: id={}", dto.getId());

        EntityInstitutionPo po = entityInstitutionMapper.selectByPrimaryKey(dto.getId());
        if (po == null) {
            throw BusinessException.build(ResponseCode.DATA_ILLEGAL, "关联不存在");
        }

        // 如果设置为主要关联，先取消其他主要关联
        if (dto.getIsPrimary() != null && dto.getIsPrimary() == 1) {
            clearPrimaryRelation(po.getEntityType(), po.getEntityId());
        }

        BeanUtils.copyProperties(dto, po);
        po.setUpdatedAt(LocalDateTime.now());

        int updated = entityInstitutionMapper.updateByPrimaryKeySelective(po);
        return updated > 0;
    }

    @Override
    @Transactional
    public Boolean deleteRelation(Long id) {
        log.info("删除实体机构关联: id={}", id);
        int deleted = entityInstitutionMapper.deleteByPrimaryKey(id);
        return deleted > 0;
    }

    @Override
    @Transactional
    public Boolean deleteRelationByEntityAndInstitution(String entityType, Long entityId, Long institutionId) {
        log.info("删除特定实体机构关联: entityType={}, entityId={}, institutionId={}",
                entityType, entityId, institutionId);
        int deleted = entityInstitutionMapper.deleteByEntityAndInstitution(entityType, entityId, institutionId);
        return deleted > 0;
    }

    @Override
    @Transactional
    public Integer deleteRelationsByEntity(String entityType, Long entityId) {
        log.info("删除实体的所有机构关联: entityType={}, entityId={}", entityType, entityId);
        return entityInstitutionMapper.deleteByEntity(entityType, entityId);
    }

    @Override
    @Transactional
    public Integer deleteRelationsByInstitution(Long institutionId) {
        log.info("删除机构的所有实体关联: institutionId={}", institutionId);
        return entityInstitutionMapper.deleteByInstitution(institutionId);
    }

    @Override
    public EntityInstitutionDTO getRelationById(Long id) {
        log.info("根据ID查询关联: id={}", id);
        EntityInstitutionPo po = entityInstitutionMapper.selectByPrimaryKey(id);
        return toDTO(po);
    }

    @Override
    public List<EntityInstitutionDTO> getInstitutionsByEntity(String entityType, Long entityId) {
        log.info("查询实体关联的所有机构: entityType={}, entityId={}", entityType, entityId);
        List<EntityInstitutionPo> poList = entityInstitutionMapper.selectByEntity(entityType, entityId);
        return poList.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public EntityInstitutionDTO getPrimaryInstitutionByEntity(String entityType, Long entityId) {
        log.info("查询实体的主要机构: entityType={}, entityId={}", entityType, entityId);
        EntityInstitutionPo po = entityInstitutionMapper.selectPrimaryByEntity(entityType, entityId);
        return toDTO(po);
    }

    @Override
    public List<EntityInstitutionDTO> getEntitiesByInstitution(Long institutionId) {
        log.info("查询机构关联的所有实体: institutionId={}", institutionId);
        List<EntityInstitutionPo> poList = entityInstitutionMapper.selectByInstitution(institutionId);
        return poList.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public List<EntityInstitutionDTO> getEntitiesByInstitutionAndType(Long institutionId, String entityType) {
        log.info("查询机构关联的特定类型实体: institutionId={}, entityType={}", institutionId, entityType);
        List<EntityInstitutionPo> poList = entityInstitutionMapper.selectByInstitutionAndType(institutionId, entityType);
        return poList.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public Boolean setPrimaryInstitution(String entityType, Long entityId, Long institutionId) {
        log.info("设置实体的主要机构: entityType={}, entityId={}, institutionId={}",
                entityType, entityId, institutionId);

        // 先取消其他主要关联
        clearPrimaryRelation(entityType, entityId);

        // 设置新的主要关联
        EntityInstitutionPo po = entityInstitutionMapper.selectByEntityAndInstitution(entityType, entityId, institutionId);
        if (po == null) {
            throw BusinessException.build(ResponseCode.DATA_ILLEGAL, "关联不存在");
        }

        po.setIsPrimary((byte) 1);
        po.setUpdatedAt(LocalDateTime.now());
        int updated = entityInstitutionMapper.updateByPrimaryKeySelective(po);
        return updated > 0;
    }

    // ==================== 私有方法 ====================

    /**
     * 清除实体的主要机构关联
     */
    private void clearPrimaryRelation(String entityType, Long entityId) {
        List<EntityInstitutionPo> primaryList = entityInstitutionMapper.selectByEntity(entityType, entityId);
        for (EntityInstitutionPo po : primaryList) {
            if (po.getIsPrimary() != null && po.getIsPrimary() == 1) {
                po.setIsPrimary((byte) 0);
                po.setUpdatedAt(LocalDateTime.now());
                entityInstitutionMapper.updateByPrimaryKeySelective(po);
            }
        }
    }

    /**
     * Po转DTO
     */
    private EntityInstitutionDTO toDTO(EntityInstitutionPo po) {
        if (po == null) {
            return null;
        }

        EntityInstitutionDTO dto = new EntityInstitutionDTO();
        BeanUtils.copyProperties(po, dto);

        // 查询机构名称
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

    /**
     * DTO转Po
     */
    private EntityInstitutionPo toEntity(EntityInstitutionDTO dto) {
        EntityInstitutionPo po = new EntityInstitutionPo();
        BeanUtils.copyProperties(dto, po);
        return po;
    }
}
