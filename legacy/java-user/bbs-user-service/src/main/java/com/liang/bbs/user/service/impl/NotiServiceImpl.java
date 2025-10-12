package com.liang.bbs.user.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;

import com.liang.bbs.user.facade.dto.NotiDTO;
import com.liang.bbs.user.facade.dto.NotiSearchDTO;
import com.liang.bbs.user.facade.dto.user.UserSsoDTO;
import com.liang.bbs.user.facade.server.NotiService;
import com.liang.bbs.user.persistence.entity.NotiPo;
import com.liang.bbs.user.persistence.entity.NotiPoExample;
import com.liang.bbs.user.persistence.mapper.NotiPoMapper;
import com.liang.bbs.user.service.mapstruct.NotiMS;
import com.liang.bbs.user.facade.server.NotiContentService;
import org.apache.commons.lang3.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.apache.dubbo.config.annotation.Service;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 通知服务实现类
 *
 */
@Slf4j
@Service
public class NotiServiceImpl implements NotiService {

    @Autowired
    private NotiPoMapper notiPoMapper;

    @Autowired
    private NotiMS notiMS;

    @Autowired
    private NotiContentService notiContentService;

    @Override
    public Boolean create(NotiDTO notiDTO, Integer userId) {
        try {
            NotiPo notiPo = notiMS.toPo(notiDTO);
            notiPo.setUserid(notiDTO.getUserid());
            notiPo.setTime(LocalDateTime.now());
            notiPo.setIsRead("0"); // 默认未读
            notiPo.setStatus("1"); // 默认启用
            
            // 处理内容中的占位符、清理HTML标签并截断长度
            if (notiPo.getContent() != null) {
                String processedContent = notiContentService.processNotificationContent(notiPo.getContent());
                notiPo.setContent(processedContent);
            }
            
            // 确保extra字段不为空字符串，而是有效的JSON
            if (notiPo.getExtra() == null || notiPo.getExtra().trim().isEmpty()) {
                notiPo.setExtra("{}");
            }
            
            return notiPoMapper.insertSelective(notiPo) > 0;
        } catch (Exception e) {
            log.error("创建通知失败, notiDTO: {}, userId: {}", notiDTO, userId, e);
            return false;
        }
    }


    @Override
    public Long getNotReadNotiCount(Integer userId) {
        try {
            NotiPoExample example = new NotiPoExample();
            example.createCriteria()
                    .andUseridEqualTo(userId)
                    .andIsReadEqualTo("0")
                    .andStatusEqualTo("1");
            
            return notiPoMapper.countByExample(example);
        } catch (Exception e) {
            log.error("获取未读通知数量失败, userId: {}", userId, e);
            return 0L;
        }
    }

    @Override
    public Boolean haveRead(Integer id, Integer userId) {
        try {
            NotiPoExample example = new NotiPoExample();
            example.createCriteria()
                    .andIdEqualTo(id)
                    .andUseridEqualTo(userId);
            
            NotiPo updatePo = new NotiPo();
            updatePo.setIsRead("1");
            
            
            return notiPoMapper.updateByExampleSelective(updatePo, example) > 0;
        } catch (Exception e) {
            log.error("标记通知已读失败, id: {}, userId: {}", id, userId, e);
            return false;
        }
    }

    @Override
    public Boolean markRead(List<Integer> ids, Integer userId) {
        try {
            NotiPoExample example = new NotiPoExample();
            example.createCriteria()
                    .andIdIn(ids)
                    .andUseridEqualTo(userId);
            
            NotiPo updatePo = new NotiPo();
            updatePo.setIsRead("1");
            
            return notiPoMapper.updateByExampleSelective(updatePo, example) > 0;
        } catch (Exception e) {
            log.error("批量标记通知已读失败, ids: {}, userId: {}", ids, userId, e);
            return false;
        }
    }

