package com.liang.bbs.user.persistence.mapper;

import com.liang.bbs.user.persistence.entity.UserRoom;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;


import com.liang.bbs.user.persistence.entity.UserPo;
import com.liang.bbs.user.persistence.entity.UserPoExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.time.LocalDateTime;
import java.util.List;

/**
 */
public interface UserPoExMapper {
    
    long countByExample(UserPoExample example);

    int deleteByExample(UserPoExample example);

    int deleteByPrimaryKey(Integer id);

    int insert(UserPo record);

    int insertSelective(UserPo record);

    List<UserPo> selectByExampleWithBLOBs(UserPoExample example);

    List<UserPo> selectByExample(UserPoExample example);

    UserPo selectByPrimaryKey(Integer id);

    UserPo selectByPhoneNumber(String phoneNumber);

    int updateByExampleSelective(@Param("record") UserPo record, @Param("example") UserPoExample example);

    int updateByExampleWithBLOBs(@Param("record") UserPo record, @Param("example") UserPoExample example);

    int updateByExample(@Param("record") UserPo record, @Param("example") UserPoExample example);

    int updateByPrimaryKeySelective(UserPo record);

    int updateByPrimaryKeyWithBLOBs(UserPo record);

    int updateByPrimaryKey(UserPo record);
    
    @Select("select * from pp_user where name=#{name}")
    UserPo selectByName(String name);
}
