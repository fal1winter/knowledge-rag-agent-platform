package com.liang.bbs.user.facade.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 学者认领搜索DTO
 */
@Data
public class ScholarClaimSearchDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private Integer userId;
    private Integer scholarId;
    /** 认领状态: 0待审核, 1已通过, 2已拒绝, null查全部 */
    private Integer status;
    private Integer pageNum = 1;
    private Integer pageSize = 10;
}
