package com.liang.bbs.user.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 学者认领实体类
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ScholarClaimPo implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private Integer id;
    private Integer userId;
    private Integer scholarId;
    /** 认领状态: 0待审核, 1已通过, 2已拒绝 */
    private Integer status;
    /** 证明材料(JSON格式) */
    private String proofMaterials;
    private String realName;
    private String institution;
    private String email;
    private String position;
    private String description;
    private Integer reviewUserId;
    private String reviewComment;
    private LocalDateTime reviewTime;
    private Boolean isDeleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
