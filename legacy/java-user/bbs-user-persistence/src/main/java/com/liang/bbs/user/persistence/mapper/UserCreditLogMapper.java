package com.liang.bbs.user.persistence.mapper;

import com.liang.bbs.user.persistence.entity.UserCreditLogPo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户积分流水Mapper
 */
@Mapper
public interface UserCreditLogMapper {

    /**
     * 插入积分流水记录
     */
    int insert(UserCreditLogPo record);

    /**
     * 根据用户ID查询积分流水（分页）
     */
    List<UserCreditLogPo> selectByUserId(@Param("userId") Integer userId,
                                          @Param("offset") Integer offset,
                                          @Param("limit") Integer limit);

    /**
     * 根据用户ID统计流水记录数
     */
    long countByUserId(@Param("userId") Integer userId);

    /**
     * 根据ID查询
     */
    UserCreditLogPo selectByPrimaryKey(Long id);
}
