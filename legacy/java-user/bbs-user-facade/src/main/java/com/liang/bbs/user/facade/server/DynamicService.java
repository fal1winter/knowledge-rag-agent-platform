package com.liang.bbs.user.facade.server;

import com.github.pagehelper.PageInfo;
import com.liang.bbs.user.facade.dto.DynamicDTO;

import java.time.LocalDateTime;

/**
 */
public interface DynamicService {

    /**
     * 获取用户的动态信息
     *
     * @param userId
     * @param currentPage
     * @param pageSize
     * @return
     */
    PageInfo<DynamicDTO> getByUserId(Integer userid, Integer currentPage, Integer pageSize);

    /**
     * 创建用户动态信息
     *
     * @param dynamicDTO
     * @return
     */
    Boolean create(DynamicDTO dynamicDTO);

    /**
     * 验证是否已经存在
     *
     * @param dynamicDTO
     * @return
     */
    Boolean verifyExist(DynamicDTO dynamicDTO);

    /**
     * 删除用户动态信息
     *
     * @param startTime
     * @param endTime
     * @return
     */
    Boolean delete(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 更新所有用户的动态信息
     *
     * @return
     */
    void updateAll();

}
