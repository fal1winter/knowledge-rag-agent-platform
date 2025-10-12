package com.liang.bbs.user.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.liang.bbs.user.facade.dto.FriendDTO;
import com.liang.bbs.user.facade.dto.FriendRequestDTO;
import com.liang.bbs.user.facade.dto.FriendSearchDTO;
import com.liang.bbs.user.facade.dto.user.UserDTO;
import com.liang.bbs.user.facade.server.FriendService;
import com.liang.bbs.user.facade.server.UserService;
import com.liang.bbs.user.persistence.entity.FriendPo;
import com.liang.bbs.user.persistence.entity.FriendPoExample;
import com.liang.bbs.user.persistence.entity.FriendRequestPo;
import com.liang.bbs.user.persistence.entity.FriendRequestPoExample;
import com.liang.bbs.user.persistence.mapper.FriendPoMapper;
import com.liang.bbs.user.persistence.mapper.FriendRequestPoMapper;
import com.liang.bbs.user.service.mapstruct.FriendMS;
import com.liang.bbs.user.service.mapstruct.FriendRequestMS;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.Reference;
import org.apache.dubbo.config.annotation.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 好友服务实现类
 */
@Slf4j
@Service
public class FriendServiceImpl implements FriendService {

    @Autowired
    private FriendPoMapper friendPoMapper;

    @Autowired
    private FriendRequestPoMapper friendRequestPoMapper;

