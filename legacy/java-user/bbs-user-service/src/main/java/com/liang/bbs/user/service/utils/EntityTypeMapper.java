package com.liang.bbs.user.service.utils;

import com.liang.bbs.article.facade.dto.PaperDTO;
import com.liang.bbs.article.facade.dto.RateDTO;
import com.liang.bbs.article.facade.dto.ScolarDTO;
import com.liang.bbs.user.facade.dto.user.UserDTO;
import lombok.extern.slf4j.Slf4j;

/**
 * 实体类型映射工具类
 * 提供静态方法进行实体类型判断和DTO包装
 * 
 */
@Slf4j
public class EntityTypeMapper {
    
    /**
     * 实体类型常量定义
     */
    public static final String TYPE_PAPER = "0";
    public static final String TYPE_SCHOLAR = "1";
    public static final String TYPE_RATE = "2";
    public static final String TYPE_USER = "3";
    
    /**
     * 根据类型获取对应的类类型
     */
    public static Class<?> getDtoClassByType(String type) {
        switch (type) {
            case TYPE_PAPER:
                return PaperDTO.class;
            case TYPE_SCHOLAR:
                return ScolarDTO.class;
            case TYPE_RATE:
                return RateDTO.class;
            case TYPE_USER:
                return UserDTO.class;
            default:
                log.warn("未知的实体类型: {}", type);
                return null;
        }
    }
    
    /**
     * 验证实体类型是否有效
     */
    public static boolean isValidType(String type) {
        return TYPE_PAPER.equals(type) || 
               TYPE_SCHOLAR.equals(type) || 
               TYPE_RATE.equals(type) || 
               TYPE_USER.equals(type);
    }
    
    /**
     * 获取实体类型的描述
     */
    public static String getTypeDescription(String type) {
        switch (type) {
            case TYPE_PAPER:
                return "论文";
            case TYPE_SCHOLAR:
                return "学者";
            case TYPE_RATE:
                return "评价";
            case TYPE_USER:
                return "用户";
            default:
                return "未知";
        }
    }
}