package com.liang.bbs.user.facade.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 学者认领DTO
 */
@Data
public class ScholarClaimDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private Integer id;
    private Integer userId;
    private Integer scholarId;
    /** 认领状态: 0待审核, 1已通过, 2已拒绝 */
    private Integer status;
    /** 证明材料(JSON格式，包含文件URL列表) */
    private String proofMaterials;
    private String realName;
    private String institution;
    private String email;
    private String position;
    private String description;
    private Integer reviewUserId;
    private String reviewComment;
    private LocalDateTime reviewTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    
    // 关联信息（用于展示）
    private String userName;
    private String userPicture;
    private String scholarName;
    private String scholarInstitution;
    private String reviewUserName;
}
