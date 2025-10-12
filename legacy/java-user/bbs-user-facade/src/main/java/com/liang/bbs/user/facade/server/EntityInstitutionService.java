package com.liang.bbs.user.facade.server;

import com.liang.bbs.user.facade.dto.EntityInstitutionDTO;

import java.util.List;

/**
 * 通用实体机构关联服务接口
 *
 */
public interface EntityInstitutionService {

    /**
     * 添加实体与机构的关联
     *
     * @param dto 关联信息
     * @return 创建的关联信息
     */
    EntityInstitutionDTO addRelation(EntityInstitutionDTO dto);

    /**
     * 批量添加实体与机构的关联
     *
     * @param dtoList 关联信息列表
     * @return 成功添加的数量
     */
    Integer batchAddRelations(List<EntityInstitutionDTO> dtoList);

    /**
     * 更新实体与机构的关联
     *
     * @param dto 关联信息
     * @return 是否成功
     */
    Boolean updateRelation(EntityInstitutionDTO dto);

    /**
     * 删除实体与机构的关联
     *
     * @param id 关联ID
     * @return 是否成功
     */
    Boolean deleteRelation(Long id);

    /**
     * 删除特定的实体-机构关联
     *
     * @param entityType 实体类型
     * @param entityId 实体ID
     * @param institutionId 机构ID
     * @return 是否成功
     */
    Boolean deleteRelationByEntityAndInstitution(String entityType, Long entityId, Long institutionId);

    /**
     * 删除实体的所有机构关联
     *
     * @param entityType 实体类型
     * @param entityId 实体ID
     * @return 删除的数量
     */
    Integer deleteRelationsByEntity(String entityType, Long entityId);

    /**
     * 删除机构的所有实体关联
     *
     * @param institutionId 机构ID
     * @return 删除的数量
     */
    Integer deleteRelationsByInstitution(Long institutionId);

    /**
     * 根据ID查询关联信息
     *
     * @param id 关联ID
     * @return 关联信息
     */
    EntityInstitutionDTO getRelationById(Long id);

    /**
     * 查询实体关联的所有机构
     *
     * @param entityType 实体类型
     * @param entityId 实体ID
     * @return 关联的机构列表
     */
    List<EntityInstitutionDTO> getInstitutionsByEntity(String entityType, Long entityId);

    /**
     * 查询实体的主要机构
     *
     * @param entityType 实体类型
     * @param entityId 实体ID
     * @return 主要机构关联信息
     */
    EntityInstitutionDTO getPrimaryInstitutionByEntity(String entityType, Long entityId);

    /**
     * 查询机构关联的所有实体
     *
     * @param institutionId 机构ID
     * @return 关联的实体列表
     */
    List<EntityInstitutionDTO> getEntitiesByInstitution(Long institutionId);

    /**
     * 查询机构关联的特定类型实体
     *
     * @param institutionId 机构ID
     * @param entityType 实体类型
     * @return 关联的实体列表
     */
    List<EntityInstitutionDTO> getEntitiesByInstitutionAndType(Long institutionId, String entityType);

    /**
     * 设置实体的主要机构
     *
     * @param entityType 实体类型
     * @param entityId 实体ID
     * @param institutionId 机构ID
     * @return 是否成功
     */
    Boolean setPrimaryInstitution(String entityType, Long entityId, Long institutionId);
}
