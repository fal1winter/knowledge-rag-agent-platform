package com.liang.bbs.material.service.impl;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import com.bbs.common.response.PageResult;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.liang.bbs.material.facade.dto.*;
import com.liang.bbs.material.facade.service.MaterialService;
import com.liang.bbs.material.persistence.entity.Material;
import com.liang.bbs.material.persistence.entity.MaterialAccess;
import com.liang.bbs.material.persistence.entity.MaterialCategory;
import com.liang.bbs.material.persistence.mapper.MaterialAccessMapper;
import com.liang.bbs.material.persistence.mapper.MaterialCategoryMapper;
import com.liang.bbs.material.persistence.mapper.MaterialMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.Service;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 资料服务实现
 *
 */
@Slf4j
@Service
public class MaterialServiceImpl implements MaterialService {

    @Autowired
    private MaterialMapper materialMapper;

    @Autowired
    private MaterialCategoryMapper categoryMapper;

    @Autowired
    private MaterialAccessMapper accessMapper;

    @Override
    @CacheEvict(cacheNames = {"material:hot", "material:recommend"}, allEntries = true)
    public MaterialDTO uploadMaterial(MaterialUploadRequest request) {
        Material material = new Material();
        BeanUtils.copyProperties(request, material);
        material.setStatus(1);
        material.setViewCount(0);
        material.setSalesCount(0);

        materialMapper.insert(material);
        log.info("[JetCache] 新资料上传，已清除热门榜单缓存");
        return convertToDTO(material);
    }

    @Override
    public void updateMaterial(Long id, MaterialDTO dto) {
        Material material = new Material();
        BeanUtils.copyProperties(dto, material);
        material.setId(id);
        materialMapper.updateByPrimaryKeySelective(material);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        Material material = new Material();
        material.setId(id);
        material.setStatus(status);
        materialMapper.updateByPrimaryKeySelective(material);
    }

    @Override
    public MaterialDTO getById(Long id) {
        Material material = materialMapper.selectByPrimaryKey(id);
        return convertToDTO(material);
    }

    @Override
    public PageResult<MaterialDTO> listMaterials(MaterialQueryRequest request) {
        PageHelper.startPage(request.getPageNum(), request.getPageSize());

        List<Material> materials = materialMapper.selectByCondition(
            request.getCategoryId(),
            request.getKeyword(),
            1,
            request.getOrderBy()
        );

        PageInfo<Material> pageInfo = new PageInfo<>(materials);

        List<MaterialDTO> dtoList = materials.stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());

        PageResult<MaterialDTO> result = new PageResult<>();
        result.setList(dtoList);
        result.setTotal(pageInfo.getTotal());
        result.setPageNum(pageInfo.getPageNum());
        result.setPageSize(pageInfo.getPageSize());

        return result;
    }

    @Override
    public PageResult<MaterialDTO> getMyMaterials(Long userId, Integer pageNum, Integer pageSize) {
        PageHelper.startPage(pageNum, pageSize);

        List<Material> materials = materialMapper.selectBySellerId(userId);
        PageInfo<Material> pageInfo = new PageInfo<>(materials);

        List<MaterialDTO> dtoList = materials.stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());

        PageResult<MaterialDTO> result = new PageResult<>();
        result.setList(dtoList);
        result.setTotal(pageInfo.getTotal());
        result.setPageNum(pageInfo.getPageNum());
        result.setPageSize(pageInfo.getPageSize());

        return result;
    }

    @Override
    public PageResult<MaterialDTO> getPurchasedMaterials(Long userId, Integer pageNum, Integer pageSize) {
        List<Long> materialIds = accessMapper.selectMaterialIdsByUserId(userId);

        if (materialIds.isEmpty()) {
            return new PageResult<>();
        }

        PageHelper.startPage(pageNum, pageSize);

        List<Material> materials = new ArrayList<>();
        for (Long materialId : materialIds) {
            Material material = materialMapper.selectByPrimaryKey(materialId);
            if (material != null) {
                materials.add(material);
            }
        }

        PageInfo<Material> pageInfo = new PageInfo<>(materials);

        List<MaterialDTO> dtoList = materials.stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());

        PageResult<MaterialDTO> result = new PageResult<>();
        result.setList(dtoList);
        result.setTotal(pageInfo.getTotal());
        result.setPageNum(pageInfo.getPageNum());
        result.setPageSize(pageInfo.getPageSize());

        return result;
    }

    @Override
    @CacheEvict(cacheNames = {"material:hot", "material:recommend"}, allEntries = true)
    public void deleteMaterial(Long id) {
        materialMapper.deleteByPrimaryKey(id);
        log.info("[JetCache] 资料删除，已清除热门榜单缓存, id={}", id);
    }

    @Override
    public PageResult<MaterialDTO> searchMaterials(String keyword, Long categoryId, Integer pageNum, Integer pageSize) {
        PageHelper.startPage(pageNum, pageSize);

        List<Material> materials = materialMapper.selectByCondition(categoryId, keyword, 1, "create_time");
        PageInfo<Material> pageInfo = new PageInfo<>(materials);

        List<MaterialDTO> dtoList = materials.stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());

        PageResult<MaterialDTO> result = new PageResult<>();
        result.setList(dtoList);
        result.setTotal(pageInfo.getTotal());
        result.setPageNum(pageInfo.getPageNum());
        result.setPageSize(pageInfo.getPageSize());

        return result;
    }

    @Override
    @Cacheable(cacheNames = "material:categories", key = "'all'")
    public List<MaterialCategoryDTO> getCategories() {
        List<MaterialCategory> categories = categoryMapper.selectAll();
        return categories.stream()
            .map(this::convertToCategoryDTO)
            .collect(Collectors.toList());
    }

    /**
     * 获取热门榜单资料，使用 Spring Cache + Redis 缓存（5分钟TTL）
     * 榜单数据变化不频繁，缓存可显著减少 DB 查询压力
     */
    @Override
    @Cacheable(cacheNames = "material:hot", key = "#limit")
    public List<MaterialDTO> getHotMaterials(Integer limit) {
        log.info("[Cache miss] 从数据库加载热门榜单, limit={}", limit);
        List<Material> materials = materialMapper.selectHotMaterials(limit);
        return materials.stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }

    @Override
    @Cacheable(cacheNames = "material:recommend", key = "#limit")
    public List<MaterialDTO> getRecommendMaterials(Long userId, Integer limit) {
        return getHotMaterials(limit);
    }

    @Override
    public void incrementViewCount(Long materialId, Long userId) {
        materialMapper.incrementViewCount(materialId);
    }

    @Override
    public boolean checkAccess(Long materialId, Long userId) {
        if (userId == null) {
            return false;
        }

        // 检查是否是资料的上传者
        Material material = materialMapper.selectByPrimaryKey(materialId);
        if (material != null && material.getSellerId().equals(userId)) {
            return true;
        }

        // 检查是否有访问权限记录
        int count = accessMapper.countByUserIdAndMaterialId(userId, materialId);
        return count > 0;
    }

    private MaterialDTO convertToDTO(Material material) {
        if (material == null) {
            return null;
        }

        MaterialDTO dto = new MaterialDTO();
        BeanUtils.copyProperties(material, dto);

        MaterialCategory category = categoryMapper.selectByPrimaryKey(material.getCategoryId());
        if (category != null) {
            dto.setCategoryName(category.getName());
        }

        return dto;
    }

    private MaterialCategoryDTO convertToCategoryDTO(MaterialCategory category) {
        MaterialCategoryDTO dto = new MaterialCategoryDTO();
        BeanUtils.copyProperties(category, dto);
        return dto;
    }
}
