package com.liang.bbs.material.facade.service;

import com.bbs.common.response.PageResult;
import com.liang.bbs.material.facade.dto.*;

import java.util.List;

/**
 * 资料服务接口
 *
 */
public interface MaterialService {

    /**
     * 上传资料
     */
    MaterialDTO uploadMaterial(MaterialUploadRequest request);

    /**
     * 更新资料信息
     */
    void updateMaterial(Long id, MaterialDTO dto);

    /**
     * 更新资料状态
     */
    void updateStatus(Long id, Integer status);

    /**
     * 获取资料详情
     */
    MaterialDTO getById(Long id);

    /**
     * 分页查询资料列表
     */
    PageResult<MaterialDTO> listMaterials(MaterialQueryRequest request);

    /**
     * 我的资料（卖家）
     */
    PageResult<MaterialDTO> getMyMaterials(Long userId, Integer pageNum, Integer pageSize);

    /**
     * 我购买的资料
     */
    PageResult<MaterialDTO> getPurchasedMaterials(Long userId, Integer pageNum, Integer pageSize);

    /**
     * 删除资料
     */
    void deleteMaterial(Long id);

    /**
     * 搜索资料
     */
    PageResult<MaterialDTO> searchMaterials(String keyword, Long categoryId, Integer pageNum, Integer pageSize);

    /**
     * 获取分类列表
     */
    List<MaterialCategoryDTO> getCategories();

    /**
     * 获取热门资料
     */
    List<MaterialDTO> getHotMaterials(Integer limit);

    /**
     * 获取推荐资料
     */
    List<MaterialDTO> getRecommendMaterials(Long userId, Integer limit);

    /**
     * 增加浏览次数
     */
    void incrementViewCount(Long materialId, Long userId);

    /**
     * 检查用户是否有访问权限
     */
    boolean checkAccess(Long materialId, Long userId);
}
