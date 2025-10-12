package com.liang.bbs.user.persistence.mapper;

import com.liang.bbs.user.persistence.entity.TaskPo;
import com.liang.bbs.user.persistence.entity.TaskPoExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface TaskPoMapper {
    long countByExample(TaskPoExample example);

    int deleteByExample(TaskPoExample example);

    int deleteByPrimaryKey(Integer id);

    int insert(TaskPo record);

    int insertSelective(TaskPo record);

    List<TaskPo> selectByExample(TaskPoExample example);

    TaskPo selectByPrimaryKey(Integer id);

    int updateByExampleSelective(@Param("record") TaskPo record, @Param("example") TaskPoExample example);

    int updateByExample(@Param("record") TaskPo record, @Param("example") TaskPoExample example);

    int updateByPrimaryKeySelective(TaskPo record);

    int updateByPrimaryKey(TaskPo record);
}