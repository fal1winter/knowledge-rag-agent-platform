package com.liang.bbs.user.persistence.mapper;

import com.liang.bbs.user.persistence.entity.UserRoom;
import com.liang.bbs.user.persistence.entity.UserRoomExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface UserRoomMapper {
    long countByExample(UserRoomExample example);

    int deleteByExample(UserRoomExample example);

    int deleteByPrimaryKey(Integer id);

    int insert(UserRoom record);

    int insertSelective(UserRoom record);

    List<UserRoom> selectByExample(UserRoomExample example);

    UserRoom selectByPrimaryKey(Integer id);

    int updateByExampleSelective(@Param("record") UserRoom record, @Param("example") UserRoomExample example);

    int updateByExample(@Param("record") UserRoom record, @Param("example") UserRoomExample example);

    int updateByPrimaryKeySelective(UserRoom record);

    int updateByPrimaryKey(UserRoom record);
}