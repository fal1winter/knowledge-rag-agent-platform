package com.liang.bbs.user.service.impl;

import com.liang.bbs.user.facade.dto.UserHomepageDTO;
import com.liang.bbs.user.facade.enums.HomepageTypeEnum;
import com.liang.bbs.user.facade.server.UserHomepageService;
import com.liang.bbs.user.persistence.entity.UserHomepagePo;
import com.liang.bbs.user.persistence.entity.UserHomepagePoExample;
import com.liang.bbs.user.persistence.mapper.UserHomepagePoMapper;
import com.liang.bbs.user.service.mapstruct.UserHomepageMS;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户主页服务实现类
 */
@Component
@Slf4j
@Service
public class UserHomepageServiceImpl implements UserHomepageService {

    @Autowired
    private UserHomepagePoMapper userHomepagePoMapper;

    @Override
    public UserHomepageDTO saveHomepage(UserHomepageDTO dto) {
        log.info("保存用户主页: userId={}, type={}", dto.getUserId(), dto.getType());

        // 验证类型
        if (!HomepageTypeEnum.isValid(dto.getType())) {
            throw new IllegalArgumentException("无效的主页类型: " + dto.getType());
        }

        // 如果是 mainpage 类型，检查是否已存在，存在则更新
        if (HomepageTypeEnum.mainpage.getCode().equals(dto.getType())) {
            UserHomepageDTO existing = getUserMainpage(dto.getUserId());
            if (existing != null) {
                dto.setId(existing.getId());
                return updateHomepage(dto);
            }
        }

        // 创建新记录
        UserHomepagePo po = UserHomepageMS.INSTANCE.toPo(dto);
        po.setCreatedAt(LocalDateTime.now());
        po.setUpdatedAt(LocalDateTime.now());
        if (po.getStatus() == null) {
            po.setStatus((byte) 1);
        }
        userHomepagePoMapper.insertSelective(po);

        return UserHomepageMS.INSTANCE.toDTO(po);
    }

    @Override
    public UserHomepageDTO updateHomepage(UserHomepageDTO dto) {
        log.info("更新用户主页: id={}", dto.getId());

        if (dto.getId() == null) {
            throw new IllegalArgumentException("主页ID不能为空");
        }

        UserHomepagePo existing = userHomepagePoMapper.selectByPrimaryKey(dto.getId());
        if (existing == null) {
            throw new IllegalArgumentException("主页不存在: " + dto.getId());
        }

        // 更新字段
        if (dto.getContent() != null) {
            existing.setContent(dto.getContent());
        }
        if (dto.getStatus() != null) {
            existing.setStatus(dto.getStatus().byteValue());
        }
        existing.setUpdatedAt(LocalDateTime.now());

        userHomepagePoMapper.updateByPrimaryKeySelective(existing);
        return UserHomepageMS.INSTANCE.toDTO(existing);
    }

    @Override
    public Boolean deleteHomepage(Long id) {
        log.info("删除用户主页: id={}", id);
        int result = userHomepagePoMapper.deleteByPrimaryKey(id);
        return result > 0;
    }

    @Override
    public UserHomepageDTO getHomepageById(Long id) {
        UserHomepagePo po = userHomepagePoMapper.selectByPrimaryKey(id);
        return po != null ? UserHomepageMS.INSTANCE.toDTO(po) : null;
    }

    @Override
    public UserHomepageDTO getUserMainpage(Integer userId) {
        log.info("获取用户主页: userId={}", userId);

        UserHomepagePoExample example = new UserHomepagePoExample();
        example.createCriteria()
                .andUserIdEqualTo(userId)
                .andTypeEqualTo(HomepageTypeEnum.mainpage.getCode());

        List<UserHomepagePo> list = userHomepagePoMapper.selectByExampleWithBLOBs(example);
        return list.isEmpty() ? null : UserHomepageMS.INSTANCE.toDTO(list.get(0));
    }

    @Override
    public List<UserHomepageDTO> getUserActivities(Integer userId) {
        log.info("获取用户动态: userId={}", userId);

        UserHomepagePoExample example = new UserHomepagePoExample();
        example.createCriteria()
                .andUserIdEqualTo(userId)
                .andTypeEqualTo(HomepageTypeEnum.activity.getCode())
                .andStatusEqualTo((byte) 1);
        example.setOrderByClause("created_at DESC");

        List<UserHomepagePo> list = userHomepagePoMapper.selectByExampleWithBLOBs(example);
        return UserHomepageMS.INSTANCE.toDTO(list);
    }

    @Override
    public List<UserHomepageDTO> getHomepagesByUserAndType(Integer userId, String type) {
        log.info("获取用户主页: userId={}, type={}", userId, type);

        UserHomepagePoExample example = new UserHomepagePoExample();
        example.createCriteria()
                .andUserIdEqualTo(userId)
                .andTypeEqualTo(type);
        example.setOrderByClause("created_at DESC");

        List<UserHomepagePo> list = userHomepagePoMapper.selectByExampleWithBLOBs(example);
        return UserHomepageMS.INSTANCE.toDTO(list);
    }

    @Override
    public List<UserHomepageDTO> getAllHomepagesByUser(Integer userId) {
        log.info("获取用户所有主页: userId={}", userId);

        UserHomepagePoExample example = new UserHomepagePoExample();
        example.createCriteria().andUserIdEqualTo(userId);
        example.setOrderByClause("type, created_at DESC");

        List<UserHomepagePo> list = userHomepagePoMapper.selectByExampleWithBLOBs(example);
        return UserHomepageMS.INSTANCE.toDTO(list);
    }

    @Override
    public List<UserHomepageDTO> getAllActivities(Integer currentPage, Integer pageSize) {
        return getAllActivities(currentPage, pageSize, null);
    }

    @Override
    public List<UserHomepageDTO> getAllActivities(Integer currentPage, Integer pageSize, String topic) {
        log.info("获取所有用户动态: page={}, size={}, topic={}", currentPage, pageSize, topic);
        int offset = 0;
        int limit = 10;
        if (currentPage != null && pageSize != null && currentPage > 0 && pageSize > 0) {
            offset = (currentPage - 1) * pageSize;
            limit = pageSize;
        }
        List<UserHomepagePo> list = userHomepagePoMapper.selectActivitiesByTopic(topic, offset, limit);
        return UserHomepageMS.INSTANCE.toDTO(list);
    }

    @Override
    public void incrementLikeCount(Long activityId, int delta) {
        UserHomepagePo po = userHomepagePoMapper.selectByPrimaryKey(activityId);
        if (po != null) {
            int newCount = Math.max(0, (po.getLikeCount() == null ? 0 : po.getLikeCount()) + delta);
            po.setLikeCount(newCount);
            userHomepagePoMapper.updateByPrimaryKeySelective(po);
        }
    }

    @Override
    public void incrementCommentCount(Long activityId, int delta) {
        UserHomepagePo po = userHomepagePoMapper.selectByPrimaryKey(activityId);
        if (po != null) {
            int newCount = Math.max(0, (po.getCommentCount() == null ? 0 : po.getCommentCount()) + delta);
            po.setCommentCount(newCount);
            userHomepagePoMapper.updateByPrimaryKeySelective(po);
        }
    }
}
