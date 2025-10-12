package com.liang.bbs.user.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.liang.bbs.user.facade.dto.EntityDTO;
import com.liang.bbs.user.facade.dto.FollowCountDTO;
import com.liang.bbs.user.facade.dto.FollowDTO;
import com.liang.bbs.user.facade.dto.FollowSearchDTO;
import com.liang.bbs.user.facade.server.EntityService;
import com.liang.bbs.user.facade.server.FollowService;
import com.liang.bbs.user.persistence.entity.FollowPo;
import com.liang.bbs.user.persistence.entity.FollowPoExample;
import com.liang.bbs.user.persistence.mapper.FollowPoMapper;
import com.liang.bbs.user.service.mapstruct.FollowMS;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.Reference;
import org.apache.dubbo.config.annotation.Service;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 关注服务实现类
 */
@Slf4j
@Service
public class FollowServiceImpl implements FollowService {

    @Autowired
    private FollowPoMapper followPoMapper;
    
    @Reference
    private EntityService entityService;

    @Override
    public PageInfo<FollowDTO> getSelect(FollowSearchDTO followSearchDTO, Integer page, Integer pageSize) {
        PageHelper.startPage(page, pageSize);
        FollowPoExample example = new FollowPoExample();
        FollowPoExample.Criteria criteria = example.createCriteria();
        
        if (followSearchDTO.getType() != null) {
            criteria.andTypeEqualTo(followSearchDTO.getType());
        }
        if (followSearchDTO.getTargetid() != null) {
            criteria.andTargetidEqualTo(followSearchDTO.getTargetid());
        }
        if (followSearchDTO.getUserid() != null) {
            criteria.andUseridEqualTo(followSearchDTO.getUserid());
        }
        if (followSearchDTO.getStatus() != null) {
            criteria.andStatusEqualTo(followSearchDTO.getStatus());
        }
        
        List<FollowPo> followPos = followPoMapper.selectByExample(example);
        List<FollowDTO> followDTOs = followPos.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        
        return new PageInfo<>(followDTOs);
    }

    @Override
    public PageInfo<FollowDTO> getFollowTo(Integer userId, Integer page, Integer pageSize,String type) {
        PageHelper.startPage(page, pageSize);
        FollowPoExample example = new FollowPoExample();
        FollowPoExample.Criteria criteria = example.createCriteria()
                .andUseridEqualTo(userId)
                .andStatusEqualTo("1");
        
        // 当type不为null时才添加type条件
        if (type != null) {
            criteria.andTypeEqualTo(type);
        }
        
        List<FollowPo> followPos = followPoMapper.selectByExample(example);
        PageInfo<FollowPo> pageInfoPo = new PageInfo<>(followPos);
        
        // 使用FollowMS映射器转换并保持分页信息
        return FollowMS.INSTANCE.toPage(pageInfoPo);
    }
@Override
    public PageInfo<FollowDTO> getFollowFrom(Integer targetId, Integer page, Integer pageSize, String type) {
        PageHelper.startPage(page, pageSize);
        FollowPoExample example = new FollowPoExample();
        FollowPoExample.Criteria criteria = example.createCriteria()
                .andTargetidEqualTo(targetId)
                .andStatusEqualTo("1");
        
        // 当type不为null时才添加type条件
        if (type != null) {
            criteria.andTypeEqualTo(type);
        }
        
        List<FollowPo> followPos = followPoMapper.selectByExample(example);
        PageInfo<FollowPo> pageInfoPo = new PageInfo<>(followPos);
        
        // 使用FollowMS映射器转换并保持分页信息
        return FollowMS.INSTANCE.toPage(pageInfoPo);
    }
    @Override
    public FollowDTO getById(Integer id) {
        FollowPo followPo = followPoMapper.selectByPrimaryKey(id);
        return followPo != null ? convertToDTO(followPo) : null;
    }

    @Override
    public FollowDTO getByFromToUser(Integer userId, Integer targetId, String type) {
        FollowPoExample example = new FollowPoExample();
        example.createCriteria()
                .andUseridEqualTo(userId)
                .andTargetidEqualTo(targetId)
                .andTypeEqualTo(type);
        
        List<FollowPo> followPos = followPoMapper.selectByExample(example);
        return followPos.isEmpty() ? null : convertToDTO(followPos.get(0));
    }

    @Override
    public Boolean updateFollowState(Integer userId, Integer targetId, String type) {
        FollowPoExample example = new FollowPoExample();
        example.createCriteria()
                .andUseridEqualTo(userId)
                .andTargetidEqualTo(targetId)
                .andTypeEqualTo(type);
        
        List<FollowPo> existingFollows = followPoMapper.selectByExample(example);
        
        if (existingFollows.isEmpty()) {
            // 创建新的关注关系
            FollowPo followPo = new FollowPo();
            followPo.setUserid(userId);
            followPo.setTargetid(targetId);
            followPo.setType(type);
            followPo.setStatus("1");
            followPo.setTime(new Date());
            return followPoMapper.insertSelective(followPo) > 0;
        } else {
            // 切换关注状态
            FollowPo existingFollow = existingFollows.get(0);
            String newStatus = "1".equals(existingFollow.getStatus()) ? "0" : "1";
            existingFollow.setStatus(newStatus);
            existingFollow.setTime(new Date());
            return followPoMapper.updateByPrimaryKeySelective(existingFollow) > 0;
        }
    }

