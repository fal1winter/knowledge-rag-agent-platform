package com.liang.bbs.user.service.impl;

import com.liang.bbs.common.enums.ResponseCode;
import com.liang.bbs.user.facade.dto.UserAccessLogMongoDTO;
import com.liang.bbs.user.facade.server.UserAccessLogMongoService;
import com.liang.bbs.user.service.utils.RedisService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.apache.dubbo.config.annotation.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.Calendar;

/**
 * MongoDB用户访问日志服务实现类
 */
@Slf4j
@Service
public class UserAccessLogMongoServiceImpl implements UserAccessLogMongoService {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private RedisService redisService;

    private static final String COLLECTION_NAME = "user_access_logs";
    
    // Redis ZSet 热门榜单 key 前缀
    private static final String HOT_RANKING_KEY_PREFIX = "hot:ranking:";

    @Override
    public ResponseCode recordAccess(UserAccessLogMongoDTO dto) {
        try {
            if (dto.getTimestamp() == null) {
                dto.setTimestamp(new Date());
            }
            dto.setCreatedAt(new Date());
            dto.setUpdatedAt(new Date());
            
            mongoTemplate.save(dto, COLLECTION_NAME);
            
            // 更新 Redis ZSet 热门榜单
            updateHotRanking(dto.getTargetType(), dto.getTargetId(), dto.getActionType());
            
            log.info("记录用户访问日志成功：userId={}, targetType={}, targetId={}, actionType={}", 
                     dto.getUserId(), dto.getTargetType(), dto.getTargetId(), dto.getActionType());
            return ResponseCode.SUCCESS;
        } catch (Exception e) {
            log.error("记录用户访问日志失败：userId={}, error={}", dto.getUserId(), e.getMessage(), e);
            return ResponseCode.SYSTEM_EXCEPTION;
        }
    }

    /**
     * 更新 Redis ZSet 热门榜单
     */
    private void updateHotRanking(String targetType, Long targetId, String actionType) {
        if (targetType == null || targetId == null) {
            return;
        }
        try {
            // 构建 key: hot:ranking:{targetType}:{actionType} 或 hot:ranking:{targetType}:all
            String key = buildHotRankingKey(targetType, actionType);
            String allKey = buildHotRankingKey(targetType, null);
            
            // 增加分数
            redisService.zincrby(key, targetId.toString(), 1);
            if (actionType != null) {
                redisService.zincrby(allKey, targetId.toString(), 1);
            }
            
            log.debug("更新热门榜单成功: key={}, targetId={}", key, targetId);
        } catch (Exception e) {
            log.warn("更新热门榜单失败: targetType={}, targetId={}, error={}", targetType, targetId, e.getMessage());
        }
    }

    /**
     * 构建热门榜单 Redis key
     */
    private String buildHotRankingKey(String targetType, String actionType) {
        if (actionType != null && !actionType.isEmpty()) {
            return HOT_RANKING_KEY_PREFIX + targetType + ":" + actionType;
        }
        return HOT_RANKING_KEY_PREFIX + targetType + ":all";
    }

    @Override
    public ResponseCode batchRecordAccess(List<UserAccessLogMongoDTO> dtoList) {
        try {
            Date now = new Date();
            dtoList.forEach(dto -> {
                if (dto.getTimestamp() == null) {
                    dto.setTimestamp(now);
                }
                dto.setCreatedAt(now);
                dto.setUpdatedAt(now);
            });
            
            mongoTemplate.insert(dtoList, COLLECTION_NAME);
            log.info("批量记录用户访问日志成功：count={}", dtoList.size());
            return ResponseCode.SUCCESS;
        } catch (Exception e) {
            log.error("批量记录用户访问日志失败：count={}, error={}", dtoList.size(), e.getMessage(), e);
            return ResponseCode.SYSTEM_EXCEPTION;
        }
    }

