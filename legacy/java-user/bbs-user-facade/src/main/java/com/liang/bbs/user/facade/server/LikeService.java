package com.liang.bbs.user.facade.server;

import com.github.pagehelper.PageInfo;
import com.liang.bbs.user.facade.dto.LikeDTO;
import com.liang.bbs.user.facade.dto.LikeSearchDTO;
import com.liang.bbs.user.facade.dto.user.UserSsoDTO;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.cache.annotation.Cacheable;

/**
 */
public interface LikeService {

    /**
     * 获取所有点赞的通过审核的文章信息
     *
     * @param startTime
     * @param endTime
     * @return
     */
    List<LikeDTO> getPaasAll(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 通过用户id获取点赞的文章信息
     *
     * @param likeSearchDTO
     * @return
     */
    @Cacheable(value = "products", key = "#id")
    PageInfo<LikeDTO> getArticleByUserId(LikeSearchDTO likeSearchDTO);

    /**
     * 通过文章id获取点赞的用户信息
     *
     * @param likeSearchDTO
     * @return
     */
    PageInfo<LikeDTO> getUserByArticleId(LikeSearchDTO likeSearchDTO);

    /**
     * 通过id获取点赞信息
     *
     * @param id
     * @return
     */
    LikeDTO getById(Integer id);

    /**
     * 通过文章id和用户id获取点赞信息
     *
     * @param articleId
     * @param userId
     * @return
     */
    LikeDTO getByArticleIdUserId(Integer articleId, Integer userid);

    /**
     * 获取文章的点赞数量
     *
     * @param articleIds
     * @return
     */
    Long getLikeCountArticle(List<Integer> articleIds);

    /**
     * 是否点赞
     *
     * @param articleId
     * @param userId
     * @return
     */
    Boolean isLike(Integer articleId, Integer userid);

    /**
     * 更新点赞状态
     *
     * @param articleId
     * @param currentUser
     * @return
     */
    Boolean updateLikeState(Integer articleId, UserSsoDTO currentUser);

    /**
     * 用户获取的点赞数量
     *
     * @param userId
     * @return
     */
    Long getUserLikeCount(Integer userid);

    /**
     * 用户获点赞的数量
     *
     * @param userId
     * @return
     */
    Long getUserTheLikeCount(Integer userid);

}
