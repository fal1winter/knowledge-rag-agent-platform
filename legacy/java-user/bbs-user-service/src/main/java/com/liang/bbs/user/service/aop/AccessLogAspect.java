package com.liang.bbs.user.service.aop;

import com.liang.bbs.user.facade.dto.UserAccessLogMongoDTO;
import com.liang.bbs.user.facade.server.UserAccessLogMongoService;
import com.liang.bbs.user.service.annotation.RecordAccess;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.Reference;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Date;
import java.util.concurrent.CompletableFuture;

/**
 * 访问日志记录切面
 * 通过AOP自动记录用户访问行为
 */
@Slf4j
@Aspect
@Component
public class AccessLogAspect {

    @Reference
    private UserAccessLogMongoService userAccessLogMongoService;

    private final SpelExpressionParser spelExpressionParser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    /**
     * 定义切点：所有带有@RecordAccess注解的方法
     */
    @Pointcut("@annotation(com.liang.bbs.user.service.annotation.RecordAccess)")
    public void recordAccessPointcut() {}

    /**
     * 环绕通知：记录访问行为
     */
    @Around("recordAccessPointcut()")
    public Object recordAccess(ProceedingJoinPoint joinPoint) throws Throwable {
        try {
            // 先执行原方法
            Object result = joinPoint.proceed();
            
            // 异步记录访问日志
            recordAccessLog(joinPoint);
            
            return result;
        } catch (Exception e) {
            log.error("记录访问行为时发生异常", e);
            throw e;
        }
    }

    /**
     * 记录访问日志
     */
    private void recordAccessLog(ProceedingJoinPoint joinPoint) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            RecordAccess annotation = method.getAnnotation(RecordAccess.class);
            
            if (annotation == null) {
                return;
            }

            // 获取方法参数
            Object[] args = joinPoint.getArgs();
            String[] paramNames = parameterNameDiscoverer.getParameterNames(method);
            EvaluationContext context = new StandardEvaluationContext();
            
            if (paramNames != null) {
                for (int i = 0; i < paramNames.length; i++) {
                    context.setVariable(paramNames[i], args[i]);
                }
            }

            // 创建访问日志DTO
            UserAccessLogMongoDTO dto = new UserAccessLogMongoDTO();
            
            // 解析SpEL表达式
            String targetType = annotation.targetType();
            String targetIdStr = parseSpelExpression(annotation.targetId(), context);
            String actionType = annotation.actionType();
            String userIdStr = parseSpelExpression(annotation.userId(), context);
            String extraInfo = parseSpelExpression(annotation.extraInfo(), context);

            // 设置DTO属性
            dto.setTargetType(targetType);
            dto.setTargetId(parseLong(targetIdStr));
            dto.setActionType(actionType);
            dto.setUserId(parseLong(userIdStr));
            dto.setTimestamp(new Date());
            
            // 将额外信息放入additionalData
            if (extraInfo != null && !extraInfo.trim().isEmpty()) {
                java.util.Map<String, Object> additionalData = new java.util.HashMap<>();
                additionalData.put("extraInfo", extraInfo);
                dto.setAdditionalData(additionalData);
            }

            // 记录日志
            if (annotation.async()) {
                recordAsync(dto);
            } else {
                userAccessLogMongoService.recordAccess(dto);
            }
            
        } catch (Exception e) {
            log.error("解析访问日志参数失败", e);
        }
    }

    /**
     * 异步记录访问日志
     */
    @Async
    private void recordAsync(UserAccessLogMongoDTO dto) {
        try {
            userAccessLogMongoService.recordAccess(dto);
        } catch (Exception e) {
            log.error("异步记录访问日志失败", e);
        }
    }

    /**
     * 解析SpEL表达式
     */
    private String parseSpelExpression(String expression, EvaluationContext context) {
        if (expression == null || expression.trim().isEmpty()) {
            return "";
        }
        
        try {
            Object value = spelExpressionParser.parseExpression(expression).getValue(context);
            return value != null ? value.toString() : "";
        } catch (Exception e) {
            log.warn("解析SpEL表达式失败: {}", expression, e);
            // 返回原始表达式作为fallback，避免空值
            return expression;
        }
    }

    /**
     * 解析Long值
     */
    private Long parseLong(String str) {
        if (str == null || str.trim().isEmpty()) {
            return null;
        }
        
        try {
            return Long.parseLong(str.trim());
        } catch (NumberFormatException e) {
            log.warn("解析Long值失败: {}", str);
            return null;
        }
    }
}