package com.liang.bbs.user.persistence.mapper;

import com.liang.bbs.user.persistence.entity.ScholarClaimPo;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 学者认领Mapper
 */
@Mapper
public interface ScholarClaimPoMapper {
    
    @Insert("INSERT INTO fs_scholar_claim (user_id, scholar_id, status, proof_materials, real_name, " +
            "institution, email, position, description, create_time, update_time) " +
            "VALUES (#{userId}, #{scholarId}, #{status}, #{proofMaterials}, #{realName}, " +
            "#{institution}, #{email}, #{position}, #{description}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ScholarClaimPo po);
    
    @Update("UPDATE fs_scholar_claim SET status = #{status}, review_user_id = #{reviewUserId}, " +
            "review_comment = #{reviewComment}, review_time = NOW(), update_time = NOW() " +
            "WHERE id = #{id} AND is_deleted = 0")
    int updateReview(ScholarClaimPo po);
    
    @Update("UPDATE fs_scholar_claim SET is_deleted = 1, update_time = NOW() WHERE id = #{id}")
    int deleteById(Integer id);
    
    @Select("SELECT * FROM fs_scholar_claim WHERE id = #{id} AND is_deleted = 0")
    ScholarClaimPo selectById(Integer id);
    
    @Select("SELECT * FROM fs_scholar_claim WHERE user_id = #{userId} AND scholar_id = #{scholarId} " +
            "AND is_deleted = 0 AND status != 2 ORDER BY create_time DESC LIMIT 1")
    ScholarClaimPo selectByUserAndScholar(@Param("userId") Integer userId, @Param("scholarId") Integer scholarId);
    
    @Select("SELECT * FROM fs_scholar_claim WHERE scholar_id = #{scholarId} AND status = 1 AND is_deleted = 0 LIMIT 1")
    ScholarClaimPo selectApprovedByScholar(Integer scholarId);
    
    @Select("SELECT * FROM fs_scholar_claim WHERE user_id = #{userId} AND is_deleted = 0 ORDER BY create_time DESC")
    List<ScholarClaimPo> selectByUserId(Integer userId);
    
    @Select("SELECT * FROM fs_scholar_claim WHERE user_id = #{userId} AND status = 1 AND is_deleted = 0")
    List<ScholarClaimPo> selectApprovedByUserId(Integer userId);
    
    @Select("<script>" +
            "SELECT * FROM fs_scholar_claim WHERE is_deleted = 0 " +
            "<if test='userId != null'>AND user_id = #{userId}</if> " +
            "<if test='scholarId != null'>AND scholar_id = #{scholarId}</if> " +
            "<if test='status != null'>AND status = #{status}</if> " +
            "ORDER BY create_time DESC" +
            "</script>")
    List<ScholarClaimPo> selectByCondition(@Param("userId") Integer userId, 
                                           @Param("scholarId") Integer scholarId, 
                                           @Param("status") Integer status);
    
    @Select("SELECT COUNT(*) FROM fs_scholar_claim WHERE status = 0 AND is_deleted = 0")
    int countPending();
}
