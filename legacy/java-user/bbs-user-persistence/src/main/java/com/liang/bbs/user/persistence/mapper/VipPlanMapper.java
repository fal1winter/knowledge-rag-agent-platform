package com.liang.bbs.user.persistence.mapper;

import com.liang.bbs.user.persistence.entity.VipPlanPo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * VIP套餐Mapper
 */
@Mapper
public interface VipPlanMapper {

    /**
     * 查询所有上架的套餐
     */
    List<VipPlanPo> selectAvailablePlans();

    /**
     * 根据ID查询
     */
    VipPlanPo selectByPrimaryKey(Integer id);

    /**
     * 插入套餐
     */
    int insert(VipPlanPo record);

    /**
     * 更新套餐
     */
    int updateByPrimaryKeySelective(VipPlanPo record);
}
