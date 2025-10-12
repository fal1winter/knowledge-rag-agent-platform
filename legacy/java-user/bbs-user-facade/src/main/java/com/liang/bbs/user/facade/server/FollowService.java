package com.liang.bbs.user.facade.server;

import com.github.pagehelper.PageInfo;
import com.liang.bbs.user.facade.dto.FollowCountDTO;
import com.liang.bbs.user.facade.dto.FollowDTO;
import com.liang.bbs.user.facade.dto.FollowSearchDTO;

/**
 * 关注服务接口
 */
public interface FollowService {

    /**
     * 获取所有关注信息（分页）
     *
     * @param followSearchDTO 搜索条件
     * @param page            页码
     * @param pageSize        每页数量
     * @return 分页的关注信息
     */
    PageInfo<FollowDTO> getSelect(FollowSearchDTO followSearchDTO, Integer page, Integer pageSize);

    /**
     * 获取用户的关注列表
     *
     * @param userId   用户ID
     * @param page     页码
     * @param pageSize 每页数量
     * @return 分页的关注列表
     */
    PageInfo<FollowDTO> getFollowTo(Integer userId, Integer page,Integer pageSize, String type);

    PageInfo<FollowDTO> getFollowFrom(Integer targetId, Integer page,Integer pageSize, String type);
    /**
     * 根据ID获取关注信息
     *
     * @param id 关注ID
     * @return 关注信息
     */
    FollowDTO getById(Integer id);

    /**
     * 根据关注者和被关注者获取关注信息
     *
     * @param userId   关注者用户ID
     * @param targetId 被关注者用户ID
     * @param type     关注类型
     * @return 关注信息
     */
    FollowDTO getByFromToUser(Integer userId, Integer targetId, String type);

    Integer getCount(Integer targetId,String t);
    /**
     * 更新关注状态（关注/取消关注）
     *
     * @param userId   关注者用户ID
     * @param targetId 被关注者用户ID
     * @param type     关注类型
     * @return 操作结果
     */
    Boolean updateFollowState(Integer userId, Integer targetId, String type);

    /**
     * 建立关注关系
     *
     * @param userId   关注者用户ID
     * @param targetId 被关注者用户ID
     * @param type     关注类型
     * @return 操作结果
     */
    Boolean createFollowRelation(Integer userId, Integer targetId, String type);

    /**
     * 删除关注关系
     *
     * @param userId   关注者用户ID
     * @param targetId 被关注者用户ID
     * @param type     关注类型
     * @return 操作结果
     */
    Boolean deleteFollowRelation(Integer userId, Integer targetId, String type);

    /**
     * 获取用户的关注统计信息
     *
     * @param userId 用户ID
     * @return 关注统计信息
     */
    FollowCountDTO getUserFollow(Integer userId);

    /**
     * 获取用户的关注列表，并填充对应的实体DTO信息
     *
     * @param userId   用户ID
     * @param page     页码
     * @param pageSize 每页数量
     * @param type     关注类型
     * @return 分页的关注列表，每个FollowDTO的dto字段已填充对应的实体信息
     */
    PageInfo<FollowDTO> getFollowListWithDto(Integer userId, Integer page, Integer pageSize, String type);

    /**
     * 获取用户的粉丝列表，并填充对应的实体DTO信息
     *
     * @param targetId 目标用户ID
     * @param page     页码
     * @param pageSize 每页数量
     * @param type     关注类型
     * @return 分页的粉丝列表，每个FollowDTO的dto字段已填充对应的实体信息
     */
    PageInfo<FollowDTO> getFollowerListWithDto(Integer targetId, Integer page, Integer pageSize, String type);
}