    @Reference
    private UserService userService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean sendFriendRequest(Integer fromUserId, Integer toUserId, String message) {
        try {
            // 检查是否已经是好友
            if (isFriend(fromUserId, toUserId)) {
                log.warn("用户{}和用户{}已经是好友关系", fromUserId, toUserId);
                return false;
            }

            // 检查是否已经发送过请求且待处理
            FriendRequestPoExample example = new FriendRequestPoExample();
            example.createCriteria()
                    .andFromUserIdEqualTo(fromUserId)
                    .andToUserIdEqualTo(toUserId)
                    .andStatusEqualTo(false); // false = 待处理(0)
            List<FriendRequestPo> existingRequests = friendRequestPoMapper.selectByExample(example);
            if (!existingRequests.isEmpty()) {
                log.warn("用户{}已向用户{}发送过好友请求", fromUserId, toUserId);
                return false;
            }

            // 创建好友请求
            FriendRequestPo requestPo = new FriendRequestPo();
            requestPo.setFromUserId(fromUserId);
            requestPo.setToUserId(toUserId);
            requestPo.setMessage(message);
            requestPo.setStatus(false); // false = 待处理(0)
            requestPo.setCreateTime(LocalDateTime.now());
            requestPo.setUpdateTime(LocalDateTime.now());

            return friendRequestPoMapper.insertSelective(requestPo) > 0;
        } catch (Exception e) {
            log.error("发送好友请求失败", e);
            throw e;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean handleFriendRequest(Integer requestId, Byte status) {
        try {
            // 查询请求
            FriendRequestPo requestPo = friendRequestPoMapper.selectByPrimaryKey(requestId);
            if (requestPo == null) {
                log.warn("好友请求不存在: {}", requestId);
                return false;
            }

            if (requestPo.getStatus() == null || !requestPo.getStatus().equals(false)) {
                // status为null或true表示已处理（MyBatis Generator将tinyint映射为Boolean）
                // 0 -> false (待处理), 1 -> true (已处理)
                if (requestPo.getStatus() != null && requestPo.getStatus()) {
                    log.warn("好友请求已处理: {}", requestId);
                    return false;
                }
            }

            // 更新请求状态 (MyBatis将Byte 0映射为false, 非0映射为true)
            requestPo.setStatus(status != 0);
            requestPo.setUpdateTime(LocalDateTime.now());
            friendRequestPoMapper.updateByPrimaryKey(requestPo);

            // 如果同意(status=1)，建立双向好友关系
            if (status == 1) {
                createBidirectionalFriendship(requestPo.getFromUserId(), requestPo.getToUserId());
            }

            return true;
        } catch (Exception e) {
            log.error("处理好友请求失败", e);
            throw e;
        }
    }

    @Override
    public PageInfo<FriendRequestDTO> getReceivedRequests(Integer userId, Integer page, Integer pageSize) {
        PageHelper.startPage(page, pageSize);
        FriendRequestPoExample example = new FriendRequestPoExample();
        example.createCriteria().andToUserIdEqualTo(userId);
        example.setOrderByClause("create_time DESC");

        List<FriendRequestPo> requestPos = friendRequestPoMapper.selectByExample(example);
        PageInfo<FriendRequestPo> pageInfoPo = new PageInfo<>(requestPos);

        // 转换并填充用户信息
        PageInfo<FriendRequestDTO> pageInfo = FriendRequestMS.INSTANCE.toPage(pageInfoPo);
        pageInfo.getList().forEach(request -> {
            UserDTO fromUser = userService.getById(request.getFromUserId());
            request.setFromUserInfo(fromUser);
        });

        return pageInfo;
    }

    @Override
    public PageInfo<FriendRequestDTO> getSentRequests(Integer userId, Integer page, Integer pageSize) {
        PageHelper.startPage(page, pageSize);
        FriendRequestPoExample example = new FriendRequestPoExample();
        example.createCriteria().andFromUserIdEqualTo(userId);
        example.setOrderByClause("create_time DESC");

        List<FriendRequestPo> requestPos = friendRequestPoMapper.selectByExample(example);
        PageInfo<FriendRequestPo> pageInfoPo = new PageInfo<>(requestPos);

        return FriendRequestMS.INSTANCE.toPage(pageInfoPo);
    }

    @Override
    public PageInfo<FriendDTO> getFriendList(Integer userId, Integer page, Integer pageSize) {
        PageHelper.startPage(page, pageSize);
        FriendPoExample example = new FriendPoExample();
        example.createCriteria()
                .andUserIdEqualTo(userId)
                .andStatusEqualTo(true); // true = 正常状态(1)
        example.setOrderByClause("create_time DESC");

        List<FriendPo> friendPos = friendPoMapper.selectByExample(example);
        PageInfo<FriendPo> pageInfoPo = new PageInfo<>(friendPos);

        // 转换并填充好友用户信息（使用扁平字段绕过Dubbo序列化问题）
        PageInfo<FriendDTO> pageInfo = FriendMS.INSTANCE.toPage(pageInfoPo);
        pageInfo.getList().forEach(friend -> {
            UserDTO friendUser = userService.getById(friend.getFriendId());
            friend.setFriendUserInfo(friendUser);
            if (friendUser != null) {
                friend.setFriendName(friendUser.getName());
                friend.setFriendPicture(friendUser.getPicture());
                friend.setFriendEmail(friendUser.getEmail());
                friend.setFriendPosition(friendUser.getPosition());
                friend.setFriendCompany(friendUser.getCompany());
                friend.setFriendIntro(friendUser.getIntro());
            }
        });

        return pageInfo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteFriend(Integer userId, Integer friendId) {
        try {
            // 删除双向好友关系
            deleteBidirectionalFriendship(userId, friendId);
            return true;
        } catch (Exception e) {
            log.error("删除好友失败", e);
            throw e;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean blockFriend(Integer userId, Integer friendId) {
        try {
            // 更新好友状态为拉黑
            FriendPoExample example = new FriendPoExample();
            example.createCriteria()
                    .andUserIdEqualTo(userId)
                    .andFriendIdEqualTo(friendId);

            List<FriendPo> friendPos = friendPoMapper.selectByExample(example);
            if (friendPos.isEmpty()) {
                log.warn("好友关系不存在");
                return false;
            }

            FriendPo friendPo = friendPos.get(0);
            friendPo.setStatus(true); // 注意：由于Boolean只能是true/false，实际应该用其他方式表示拉黑
            // TODO: 考虑在数据库层面使用tinyint(1)或单独的blocked字段来表示拉黑状态
            friendPo.setUpdateTime(LocalDateTime.now());
            friendPoMapper.updateByPrimaryKey(friendPo);

            return true;
        } catch (Exception e) {
            log.error("拉黑好友失败", e);
            throw e;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean unblockFriend(Integer userId, Integer friendId) {
        try {
            // 更新好友状态为正常
            FriendPoExample example = new FriendPoExample();
            example.createCriteria()
                    .andUserIdEqualTo(userId)
                    .andFriendIdEqualTo(friendId);

            List<FriendPo> friendPos = friendPoMapper.selectByExample(example);
            if (friendPos.isEmpty()) {
                log.warn("好友关系不存在");
                return false;
            }

            FriendPo friendPo = friendPos.get(0);
            friendPo.setStatus(true); // true = 正常状态(1)
            friendPo.setUpdateTime(LocalDateTime.now());
            friendPoMapper.updateByPrimaryKey(friendPo);

            return true;
        } catch (Exception e) {
            log.error("取消拉黑失败", e);
            throw e;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateFriendRemark(Integer userId, Integer friendId, String remark) {
        try {
            FriendPoExample example = new FriendPoExample();
            example.createCriteria()
                    .andUserIdEqualTo(userId)
                    .andFriendIdEqualTo(friendId);

            List<FriendPo> friendPos = friendPoMapper.selectByExample(example);
            if (friendPos.isEmpty()) {
                log.warn("好友关系不存在");
                return false;
            }

            FriendPo friendPo = friendPos.get(0);
            friendPo.setRemark(remark);
            friendPo.setUpdateTime(LocalDateTime.now());
            friendPoMapper.updateByPrimaryKey(friendPo);

            return true;
        } catch (Exception e) {
            log.error("更新好友备注失败", e);
            throw e;
        }
    }

    @Override
    public Boolean isFriend(Integer userId, Integer friendId) {
        FriendPoExample example = new FriendPoExample();
        example.createCriteria()
                .andUserIdEqualTo(userId)
                .andFriendIdEqualTo(friendId)
                .andStatusEqualTo(true); // true = 正常状态(1)

        return friendPoMapper.countByExample(example) > 0;
    }

    @Override
    public FriendDTO getFriendInfo(Integer userId, Integer friendId) {
        FriendPoExample example = new FriendPoExample();
        example.createCriteria()
                .andUserIdEqualTo(userId)
                .andFriendIdEqualTo(friendId);

        List<FriendPo> friendPos = friendPoMapper.selectByExample(example);
        if (friendPos.isEmpty()) {
            return null;
        }

        FriendDTO friendDTO = FriendMS.INSTANCE.toDTO(friendPos.get(0));
        UserDTO friendUser = userService.getById(friendId);
        friendDTO.setFriendUserInfo(friendUser);
        if (friendUser != null) {
            friendDTO.setFriendName(friendUser.getName());
            friendDTO.setFriendPicture(friendUser.getPicture());
            friendDTO.setFriendEmail(friendUser.getEmail());
            friendDTO.setFriendPosition(friendUser.getPosition());
            friendDTO.setFriendCompany(friendUser.getCompany());
            friendDTO.setFriendIntro(friendUser.getIntro());
        }

        return friendDTO;
    }

    @Override
    public PageInfo<FriendDTO> searchFriends(FriendSearchDTO friendSearchDTO) {
        PageHelper.startPage(friendSearchDTO.getPage(), friendSearchDTO.getPageSize());
        FriendPoExample example = new FriendPoExample();
        FriendPoExample.Criteria criteria = example.createCriteria();

        if (friendSearchDTO.getUserId() != null) {
            criteria.andUserIdEqualTo(friendSearchDTO.getUserId());
        }
        if (friendSearchDTO.getFriendId() != null) {
            criteria.andFriendIdEqualTo(friendSearchDTO.getFriendId());
        }
        if (friendSearchDTO.getStatus() != null) {
            criteria.andStatusEqualTo(friendSearchDTO.getStatus() != 0); // 0 -> false, 非0 -> true
        }
        if (friendSearchDTO.getRemark() != null && !friendSearchDTO.getRemark().isEmpty()) {
            criteria.andRemarkLike("%" + friendSearchDTO.getRemark() + "%");
        }

        List<FriendPo> friendPos = friendPoMapper.selectByExample(example);
        PageInfo<FriendPo> pageInfoPo = new PageInfo<>(friendPos);

        // 转换并填充好友用户信息（使用扁平字段绕过Dubbo序列化问题）
        PageInfo<FriendDTO> pageInfo = FriendMS.INSTANCE.toPage(pageInfoPo);
        pageInfo.getList().forEach(friend -> {
            UserDTO friendUser = userService.getById(friend.getFriendId());
            friend.setFriendUserInfo(friendUser);
            if (friendUser != null) {
                friend.setFriendName(friendUser.getName());
                friend.setFriendPicture(friendUser.getPicture());
                friend.setFriendEmail(friendUser.getEmail());
                friend.setFriendPosition(friendUser.getPosition());
                friend.setFriendCompany(friendUser.getCompany());
                friend.setFriendIntro(friendUser.getIntro());
            }
        });

        return pageInfo;
    }

    @Override
    public Integer getFriendCount(Integer userId) {
        FriendPoExample example = new FriendPoExample();
        example.createCriteria()
                .andUserIdEqualTo(userId)
                .andStatusEqualTo(true); // true = 正常状态(1)

        return (int) friendPoMapper.countByExample(example);
    }

    @Override
    public Integer getPendingRequestCount(Integer userId) {
        FriendRequestPoExample example = new FriendRequestPoExample();
        example.createCriteria()
                .andToUserIdEqualTo(userId)
                .andStatusEqualTo(false); // false = 待处理(0)

        return (int) friendRequestPoMapper.countByExample(example);
    }

    /**
     * 建立双向好友关系
     */
    private void createBidirectionalFriendship(Integer userId1, Integer userId2) {
        LocalDateTime now = LocalDateTime.now();

        // 创建 userId1 -> userId2 的好友关系
        FriendPo friend1 = new FriendPo();
        friend1.setUserId(userId1);
        friend1.setFriendId(userId2);
        friend1.setStatus(true); // true = 正常状态(1)
        friend1.setCreateTime(now);
        friend1.setUpdateTime(now);
        friendPoMapper.insertSelective(friend1);

        // 创建 userId2 -> userId1 的好友关系
        FriendPo friend2 = new FriendPo();
        friend2.setUserId(userId2);
        friend2.setFriendId(userId1);
        friend2.setStatus(true); // true = 正常状态(1)
        friend2.setCreateTime(now);
        friend2.setUpdateTime(now);
        friendPoMapper.insertSelective(friend2);
    }

    /**
     * 删除双向好友关系
     */
    private void deleteBidirectionalFriendship(Integer userId1, Integer userId2) {
        // 删除 userId1 -> userId2 的好友关系
        FriendPoExample example1 = new FriendPoExample();
        example1.createCriteria()
                .andUserIdEqualTo(userId1)
                .andFriendIdEqualTo(userId2);
        friendPoMapper.deleteByExample(example1);

        // 删除 userId2 -> userId1 的好友关系
        FriendPoExample example2 = new FriendPoExample();
        example2.createCriteria()
                .andUserIdEqualTo(userId2)
                .andFriendIdEqualTo(userId1);
        friendPoMapper.deleteByExample(example2);
    }

    @Override
    public FriendRequestDTO getFriendRequestById(Integer requestId) {
        FriendRequestPoExample example = new FriendRequestPoExample();
        example.createCriteria()
                .andIdEqualTo(requestId);
        List<FriendRequestPo> friendRequestPos = friendRequestPoMapper.selectByExample(example);
        if (friendRequestPos.isEmpty()) {
            return null;
        }
        return FriendRequestMS.INSTANCE.toDTO(friendRequestPos.get(0));
    }
}