    @Override
    public List<UserAccessLogMongoDTO> getUserAccessLogs(Long userId, String targetType, String actionType,
                                                          Date startTime, Date endTime, Integer limit) {
        try {
            Query query = new Query();
            query.addCriteria(Criteria.where("userId").is(userId));
            
            addCommonCriteria(query, targetType, actionType, startTime, endTime);
            
            query.with(Sort.by(Sort.Direction.DESC, "timestamp"));
            if (limit != null && limit > 0) {
                query.limit(limit);
            }
            
            return mongoTemplate.find(query, UserAccessLogMongoDTO.class, COLLECTION_NAME);
        } catch (Exception e) {
            log.error("获取用户访问日志失败：userId={}, error={}", userId, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<UserAccessLogMongoDTO> getTargetAccessLogs(String targetType, Long targetId, String actionType,
                                                           Date startTime, Date endTime, Integer limit) {
        try {
            Query query = new Query();
            query.addCriteria(Criteria.where("targetType").is(targetType));
            query.addCriteria(Criteria.where("targetId").is(targetId));
            
            addCommonCriteria(query, null, actionType, startTime, endTime);
            
            query.with(Sort.by(Sort.Direction.DESC, "timestamp"));
            if (limit != null && limit > 0) {
                query.limit(limit);
            }
            
            return mongoTemplate.find(query, UserAccessLogMongoDTO.class, COLLECTION_NAME);
        } catch (Exception e) {
            log.error("获取目标访问日志失败：targetType={}, targetId={}, error={}", 
                     targetType, targetId, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public long getAccessCount(String targetType, Long targetId, String actionType,
                               Date startTime, Date endTime) {
        try {
            Criteria criteria = new Criteria();
            
            if (targetType != null) {
                criteria.and("targetType").is(targetType);
            }
            if (targetId != null) {
                criteria.and("targetId").is(targetId);
            }
            if (actionType != null) {
                criteria.and("actionType").is(actionType);
            }
            if (startTime != null || endTime != null) {
                Criteria timestampCriteria = Criteria.where("timestamp");
                if (startTime != null) {
                    timestampCriteria.gte(startTime);
                }
                if (endTime != null) {
                    // 将结束时间增加1秒，确保包含边界值
                    Calendar calendar = Calendar.getInstance();
                    calendar.setTime(endTime);
                    calendar.add(Calendar.SECOND, 1);
                    timestampCriteria.lt(calendar.getTime());
                }
                criteria.andOperator(timestampCriteria);
            }
            
            Query query = new Query(criteria);
            return mongoTemplate.count(query, COLLECTION_NAME);
        } catch (Exception e) {
            log.error("获取访问数量失败：error={}", e.getMessage(), e);
            return 0;
        }
    }

    @Override
    public List<Map<String, Object>> getAccessStatistics(String groupBy, String targetType,
                                                         Date startTime, Date endTime) {
        try {
            List<AggregationOperation> operations = new ArrayList<>();
            
            // 匹配条件
            Criteria criteria = new Criteria();
            if (targetType != null) {
                criteria.and("targetType").is(targetType);
            }
            
            Criteria timestampCriteria = Criteria.where("timestamp");
            boolean hasTimeCriteria = false;
            if (startTime != null) {
                timestampCriteria.gte(startTime);
                hasTimeCriteria = true;
            }
            if (endTime != null) {
                // 将结束时间增加1秒，确保包含边界值
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(endTime);
                calendar.add(Calendar.SECOND, 1);
                timestampCriteria.lt(calendar.getTime());
                hasTimeCriteria = true;
            }
            
            if (hasTimeCriteria) {
                criteria.andOperator(timestampCriteria);
            }
            
            operations.add(Aggregation.match(criteria));
            
            // 分组
            String groupField;
            switch (groupBy) {
                case "targetType":
                    groupField = "$targetType";
                    break;
                case "actionType":
                    groupField = "$actionType";
                    break;
                case "date":
                    groupField = "$date";
                    operations.add(Aggregation.project()
                            .andExpression("{ $dateToString: { format: '%Y-%m-%d', date: '$timestamp' } }").as("date"));
                    break;
                default:
                    groupField = "$targetType";
            }
            
            operations.add(Aggregation.group(groupField).count().as("count"));
            operations.add(Aggregation.sort(Sort.Direction.DESC, "count"));
            
            Aggregation aggregation = Aggregation.newAggregation(operations);
            AggregationResults<Map> results = mongoTemplate.aggregate(aggregation, COLLECTION_NAME, Map.class);
            
            return results.getMappedResults().stream()
                    .map(map -> {
                        Map<String, Object> result = new HashMap<>();
                        result.put("key", map.get("_id"));
                        result.put("count", map.get("count"));
                        return result;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("获取访问统计失败：error={}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    // 移除 @Cacheable 注解，因为 Redis ZSet 本身就是实时的
    public List<Map<String, Object>> getPopularTargets(String targetType, String actionType,
                                                       Date startTime, Date endTime, Integer limit) {
        // 如果没有时间范围限制，优先从 Redis ZSet 读取
        if (startTime == null && endTime == null) {
            List<Map<String, Object>> redisResult = getPopularTargetsFromRedis(targetType, actionType, limit);
            if (!redisResult.isEmpty()) {
                log.info("从 Redis ZSet 获取热门榜单成功: targetType={}, count={}", targetType, redisResult.size());
                return redisResult;
            }
            log.info("Redis ZSet 无数据，回退到 MongoDB 查询");
        }
        
        // 回退到 MongoDB 聚合查询
        return getPopularTargetsFromMongo(targetType, actionType, startTime, endTime, limit);
    }

    /**
     * 从 Redis ZSet 获取热门榜单
     */
    private List<Map<String, Object>> getPopularTargetsFromRedis(String targetType, String actionType, Integer limit) {
        try {
            String key = buildHotRankingKey(targetType, actionType);
            int queryLimit = (limit != null && limit > 0) ? limit : 10;
            
            List<Map<String, Object>> zsetResult = redisService.zrevrangeWithScores(key, queryLimit);
            
            return zsetResult.stream()
                    .map(item -> {
                        Map<String, Object> result = new HashMap<>();
                        result.put("targetId", Long.parseLong((String) item.get("member")));
                        result.put("count", ((Double) item.get("score")).intValue());
                        return result;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("从 Redis 获取热门榜单失败: targetType={}, error={}", targetType, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 从 MongoDB 获取热门榜单（原有逻辑）
     */
    private List<Map<String, Object>> getPopularTargetsFromMongo(String targetType, String actionType,
                                                                  Date startTime, Date endTime, Integer limit) {
        try {
            List<AggregationOperation> operations = new ArrayList<>();
            
            Criteria criteria = Criteria.where("targetType").is(targetType);
            if (actionType != null) {
                criteria.and("actionType").is(actionType);
            }
            
            Criteria timestampCriteria = Criteria.where("timestamp");
            boolean hasTimeCriteria = false;
            if (startTime != null) {
                timestampCriteria.gte(startTime);
                hasTimeCriteria = true;
            }
            if (endTime != null) {
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(endTime);
                calendar.add(Calendar.SECOND, 1);
                timestampCriteria.lt(calendar.getTime());
                hasTimeCriteria = true;
            }
            
            if (hasTimeCriteria) {
                criteria.andOperator(timestampCriteria);
            }
            
            operations.add(Aggregation.match(criteria));
            
            operations.add(Aggregation.group("$targetId").count().as("count"));
            operations.add(Aggregation.sort(Sort.Direction.DESC, "count"));
            
            if (limit != null && limit > 0) {
                operations.add(Aggregation.limit(limit));
            }
            
            Aggregation aggregation = Aggregation.newAggregation(operations);
            AggregationResults<Map> results = mongoTemplate.aggregate(aggregation, COLLECTION_NAME, Map.class);
            
            return results.getMappedResults().stream()
                    .map(map -> {
                        Map<String, Object> result = new HashMap<>();
                        result.put("targetId", map.get("_id"));
                        result.put("count", map.get("count"));
                        return result;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("获取热门目标失败：error={}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<Map<String, Object>> getActiveUsers(Date startTime, Date endTime, Integer limit) {
        try {
            List<AggregationOperation> operations = new ArrayList<>();
            
            Criteria criteria = new Criteria();
            Criteria timestampCriteria = Criteria.where("timestamp");
            boolean hasTimeCriteria = false;
            if (startTime != null) {
                timestampCriteria.gte(startTime);
                hasTimeCriteria = true;
            }
            if (endTime != null) {
                // 将结束时间增加1秒，确保包含边界值
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(endTime);
                calendar.add(Calendar.SECOND, 1);
                timestampCriteria.lt(calendar.getTime());
                hasTimeCriteria = true;
            }
            
            if (hasTimeCriteria) {
                criteria.andOperator(timestampCriteria);
            }
            
            operations.add(Aggregation.match(criteria));
            
            operations.add(Aggregation.group("$userId").count().as("count"));
            operations.add(Aggregation.sort(Sort.Direction.DESC, "count"));
            
            if (limit != null && limit > 0) {
                operations.add(Aggregation.limit(limit));
            }
            
            Aggregation aggregation = Aggregation.newAggregation(operations);
            AggregationResults<Map> results = mongoTemplate.aggregate(aggregation, COLLECTION_NAME, Map.class);
            
            return results.getMappedResults().stream()
                    .map(map -> {
                        Map<String, Object> result = new HashMap<>();
                        result.put("userId", map.get("_id"));
                        result.put("count", map.get("count"));
                        return result;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("获取活跃用户失败：error={}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public long deleteUserAccessLogs(Long userId, String targetType, Long targetId) {
        try {
            Query query = new Query();
            query.addCriteria(Criteria.where("userId").is(userId));
            
            if (targetType != null) {
                query.addCriteria(Criteria.where("targetType").is(targetType));
            }
            if (targetId != null) {
                query.addCriteria(Criteria.where("targetId").is(targetId));
            }
            
            long deletedCount = mongoTemplate.remove(query, COLLECTION_NAME).getDeletedCount();
            log.info("删除用户访问日志成功：userId={}, deletedCount={}", userId, deletedCount);
            return deletedCount;
        } catch (Exception e) {
            log.error("删除用户访问日志失败：userId={}, error={}", userId, e.getMessage(), e);
            return 0;
        }
    }

    @Override
    public List<Map<String, Object>> getRealTimeTrend(int hours) {
        try {
            Date endTime = new Date();
            Date startTime = new Date(endTime.getTime() - hours * 3600 * 1000L);
            
            List<AggregationOperation> operations = new ArrayList<>();
            
            Criteria timestampCriteria = Criteria.where("timestamp");
            timestampCriteria.gte(startTime).lte(endTime);
            
            operations.add(Aggregation.match(timestampCriteria));
            
            operations.add(Aggregation.project()
                    .andExpression("{ $dateToString: { format: '%Y-%m-%d %H:00:00', date: '$timestamp' } }").as("hour"));
            
            operations.add(Aggregation.group("$hour").count().as("count"));
            operations.add(Aggregation.sort(Sort.Direction.ASC, "_id"));
            
            Aggregation aggregation = Aggregation.newAggregation(operations);
            AggregationResults<Map> results = mongoTemplate.aggregate(aggregation, COLLECTION_NAME, Map.class);
            
            return results.getMappedResults().stream()
                    .map(map -> {
                        Map<String, Object> result = new HashMap<>();
                        result.put("time", map.get("_id"));
                        result.put("count", map.get("count"));
                        return result;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("获取实时趋势失败：error={}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<UserAccessLogMongoDTO> getUserBehaviorPath(Long userId, Date startTime, Date endTime) {
        try {
            Criteria criteria = Criteria.where("userId").is(userId);
            
            Criteria timestampCriteria = Criteria.where("timestamp");
            boolean hasTimeCriteria = false;
            if (startTime != null) {
                timestampCriteria.gte(startTime);
                hasTimeCriteria = true;
            }
            if (endTime != null) {
                // 将结束时间增加1秒，确保包含边界值
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(endTime);
                calendar.add(Calendar.SECOND, 1);
                timestampCriteria.lt(calendar.getTime());
                hasTimeCriteria = true;
            }
            
            if (hasTimeCriteria) {
                criteria.andOperator(timestampCriteria);
            }
            
            Query query = new Query(criteria);
            query.with(Sort.by(Sort.Direction.ASC, "timestamp"));
            
            return mongoTemplate.find(query, UserAccessLogMongoDTO.class, COLLECTION_NAME);
        } catch (Exception e) {
            log.error("获取用户行为路径失败：userId={}, error={}", userId, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public Map<String, Object> getTargetUserPortrait(String targetType, Long targetId) {
        try {
            List<AggregationOperation> operations = new ArrayList<>();
            
            operations.add(Aggregation.match(
                    Criteria.where("targetType").is(targetType)
                            .and("targetId").is(targetId)));
            
            operations.add(Aggregation.group("$userId").count().as("count"));
            
            Aggregation aggregation = Aggregation.newAggregation(operations);
            AggregationResults<Map> results = mongoTemplate.aggregate(aggregation, COLLECTION_NAME, Map.class);
            
            List<Map> userStats = results.getMappedResults();
            
            Map<String, Object> portrait = new HashMap<>();
            portrait.put("totalUsers", userStats.size());
            portrait.put("totalVisits", userStats.stream().mapToLong(m -> (Long) m.get("count")).sum());
            portrait.put("avgVisitsPerUser", userStats.stream().mapToLong(m -> (Long) m.get("count")).average().orElse(0));
            
            return portrait;
        } catch (Exception e) {
            log.error("获取目标用户画像失败：targetType={}, targetId={}, error={}", 
                     targetType, targetId, e.getMessage(), e);
            return Collections.emptyMap();
        }
    }

    private void addCommonCriteria(Query query, String targetType, String actionType,
                                   Date startTime, Date endTime) {
        Criteria criteria = new Criteria();
        boolean hasCriteria = false;
        
        if (targetType != null) {
            criteria.and("targetType").is(targetType);
            hasCriteria = true;
        }
        if (actionType != null) {
            criteria.and("actionType").is(actionType);
            hasCriteria = true;
        }
        
        Criteria timestampCriteria = Criteria.where("timestamp");
        boolean hasTimeCriteria = false;
        if (startTime != null) {
            timestampCriteria.gte(startTime);
            hasTimeCriteria = true;
        }
        if (endTime != null) {
            // 将结束时间增加1秒，确保包含边界值
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(endTime);
            calendar.add(Calendar.SECOND, 1);
            timestampCriteria.lt(calendar.getTime());
            hasTimeCriteria = true;
        }
        
        if (hasTimeCriteria) {
            criteria.andOperator(timestampCriteria);
            hasCriteria = true;
        }
        
        if (hasCriteria) {
            query.addCriteria(criteria);
        }
    }
}