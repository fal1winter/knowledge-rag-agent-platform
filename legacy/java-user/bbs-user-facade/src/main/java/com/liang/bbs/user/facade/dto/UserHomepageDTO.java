package com.liang.bbs.user.facade.dto;

import java.io.Serializable;
import java.time.LocalDateTime;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 用户主页DTO
 */
@Data
@ApiModel(value = "UserHomepage对象", description = "用户主页表")
public class UserHomepageDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("主键ID")
    private Long id;

    @ApiModelProperty("用户ID")
    private Integer userId;

    @ApiModelProperty("主页内容")
    private String content;

    @ApiModelProperty("状态: 1-正常, 0-禁用")
    private Integer status;

    @ApiModelProperty("类型: mainpage, activity")
    private String type;

    @ApiModelProperty("动态主题: 求学/科研/职场/生活/文化/娱乐")
    private String topic;

    @ApiModelProperty("点赞数")
    private Integer likeCount;

    @ApiModelProperty("评论数")
    private Integer commentCount;

    @ApiModelProperty("创建时间")
    private LocalDateTime createdAt;

    @ApiModelProperty("更新时间")
    private LocalDateTime updatedAt;
}
