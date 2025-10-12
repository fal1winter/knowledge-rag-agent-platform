package com.liang.bbs.user.facade.dto;

import java.io.Serializable;
import java.time.LocalDateTime;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 聊天室关联DTO
 */
@Data
@ApiModel(value = "ChatroomRelation对象", description = "聊天室关联表")
public class ChatroomRelationDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("主键ID")
    private Long id;

    @ApiModelProperty("聊天室ID")
    private Integer chatroomId;

    @ApiModelProperty("关联实体类型: user, paper, institution, scholar, rating")
    private String entityType;

    @ApiModelProperty("关联实体ID")
    private Long entityId;

    @ApiModelProperty("创建时间")
    private LocalDateTime createdAt;

    @ApiModelProperty("创建者ID")
    private Integer createdBy;
}
