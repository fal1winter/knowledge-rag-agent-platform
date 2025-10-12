package com.liang.bbs.user.service.impl;

import com.liang.bbs.user.facade.dto.PpLikeDTO;
import com.liang.bbs.user.facade.server.PpLikeService;
import com.liang.bbs.user.persistence.entity.PpLikePo;
import com.liang.bbs.user.persistence.entity.PpLikePoExample;
import com.liang.bbs.user.persistence.mapper.PpLikePoMapper;
import com.liang.bbs.user.service.mapstruct.PpLikeMS;
import org.springframework.beans.factory.annotation.Autowired;
import org.apache.dubbo.config.annotation.Service;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 点赞服务实现类
 */
@Service
public class PpLikeServiceImpl implements PpLikeService {

    @Autowired
    private PpLikePoMapper ppLikePoMapper;

    @Autowired
    private PpLikeMS ppLikeMS;

    @Override
    public Boolean addLike(PpLikeDTO ppLikeDTO) {
        // 检查是否已经点赞
        if (checkLikeExists(ppLikeDTO.getUserId(), ppLikeDTO.getTargetId(), ppLikeDTO.getType())) {
            return false;
        }

        PpLikePo po = PpLikeMS.INSTANCE.toPo(ppLikeDTO);
        po.setTime(new java.util.Date());
        po.setStatus("1");
        
        int result = ppLikePoMapper.insertSelective(po);
        return result > 0;
    }

    @Override
    public Boolean cancelLike(Integer userId, Integer targetId, String type) {
        PpLikePoExample example = new PpLikePoExample();
        example.createCriteria()
                .andUserIdEqualTo(userId)
                .andTargetIdEqualTo(targetId)
                .andTypeEqualTo(type)
                .andStatusEqualTo("1");

        PpLikePo po = new PpLikePo();
        po.setStatus("0");
        
        int result = ppLikePoMapper.updateByExampleSelective(po, example);
        return result > 0;
    }

    @Override
    public Boolean checkLikeStatus(Integer userId, Integer targetId, String type) {
        return checkLikeExists(userId, targetId, type);
    }

    @Override
    public Long getLikeCount(Integer targetId, String type) {
        PpLikePoExample example = new PpLikePoExample();
        example.createCriteria()
                .andTargetIdEqualTo(targetId)
                .andTypeEqualTo(type)
                .andStatusEqualTo("1");

        return ppLikePoMapper.countByExample(example);
    }

    @Override
    public List<PpLikeDTO> getUserLikes(Integer userId, String type) {
        PpLikePoExample example = new PpLikePoExample();
        example.createCriteria()
                .andUserIdEqualTo(userId)
                .andTypeEqualTo(type)
                .andStatusEqualTo("1");
        example.setOrderByClause("time DESC");

        List<PpLikePo> poList = ppLikePoMapper.selectByExample(example);
        return poList.stream()
                .map(ppLikeMS.INSTANCE::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 检查点赞记录是否存在
     */
    private boolean checkLikeExists(Integer userId, Integer targetId, String type) {
        PpLikePoExample example = new PpLikePoExample();
        example.createCriteria()
                .andUserIdEqualTo(userId)
                .andTargetIdEqualTo(targetId)
                .andTypeEqualTo(type)
                .andStatusEqualTo("1");
        
        return ppLikePoMapper.countByExample(example) > 0;
    }
}