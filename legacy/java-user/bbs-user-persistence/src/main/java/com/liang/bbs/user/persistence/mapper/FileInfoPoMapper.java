package com.liang.bbs.user.persistence.mapper;

import com.liang.bbs.user.persistence.entity.FileInfoPo;
import com.liang.bbs.user.persistence.entity.FileInfoPoExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface FileInfoPoMapper {
    long countByExample(FileInfoPoExample example);

    int deleteByExample(FileInfoPoExample example);

    int deleteByPrimaryKey(Integer id);

    int insert(FileInfoPo record);

    int insertSelective(FileInfoPo record);

    List<FileInfoPo> selectByExample(FileInfoPoExample example);

    FileInfoPo selectByPrimaryKey(Integer id);

    int updateByExampleSelective(@Param("record") FileInfoPo record, @Param("example") FileInfoPoExample example);

    int updateByExample(@Param("record") FileInfoPo record, @Param("example") FileInfoPoExample example);

    int updateByPrimaryKeySelective(FileInfoPo record);

    int updateByPrimaryKey(FileInfoPo record);
}