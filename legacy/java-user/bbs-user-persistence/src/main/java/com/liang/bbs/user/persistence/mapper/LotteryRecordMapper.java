package com.liang.bbs.user.persistence.mapper;

import com.liang.bbs.user.persistence.entity.LotteryRecordPo;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface LotteryRecordMapper {

    @Insert("INSERT INTO lottery_record (user_id, prize, prize_type) VALUES (#{userId}, #{prize}, #{prizeType})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(LotteryRecordPo record);

    @Select("SELECT * FROM lottery_record WHERE user_id = #{userId} ORDER BY draw_time DESC")
    List<LotteryRecordPo> selectByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM lottery_record ORDER BY draw_time DESC LIMIT #{limit}")
    List<LotteryRecordPo> selectRecent(@Param("limit") Integer limit);
}