package com.liang.bbs.user.facade.dto;


import java.io.Serializable;
import java.time.LocalDateTime;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * <p>
 * 聊天室表
 * </p>
 *
 */
@Data
@ApiModel(value = "Chatroom对象", description = "聊天室表")
public class ChatRoomDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("聊天室ID")
    
    private Integer id;

    @ApiModelProperty("聊天室名称")
    private String name;

    @ApiModelProperty("聊天室描述")
    private String description;

    @ApiModelProperty("创建者ID")
    private Integer creatorId;

    @ApiModelProperty("最大成员数")
    private Integer maxMembers;

    @ApiModelProperty("是否私有")
    private Integer isPrivate;

    @ApiModelProperty("密码(私有房间使用)")
    private String password;

    @ApiModelProperty("创建时间")
    private LocalDateTime createdAt;

    @ApiModelProperty("更新时间")
    private LocalDateTime updatedAt;

    @ApiModelProperty("状态(1-活跃,0-关闭)")
    private Byte status;

    @ApiModelProperty("聊天室头像URL")
    private String avatarUrl;


    @Override
    public String toString() {
        return "Chatroom{" +
            "id = " + id +
            ", name = " + name +
            ", description = " + description +
            ", creatorId = " + creatorId +
            ", maxMembers = " + maxMembers +
            ", isPrivate = " + isPrivate +
            ", password = " + password +
            ", createdAt = " + createdAt +
            ", updatedAt = " + updatedAt +
            ", status = " + status +
            ", avatarUrl = " + avatarUrl +
        "}";
    }
}
