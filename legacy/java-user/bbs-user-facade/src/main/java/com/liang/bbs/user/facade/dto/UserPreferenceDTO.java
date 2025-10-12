package com.liang.bbs.user.facade.dto;

import lombok.Data;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 用户偏好DTO
 */
@Data
public class UserPreferenceDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer id;
    private Integer userId;
    private String preferenceText;
    private List<String> preferenceKeywords;
    private List<String> preferenceTopics;
    private Integer lastAnalyzedLogCount;
    private Integer currentLogCount;
    private Integer analysisCount;
    private Date createTime;
    private Date updateTime;
}
