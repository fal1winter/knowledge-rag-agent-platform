# AOP快速记录访问行为使用指南

## 概述
通过AOP技术，现在可以在任何Spring管理的组件中快速记录用户访问行为，无需显式调用日志服务。

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
    public Article getArticle(Long userId, Long articleId) {
        // 业务逻辑
        return article;
    }
}
```

### 2. 注解参数说明

| 参数 | 类型 | 示例 | 说明 |
|------|------|------|------|
| `targetType` | String | `"article"` | 目标类型，如：article、comment、user |
| `targetId` | String | `"#articleId"` | 目标ID，支持SpEL表达式，从方法参数获取 |
| `actionType` | String | `"view"` | 动作类型，如：view、like、create、update |
| `userId` | String | `"#userId"` | 用户ID，支持SpEL表达式 |
| `extraInfo` | String | `"#article.title"` | 附加信息，支持SpEL表达式，存储在additionalData.extraInfo |
| `async` | boolean | `true` | 是否异步记录，默认异步提高性能 |

### 3. SpEL表达式示例

- 直接参数：`"#userId"`
- 嵌套属性：`"#article.id"`
- 方法调用：`"#article.getTitle()"`
- 字符串拼接：`"#user.name + '访问了' + #article.title"`

### 4. 实际应用示例

#### 文章服务
```java
@Service
public class ArticleService {
    
    @RecordAccess(targetType="article", targetId="#id", actionType="view", userId="#userId")
    public Article viewArticle(Long userId, Long id) {
        return articleRepository.findById(id);
    }
    
    @RecordAccess(targetType="article", targetId="#id", actionType="like", userId="#userId")
    public void likeArticle(Long userId, Long id) {
        articleRepository.incrementLikes(id);
    }
}
```

#### 评论服务
```java
@Service
public class CommentService {
    
    @RecordAccess(
        targetType="comment", 
        targetId="#commentId", 
        actionType="create", 
        userId="#userId",
        extraInfo="#content"
    )
    public Comment addComment(Long userId, Long commentId, String content) {
        // 保存评论
        return comment;
    }
}
```

#### 用户服务
```java
@Service
public class UserService {
    
    @RecordAccess(targetType="user", targetId="#targetUserId", actionType="follow", userId="#userId")
    public void followUser(Long userId, Long targetUserId) {
        // 关注逻辑
    }
}
```

### 5. 验证记录

可以通过以下方式验证记录是否成功：

```java
@Autowired
private UserAccessLogMongoService userAccessLogMongoService;

// 获取用户访问记录
List<UserAccessLogMongoDTO> logs = userAccessLogMongoService.getUserAccessLogs(userId, null, null, null, null, 10);

// 获取统计数量
long count = userAccessLogMongoService.getAccessCount("article", articleId, "view", null, null);
```

### 6. 注意事项

1. **方法必须是Spring管理的Bean**：确保添加注解的类有`@Service`、`@Component`等注解
2. **参数可访问**：SpEL表达式引用的参数必须是方法的实际参数
3. **异步处理**：默认异步记录，提高性能，但可能在异常时丢失少量记录
4. **错误处理**：AOP内部有完善的错误处理，不会影响主业务流程

### 7. 调试和监控

- 查看日志：搜索"记录访问行为"或"异步记录访问日志"
- 检查MongoDB：查询user_access_logs集合
- 验证统计：使用UserAccessLogMongoService的统计接口

### 8. 扩展建议

- **自定义动作类型**：根据业务需求定义新的actionType
- **元数据丰富**：可以扩展记录IP、设备信息等
- **批量处理**：对于高频操作，可以考虑批量记录优化

通过以上方式，你可以在任何Spring组件中快速集成访问行为记录功能，无需修改原有业务逻辑代码。