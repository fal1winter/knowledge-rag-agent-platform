package com.liang.bbs.user.service.utils;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.redisson.client.protocol.ScoredEntry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Redis 服务工具类，使用 Redisson 客户端
 * 
 */
@Slf4j
@Component
public class RedisService {

    @Autowired
    private RedissonClient redissonClient;

    /**
     * 设置字符串值，带过期时间
     *
     * @param key     键
     * @param value   值
     * @param timeout 过期时间
     * @param unit    时间单位
     */
    public void set(String key, String value, long timeout, TimeUnit unit) {
        try {
            RBucket<String> bucket = redissonClient.getBucket(key);
            bucket.set(value, timeout, unit);
            log.debug("Redis set: key={}, timeout={} {}", key, timeout, unit);
        } catch (Exception e) {
            log.error("Redis set failed: key={}", key, e);
            throw new RuntimeException("Redis 操作失败", e);
        }
    }

    /**
     * 设置字符串值，不带过期时间
     *
     * @param key   键
     * @param value 值
     */
    public void set(String key, String value) {
        try {
            RBucket<String> bucket = redissonClient.getBucket(key);
            bucket.set(value);
            log.debug("Redis set: key={}", key);
        } catch (Exception e) {
            log.error("Redis set failed: key={}", key, e);
            throw new RuntimeException("Redis 操作失败", e);
        }
    }

    /**
     * 获取字符串值
     *
     * @param key 键
     * @return 值，不存在返回 null
     */
    public String get(String key) {
        try {
            RBucket<String> bucket = redissonClient.getBucket(key);
            return bucket.get();
        } catch (Exception e) {
            log.error("Redis get failed: key={}", key, e);
            return null;
        }
    }

    /**
     * 删除键
     *
     * @param key 键
     * @return 是否删除成功
     */
    public boolean delete(String key) {
        try {
            RBucket<String> bucket = redissonClient.getBucket(key);
            return bucket.delete();
        } catch (Exception e) {
            log.error("Redis delete failed: key={}", key, e);
            return false;
        }
    }

    /**
     * 获取并删除（原子操作）
     *
     * @param key 键
     * @return 值，不存在返回 null
     */
    public String getAndDelete(String key) {
        try {
            RBucket<String> bucket = redissonClient.getBucket(key);
            return bucket.getAndDelete();
        } catch (Exception e) {
            log.error("Redis getAndDelete failed: key={}", key, e);
            return null;
        }
    }

    /**
     * 判断键是否存在
     *
     * @param key 键
     * @return 是否存在
     */
    public boolean exists(String key) {
        try {
            RBucket<String> bucket = redissonClient.getBucket(key);
            return bucket.isExists();
        } catch (Exception e) {
            log.error("Redis exists failed: key={}", key, e);
            return false;
        }
    }

    /**
     * 设置过期时间
     *
     * @param key     键
     * @param timeout 过期时间
     * @param unit    时间单位
     * @return 是否设置成功
     */
    public boolean expire(String key, long timeout, TimeUnit unit) {
        try {
            RBucket<String> bucket = redissonClient.getBucket(key);
            return bucket.expire(timeout, unit);
        } catch (Exception e) {
            log.error("Redis expire failed: key={}", key, e);
            return false;
        }
    }

    // ==================== ZSet 操作 ====================

    /**
     * ZSet 增加分数（用于热门榜单）
     *
     * @param key    键
     * @param member 成员
     * @param score  增加的分数
     * @return 新的分数
     */
    public double zincrby(String key, String member, double score) {
        try {
            RScoredSortedSet<String> zset = redissonClient.getScoredSortedSet(key);
            Double newScore = zset.addScore(member, score);
            log.debug("Redis zincrby: key={}, member={}, score={}, newScore={}", key, member, score, newScore);
            return newScore != null ? newScore : 0;
        } catch (Exception e) {
            log.error("Redis zincrby failed: key={}, member={}", key, member, e);
            return 0;
        }
    }

    /**
     * 获取 ZSet 前 N 名（按分数从高到低）
     *
     * @param key   键
     * @param limit 数量限制
     * @return 成员和分数的列表
     */
    public List<Map<String, Object>> zrevrangeWithScores(String key, int limit) {
        try {
            RScoredSortedSet<String> zset = redissonClient.getScoredSortedSet(key);
            Collection<ScoredEntry<String>> entries = zset.entryRangeReversed(0, limit - 1);
            
            List<Map<String, Object>> result = new ArrayList<>();
            for (ScoredEntry<String> entry : entries) {
                Map<String, Object> item = new HashMap<>();
                item.put("member", entry.getValue());
                item.put("score", entry.getScore());
                result.add(item);
            }
            return result;
        } catch (Exception e) {
            log.error("Redis zrevrangeWithScores failed: key={}", key, e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取 ZSet 成员的分数
     *
     * @param key    键
     * @param member 成员
     * @return 分数，不存在返回 null
     */
    public Double zscore(String key, String member) {
        try {
            RScoredSortedSet<String> zset = redissonClient.getScoredSortedSet(key);
            return zset.getScore(member);
        } catch (Exception e) {
            log.error("Redis zscore failed: key={}, member={}", key, member, e);
            return null;
        }
    }

    /**
     * 删除 ZSet 成员
     *
     * @param key    键
     * @param member 成员
     * @return 是否删除成功
     */
    public boolean zrem(String key, String member) {
        try {
            RScoredSortedSet<String> zset = redissonClient.getScoredSortedSet(key);
            return zset.remove(member);
        } catch (Exception e) {
            log.error("Redis zrem failed: key={}, member={}", key, member, e);
            return false;
        }
    }

    /**
     * 获取 ZSet 大小
     *
     * @param key 键
     * @return 成员数量
     */
    public int zcard(String key) {
        try {
            RScoredSortedSet<String> zset = redissonClient.getScoredSortedSet(key);
            return zset.size();
        } catch (Exception e) {
            log.error("Redis zcard failed: key={}", key, e);
            return 0;
        }
    }

    /**
     * 设置 ZSet 过期时间
     *
     * @param key     键
     * @param timeout 过期时间
     * @param unit    时间单位
     * @return 是否设置成功
     */
    public boolean expireZSet(String key, long timeout, TimeUnit unit) {
        try {
            RScoredSortedSet<String> zset = redissonClient.getScoredSortedSet(key);
            return zset.expire(timeout, unit);
        } catch (Exception e) {
            log.error("Redis expireZSet failed: key={}", key, e);
            return false;
        }
    }

    /**
     * 删除 ZSet
     *
     * @param key 键
     * @return 是否删除成功
     */
    public boolean deleteZSet(String key) {
        try {
            RScoredSortedSet<String> zset = redissonClient.getScoredSortedSet(key);
            return zset.delete();
        } catch (Exception e) {
            log.error("Redis deleteZSet failed: key={}", key, e);
            return false;
        }
    }
}
