package com.liang.bbs.user.facade.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 通知表
 * </p>
 *
 */
@Data
@ApiModel(value = "Noti对象", description = "通知表")
public class NotiDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("通知ID")
    private Integer id;

    @ApiModelProperty("用户ID")
    private Integer userid;

    @ApiModelProperty("通知类型")
    private String type;

    @ApiModelProperty("通知内容")
    private String content;

    @ApiModelProperty("通知时间")
    private LocalDateTime time;

    @ApiModelProperty("通知状态")
    private String status;

    @ApiModelProperty("过期时间")
    private LocalDateTime expiretime;

    @ApiModelProperty("发送者ID")
    private Integer senderid;

    @ApiModelProperty("是否已读")
    private String isRead;

    @ApiModelProperty("额外信息")
    private String extra;

    @Override
    public String toString() {
        return "NotiDTO{" +
                "id = " + id +
                ", userid = " + userid +
                ", type = " + type +
                ", content = " + content +
                ", time = " + time +
                ", status = " + status +
                ", expiretime = " + expiretime +
                ", senderid = " + senderid +
                ", isRead = " + isRead +
                ", extra = " + extra +
                "}";
    }
}