    @Override
    public Boolean createFollowRelation(Integer userId, Integer targetId, String type) {
        // 检查是否已存在关注关系
        FollowPoExample example = new FollowPoExample();
        example.createCriteria()
                .andUseridEqualTo(userId)
                .andTargetidEqualTo(targetId)
                .andTypeEqualTo(type);
        
        List<FollowPo> existingFollows = followPoMapper.selectByExample(example);
        
        if (!existingFollows.isEmpty()) {
            // 已存在关注关系，更新为已关注状态
            FollowPo existingFollow = existingFollows.get(0);
            if (!"1".equals(existingFollow.getStatus())) {
                existingFollow.setStatus("1");
                existingFollow.setTime(new Date());
                return followPoMapper.updateByPrimaryKeySelective(existingFollow) > 0;
            }
            return true; // 已经关注了
        }
        
        // 创建新的关注关系
        FollowPo followPo = new FollowPo();
        followPo.setUserid(userId);
        followPo.setTargetid(targetId);
        followPo.setType(type);
        followPo.setStatus("1");
        followPo.setTime(new Date());
        return followPoMapper.insertSelective(followPo) > 0;
    }

    @Override
    public Boolean deleteFollowRelation(Integer userId, Integer targetId, String type) {
        FollowPoExample example = new FollowPoExample();
        example.createCriteria()
                .andUseridEqualTo(userId)
                .andTargetidEqualTo(targetId)
                .andTypeEqualTo(type)
                .andStatusEqualTo("1");
        
        List<FollowPo> existingFollows = followPoMapper.selectByExample(example);
        
        if (existingFollows.isEmpty()) {
            return false; // 不存在关注关系
        }
        
        // 将状态更新为取消关注
        FollowPo existingFollow = existingFollows.get(0);
        existingFollow.setStatus("0");
        existingFollow.setTime(new Date());
        return followPoMapper.updateByPrimaryKeySelective(existingFollow) > 0;

    }
@Override
public Integer getCount(Integer targetId,String t){
    FollowPoExample followExample = new FollowPoExample();
        followExample.createCriteria()
                .andTargetidEqualTo(targetId)
                .andStatusEqualTo("1");
                
        Integer followCount = followPoMapper.countByExample(followExample);
        return followCount;
}
    @Override
    public PageInfo<FollowDTO> getFollowListWithDto(Integer userId, Integer page, Integer pageSize, String type) {
        try {
            // 获取用户的关注列表
            PageInfo<FollowDTO> followPage = getFollowTo(userId, page, pageSize, type);
            
            if (followPage.getList() != null && !followPage.getList().isEmpty()) {
                // 为每个关注记录填充对应的DTO信息
                for (FollowDTO followDTO : followPage.getList()) {
                    try {
                        // 根据type和targetid获取对应的实体DTO
                        EntityDTO entityDTO = entityService.getById(followDTO.getTargetid(), followDTO.getType());
                        if (entityDTO != null) {
                            followDTO.setDto(entityDTO);
                        }
                    } catch (Exception e) {
                        // 如果获取实体信息失败，记录日志但不影响主流程
                        log.error("获取实体信息失败 - type: {}, targetId: {}", followDTO.getType(), followDTO.getTargetid(), e);
                    }
                }
            }
            
            return followPage;
        } catch (Exception e) {
            log.error("获取用户关注列表失败, userId: {}, type: {}", userId, type, e);
            return new PageInfo<>();
        }
    }

    @Override
    public PageInfo<FollowDTO> getFollowerListWithDto(Integer targetId, Integer page, Integer pageSize, String type) {
        try {
            // 获取用户的粉丝列表
            PageInfo<FollowDTO> followerPage = getFollowFrom(targetId, page, pageSize, type);
            
            if (followerPage.getList() != null && !followerPage.getList().isEmpty()) {
                // 为每个粉丝记录填充对应的用户信息
                for (FollowDTO followDTO : followerPage.getList()) {
                    try {
                        // 粉丝记录中的userid就是粉丝的用户ID
                        EntityDTO entityDTO = entityService.getById(followDTO.getUserid(), "3");
                        if (entityDTO != null) {
                            followDTO.setDto(entityDTO);
                        }
                    } catch (Exception e) {
                        // 如果获取实体信息失败，记录日志但不影响主流程
                        log.error("获取用户实体信息失败 - userId: {}", followDTO.getUserid(), e);
                    }
                }
            }
            
            return followerPage;
        } catch (Exception e) {
            log.error("获取用户粉丝列表失败, targetId: {}, type: {}", targetId, type, e);
            return new PageInfo<>();
        }
    }

    @Override
    public FollowCountDTO getUserFollow(Integer userId) {
        FollowCountDTO followCountDTO = new FollowCountDTO();
        followCountDTO.setUserId(userId);
        
        // 获取关注数量（我关注了多少人）
        FollowPoExample followExample = new FollowPoExample();
        followExample.createCriteria()
                .andUseridEqualTo(userId)
                .andStatusEqualTo("1");
                
        Integer followCount = followPoMapper.countByExample(followExample);
        followCountDTO.setFollowCount(followCount);
        
        // 获取粉丝数量（有多少人关注我）
        FollowPoExample fansExample = new FollowPoExample();
        fansExample.createCriteria()
                .andTargetidEqualTo(userId)
                .andTypeEqualTo("3")
                .andStatusEqualTo("1");
                
        Integer fansCount = followPoMapper.countByExample(fansExample);
        followCountDTO.setFansCount(fansCount);
        
        return followCountDTO;
    }

    /**
     * 将PO转换为DTO
     *
     * @param followPo 关注PO对象
     * @return 关注DTO对象
     */
    private FollowDTO convertToDTO(FollowPo followPo) {
        FollowDTO followDTO = new FollowDTO();
        BeanUtils.copyProperties(followPo, followDTO);
        return followDTO;
    }
}