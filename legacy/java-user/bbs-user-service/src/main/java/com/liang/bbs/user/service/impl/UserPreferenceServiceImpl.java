package com.liang.bbs.user.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liang.bbs.user.facade.dto.UserPreferenceDTO;
import com.liang.bbs.user.facade.server.UserPreferenceService;
import com.liang.bbs.user.persistence.entity.UserPreferencePo;
import com.liang.bbs.user.persistence.mapper.UserPreferencePoMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户偏好服务实现
 */
@Slf4j
@Service
public class UserPreferenceServiceImpl implements UserPreferenceService {

    @Autowired
    private UserPreferencePoMapper userPreferencePoMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public UserPreferenceDTO getByUserId(Integer userId) {
        UserPreferencePo po = userPreferencePoMapper.selectByUserId(userId);
        return po != null ? convertToDTO(po) : null;
    }

    @Override
    public boolean saveOrUpdate(UserPreferenceDTO dto) {
        try {
            UserPreferencePo po = convertToPo(dto);
            if (userPreferencePoMapper.existsByUserId(dto.getUserId()) > 0) {
                return userPreferencePoMapper.updateByUserId(po) > 0;
            } else {
                return userPreferencePoMapper.insert(po) > 0;
            }
        } catch (Exception e) {
            log.error("保存用户偏好失败: userId={}", dto.getUserId(), e);
            return false;
        }
    }

    @Override
    public void incrementLogCount(Integer userId) {
        try {
            if (userPreferencePoMapper.existsByUserId(userId) > 0) {
                userPreferencePoMapper.incrementLogCount(userId);
            } else {
                UserPreferencePo po = new UserPreferencePo();
                po.setUserId(userId);
                po.setCurrentLogCount(1);
                po.setLastAnalyzedLogCount(0);
                po.setAnalysisCount(0);
                userPreferencePoMapper.insert(po);
            }
        } catch (Exception e) {
            log.error("增加用户日志计数失败: userId={}", userId, e);
        }
    }

    @Override
    public List<UserPreferenceDTO> getNeedAnalysisUsers(Integer threshold) {
        List<UserPreferencePo> poList = userPreferencePoMapper.selectNeedAnalysis(threshold);
        return poList.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Override
    public boolean needAnalysis(Integer userId, Integer threshold) {
        UserPreferencePo po = userPreferencePoMapper.selectByUserId(userId);
        if (po == null) {
            return false;
        }
        int diff = po.getCurrentLogCount() - po.getLastAnalyzedLogCount();
        return diff >= threshold;
    }

    private UserPreferenceDTO convertToDTO(UserPreferencePo po) {
        UserPreferenceDTO dto = new UserPreferenceDTO();
        dto.setId(po.getId());
        dto.setUserId(po.getUserId());
        dto.setPreferenceText(po.getPreferenceText());
        dto.setLastAnalyzedLogCount(po.getLastAnalyzedLogCount());
        dto.setCurrentLogCount(po.getCurrentLogCount());
        dto.setAnalysisCount(po.getAnalysisCount());
        dto.setCreateTime(po.getCreateTime());
        dto.setUpdateTime(po.getUpdateTime());

        try {
            if (po.getPreferenceKeywords() != null) {
                dto.setPreferenceKeywords(objectMapper.readValue(po.getPreferenceKeywords(),
                        new TypeReference<List<String>>() {}));
            }
            if (po.getPreferenceTopics() != null) {
                dto.setPreferenceTopics(objectMapper.readValue(po.getPreferenceTopics(),
                        new TypeReference<List<String>>() {}));
            }
        } catch (Exception e) {
            log.warn("解析用户偏好JSON失败: userId={}", po.getUserId(), e);
            dto.setPreferenceKeywords(new ArrayList<>());
            dto.setPreferenceTopics(new ArrayList<>());
        }
        return dto;
    }

    private UserPreferencePo convertToPo(UserPreferenceDTO dto) {
        UserPreferencePo po = new UserPreferencePo();
        po.setId(dto.getId());
        po.setUserId(dto.getUserId());
        po.setPreferenceText(dto.getPreferenceText());
        po.setLastAnalyzedLogCount(dto.getLastAnalyzedLogCount());
        po.setCurrentLogCount(dto.getCurrentLogCount());
        po.setAnalysisCount(dto.getAnalysisCount());

        try {
            if (dto.getPreferenceKeywords() != null) {
                po.setPreferenceKeywords(objectMapper.writeValueAsString(dto.getPreferenceKeywords()));
            }
            if (dto.getPreferenceTopics() != null) {
                po.setPreferenceTopics(objectMapper.writeValueAsString(dto.getPreferenceTopics()));
            }
        } catch (Exception e) {
            log.warn("序列化用户偏好JSON失败: userId={}", dto.getUserId(), e);
        }
        return po;
    }
}
