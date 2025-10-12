package com.liang.bbs.user.service.impl;

import com.liang.bbs.user.facade.dto.user.UserDTO;
import com.liang.bbs.user.facade.server.ActivityInteractionService;
import com.liang.bbs.user.facade.server.UserService;
import com.liang.bbs.user.facade.server.UserHomepageService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 动态互动服务实现（点赞、评论）
 */
@Component
@Slf4j
@Service
public class ActivityInteractionServiceImpl implements ActivityInteractionService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserHomepageService userHomepageService;

    @Autowired
    private UserService userService;

    @Override
    public Map<String, Object> likeActivity(Long activityId, Integer userId) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (userId == null) {
                result.put("success", false);
                result.put("message", "请先登录");
                return result;
            }
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activity_like WHERE activity_id = ? AND user_id = ?",
                Integer.class, activityId, userId);
            boolean liked = count != null && count > 0;
            if (liked) {
                jdbcTemplate.update("DELETE FROM activity_like WHERE activity_id = ? AND user_id = ?",
                    activityId, userId);
                userHomepageService.incrementLikeCount(activityId, -1);
                result.put("liked", false);
                result.put("message", "取消点赞");
            } else {
                jdbcTemplate.update("INSERT INTO activity_like (activity_id, user_id, created_at) VALUES (?, ?, NOW())",
                    activityId, userId);
                userHomepageService.incrementLikeCount(activityId, 1);
                result.put("liked", true);
                result.put("message", "点赞成功");
            }
            Integer likeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activity_like WHERE activity_id = ?",
                Integer.class, activityId);
            result.put("likeCount", likeCount != null ? likeCount : 0);
            result.put("success", true);
        } catch (Exception e) {
            log.error("点赞动态失败: activityId={}", activityId, e);
            result.put("success", false);
            result.put("message", "操作失败");
        }
        return result;
    }

    @Override
    public Map<String, Object> getLikeStatus(Long activityId, Integer userId) {
        Map<String, Object> result = new HashMap<>();
        try {
            Integer likeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activity_like WHERE activity_id = ?",
                Integer.class, activityId);
            result.put("likeCount", likeCount != null ? likeCount : 0);
            if (userId != null) {
                Integer userLiked = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM activity_like WHERE activity_id = ? AND user_id = ?",
                    Integer.class, activityId, userId);
                result.put("liked", userLiked != null && userLiked > 0);
            } else {
                result.put("liked", false);
            }
            result.put("success", true);
        } catch (Exception e) {
            result.put("likeCount", 0);
            result.put("liked", false);
            result.put("success", false);
        }
        return result;
    }

    @Override
    public Map<Long, Map<String, Object>> getBatchLikeStatus(List<Long> activityIds, Integer userId) {
        Map<Long, Map<String, Object>> result = new HashMap<>();
        if (activityIds == null || activityIds.isEmpty()) {
            return result;
        }

        try {
            // 批量获取点赞数
            String inClause = activityIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
            List<Map<String, Object>> likeCounts = jdbcTemplate.queryForList(
                "SELECT activity_id, COUNT(*) as count FROM activity_like WHERE activity_id IN (" + inClause + ") GROUP BY activity_id");

            Map<Long, Integer> likeCountMap = new HashMap<>();
            for (Map<String, Object> row : likeCounts) {
                Long activityId = ((Number) row.get("activity_id")).longValue();
                Integer count = ((Number) row.get("count")).intValue();
                likeCountMap.put(activityId, count);
            }

            // 批量获取用户点赞状态
            Map<Long, Boolean> userLikedMap = new HashMap<>();
            if (userId != null) {
                List<Map<String, Object>> userLikes = jdbcTemplate.queryForList(
                    "SELECT activity_id FROM activity_like WHERE activity_id IN (" + inClause + ") AND user_id = ?", userId);
                for (Map<String, Object> row : userLikes) {
                    Long activityId = ((Number) row.get("activity_id")).longValue();
                    userLikedMap.put(activityId, true);
                }
            }

            // 组装结果
            for (Long activityId : activityIds) {
                Map<String, Object> status = new HashMap<>();
                status.put("likeCount", likeCountMap.getOrDefault(activityId, 0));
                status.put("liked", userLikedMap.getOrDefault(activityId, false));
                status.put("success", true);
                result.put(activityId, status);
            }
        } catch (Exception e) {
            log.error("批量获取点赞状态失败", e);
            // 返回默认值
            for (Long activityId : activityIds) {
                Map<String, Object> status = new HashMap<>();
                status.put("likeCount", 0);
                status.put("liked", false);
                status.put("success", false);
                result.put(activityId, status);
            }
        }
        return result;
    }

    @Override
    public Map<String, Object> addComment(Long activityId, Integer userId, String content) {
        return addComment(activityId, userId, content, null, null);
    }

    /**
     * 添加评论（支持嵌套）
     * @param activityId 动态ID
     * @param userId 用户ID
     * @param content 评论内容
     * @param parentId 父评论ID，null表示顶级评论
     * @param replyToUserId 回复的目标用户ID
     */
    public Map<String, Object> addComment(Long activityId, Integer userId, String content, Long parentId, Integer replyToUserId) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (content == null || content.trim().isEmpty()) {
                result.put("success", false);
                result.put("message", "评论内容不能为空");
                return result;
            }
            if (content.length() > 500) {
                result.put("success", false);
                result.put("message", "评论内容不能超过500字");
                return result;
            }

            // 构建 reply_path
            String replyPath;
            if (parentId == null) {
                // 顶级评论，先插入再更新 reply_path
                jdbcTemplate.update(
                    "INSERT INTO activity_comment (activity_id, user_id, content, parent_id, reply_to_user_id, status, created_at, updated_at) VALUES (?, ?, ?, NULL, NULL, 1, NOW(), NOW())",
                    activityId, userId, content.trim());
                Long commentId = jdbcTemplate.queryForObject(
                    "SELECT id FROM activity_comment WHERE activity_id = ? AND user_id = ? ORDER BY created_at DESC LIMIT 1",
                    Long.class, activityId, userId);
                replyPath = String.valueOf(commentId);
                jdbcTemplate.update("UPDATE activity_comment SET reply_path = ? WHERE id = ?", replyPath, commentId);
                result.put("commentId", commentId);
            } else {
                // 子评论，获取父评论的 reply_path
                String parentPath = jdbcTemplate.queryForObject(
                    "SELECT reply_path FROM activity_comment WHERE id = ?",
                    String.class, parentId);
                if (parentPath == null || parentPath.isEmpty()) {
                    parentPath = String.valueOf(parentId);
                }

                jdbcTemplate.update(
                    "INSERT INTO activity_comment (activity_id, user_id, content, parent_id, reply_to_user_id, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, 1, NOW(), NOW())",
                    activityId, userId, content.trim(), parentId, replyToUserId);
                Long commentId = jdbcTemplate.queryForObject(
                    "SELECT id FROM activity_comment WHERE activity_id = ? AND user_id = ? ORDER BY created_at DESC LIMIT 1",
                    Long.class, activityId, userId);
                replyPath = parentPath + "/" + commentId;
                jdbcTemplate.update("UPDATE activity_comment SET reply_path = ? WHERE id = ?", replyPath, commentId);
                result.put("commentId", commentId);
            }

            userHomepageService.incrementCommentCount(activityId, 1);
            result.put("success", true);
            result.put("message", "评论成功");
            Integer commentCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activity_comment WHERE activity_id = ? AND status = 1",
                Integer.class, activityId);
            result.put("commentCount", commentCount != null ? commentCount : 0);
        } catch (Exception e) {
            log.error("添加评论失败: activityId={}, parentId={}", activityId, parentId, e);
            result.put("success", false);
            result.put("message", "评论失败");
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> getComments(Long activityId, Integer page, Integer pageSize) {
        try {
            // 获取所有评论（扁平化存储）
            List<Map<String, Object>> allComments = jdbcTemplate.queryForList(
                "SELECT id, activity_id, user_id, content, parent_id, reply_to_user_id, reply_path, created_at " +
                "FROM activity_comment WHERE activity_id = ? AND status = 1 ORDER BY reply_path ASC",
                activityId);

            // 收集所有用户ID
            Set<Integer> userIds = new HashSet<>();
            for (Map<String, Object> c : allComments) {
                Object uid = c.get("user_id");
                if (uid != null) userIds.add(((Number) uid).intValue());
                Object replyToUid = c.get("reply_to_user_id");
                if (replyToUid != null) userIds.add(((Number) replyToUid).intValue());
            }

            // 批量获取用户信息
            Map<Integer, Map<String, String>> userInfoMap = new HashMap<>();
            for (Integer uid : userIds) {
                try {
                    UserDTO u = userService.getByIdWithoutSensitive(uid);
                    if (u != null) {
                        Map<String, String> info = new HashMap<>();
                        info.put("name", u.getName() != null ? u.getName() : "用户" + uid);
                        info.put("avatar", u.getPicture() != null ? u.getPicture() : "");
                        userInfoMap.put(uid, info);
                    }
                } catch (Exception ignored) {}
            }

            // 构建评论树
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> c : allComments) {
                Map<String, Object> item = new HashMap<>(c);
                int uid = ((Number) c.get("user_id")).intValue();
                Map<String, String> info = userInfoMap.getOrDefault(uid, new HashMap<>());
                item.put("userName", info.getOrDefault("name", "用户" + uid));
                item.put("userAvatar", info.getOrDefault("avatar", ""));

                // 添加回复目标用户信息
                Object replyToUid = c.get("reply_to_user_id");
                if (replyToUid != null) {
                    int replyUid = ((Number) replyToUid).intValue();
                    Map<String, String> replyInfo = userInfoMap.getOrDefault(replyUid, new HashMap<>());
                    item.put("replyToUserName", replyInfo.getOrDefault("name", "用户" + replyUid));
                }

                // 计算嵌套层级（通过 reply_path 中的 / 数量）
                String replyPath = (String) c.get("reply_path");
                int level = replyPath != null ? replyPath.split("/").length - 1 : 0;
                item.put("level", level);

                result.add(item);
            }

            // 分页处理
            int offset = (page - 1) * pageSize;
            int end = Math.min(offset + pageSize, result.size());
            if (offset >= result.size()) {
                return Collections.emptyList();
            }
            return result.subList(offset, end);
        } catch (Exception e) {
            log.error("获取评论列表失败: activityId={}", activityId, e);
            return Collections.emptyList();
        }
    }

    @Override
    public Boolean deleteComment(Long commentId, Integer userId) {
        try {
            int rows = jdbcTemplate.update(
                "UPDATE activity_comment SET status = 0 WHERE id = ? AND user_id = ?",
                commentId, userId);
            if (rows > 0) {
                try {
                    Long activityId = jdbcTemplate.queryForObject(
                        "SELECT activity_id FROM activity_comment WHERE id = ?",
                        Long.class, commentId);
                    if (activityId != null) {
                        userHomepageService.incrementCommentCount(activityId, -1);
                    }
                } catch (Exception ignored) {}
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("删除评论失败: commentId={}", commentId, e);
            return false;
        }
    }
}
