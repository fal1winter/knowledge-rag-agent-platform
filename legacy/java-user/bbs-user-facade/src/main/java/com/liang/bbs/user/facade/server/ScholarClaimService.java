package com.liang.bbs.user.facade.server;

import com.github.pagehelper.PageInfo;
import com.liang.bbs.user.facade.dto.ScholarClaimDTO;
import com.liang.bbs.user.facade.dto.ScholarClaimSearchDTO;

import java.util.List;

/**
 * 学者认领服务接口
 */
public interface ScholarClaimService {
    
    /**
     * 提交学者认领申请
     */
    Integer submitClaim(ScholarClaimDTO claimDTO);
    
    /**
     * 审核认领申请
     * @param claimId 认领ID
     * @param approved 是否通过
     * @param reviewUserId 审核人ID
     * @param reviewComment 审核意见
     */
    Boolean reviewClaim(Integer claimId, Boolean approved, Integer reviewUserId, String reviewComment);
    
    /**
     * 撤销认领申请（用户自己撤销待审核的申请）
     */
    Boolean cancelClaim(Integer claimId, Integer userId);
    
    /**
     * 根据ID获取认领详情
     */
    ScholarClaimDTO getById(Integer id);
    
    /**
     * 检查用户是否已认领某学者
     */
    Boolean isClaimedByUser(Integer userId, Integer scholarId);
    
    /**
     * 检查学者是否已被认领
     */
    Boolean isScholarClaimed(Integer scholarId);
    
    /**
     * 获取学者的认领用户ID（已通过的）
     */
    Integer getClaimOwner(Integer scholarId);
    
    /**
     * 获取用户的所有认领记录
     */
    List<ScholarClaimDTO> getByUserId(Integer userId);
    
    /**
     * 获取用户已通过的认领列表
     */
    List<ScholarClaimDTO> getApprovedByUserId(Integer userId);
    
    /**
     * 分页查询认领记录（管理员用）
     */
    PageInfo<ScholarClaimDTO> search(ScholarClaimSearchDTO searchDTO);
    
    /**
     * 获取待审核数量
     */
    int countPending();
}
