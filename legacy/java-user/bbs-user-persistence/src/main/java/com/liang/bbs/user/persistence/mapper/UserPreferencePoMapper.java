package com.liang.bbs.user.persistence.mapper;

import com.liang.bbs.user.persistence.entity.UserPreferencePo;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 用户偏好Mapper
 */
@Mapper
public interface UserPreferencePoMapper {

    @Select("SELECT * FROM user_preference WHERE user_id = #{userId}")
    UserPreferencePo selectByUserId(@Param("userId") Integer userId);

    @Insert("INSERT INTO user_preference (user_id, preference_text, preference_keywords, preference_topics, " +
            "last_analyzed_log_count, current_log_count, analysis_count, create_time, update_time) " +
            "VALUES (#{userId}, #{preferenceText}, #{preferenceKeywords}, #{preferenceTopics}, " +
            "#{lastAnalyzedLogCount}, #{currentLogCount}, #{analysisCount}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserPreferencePo po);

    @Update("UPDATE user_preference SET preference_text = #{preferenceText}, " +
            "preference_keywords = #{preferenceKeywords}, preference_topics = #{preferenceTopics}, " +
            "last_analyzed_log_count = #{lastAnalyzedLogCount}, current_log_count = #{currentLogCount}, " +
            "analysis_count = #{analysisCount}, update_time = NOW() WHERE user_id = #{userId}")
    int updateByUserId(UserPreferencePo po);

    @Select("SELECT * FROM user_preference WHERE current_log_count - last_analyzed_log_count >= #{threshold}")
    List<UserPreferencePo> selectNeedAnalysis(@Param("threshold") Integer threshold);

    @Update("UPDATE user_preference SET current_log_count = current_log_count + 1, update_time = NOW() " +
            "WHERE user_id = #{userId}")
    int incrementLogCount(@Param("userId") Integer userId);

    @Select("SELECT COUNT(*) FROM user_preference WHERE user_id = #{userId}")
    int existsByUserId(@Param("userId") Integer userId);
}
