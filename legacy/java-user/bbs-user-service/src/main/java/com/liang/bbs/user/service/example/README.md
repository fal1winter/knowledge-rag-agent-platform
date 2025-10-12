# 通过AOP快速记录访问行为指南

## 概述

本项目通过AOP（面向切面编程）技术，提供了一种无需显式调用日志服务即可记录用户访问行为的方法。只需在需要记录访问行为的方法上添加`@RecordAccess`注解即可。

## 使用方法

### 1. 添加注解

在需要记录访问行为的方法上添加`@RecordAccess`注解：

```java
import com.liang.bbs.user.service.annotation.RecordAccess;

@Service
public class ArticleService {
    
    @RecordAccess(
        targetType = "article",
        targetId = "#articleId", 
        actionType = "view",
        userId = "#userId",
        extraInfo = "#article.title"
    )
    public Article viewArticle(Long userId, Long articleId, Article article) {
        // 业务逻辑
        return article;
    }
}
```

### 2. 注解参数说明

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `targetType` | String | `""` | 目标类型，如：article、user、comment |
| `targetId` | String | `""` | 目标ID，支持SpEL表达式，如：`"#articleId"` |
| `actionType` | String | `"view"` | 动作类型，如：view、like、create、update、delete |
| `userId` | String | `""` | 用户ID，支持SpEL表达式，如：`"#userId"` |
| `extraInfo` | String | `""` | 额外信息，支持SpEL表达式，将存储在additionalData.extraInfo中，如：`"#article.title"` |
| `async` | boolean | `true` | 是否异步记录，默认异步提高性能 |

### 3. SpEL表达式使用

支持Spring Expression Language (SpEL) 表达式：

- `#paramName`：引用方法参数
- `#paramName.field`：引用参数的字段
- `#root.args[0]`：引用第1个参数
- `#this`：当前对象

### 4. 实际应用示例

#### 示例1：文章服务
```java
@Service
public class ArticleService {
    
    @RecordAccess(targetType="article", targetId="#id", actionType="view", userId="#userId")
    public Article getArticle(Long id, Long userId) {
        // 获取文章逻辑
    }
    
    @RecordAccess(targetType="article", targetId="#articleId", actionType="like", userId="#userId")
    public void likeArticle(Long userId, Long articleId) {
        // 点赞文章逻辑
    }
}
```

#### 示例2：评论服务
```java
@Service
public class CommentService {
    
    @RecordAccess(
        targetType="comment", 
        targetId="#comment.id", 
        actionType="create",
        userId="#comment.userId",
        extraInfo="#comment.content"
    )
    public Comment createComment(Comment comment) {
        // 创建评论逻辑
    }
}
```

#### 示例3：用户服务
```java
@Service
public class UserService {
    
    @RecordAccess(targetType="user", targetId="#targetUserId", actionType="follow", userId="#currentUserId")
    public void followUser(Long currentUserId, Long targetUserId) {
        // 关注用户逻辑
    }
}
```

## 配置说明

### 1. 启用AOP

确保在配置类中启用AOP和异步功能：

```java
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
@EnableAsync
public class AopConfig {
    // 配置类
}
```

### 2. 依赖检查

确保`pom.xml`中包含以下依赖：

```xml
<!-- AOP依赖 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>

<!-- 异步执行依赖 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

## 注意事项

1. **性能影响**：默认使用异步记录，对业务性能影响极小
2. **错误处理**：记录失败不会影响主业务流程
3. **表达式安全**：确保SpEL表达式正确，避免空指针异常
4. **参数命名**：方法参数必须有明确名称，或使用`@Param`注解

## 调试和监控

### 1. 查看日志

查看`bbs-user-info.log`日志文件，搜索关键词`"记录用户访问日志"`：

```bash
tail -f logs/bbs-user/bbs-user-info.log | grep "记录用户访问日志"
```

### 2. 验证数据

使用REST接口验证数据是否正确记录：

```bash
# 获取用户访问记录
curl "http://localhost:8080/bbs/access/mongo/user/123/logs?limit=10"

# 获取访问统计
curl "http://localhost:8080/bbs/access/mongo/count?targetType=article&targetId=456"
```

## 常见问题

### Q1: 注解不生效怎么办？
A: 检查以下几点：
- 确保方法被Spring容器管理（有`@Service`等注解）
- 检查AOP配置是否正确启用
- 确认是从Spring容器获取的bean调用方法（自调用不生效）

### Q2: SpEL表达式解析失败？
A: 检查表达式语法：
- 确保参数名称正确
- 使用`#paramName`格式引用参数
- 复杂表达式使用`#root.args[index]`

### Q3: 如何关闭异步记录？
A: 设置`async=false`：
```java
@RecordAccess(..., async=false)
```

## 扩展建议

1. **自定义注解**：可以基于`@RecordAccess`创建更具体的注解
2. **批量记录**：支持批量操作，使用`@RecordAccess`结合循环
3. **条件记录**：添加条件表达式，只在满足条件时记录
4. **性能优化**：根据实际需求调整异步线程池配置