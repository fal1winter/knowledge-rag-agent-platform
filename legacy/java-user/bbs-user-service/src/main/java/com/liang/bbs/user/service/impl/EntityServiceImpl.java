package com.liang.bbs.user.service.impl;

import com.github.pagehelper.PageInfo;
import com.liang.bbs.article.facade.dto.PaperDTO;
import com.liang.bbs.article.facade.dto.RateDTO;
import com.liang.bbs.article.facade.dto.ScolarDTO;
import com.liang.bbs.article.facade.server.PaperService;
import com.liang.bbs.article.facade.server.RateService;
import com.liang.bbs.user.facade.dto.EntityDTO;
import com.liang.bbs.user.facade.dto.user.UserDTO;
import com.liang.bbs.user.facade.server.EntityService;
import com.liang.bbs.article.facade.server.ScolarService;
import com.liang.bbs.user.facade.server.UserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.Reference;
import org.apache.dubbo.config.annotation.Service;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 实体服务实现类
 * 用于统一处理不同类型实体的获取操作
 * 
 */
@Slf4j
@Service
@Component
public class EntityServiceImpl implements EntityService {

    @Reference
    private PaperService paperService;
    
    @Reference
    private UserService userService;
    
    @Reference
    private RateService rateService;
    @Reference
    private ScolarService ScolarService;

    /**
     * 方法1：批量获取实体列表（只返回列表，无分页信息）
     * 根据输入的实体列表，按类型分别调用对应的getById方法获取DTO数据
     * 
     * @param list 包含id和type的实体列表
     * @return 实体DTO列表，每个实体包含对应的DTO数据
     */
    @Override
    public List<EntityDTO> getList(List<EntityDTO> list) {
        if (list == null || list.isEmpty()) {
            return new ArrayList<>();
        }

        // 直接处理所有数据，无需分页
        return list.stream()
            .map(entity -> {
                EntityDTO result = new EntityDTO();
                result.setId(entity.getId());
                result.setType(entity.getType());
                
                try {
                    Object dto = getDtoByType(entity.getId(), entity.getType());
                    result.setDto(dto);
                } catch (Exception e) {
                    log.error("获取实体数据失败，id: {}, type: {}", entity.getId(), entity.getType(), e);
                    result.setDto(null);
                }
                
                return result;
            })
            .collect(Collectors.toList());
    }

    /**
     * 方法2：分页获取实体列表
     * 根据输入的实体列表，按类型分别调用对应的getById方法获取DTO数据
     * 
     * @param list 包含id和type的实体列表
     * @param pageNum 页码
     * @param pageSize 每页数量
     * @return 分页的实体DTO列表，包含分页信息
     */
    @Override
    public PageInfo<EntityDTO> getPage(List<EntityDTO> list, Integer pageNum, Integer pageSize) {
        if (list == null || list.isEmpty()) {
            return new PageInfo<>();
        }

        // 参数校验
        if (pageNum == null || pageNum < 1) {
            pageNum = 1;
        }
        if (pageSize == null || pageSize < 1) {
            pageSize = 10;
        }

        // 计算分页
        int total = list.size();
        int startIndex = (pageNum - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, total);
        
        if (startIndex >= total) {
            PageInfo<EntityDTO> emptyPage = new PageInfo<>();
            emptyPage.setList(new ArrayList<>());
            emptyPage.setTotal(total);
            emptyPage.setPages((int) Math.ceil((double) total / pageSize));
            emptyPage.setPageNum(pageNum);
            emptyPage.setPageSize(pageSize);
            emptyPage.setHasNextPage(false);
            return emptyPage;
        }

        // 获取当前页数据
        List<EntityDTO> pageList = list.subList(startIndex, endIndex);

        // 按类型分组处理
        List<EntityDTO> resultList = pageList.stream()
            .map(entity -> {
                EntityDTO result = new EntityDTO();
                result.setId(entity.getId());
                result.setType(entity.getType());
                
                try {
                    Object dto = getDtoByType(entity.getId(), entity.getType());
                    result.setDto(dto);
                } catch (Exception e) {
                    log.error("获取实体数据失败，id: {}, type: {}", entity.getId(), entity.getType(), e);
                    result.setDto(null);
                }
                
                return result;
            })
            .collect(Collectors.toList());

        // 创建PageInfo对象，保持分页信息
        PageInfo<EntityDTO> pageInfo = new PageInfo<>();
        pageInfo.setList(resultList);
        pageInfo.setTotal(total);
        pageInfo.setPages((int) Math.ceil((double) total / pageSize));
        pageInfo.setPageNum(pageNum);
        pageInfo.setPageSize(pageSize);
        pageInfo.setHasNextPage(endIndex < total);
        
        return pageInfo;
    }

    /**
     * 方法2：单个实体获取
     * 根据id和type获取单个实体数据
     * 
     * @param id 实体ID
     * @param type 实体类型（0:paper, 1:scholar, 2:rate, 3:user）
     * @return 包含DTO数据的实体对象
     */
    @Override
    public EntityDTO getById(Integer id, String type) {
        EntityDTO entityDTO = new EntityDTO();
        entityDTO.setId(id);
        entityDTO.setType(type);
        
        try {
            Object dto = getDtoByType(id, type);
            entityDTO.setDto(dto);
        } catch (Exception e) {
            log.error("获取实体数据失败，id: {}, type: {}", id, type, e);
            entityDTO.setDto(null);
        }
        
        return entityDTO;
    }

    /**
     * 根据类型获取对应的DTO数据
     * 使用本地缓存减少重复的服务调用
     * 
     * @param id 实体ID
     * @param type 实体类型
     * @return DTO对象
     */
    private Object getDtoByType(Integer id, String type) {
        if (id == null || type == null) {
            return null;
        }

        // 构建缓存key
        String cacheKey = type + "_" + id;
        
        try {
            switch (type) {
                case "0": // paper
                    return paperService.getById(id);
                    
                case "1": // scholar
                    return ScolarService.getById(id);
                    
                case "2": // rate
                    return rateService.getById(id);
                    
                case "3": // user
                    return userService.getById(id);
                    
                default:
                    log.warn("未知的实体类型: {}", type);
                    return null;
            }
        } catch (Exception e) {
            log.error("获取实体数据失败，id: {}, type: {}", id, type, e);
            return null;
        }
    }
}