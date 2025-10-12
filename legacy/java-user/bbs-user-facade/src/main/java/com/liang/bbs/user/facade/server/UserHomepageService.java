package com.liang.bbs.user.facade.server;

import com.liang.bbs.user.facade.dto.UserHomepageDTO;

import java.util.List;

/**
 * 用户主页服务接口
 */
public interface UserHomepageService {

    /**
     * 创建或更新主页（mainpage类型会自动更新已有记录）
     * @param dto 主页信息
     * @return 创建/更新后的主页
     */
    UserHomepageDTO saveHomepage(UserHomepageDTO dto);

    /**
     * 更新主页
     * @param dto 主页信息
     * @return 更新后的主页
     */
    UserHomepageDTO updateHomepage(UserHomepageDTO dto);

    /**
     * 删除主页
     * @param id 主页ID
     * @return 是否成功
     */
    Boolean deleteHomepage(Long id);

    /**
     * 根据ID获取主页
     * @param id 主页ID
     * @return 主页信息
     */
    UserHomepageDTO getHomepageById(Long id);

    /**
     * 获取用户的主页（mainpage类型）
     * @param userId 用户ID
     * @return 主页信息
     */
    UserHomepageDTO getUserMainpage(Integer userId);

    /**
     * 获取用户的所有动态（activity类型）
     * @param userId 用户ID
     * @return 动态列表
     */
    List<UserHomepageDTO> getUserActivities(Integer userId);

    /**
     * 根据用户ID和类型获取主页
     * @param userId 用户ID
     * @param type 类型
     * @return 主页列表
     */
    List<UserHomepageDTO> getHomepagesByUserAndType(Integer userId, String type);

    /**
     * 获取用户的所有主页记录
     * @param userId 用户ID
     * @return 主页列表
     */
    List<UserHomepageDTO> getAllHomepagesByUser(Integer userId);

    /**
     * 获取所有用户的动态（activity类型），按时间倒序，分页
     * @param currentPage 当前页
     * @param pageSize 每页大小
     * @return 动态列表
     */
    List<UserHomepageDTO> getAllActivities(Integer currentPage, Integer pageSize);

    List<UserHomepageDTO> getAllActivities(Integer currentPage, Integer pageSize, String topic);

    void incrementLikeCount(Long activityId, int delta);

    void incrementCommentCount(Long activityId, int delta);

}
