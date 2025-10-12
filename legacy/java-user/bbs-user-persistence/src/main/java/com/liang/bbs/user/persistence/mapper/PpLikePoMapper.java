package com.liang.bbs.user.persistence.mapper;

import com.liang.bbs.user.persistence.entity.PpLikePo;
import com.liang.bbs.user.persistence.entity.PpLikePoExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface PpLikePoMapper {
    long countByExample(PpLikePoExample example);

    int deleteByExample(PpLikePoExample example);

    int insert(PpLikePo record);

    int insertSelective(PpLikePo record);

    List<PpLikePo> selectByExample(PpLikePoExample example);

    PpLikePo selectByPrimaryKey(Integer id);

    int updateByExampleSelective(@Param("record") PpLikePo record, @Param("example") PpLikePoExample example);

    int updateByExample(@Param("record") PpLikePo record, @Param("example") PpLikePoExample example);

    int updateByPrimaryKeySelective(PpLikePo record);

    int updateByPrimaryKey(PpLikePo record);
}