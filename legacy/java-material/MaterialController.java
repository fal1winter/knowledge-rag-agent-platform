package com.liang.bbs.rest.controller;

import com.liang.bbs.common.web.basic.ResponseResult;
import com.bbs.common.response.PageResult;
import com.liang.bbs.material.facade.dto.MaterialDTO;
import com.liang.bbs.material.facade.dto.MaterialQueryRequest;
import com.liang.bbs.material.facade.dto.MaterialUploadRequest;
import com.liang.bbs.material.facade.service.MaterialService;
import com.liang.bbs.rest.config.login.NoNeedLogin;
import com.liang.bbs.user.facade.utils.UserContextUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.Reference;
import org.springframework.web.bind.annotation.*;

/**
 * RAG资料管理Controller
 *
 */
@Slf4j
@RestController
@RequestMapping("/bbs/material")
@Api(tags = "RAG资料管理")
public class MaterialController {

    @Reference
    private MaterialService materialService;

    @PostMapping("/upload")
    @ApiOperation("上传资料")
    public ResponseResult<MaterialDTO> uploadMaterial(@RequestBody MaterialUploadRequest request) {
        Long userId = Long.valueOf(UserContextUtils.currentUser().getUserId());
        log.info("用户 {} 上传资料: {}", userId, request.getTitle());

        request.setSellerId(userId);
        MaterialDTO material = materialService.uploadMaterial(request);

        return ResponseResult.success(material);
    }

    @PutMapping("/{id}")
    @ApiOperation("更新资料信息")
    public ResponseResult<Void> updateMaterial(@PathVariable Long id,
                                               @RequestBody MaterialDTO dto) {
        Long userId = Long.valueOf(UserContextUtils.currentUser().getUserId());
        log.info("用户 {} 更新资料 {}", userId, id);

        MaterialDTO material = materialService.getById(id);
        if (!material.getSellerId().equals(userId)) {
            return ResponseResult.build(com.liang.bbs.common.enums.ResponseCode.ERROR, null);
        }

        materialService.updateMaterial(id, dto);
        return ResponseResult.success(null);
    }

    @PutMapping("/{id}/status")
    @ApiOperation("更新资料状态")
    public ResponseResult<Void> updateStatus(@PathVariable Long id,
                                             @RequestParam Integer status) {
        Long userId = Long.valueOf(UserContextUtils.currentUser().getUserId());
        log.info("用户 {} 更新资料 {} 状态为 {}", userId, id, status);

        MaterialDTO material = materialService.getById(id);
        if (!material.getSellerId().equals(userId)) {
            return ResponseResult.build(com.liang.bbs.common.enums.ResponseCode.ERROR, null);
        }

        materialService.updateStatus(id, status);
        return ResponseResult.success(null);
    }

    @GetMapping("/{id}")
    @NoNeedLogin
    @ApiOperation("获取资料详情")
    public ResponseResult<MaterialDTO> getMaterialDetail(@PathVariable Long id) {
        log.info("查询资料详情: {}", id);

        MaterialDTO material = materialService.getById(id);

        if (UserContextUtils.currentUser() != null) {
            Long userId = Long.valueOf(UserContextUtils.currentUser().getUserId());
            materialService.incrementViewCount(id, userId);
        }

        return ResponseResult.success(material);
    }

    @GetMapping("/list")
    @NoNeedLogin
    @ApiOperation("分页查询资料列表")
    public ResponseResult<PageResult<MaterialDTO>> listMaterials(MaterialQueryRequest request) {
        log.info("查询资料列表: {}", request);

        PageResult<MaterialDTO> result = materialService.listMaterials(request);
        return ResponseResult.success(result);
    }

    @GetMapping("/my-materials")
    @ApiOperation("我的资料")
    public ResponseResult<PageResult<MaterialDTO>> myMaterials(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Long userId = Long.valueOf(UserContextUtils.currentUser().getUserId());
        log.info("查询用户 {} 的资料列表", userId);

        PageResult<MaterialDTO> result = materialService.getMyMaterials(userId, pageNum, pageSize);
        return ResponseResult.success(result);
    }

    @GetMapping("/purchased")
    @ApiOperation("我购买的资料")
    public ResponseResult<PageResult<MaterialDTO>> purchasedMaterials(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Long userId = Long.valueOf(UserContextUtils.currentUser().getUserId());
        log.info("查询用户 {} 购买的资料", userId);

        PageResult<MaterialDTO> result = materialService.getPurchasedMaterials(userId, pageNum, pageSize);
        return ResponseResult.success(result);
    }

    @DeleteMapping("/{id}")
    @ApiOperation("删除资料")
    public ResponseResult<Void> deleteMaterial(@PathVariable Long id) {
        Long userId = Long.valueOf(UserContextUtils.currentUser().getUserId());
        log.info("用户 {} 删除资料 {}", userId, id);

        MaterialDTO material = materialService.getById(id);
        if (!material.getSellerId().equals(userId)) {
            return ResponseResult.build(com.liang.bbs.common.enums.ResponseCode.ERROR, null);
        }

        materialService.deleteMaterial(id);
        return ResponseResult.success(null);
    }

    @GetMapping("/search")
    @NoNeedLogin
    @ApiOperation("搜索资料")
    public ResponseResult<PageResult<MaterialDTO>> searchMaterials(
            @RequestParam String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        log.info("搜索资料: keyword={}, categoryId={}", keyword, categoryId);

        PageResult<MaterialDTO> result = materialService.searchMaterials(
            keyword, categoryId, pageNum, pageSize);
        return ResponseResult.success(result);
    }

    @GetMapping("/categories")
    @NoNeedLogin
    @ApiOperation("获取分类列表")
    public ResponseResult<?> getCategories() {
        return ResponseResult.success(materialService.getCategories());
    }

    @GetMapping("/hot")
    @NoNeedLogin
    @ApiOperation("获取热门资料")
    public ResponseResult<?> getHotMaterials(
            @RequestParam(defaultValue = "10") Integer limit) {
        return ResponseResult.success(materialService.getHotMaterials(limit));
    }

    @GetMapping("/recommend")
    @ApiOperation("获取推荐资料")
    public ResponseResult<?> getRecommendMaterials(
            @RequestParam(defaultValue = "10") Integer limit) {
        Long userId = Long.valueOf(UserContextUtils.currentUser().getUserId());
        return ResponseResult.success(materialService.getRecommendMaterials(userId, limit));
    }

    @GetMapping("/{id}/check-access")
    @ApiOperation("检查用户是否有访问权限")
    public ResponseResult<Boolean> checkAccess(@PathVariable Long id) {
        Long userId = Long.valueOf(UserContextUtils.currentUser().getUserId());
        log.info("检查用户 {} 对资料 {} 的访问权限", userId, id);

        boolean hasAccess = materialService.checkAccess(id, userId);
        return ResponseResult.success(hasAccess);
    }
}
