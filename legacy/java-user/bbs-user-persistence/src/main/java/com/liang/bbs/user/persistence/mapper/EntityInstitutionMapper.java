package com.liang.bbs.user.persistence.mapper;

import com.liang.bbs.user.persistence.entity.EntityInstitutionPo;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 通用实体机构关联表Mapper接口
 *
 */
public interface EntityInstitutionMapper {

    /**
     * 插入记录
     */
    int insert(EntityInstitutionPo record);

    /**
     * 选择性插入记录
     */
    int insertSelective(EntityInstitutionPo record);

    /**
     * 根据主键查询
     */
    EntityInstitutionPo selectByPrimaryKey(Long id);

    /**
     * 根据主键选择性更新
     */
    int updateByPrimaryKeySelective(EntityInstitutionPo record);

    /**
     * 根据主键更新
     */
    int updateByPrimaryKey(EntityInstitutionPo record);

    /**
     * 根据主键删除
     */
    int deleteByPrimaryKey(Long id);

    /**
     * 根据实体类型和实体ID查询所有关联的机构
     */
    List<EntityInstitutionPo> selectByEntity(@Param("entityType") String entityType,
                                             @Param("entityId") Long entityId);

    /**
     * 根据机构ID查询所有关联的实体
     */
    List<EntityInstitutionPo> selectByInstitution(@Param("institutionId") Long institutionId);

    /**
     * 根据机构ID和实体类型查询关联的实体
     */
    List<EntityInstitutionPo> selectByInstitutionAndType(@Param("institutionId") Long institutionId,
                                                          @Param("entityType") String entityType);

    /**
     * 查询实体的主要机构关联
     */
    EntityInstitutionPo selectPrimaryByEntity(@Param("entityType") String entityType,
                                               @Param("entityId") Long entityId);

    /**
     * 根据实体类型、实体ID和机构ID查询
     */
    EntityInstitutionPo selectByEntityAndInstitution(@Param("entityType") String entityType,
                                                      @Param("entityId") Long entityId,
                                                      @Param("institutionId") Long institutionId);

    /**
     * 删除实体的所有机构关联
     */
    int deleteByEntity(@Param("entityType") String entityType,
                       @Param("entityId") Long entityId);

    /**
     * 删除机构的所有实体关联
     */
    int deleteByInstitution(@Param("institutionId") Long institutionId);

    /**
     * 删除特定的实体-机构关联
     */
    int deleteByEntityAndInstitution(@Param("entityType") String entityType,
                                      @Param("entityId") Long entityId,
                                      @Param("institutionId") Long institutionId);

    /**
     * 批量插入
     */
    int batchInsert(@Param("list") List<EntityInstitutionPo> list);
}
