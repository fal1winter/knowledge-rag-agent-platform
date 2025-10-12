package com.liang.bbs.user.facade.server;

import com.github.pagehelper.PageInfo;
import com.liang.bbs.user.facade.dto.FriendDTO;
import com.liang.bbs.user.facade.dto.FriendRequestDTO;
import com.liang.bbs.user.facade.dto.FriendSearchDTO;

import java.util.List;

/**
 * 好友服务接口
 */
public interface FriendService {

    /**
     * 发送好友请求
     *
     * @param fromUserId 发起用户ID
     * @param toUserId   接收用户ID
     * @param message    请求消息
     * @return 操作结果
     */
    Boolean sendFriendRequest(Integer fromUserId, Integer toUserId, String message);

    /**
     * 处理好友请求（同意/拒绝）
     *
     * @param requestId 请求ID
     * @param status    状态(1:已同意,2:已拒绝)
     * @return 操作结果
     */
    Boolean handleFriendRequest(Integer requestId, Byte status);

    /**
     * 获取用户收到的好友请求列表
     *
     * @param userId   用户ID
     * @param page     页码
     * @param pageSize 每页数量
     * @return 分页的好友请求列表
     */
    PageInfo<FriendRequestDTO> getReceivedRequests(Integer userId, Integer page, Integer pageSize);

    /**
     * 获取用户发送的好友请求列表
     *
     * @param userId   用户ID
     * @param page     页码
     * @param pageSize 每页数量
     * @return 分页的好友请求列表
     */
    PageInfo<FriendRequestDTO> getSentRequests(Integer userId, Integer page, Integer pageSize);

    /**
     * 获取用户的好友列表
     *
     * @param userId   用户ID
     * @param page     页码
     * @param pageSize 每页数量
     * @return 分页的好友列表
     */
    PageInfo<FriendDTO> getFriendList(Integer userId, Integer page, Integer pageSize);

    /**
     * 删除好友
     *
     * @param userId   用户ID
     * @param friendId 好友ID
     * @return 操作结果
     */
    Boolean deleteFriend(Integer userId, Integer friendId);

    /**
     * 拉黑好友
     *
     * @param userId   用户ID
     * @param friendId 好友ID
     * @return 操作结果
     */
    Boolean blockFriend(Integer userId, Integer friendId);

    /**
     * 取消拉黑
     *
     * @param userId   用户ID
     * @param friendId 好友ID
     * @return 操作结果
     */
    Boolean unblockFriend(Integer userId, Integer friendId);

    /**
     * 更新好友备注
     *
     * @param userId   用户ID
     * @param friendId 好友ID
     * @param remark   备注
     * @return 操作结果
     */
    Boolean updateFriendRemark(Integer userId, Integer friendId, String remark);

    /**
     * 检查是否是好友关系
     *
     * @param userId   用户ID
     * @param friendId 好友ID
     * @return 是否是好友
     */
    Boolean isFriend(Integer userId, Integer friendId);

    /**
     * 获取好友信息
     *
     * @param userId   用户ID
     * @param friendId 好友ID
     * @return 好友信息
     */
    FriendDTO getFriendInfo(Integer userId, Integer friendId);

    /**
     * 搜索好友（支持备注搜索）
     *
     * @param friendSearchDTO 搜索条件
     * @return 好友列表
     */
    PageInfo<FriendDTO> searchFriends(FriendSearchDTO friendSearchDTO);

    /**
     * 获取用户的好友数量
     *
     * @param userId 用户ID
     * @return 好友数量
     */
    Integer getFriendCount(Integer userId);

    /**
     * 获取用户待处理的好友请求数量
     *
     * @param userId 用户ID
     * @return 待处理请求数量
     */
    Integer getPendingRequestCount(Integer userId);

    FriendRequestDTO getFriendRequestById(Integer requestId);
}

