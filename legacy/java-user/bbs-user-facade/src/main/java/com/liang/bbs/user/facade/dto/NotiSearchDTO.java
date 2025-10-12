package com.liang.bbs.user.facade.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 通知查询条件
 *
 */
@Data
@ApiModel(value = "通知查询条件")
public class NotiSearchDTO implements Serializable {

    @ApiModelProperty(value = "通知ID")
    private Integer id;

    @ApiModelProperty(value = "用户ID")
    private Integer userid;

    @ApiModelProperty(value = "通知类型")
    private String type;

    @ApiModelProperty(value = "通知内容关键字")
    private String content;

    @ApiModelProperty(value = "通知状态")
    private String status;

    @ApiModelProperty(value = "是否已读：0-未读，1-已读")
    private String isRead;

    @ApiModelProperty(value = "发送者ID")
    private Integer senderid;

    @ApiModelProperty(value = "当前页码，默认1")
    private Integer pageNum = 1;

    @ApiModelProperty(value = "每页条数，默认10")
    private Integer pageSize = 10;

    private static final long serialVersionUID = 1L;
}