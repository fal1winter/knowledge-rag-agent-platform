package com.liang.bbs.user.facade.server;

import com.liang.bbs.user.facade.dto.UserPreferenceDTO;
import java.util.List;

/**
 * 用户偏好服务接口
 */
public interface UserPreferenceService {

    /**
     * 获取用户偏好
     */
    UserPreferenceDTO getByUserId(Integer userId);

    /**
     * 保存或更新用户偏好
     */
    boolean saveOrUpdate(UserPreferenceDTO dto);

    /**
     * 增加用户日志计数
     */
    void incrementLogCount(Integer userId);

    /**
     * 获取需要分析的用户列表
     */
    List<UserPreferenceDTO> getNeedAnalysisUsers(Integer threshold);

    /**
     * 检查用户是否需要分析
     */
    boolean needAnalysis(Integer userId, Integer threshold);
}
