package com.liang.bbs.user.persistence.entity;

import lombok.Data;
import java.io.Serializable;
import java.util.Date;

/**
 * 用户偏好分析实体
 */
@Data
public class UserPreferencePo implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer id;
    private Integer userId;
    private String preferenceText;
    private String preferenceKeywords;
    private String preferenceTopics;
    private Integer lastAnalyzedLogCount;
    private Integer currentLogCount;
    private Integer analysisCount;
    private Date createTime;
    private Date updateTime;
}
