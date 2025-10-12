package com.liang.bbs.user.facade.server;
import java.util.List;

import com.github.pagehelper.PageInfo;
import com.liang.bbs.user.facade.dto.EntityDTO;
import com.liang.bbs.user.facade.dto.user.UserDTO;

/**
 * 实体服务接口
 * 用于统一处理不同类型实体的获取操作
 * 
 */
public interface EntityService {
    
    /**
     * 方法1：批量获取实体列表（只返回列表，无分页信息）
     * 根据输入的实体列表，按类型分别调用对应的getById方法获取DTO数据
     *
     * @param list 包含id和type的实体列表
     * @return 实体DTO列表，每个实体包含对应的DTO数据
     */
    List<EntityDTO> getList(List<EntityDTO> list);

    /**
     * 方法2：分页获取实体列表
     * 根据输入的实体列表，按类型分别调用对应的getById方法获取DTO数据
     *
     * @param list 包含id和type的实体列表
     * @param pageNum 页码
     * @param pageSize 每页数量
     * @return 分页的实体DTO列表，包含分页信息
     */
    PageInfo<EntityDTO> getPage(List<EntityDTO> list, Integer pageNum, Integer pageSize);
    
    /**
     * 方法2：单个实体获取
     * 根据id和type获取单个实体数据
     * 
     * @param id 实体ID
     * @param type 实体类型（0:paper, 1:scholar, 2:rate, 3:user）
     * @return 包含DTO数据的实体对象
     */
    public EntityDTO getById(Integer id, String type);
}
