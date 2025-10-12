package com.liang.bbs.user.service.aspect;

import com.liang.bbs.user.facade.annotation.AutoCreateNoti;
import com.liang.bbs.user.facade.dto.NotiContext;
import com.liang.bbs.user.facade.dto.NotiDTO;
import com.liang.bbs.user.facade.server.NotiService;
import com.liang.bbs.user.facade.server.NotiContentService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.Reference;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.LocalVariableTableParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 自动创建通知的切面
 *
 */
@Slf4j
@Aspect
@Component
public class AutoCreateNotiAspect {

    @Reference
    private NotiService notiService;

    @Autowired
    private NotiContentService notiContentService;

    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final LocalVariableTableParameterNameDiscoverer nameDiscoverer = new LocalVariableTableParameterNameDiscoverer();

    /**
     * 定义切点：所有带有@AutoCreateNoti注解的方法
     */
    @Pointcut("@annotation(com.liang.bbs.user.facade.annotation.AutoCreateNoti)")
    public void autoCreateNotiPointcut() {}

    /**
     * 环绕通知
     */
    @Around("autoCreateNotiPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        // 先执行原方法
        Object result = joinPoint.proceed();
        
        try {
            // 获取注解信息
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            AutoCreateNoti annotation = method.getAnnotation(AutoCreateNoti.class);
            
            // 构建上下文
            NotiContext context = buildContext(joinPoint, result);
            
            // 创建通知
            createNotification(annotation, context);
            
        } catch (Exception e) {
            log.error("自动创建通知失败", e);
        }
        
        return result;
    }

    /**
     * 构建通知上下文
     */
    private NotiContext buildContext(ProceedingJoinPoint joinPoint, Object result) {
        NotiContext context = new NotiContext();
        context.setReturnValue(result);
        
        // 获取方法参数
        Object[] args = joinPoint.getArgs();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] parameterNames = nameDiscoverer.getParameterNames(signature.getMethod());
        
        Map<String, Object> methodArgs = new HashMap<>();
        if (parameterNames != null) {
            for (int i = 0; i < parameterNames.length; i++) {
                if (i < args.length) {
                    methodArgs.put(parameterNames[i], args[i]);
                }
            }
        }
        context.setMethodArgs(methodArgs);
        
        return context;
    }

    /**
     * 创建通知
     */
    private void createNotification(AutoCreateNoti annotation, NotiContext context) {
        NotiDTO notiDTO = new NotiDTO();
        
        // 使用SpEL解析表达式
        EvaluationContext evaluationContext = new StandardEvaluationContext(context);
        
        // 设置内容
        if (!annotation.content().isEmpty()) {
            String content = parseExpression(annotation.content(), evaluationContext);
            // 处理内容中的占位符
            String processedContent = notiContentService.processNotificationContent(content);
            notiDTO.setContent(processedContent);
        }
        
        // 设置类型 - 注意type是String类型
        notiDTO.setType(String.valueOf(annotation.type()));
        
        // 设置发送者ID
        if (!annotation.senderId().isEmpty()) {
            String senderIdStr = parseExpression(annotation.senderId(), evaluationContext);
            try {
                notiDTO.setSenderid(Integer.valueOf(senderIdStr));
            } catch (NumberFormatException e) {
                log.warn("发送者ID格式错误: {}", senderIdStr);
            }
        }
        
        // 设置接收者ID
        if (!annotation.receiverId().isEmpty()) {
            String receiverIdStr = parseExpression(annotation.receiverId(), evaluationContext);
            try {
                notiDTO.setUserid(Integer.valueOf(receiverIdStr));
            } catch (NumberFormatException e) {
                log.warn("接收者ID格式错误: {}", receiverIdStr);
            }
        }
        
        // 设置额外数据
        if (!annotation.extra().isEmpty()) {
            notiDTO.setExtra(parseExpression(annotation.extra(), evaluationContext));
        } else {
            notiDTO.setExtra("{}");
        }
        
        // 创建通知
        if (annotation.async()) {
            CompletableFuture.runAsync(() -> {
                try {
                    notiService.create(notiDTO, notiDTO.getUserid());
                } catch (Exception e) {
                    log.error("异步创建通知失败", e);
                }
            });
        } else {
            notiService.create(notiDTO, notiDTO.getUserid());
        }
    }

    /**
     * 解析SpEL表达式
     */
    private String parseExpression(String expression, EvaluationContext context) {
        try {
            return parser.parseExpression(expression).getValue(context, String.class);
        } catch (Exception e) {
            log.warn("表达式解析失败: {}", expression, e);
            return expression;
        }
    }
}