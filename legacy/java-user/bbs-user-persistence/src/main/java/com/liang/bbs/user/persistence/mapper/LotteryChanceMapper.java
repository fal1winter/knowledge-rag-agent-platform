package com.liang.bbs.user.persistence.mapper;

import com.liang.bbs.user.persistence.entity.LotteryChancePo;
import org.apache.ibatis.annotations.*;

@Mapper
public interface LotteryChanceMapper {

    @Select("SELECT * FROM lottery_chance WHERE user_id = #{userId}")
    LotteryChancePo selectByUserId(@Param("userId") Long userId);

    @Insert("INSERT INTO lottery_chance (user_id, chances) VALUES (#{userId}, #{chances}) " +
            "ON DUPLICATE KEY UPDATE chances = #{chances}")
    void insertOrUpdate(@Param("userId") Long userId, @Param("chances") Integer chances);

    @Update("UPDATE lottery_chance SET chances = chances - 1 WHERE user_id = #{userId} AND chances > 0")
    int decrementChance(@Param("userId") Long userId);

    @Select("SELECT chances FROM lottery_chance WHERE user_id = #{userId}")
    Integer getChances(@Param("userId") Long userId);

    @Select("SELECT * FROM lottery_chance ORDER BY update_time DESC")
    java.util.List<LotteryChancePo> selectAll();

    @Select("SELECT * FROM lottery_chance WHERE chances > 0 ORDER BY update_time DESC")
    java.util.List<LotteryChancePo> selectAllWithChances();
}