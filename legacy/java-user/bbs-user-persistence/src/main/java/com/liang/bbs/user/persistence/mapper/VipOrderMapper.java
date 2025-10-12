package com.liang.bbs.user.persistence.mapper;

import com.liang.bbs.user.persistence.entity.VipOrderPo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * VIP订单Mapper
 */
@Mapper
public interface VipOrderMapper {

    /**
     * 插入订单
     */
    int insert(VipOrderPo record);

    /**
     * 根据订单号查询
     */
    VipOrderPo selectByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 根据用户ID查询订单列表
     */
    List<VipOrderPo> selectByUserId(@Param("userId") Integer userId,
                                     @Param("offset") Integer offset,
                                     @Param("limit") Integer limit);

    /**
     * 统计用户订单数
     */
    long countByUserId(@Param("userId") Integer userId);
}