    @Override
    public PageInfo<NotiDTO> getList(NotiSearchDTO notiSearchDTO, Integer pageNum, Integer pageSize, Integer userId) {
        try {
            PageHelper.startPage(pageNum, pageSize);
            
            NotiPoExample example = new NotiPoExample();
            NotiPoExample.Criteria criteria = example.createCriteria()
                    .andUseridEqualTo(userId)
                    .andStatusEqualTo("1");
            
            // 根据查询条件添加过滤
            if (notiSearchDTO.getType() != null) {
                criteria.andTypeEqualTo(notiSearchDTO.getType());
            }
            if (notiSearchDTO.getSenderid() != null) {
                criteria.andSenderidEqualTo(notiSearchDTO.getSenderid());
            }
            if (StringUtils.isNotBlank(notiSearchDTO.getContent())) {
                criteria.andContentLike("%" + notiSearchDTO.getContent() + "%");
            }
            
            // 按时间倒序排序
            example.setOrderByClause("time desc");
            
            List<NotiPo> poList = notiPoMapper.selectByExampleWithBLOBs(example);
            List<NotiDTO> dtoList = notiMS.toDTO(poList);
            
            // 处理通知内容中的占位符
            for (NotiDTO dto : dtoList) {
                if (dto.getContent() != null) {
                    String processedContent = notiContentService.processNotificationContent(dto.getContent());
                    dto.setContent(processedContent);
                }
            }
            
            return new PageInfo<>(dtoList);
        } catch (Exception e) {
            log.error("获取通知列表失败, notiSearchDTO: {}, userId: {}", notiSearchDTO, userId, e);
            return new PageInfo<>(Collections.emptyList());
        }
    }

    @Override
    public PageInfo<NotiDTO> getNotiById(Integer userId, Integer pageNum, Integer pageSize) {
        try {
            PageHelper.startPage(pageNum, pageSize);
            
            NotiPoExample example = new NotiPoExample();
            example.createCriteria()
                    .andUseridEqualTo(userId)
                    .andStatusEqualTo("1");
            
            // 按时间倒序排序
            example.setOrderByClause("time desc");
            
            List<NotiPo> poList = notiPoMapper.selectByExampleWithBLOBs(example);
            List<NotiDTO> dtoList = notiMS.toDTO(poList);
            
            // 处理通知内容中的占位符
            for (NotiDTO dto : dtoList) {
                if (dto.getContent() != null) {
                    String processedContent = notiContentService.processNotificationContent(dto.getContent());
                    dto.setContent(processedContent);
                }
            }
            
            return new PageInfo<>(dtoList);
        } catch (Exception e) {
            log.error("根据用户ID获取通知列表失败, userId: {}", userId, e);
            return new PageInfo<>(Collections.emptyList());
        }
    }

    @Override
    public PageInfo<NotiDTO> getNotiDetail(Integer userId, String isRead, Integer pageNum, Integer pageSize) {
        try {
            PageHelper.startPage(pageNum, pageSize);
            
            NotiPoExample example = new NotiPoExample();
            NotiPoExample.Criteria criteria = example.createCriteria()
                    .andUseridEqualTo(userId)
                    .andStatusEqualTo("1");
            if (isRead != null) {
                criteria.andIsReadEqualTo(isRead);
            }
            
            // 按时间倒序排序
            example.setOrderByClause("time desc");
            
            List<NotiPo> poList = notiPoMapper.selectByExampleWithBLOBs(example);
            PageInfo<NotiPo>po=new PageInfo<>(poList);
            PageInfo<NotiDTO> dto = notiMS.INSTANCE.toPage(po);
            
            return dto;
        } catch (Exception e) {
            log.error("根据用户ID和已读状态获取通知列表失败, userId: {}, isRead: {}", userId, isRead, e);
            return new PageInfo<>(Collections.emptyList());
        }
    }


    @Override
    public List<NotiDTO> getNotiList(Integer userId) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getNotiList'");
    }
}