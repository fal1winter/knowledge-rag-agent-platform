package com.liang.bbs.rest.service.agent;

import com.liang.bbs.rest.dto.agent.AgentChatResponse;
import com.liang.bbs.rest.dto.agent.AgentConversation;
import com.liang.bbs.rest.repository.AgentConversationRepository;
import com.liang.bbs.rest.service.ClawHubService;
import com.liang.bbs.rest.service.agent.tool.AgentTool;
import com.liang.bbs.rest.service.agent.tool.ToolRegistry;
import com.liang.bbs.rest.service.agent.tool.ToolKit;
import com.liang.bbs.rest.service.agent.tool.ToolKitRegistry;
import com.liang.bbs.rest.service.agent.tool.ToolRegistryChangeEvent;
import com.liang.bbs.rest.service.agent.tool.OrchestratorTool;
import com.liang.bbs.rest.service.agent.CircuitBreakerService;
import com.liang.bbs.rest.service.agent.RateLimiterService;
import com.liang.bbs.rest.service.agent.AgentCacheService;
import com.liang.bbs.rest.service.agent.QueryRewriterService;
import com.liang.bbs.rest.service.agent.CrossEncoderRerankService;
import com.liang.bbs.rest.service.agent.core.AgentContext;
import com.liang.bbs.rest.service.agent.core.AgentRegistry;
import com.liang.bbs.rest.service.agent.core.SubAgentSpawner;
import com.liang.bbs.rest.service.agent.statemachine.AgentStateMachine;
import com.liang.bbs.rest.service.agent.statemachine.AgentState;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.TokenStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 基于 LangChain4j 的 Agent 服务
 * 使用 OpenAiChatModel + AiServices 实现真正的 Function Calling
 * 支持工具的运行时动态注册/注销
 */
@Service
@Slf4j
public class LangChainAgentService implements ApplicationListener<ToolRegistryChangeEvent> {

    // API 配置（从 application.yml 读取，支持环境变量覆盖）
    @Value("${llm.openrouter.api-key:}")
    private String openrouterApiKey;

    @Value("${llm.openrouter.base-url:https://openrouter.fans/v1}")
    private String openrouterBaseUrl;

    @Value("${llm.openrouter.model:deepseek-chat}")
    private String openrouterModel;

    @Value("${llm.openrouter.temperature:0.7}")
    private double openrouterTemperature;

    @Value("${llm.openrouter.max-tokens:1500}")
    private int openrouterMaxTokens;

    @Value("${llm.openrouter.timeout-seconds:60}")
    private int openrouterTimeoutSeconds;

    @Value("${llm.zju.api-key:}")
    private String zjuApiKey;

    @Value("${llm.zju.base-url:https://chat.zju.edu.cn/api/ai/v1}")
    private String zjuBaseUrl;

    @Value("${llm.zju.model:qwen3}")
    private String zjuModel;

    @Value("${llm.zju.temperature:0.7}")
    private double zjuTemperature;

    @Value("${llm.zju.max-tokens:1500}")
    private int zjuMaxTokens;

    @Value("${llm.zju.timeout-seconds:60}")
    private int zjuTimeoutSeconds;

    // 静态基础 system prompt（不含工具列表）
    private static final String BASE_SYSTEM_PROMPT = buildSystemPrompt();

    private static String buildSystemPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个学术论文评价系统的AI助手，名叫\"学术小助手\"。\n\n");
        sb.append("你的主要职责是帮助用户查询和了解论文、学者的相关信息。你可以：\n");
        sb.append("1. 查询论文详情（标题、作者、摘要、关键词等）\n");
        sb.append("2. 查询学者信息（姓名、机构、简介等）\n");
        sb.append("3. 获取论文或学者的评价列表\n");
        sb.append("4. 获取论文的AI阅读笔记\n");
        sb.append("5. 搜索论文或学者\n");
        sb.append("6. 【重要】帮助用户将高质量评价写入系统\n\n");

        sb.append("## 评价收集功能（重要）\n");
        sb.append("当你在对话中检测到用户对某篇论文或学者发表了高质量的评价时，你应该：\n");
        sb.append("1. 识别评价特征：用户的评价包含具体的观点、分析、评分建议，或者对论文/学者有实质性的评论\n");
        sb.append("2. 主动询问用户：\"您对这篇论文/学者的评价很有价值，是否愿意将这条评价写入系统，让更多人看到？\"\n");
        sb.append("3. 如果用户同意，请确认以下信息：\n");
        sb.append("   - 论文/学者的ID（如果用户没有提供，需要先搜索确认）\n");
        sb.append("   - 评分（1-10分，可以询问用户想给多少分）\n");
        sb.append("   - 评价内容（整理用户的评价，可以适当润色但保持原意）\n");
        sb.append("4. 获得用户明确同意后，调用 createPaperRating 或 createScholarRating 工具写入评价\n\n");

        sb.append("高质量评价的特征：\n");
        sb.append("- 包含对论文创新性、方法论、实验设计、写作质量等方面的具体评论\n");
        sb.append("- 有明确的观点和理由\n");
        sb.append("- 评价内容超过20个字\n");
        sb.append("- 不是简单的\"好\"或\"不好\"等模糊评价\n\n");

        sb.append("## Skill 安装能力\n");
        sb.append("当用户提到想要安装、获取、使用某类功能（如股市资讯、新闻、天气、翻译等）时，你应该：\n");
        sb.append("1. 使用 searchClawHubSkills 工具搜索相关 skill\n");
        sb.append("2. 向用户展示搜索结果，询问是否安装\n");
        sb.append("3. 用户确认后，调用 installClawHubSkill 工具安装\n");
        sb.append("4. 安装成功后告知用户该 skill 的功能已就绪\n\n");
        sb.append("回复要求：\n");
        sb.append("- 使用中文回复\n");
        sb.append("- 回答要简洁明了，重点突出\n");
        sb.append("- 如果用户询问的内容需要调用工具，请先调用工具获取信息，然后基于获取的信息给出友好的回复\n");
        sb.append("- 如果工具返回的信息较多，请帮用户总结关键点\n");
        sb.append("- 保持友好、专业的语气\n");
        sb.append("- 在写入评价前，必须获得用户的明确同意（如\"好的\"、\"可以\"、\"同意\"等）");
        return sb.toString();
    }

    private final ToolRegistry toolRegistry;
    private final ToolKitRegistry toolkitRegistry;
    private final AgentConversationRepository conversationRepository;
    private final ClawHubService clawHubService;
    private final OrchestratorTool orchestratorTool;
    private final CircuitBreakerService circuitBreakerService;
    private final RateLimiterService rateLimiterService;
    private final AgentCacheService agentCacheService;
    private final ConversationSummarizerService conversationSummarizer;
    private final CrossEncoderRerankService crossEncoderRerankService;
    private final SubAgentSpawner subAgentSpawner;
    private final AgentRegistry agentRegistry;

    @Value("${agent.rate-limiter.enabled:true}")
    private boolean rateLimiterEnabled;

    @Value("${agent.cache.enabled:true}")
    private boolean cacheEnabled;

    @Value("${agent.summarizer.enabled:true}")
    private boolean summarizerEnabled;

    public LangChainAgentService(
            ToolRegistry toolRegistry,
            ToolKitRegistry toolkitRegistry,
            AgentConversationRepository conversationRepository,
            ClawHubService clawHubService,
            OrchestratorTool orchestratorTool,
            CircuitBreakerService circuitBreakerService,
            RateLimiterService rateLimiterService,
            AgentCacheService agentCacheService,
            ConversationSummarizerService conversationSummarizer,
            CrossEncoderRerankService crossEncoderRerankService,
            SubAgentSpawner subAgentSpawner,
            AgentRegistry agentRegistry) {
        this.toolRegistry = toolRegistry;
        this.toolkitRegistry = toolkitRegistry;
        this.conversationRepository = conversationRepository;
        this.clawHubService = clawHubService;
        this.orchestratorTool = orchestratorTool;
        this.circuitBreakerService = circuitBreakerService;
        this.rateLimiterService = rateLimiterService;
        this.agentCacheService = agentCacheService;
        this.conversationSummarizer = conversationSummarizer;
        this.crossEncoderRerankService = crossEncoderRerankService;
        this.subAgentSpawner = subAgentSpawner;
        this.agentRegistry = agentRegistry;

        // 注册子 Agent 孵化工具（使 LLM 能够通过 callTool("spawnSubAgent", {...}) 调用）
        SpawnSubAgentTool spawnTool = new SpawnSubAgentTool();
        toolRegistry.register(spawnTool);
        log.info("SpawnSubAgentTool 已注册到 ToolRegistry");
    }

    /**
     * 构建动态 system prompt：包含工具集结构 + 已安装 skill 列表 + 推荐工具（从 contextStore 注入）。
     */
    private String buildDynamicSystemPrompt() {
        StringBuilder sb = new StringBuilder(BASE_SYSTEM_PROMPT);

        // 从 contextStore 获取 AgentContext（用于注入推荐工具等信息）
        // 遍历 memoryStore 取第一个 sessionId 作为当前会话标识
        String currentSessionId = null;
        for (String sid : memoryStore.keySet()) {
            currentSessionId = sid;
            break;
        }
        AgentContext ctx = (currentSessionId != null) ? contextStore.get(currentSessionId) : null;

        // ===== 推荐工具区域（来自父 Agent） =====
        if (ctx != null && ctx.getRecommendedTools() != null && !ctx.getRecommendedTools().isEmpty()) {
            sb.append("\n\n## 父 Agent 推荐工具\n");
            sb.append("以下工具由父 Agent 推荐使用，可优先调用：\n");
            for (String toolName : ctx.getRecommendedTools()) {
                AgentTool tool = toolRegistry.getTool(toolName);
                if (tool != null) {
                    sb.append("  ▶ ").append(toolName);
                    String intent = tool.userIntent();
                    if (intent != null && !intent.isEmpty()) {
                        sb.append(": ").append(intent);
                    }
                    sb.append("\n");
                    // 展示完整参数 schema
                    for (AgentTool.ParamDef param : tool.parameters()) {
                        sb.append("    - ").append(param.name())
                          .append(" (").append(param.required() ? "必填" : "可选");
                        if (param.defaultValue() != null && !param.defaultValue().isEmpty()) {
                            sb.append(", 默认值=").append(param.defaultValue());
                        }
                        sb.append(")");
                        if (param.description() != null && !param.description().isEmpty()) {
                            sb.append(": ").append(param.description());
                        }
                        sb.append("\n");
                    }
                } else {
                    sb.append("  - ").append(toolName).append("\n");
                }
            }
            sb.append("\n> 提示：推荐工具仅为建议，你仍可访问全部工具。\n\n");
        }

        // 工具集结构（核心工具集完整 schema，非核心工具集用 getToolSchema）
        sb.append("\n\n## 可用工具集\n");
        for (ToolKit kit : toolkitRegistry.getAllKits()) {
            sb.append("【").append(kit.getName()).append("】");
            if (kit.isCore()) sb.append(" [核心]");
            sb.append("：").append(kit.getDescription()).append("\n");
            sb.append("  包含工具：");
            for (AgentTool t : kit.getTools()) {
                sb.append(t.name());
                if (t.userIntent() != null && !t.userIntent().isEmpty()) {
                    sb.append("(").append(t.userIntent()).append(")");
                }
                sb.append("、");
            }
            int len = sb.length();
            if (sb.charAt(len - 1) == '、') {
                sb.setLength(len - 1);
            }
            sb.append("\n");
        }
        sb.append("\n使用说明：调用 callTool 工具，传入 toolName 和 argsJson 两个参数。\n");
        sb.append("  - 核心工具：可直接调用，参数 schema 已在上方完整展示\n");
        sb.append("  - 非核心工具：首次使用请先调用 callTool(\"getToolSchema\", {\"toolName\":\"工具名\"}) 获取参数定义\n");
        sb.append("  - 推荐工具：如上方有推荐，优先使用推荐工具（参数 schema 已展示）\n");

        // 已安装 ClawHub skill 列表
        List<ClawHubService.SkillInfo> skills = clawHubService.getInstalledSkillInfos();
        if (!skills.isEmpty()) {
            sb.append("\n\n## 当前已安装的 Skill 列表\n");
            sb.append("以下 skill 已就绪，当用户的问题与某个 skill 的能力匹配时，直接调用 executeSkill 工具执行，无需询问是否安装。\n\n");
            for (int i = 0; i < skills.size(); i++) {
                ClawHubService.SkillInfo s = skills.get(i);
                sb.append(i + 1).append(". **").append(s.name).append("**");
                if (s.description != null && !s.description.isEmpty()) {
                    sb.append("：").append(s.description);
                }
                sb.append("\n");
            }
            sb.append("\n> 提示：执行 skill 时调用 callTool(\"executeSkill\", {\"skillName\": \"xxx\", \"command\": \"...\"})\n");
        }
        return sb.toString();
    }

    private final Map<String, ChatMemory> memoryStore = new ConcurrentHashMap<>();

    /**
     * sessionId → AgentContext 映射
     * 用于在 buildDynamicSystemPrompt 中注入推荐工具列表等信息
     */
    private final Map<String, AgentContext> contextStore = new ConcurrentHashMap<>();

    // AI 助手接口
    private ScholarAssistant assistant;
    private ScholarAssistant backupAssistant;

    // 流式 AI 助手接口
    private StreamingScholarAssistant streamingAssistant;
    private StreamingScholarAssistant streamingBackupAssistant;

    // 是否初始化成功
    private volatile boolean initialized = false;

    // 是否使用备用 API
    private volatile boolean useBackupApi = false;

    // 定义 AI 助手接口
    interface ScholarAssistant {
        String chat(@MemoryId String sessionId, @dev.langchain4j.service.UserMessage String userMessage);
    }

    // 定义流式 AI 助手接口
    interface StreamingScholarAssistant {
        TokenStream chat(@MemoryId String sessionId, @dev.langchain4j.service.UserMessage String userMessage);
    }

    @PostConstruct
    public void init() {
        try {
            List<ToolSpecification> toolSpecs = buildToolSpecifications();

            // 构建 Map<ToolSpecification, ToolExecutor>（所有工具共享同一个 executor）
            Map<ToolSpecification, ToolExecutor> executorMap = new java.util.HashMap<ToolSpecification, ToolExecutor>();
            for (ToolSpecification spec : toolSpecs) {
                executorMap.put(spec, orchestratorTool);
            }

            // 创建主 ChatLanguageModel (OpenRouter)
            ChatLanguageModel openRouterModel = createOpenRouterModel();
            assistant = AiServices.builder(ScholarAssistant.class)
                .chatLanguageModel(openRouterModel)
                .chatMemoryProvider(this::getOrCreateMemory)
                .tools(executorMap)
                .systemMessageProvider(memoryId -> buildDynamicSystemPrompt())
                .build();

            // 创建备用 ChatLanguageModel (ZJU API)
            ChatLanguageModel zjuModel = createZjuModel();
            backupAssistant = AiServices.builder(ScholarAssistant.class)
                .chatLanguageModel(zjuModel)
                .chatMemoryProvider(this::getOrCreateMemory)
                .tools(executorMap)
                .systemMessageProvider(memoryId -> buildDynamicSystemPrompt())
                .build();

            // 创建流式主 ChatLanguageModel (OpenRouter)
            StreamingChatLanguageModel streamingOpenRouterModel = createStreamingOpenRouterModel();
            streamingAssistant = AiServices.builder(StreamingScholarAssistant.class)
                .streamingChatLanguageModel(streamingOpenRouterModel)
                .chatMemoryProvider(this::getOrCreateMemory)
                .tools(executorMap)
                .systemMessageProvider(memoryId -> buildDynamicSystemPrompt())
                .build();

            // 创建流式备用 ChatLanguageModel (ZJU API)
            StreamingChatLanguageModel streamingZjuModel = createStreamingZjuModel();
            streamingBackupAssistant = AiServices.builder(StreamingScholarAssistant.class)
                .streamingChatLanguageModel(streamingZjuModel)
                .chatMemoryProvider(this::getOrCreateMemory)
                .tools(executorMap)
                .systemMessageProvider(memoryId -> buildDynamicSystemPrompt())
                .build();

            initialized = true;
            log.info("LangChain4j Agent 初始化完成 (主API: OpenRouter, 备用API: ZJU, 支持流式输出, 工具集: {}, 工具: {})",
                toolkitRegistry.getAllKits().size(), toolSpecs.size());
        } catch (Exception e) {
            log.error("LangChain4j Agent 初始化失败", e);
            initialized = false;
        }
    }

    /**
     * 构建工具规格列表：仅返回 OrchestratorTool (callTool) 一个入口
     */
    private List<ToolSpecification> buildToolSpecifications() {
        return Collections.singletonList(toolSpecification(orchestratorTool));
    }

    private ToolSpecification toolSpecification(AgentTool tool) {
        ToolSpecification.Builder builder = ToolSpecification.builder()
            .name(tool.name())
            .description(tool.description());

        for (AgentTool.ParamDef param : tool.parameters()) {
            builder.addParameter(param.name(),
                JsonSchemaProperty.description(param.description()));
        }

        return builder.build();
    }

    /**
     * 监听工具注册变更事件，动态重建 AI 助手
     */
    @Override
    public void onApplicationEvent(ToolRegistryChangeEvent event) {
        log.info("收到工具注册变更事件: type={}, tool={}", event.getChangeType(), event.getToolName());
        try {
            rebuildAssistants();
        } catch (Exception e) {
            log.error("重建 AI 助手失败", e);
        }
    }

    /**
     * 重建所有 AI 助手实例（使用当前 ToolRegistry 中的工具）
     */
    private synchronized void rebuildAssistants() {
        if (!initialized) {
            log.warn("Agent 未初始化，跳过重建");
            return;
        }
        log.info("开始重建 AI 助手，当前工具数: {}", toolRegistry.size());

        List<ToolSpecification> toolSpecs = buildToolSpecifications();
        Map<ToolSpecification, ToolExecutor> executorMap = new java.util.HashMap<ToolSpecification, ToolExecutor>();
        for (ToolSpecification spec : toolSpecs) {
            executorMap.put(spec, orchestratorTool);
        }

        // 清理旧 assistant（它们持有的 tool references 会被 GC）
        assistant = null;
        backupAssistant = null;
        streamingAssistant = null;
        streamingBackupAssistant = null;

        try {
            ChatLanguageModel openRouterModel = createOpenRouterModel();
            assistant = AiServices.builder(ScholarAssistant.class)
                .chatLanguageModel(openRouterModel)
                .chatMemoryProvider(this::getOrCreateMemory)
                .tools(executorMap)
                .systemMessageProvider(memoryId -> buildDynamicSystemPrompt())
                .build();

            ChatLanguageModel zjuModel = createZjuModel();
            backupAssistant = AiServices.builder(ScholarAssistant.class)
                .chatLanguageModel(zjuModel)
                .chatMemoryProvider(this::getOrCreateMemory)
                .tools(executorMap)
                .systemMessageProvider(memoryId -> buildDynamicSystemPrompt())
                .build();

            StreamingChatLanguageModel streamingOpenRouterModel = createStreamingOpenRouterModel();
            streamingAssistant = AiServices.builder(StreamingScholarAssistant.class)
                .streamingChatLanguageModel(streamingOpenRouterModel)
                .chatMemoryProvider(this::getOrCreateMemory)
                .tools(executorMap)
                .systemMessageProvider(memoryId -> buildDynamicSystemPrompt())
                .build();

            StreamingChatLanguageModel streamingZjuModel = createStreamingZjuModel();
            streamingBackupAssistant = AiServices.builder(StreamingScholarAssistant.class)
                .streamingChatLanguageModel(streamingZjuModel)
                .chatMemoryProvider(this::getOrCreateMemory)
                .tools(executorMap)
                .systemMessageProvider(memoryId -> buildDynamicSystemPrompt())
                .build();

            log.info("AI 助手重建完成，工具集: {}, 工具: {}", toolkitRegistry.getAllKits().size(), toolSpecs.size());
        } catch (Exception e) {
            log.error("重建 AI 助手失败", e);
        }
    }

    /**
     * 创建主 ChatLanguageModel (OpenRouter)
     */
    private ChatLanguageModel createOpenRouterModel() {
        log.info("创建 OpenRouter 主 ChatLanguageModel: model={}, baseUrl={}",
            openrouterModel, openrouterBaseUrl);
        return OpenAiChatModel.builder()
            .apiKey(openrouterApiKey)
            .baseUrl(openrouterBaseUrl)
            .modelName(openrouterModel)
            .temperature(openrouterTemperature)
            .timeout(Duration.ofSeconds(openrouterTimeoutSeconds))
            .maxTokens(openrouterMaxTokens)
            .logRequests(true)
            .logResponses(true)
            .build();
    }

    /**
     * 创建备用 ChatLanguageModel (ZJU API)
     */
    private ChatLanguageModel createZjuModel() {
        log.info("创建 ZJU API 备用 ChatLanguageModel: model={}, baseUrl={}", zjuModel, zjuBaseUrl);
        return OpenAiChatModel.builder()
            .apiKey(zjuApiKey)
            .baseUrl(zjuBaseUrl)
            .modelName(zjuModel)
            .temperature(zjuTemperature)
            .timeout(Duration.ofSeconds(zjuTimeoutSeconds))
            .maxTokens(zjuMaxTokens)
            .logRequests(true)
            .logResponses(true)
            .build();
    }

    /**
     * 创建流式主 ChatLanguageModel (OpenRouter)
     */
    private StreamingChatLanguageModel createStreamingOpenRouterModel() {
        log.info("创建 OpenRouter 流式 ChatLanguageModel: model={}", openrouterModel);
        return OpenAiStreamingChatModel.builder()
            .apiKey(openrouterApiKey)
            .baseUrl(openrouterBaseUrl)
            .modelName(openrouterModel)
            .temperature(openrouterTemperature)
            .timeout(Duration.ofSeconds(openrouterTimeoutSeconds))
            .logRequests(true)
            .logResponses(true)
            .build();
    }

    /**
     * 创建流式备用 ChatLanguageModel (ZJU API)
     */
    private StreamingChatLanguageModel createStreamingZjuModel() {
        log.info("创建 ZJU API 流式 ChatLanguageModel: model={}", zjuModel);
        return OpenAiStreamingChatModel.builder()
            .apiKey(zjuApiKey)
            .baseUrl(zjuBaseUrl)
            .modelName(zjuModel)
            .temperature(zjuTemperature)
            .timeout(Duration.ofSeconds(zjuTimeoutSeconds))
            .logRequests(true)
            .logResponses(true)
            .build();
    }

    /**
     * 获取或创建会话记忆
     */
    private ChatMemory getOrCreateMemory(Object memoryId) {
        String sessionId = memoryId.toString();
        return memoryStore.computeIfAbsent(sessionId, id -> {
            ChatMemory memory = MessageWindowChatMemory.withMaxMessages(20);

            try {
                Optional<AgentConversation> conv = conversationRepository.findById(sessionId);
                if (conv.isPresent()) {
                    for (AgentConversation.AgentMessage msg : conv.get().getMessages()) {
                        if ("user".equals(msg.getRole()) && msg.getContent() != null) {
                            memory.add(UserMessage.from(msg.getContent()));
                        } else if ("assistant".equals(msg.getRole()) && msg.getContent() != null) {
                            memory.add(AiMessage.from(msg.getContent()));
                        }
                    }
                    log.info("从数据库加载了 {} 条历史消息", conv.get().getMessages().size());
                }
            } catch (Exception e) {
                log.warn("加载历史消息失败: {}", e.getMessage());
            }

            return memory;
        });
    }

    /**
     * 处理用户消息（流式）
     */
    public void chatStream(String sessionId, String userMessage, Integer userId, SseEmitter emitter) {
        new Thread(() -> {
            StringBuilder fullResponse = new StringBuilder();
            String actualSessionId = null;
            AgentConversation conversation = null;

            try {
                if (!initialized || streamingAssistant == null) {
                    log.warn("Agent 未初始化，尝试重新初始化");
                    init();
                    if (!initialized) {
                        emitter.send(SseEmitter.event()
                            .name("error")
                            .data("{\"error\":\"AI服务初始化失败，请稍后再试\"}"));
                        emitter.complete();
                        return;
                    }
                }

                // ===== 前置拦截：限流检查 =====
                if (rateLimiterEnabled && !rateLimiterService.tryAcquire(sessionId, userId)) {
                    log.warn("流式请求被限流: sessionId={}, userId={}", sessionId, userId);
                    emitter.send(SseEmitter.event()
                        .name("error")
                        .data("{\"error\":\"请求过于频繁，请稍后再试\"}"));
                    emitter.complete();
                    return;
                }

                conversation = getOrCreateConversation(sessionId, userId);
                actualSessionId = conversation.getId();

                emitter.send(SseEmitter.event()
                    .name("session")
                    .data("{\"sessionId\":\"" + actualSessionId + "\"}"));

                addMessage(conversation, "user", userMessage, null, null);

                log.info("处理用户消息（流式）: sessionId={}, message={}", actualSessionId, userMessage);

                // ===== 缓存命中检查 =====
                String cachedReply = null;
                if (cacheEnabled) {
                    String cacheKey = agentCacheService.buildCacheKey(userMessage, userId);
                    cachedReply = agentCacheService.get(cacheKey);
                    if (cachedReply != null && cachedReply.length() > 5) {
                        log.info("流式请求缓存命中: sessionId={}", actualSessionId);
                        // 缓存命中，直接返回缓存内容
                        String finalCached = cachedReply;
                        emitter.send(SseEmitter.event()
                            .name("token")
                            .data(finalCached));
                        addMessage(conversation, "assistant", finalCached, null, null);
                        conversation.setUpdateTime(LocalDateTime.now());
                        conversationRepository.save(conversation);
                        checkSummarize(actualSessionId);
                        emitter.send(SseEmitter.event()
                            .name("done")
                            .data("{\"sessionId\":\"" + actualSessionId + "\", \"cached\":true}"));
                        emitter.complete();
                        return;
                    }
                }

                TokenStream tokenStream;
                final String chatSessionId = actualSessionId;
                try {
                    // ===== 熔断器保护 + LLM 调用 =====
                    tokenStream = circuitBreakerService.execute("openrouter", () -> {
                        if (useBackupApi) {
                            log.info("使用备用 API (ZJU) - 流式");
                            return streamingBackupAssistant.chat(chatSessionId, userMessage);
                        } else {
                            log.info("使用主 API (OpenRouter) - 流式");
                            return streamingAssistant.chat(chatSessionId, userMessage);
                        }
                    }, null);

                    if (tokenStream == null) {
                        throw new RuntimeException("LLM 流式返回为空");
                    }

                } catch (Exception e) {
                    String errorMsg = e.getMessage();
                    log.error("LangChain4j 流式调用失败: {}", errorMsg);

                    if (errorMsg != null && (errorMsg.contains("quota") || errorMsg.contains("rate_limit")
                            || errorMsg.contains("insufficient") || errorMsg.contains("429"))) {
                        log.warn("主 API (OpenRouter) 配额不足或限流，切换到备用 API (ZJU)");
                        tokenStream = circuitBreakerService.execute("zju", () ->
                            streamingBackupAssistant.chat(chatSessionId, userMessage), null);
                    } else {
                        // 尝试备用 API
                        try {
                            tokenStream = circuitBreakerService.execute("zju", () ->
                                streamingBackupAssistant.chat(chatSessionId, userMessage), null);
                        } catch (Exception backupError) {
                            log.error("备用 API 也失败: {}", backupError.getMessage());
                            emitter.send(SseEmitter.event()
                                .name("error")
                                .data("{\"error\":\"处理失败: " + backupError.getMessage() + "\"}"));
                            emitter.completeWithError(backupError);
                            return;
                        }
                    }
                }

                final AgentConversation finalConversation = conversation;
                final String finalSessionId = actualSessionId;

                tokenStream
                    .onNext(token -> {
                        try {
                            fullResponse.append(token);
                            emitter.send(SseEmitter.event()
                                .name("token")
                                .data(token));
                        } catch (Exception e) {
                            log.error("发送 token 失败", e);
                        }
                    })
                    .onComplete(response -> {
                        try {
                            String cleanedResponse = cleanResponse(fullResponse.toString());
                            log.info("Agent 流式回复完成: {}", cleanedResponse);

                            addMessage(finalConversation, "assistant", cleanedResponse, null, null);

                            if (finalConversation.getTitle() == null || finalConversation.getTitle().isEmpty()) {
                                String title = userMessage.length() > 30 ? userMessage.substring(0, 30) + "..." : userMessage;
                                finalConversation.setTitle(title);
                            }

                            finalConversation.setUpdateTime(LocalDateTime.now());
                            conversationRepository.save(finalConversation);

                            // ===== 写缓存 =====
                            if (cacheEnabled && cleanedResponse.length() > 10) {
                                String cacheKey = agentCacheService.buildCacheKey(userMessage, userId);
                                agentCacheService.set(cacheKey, cleanedResponse);
                            }

                            // ===== 触发对话压缩检查 =====
                            checkSummarize(finalSessionId);

                            emitter.send(SseEmitter.event()
                                .name("done")
                                .data("{\"sessionId\":\"" + finalSessionId + "\"}"));
                            emitter.complete();
                        } catch (Exception e) {
                            log.error("完成流式响应失败", e);
                            try {
                                emitter.send(SseEmitter.event()
                                    .name("error")
                                    .data("{\"error\":\"" + e.getMessage() + "\"}"));
                                emitter.completeWithError(e);
                            } catch (Exception ex) {
                                log.error("发送错误消息失败", ex);
                            }
                        }
                    })
                    .onError(error -> {
                        log.error("Agent 流式处理失败", error);
                        try {
                            emitter.send(SseEmitter.event()
                                .name("error")
                                .data("{\"error\":\"" + error.getMessage() + "\"}"));
                            emitter.completeWithError(error);
                        } catch (Exception e) {
                            log.error("发送错误消息失败", e);
                        }
                    })
                    .start();

            } catch (Exception e) {
                log.error("Agent 流式处理消息失败", e);
                try {
                    emitter.send(SseEmitter.event()
                        .name("error")
                        .data("{\"error\":\"处理失败: " + e.getMessage() + "\"}"));
                    emitter.completeWithError(e);
                } catch (Exception ex) {
                    log.error("发送错误消息失败", ex);
                }
            }
        }).start();
    }

    /**
     * 处理用户消息
     */
    public AgentChatResponse chat(String sessionId, String userMessage, Integer userId) {
        try {
            if (!initialized || assistant == null) {
                log.warn("Agent 未初始化，尝试重新初始化");
                init();
                if (!initialized) {
                    return AgentChatResponse.error("AI服务初始化失败，请稍后再试");
                }
            }

            // ===== 前置拦截：限流检查 =====
            if (rateLimiterEnabled && !rateLimiterService.tryAcquire(sessionId, userId)) {
                return AgentChatResponse.error("请求过于频繁，请稍后再试");
            }

            AgentConversation conversation = getOrCreateConversation(sessionId, userId);
            String actualSessionId = conversation.getId();

            addMessage(conversation, "user", userMessage, null, null);

            log.info("处理用户消息: sessionId={}, message={}", actualSessionId, userMessage);

            // ===== 缓存命中检查 =====
            if (cacheEnabled) {
                String cacheKey = agentCacheService.buildCacheKey(userMessage, userId);
                String cached = agentCacheService.get(cacheKey);
                if (cached != null && cached.length() > 5) {
                    log.info("缓存命中，回接用户消息后返回缓存响应: sessionId={}", actualSessionId);

                    addMessage(conversation, "assistant", cached, null, null);
                    conversation.setUpdateTime(LocalDateTime.now());
                    conversationRepository.save(conversation);

                    // 触发对话压缩检查
                    checkSummarize(conversation.getId());

                    AgentChatResponse response = new AgentChatResponse();
                    response.setSuccess(true);
                    response.setSessionId(actualSessionId);
                    response.setReply(cached);
                    response.setCached(true);
                    return response;
                }
            }

            String reply;
            try {
                // ===== 主逻辑：熔断器保护 + LLM 调用 =====
                reply = circuitBreakerService.execute("openrouter", () -> {
                    if (useBackupApi) {
                        log.info("使用备用 API (ZJU)");
                        return backupAssistant.chat(actualSessionId, userMessage);
                    } else {
                        log.info("使用主 API (OpenRouter)");
                        return assistant.chat(actualSessionId, userMessage);
                    }
                }, null);

                if (reply == null) {
                    throw new RuntimeException("LLM 返回为空");
                }

            } catch (Exception e) {
                String errorMsg = e.getMessage();
                log.error("LangChain4j 调用失败: {}", errorMsg);

                if (errorMsg != null && (errorMsg.contains("quota") || errorMsg.contains("rate_limit")
                        || errorMsg.contains("insufficient") || errorMsg.contains("429"))) {
                    log.warn("主 API (OpenRouter) 配额不足或限流，切换到备用 API (ZJU)");
                    useBackupApi = true;
                    try {
                        reply = circuitBreakerService.execute("zju", () ->
                            backupAssistant.chat(actualSessionId, userMessage), null);
                    } catch (Exception backupError) {
                        log.error("备用 API 也失败: {}", backupError.getMessage());
                        throw backupError;
                    }
                } else {
                    init();
                    if (initialized) {
                        reply = circuitBreakerService.execute("openrouter", () ->
                            assistant.chat(actualSessionId, userMessage), null);
                    } else {
                        throw e;
                    }
                }
            }

            reply = cleanResponse(reply);

            log.info("Agent 回复: {}", reply);

            addMessage(conversation, "assistant", reply, null, null);

            if (conversation.getTitle() == null || conversation.getTitle().isEmpty()) {
                String title = userMessage.length() > 30 ? userMessage.substring(0, 30) + "..." : userMessage;
                conversation.setTitle(title);
            }

            conversation.setUpdateTime(LocalDateTime.now());
            conversationRepository.save(conversation);

            // ===== 写缓存 =====
            if (cacheEnabled && reply.length() > 10) {
                String cacheKey = agentCacheService.buildCacheKey(userMessage, userId);
                agentCacheService.set(cacheKey, reply);
            }

            // ===== 触发对话压缩检查 =====
            checkSummarize(conversation.getId());

            AgentChatResponse response = new AgentChatResponse();
            response.setSuccess(true);
            response.setSessionId(actualSessionId);
            response.setReply(reply);

            return response;

        } catch (Exception e) {
            log.error("Agent 处理消息失败", e);
            return AgentChatResponse.error("处理失败: " + e.getMessage());
        }
    }

    /**
     * 清理响应内容
     */
    private String cleanResponse(String response) {
        if (response == null) return "";
        response = response.replaceAll("<think>[\\s\\S]*?", "").trim();
        response = response.replaceAll("\\[TOOL_CALL:[^\\]]+\\]", "").trim();
        return response;
    }

    /**
     * 获取或创建会话
     */
    private AgentConversation getOrCreateConversation(String sessionId, Integer userId) {
        if (sessionId != null && !sessionId.isEmpty()) {
            Optional<AgentConversation> existing = conversationRepository.findById(sessionId);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        AgentConversation conversation = new AgentConversation();
        conversation.setUserId(userId);
        conversation.setCreateTime(LocalDateTime.now());
        conversation.setUpdateTime(LocalDateTime.now());
        return conversationRepository.save(conversation);
    }

    /**
     * 添加消息到会话
     */
    private void addMessage(AgentConversation conversation, String role, String content,
                           String toolName, String toolResult) {
        AgentConversation.AgentMessage msg = new AgentConversation.AgentMessage();
        msg.setRole(role);
        msg.setContent(content);
        msg.setToolName(toolName);
        msg.setToolResult(toolResult);
        msg.setTimestamp(LocalDateTime.now());
        conversation.getMessages().add(msg);
    }

    /**
     * 获取用户会话列表
     */
    public List<AgentConversation> getUserSessions(Integer userId) {
        return conversationRepository.findTop20ByUserIdOrderByUpdateTimeDesc(userId);
    }

    /**
     * 获取会话详情
     */
    public AgentConversation getSession(String sessionId) {
        return conversationRepository.findById(sessionId).orElse(null);
    }

    /**
     * 删除会话
     */
    public boolean deleteSession(String sessionId, Integer userId) {
        Optional<AgentConversation> conv = conversationRepository.findById(sessionId);
        if (conv.isPresent() && Objects.equals(conv.get().getUserId(), userId)) {
            conversationRepository.deleteById(sessionId);
            memoryStore.remove(sessionId);
            return true;
        }
        return false;
    }

    public boolean deleteSession(String sessionId, String anonymousId) {
        Optional<AgentConversation> conv = conversationRepository.findById(sessionId);
        if (conv.isPresent() && anonymousId != null && anonymousId.equals(conv.get().getAnonymousId())) {
            conversationRepository.deleteById(sessionId);
            memoryStore.remove(sessionId);
            return true;
        }
        return false;
    }

    /**
     * 清理会话记忆（用于重置对话）
     */
    public void clearMemory(String sessionId) {
        memoryStore.remove(sessionId);
    }

    /**
     * 获取或创建资料RAG会话（绑定 materialId）
     */
    public AgentConversation getOrCreateMaterialConversation(String sessionId, Integer userId, Long materialId) {
        return getOrCreateMaterialConversation(sessionId, userId, null, materialId);
    }

    public AgentConversation getOrCreateMaterialConversation(String sessionId, Integer userId, String anonymousId, Long materialId) {
        if (sessionId != null && !sessionId.isEmpty()) {
            Optional<AgentConversation> existing = conversationRepository.findById(sessionId);
            if (existing.isPresent() && materialId.equals(existing.get().getMaterialId())) {
                return existing.get();
            }
        }
        AgentConversation conversation = new AgentConversation();
        conversation.setUserId(userId);
        conversation.setAnonymousId(anonymousId);
        conversation.setMaterialId(materialId);
        conversation.setCreateTime(LocalDateTime.now());
        conversation.setUpdateTime(LocalDateTime.now());
        return conversationRepository.save(conversation);
    }

    /**
     * 获取某资料下的会话列表
     */
    public List<AgentConversation> getMaterialSessions(Integer userId, Long materialId) {
        return conversationRepository.findByUserIdAndMaterialIdOrderByUpdateTimeDesc(userId, materialId);
    }

    public List<AgentConversation> getMaterialSessions(String anonymousId, Long materialId) {
        if (anonymousId == null || anonymousId.isEmpty()) {
            return Collections.emptyList();
        }
        return conversationRepository.findByAnonymousIdAndMaterialIdOrderByUpdateTimeDesc(anonymousId, materialId);
    }

    /**
     * 检查并触发对话摘要压缩
     */
    private void checkSummarize(String sessionId) {
        if (!summarizerEnabled || conversationSummarizer == null) {
            return;
        }
        try {
            conversationSummarizer.checkAndSummarize(sessionId);
        } catch (Exception e) {
            log.warn("对话摘要压缩失败: sessionId={}, error={}", sessionId, e.getMessage());
        }
    }

    // ==================== AgentContext 管理 ====================

    /**
     * 注册 AgentContext 到 contextStore
     * 供 buildDynamicSystemPrompt() 注入推荐工具等信息
     */
    public void registerContext(String sessionId, AgentContext context) {
        if (sessionId != null && context != null) {
            contextStore.put(sessionId, context);
            log.debug("AgentContext 已注册: sessionId={}, recommendedTools={}",
                sessionId, context.getRecommendedTools());
        }
    }

    /**
     * 获取当前会话的 AgentContext
     */
    public AgentContext getContext(String sessionId) {
        return contextStore.get(sessionId);
    }

    // ==================== 子 Agent 孵化工具 ====================

    /**
     * LLM 可调用的子 Agent 孵化工具
     * 允许 AI Agent 在对话过程中动态孵化子 Agent 来处理复杂任务
     */
    public class SpawnSubAgentTool implements AgentTool {
        @Override
        public String name() {
            return "spawnSubAgent";
        }

        @Override
        public String userIntent() {
            return "孵化子 Agent 来处理复杂任务";
        }

        @Override
        public String description() {
            return "当遇到需要深入研究或并行处理的任务时，孵化一个子 Agent 来处理。\n"
                + "适用场景：\n"
                + "  - 需要多角度并行搜索信息\n"
                + "  - 需要对某篇论文进行深入分析（相关工作、方法对比等）\n"
                + "  - 需要搜索更广泛的领域知识\n"
                + "参数：\n"
                + "  - agentType: 子 Agent 类型（如 paper_researcher, web_knowledge_miner）\n"
                + "  - taskInput: 传递给子 Agent 的任务描述\n"
                + "  - parentAgentInstanceId: 父 Agent 实例 ID（从上下文获取）\n\n"
                + "返回：子 Agent 实例 ID，可用于查询子 Agent 状态";
        }

        @Override
        public List<ParamDef> parameters() {
            return java.util.Arrays.asList(
                new ParamDef("agentType", "子 Agent 类型，如 paper_researcher / web_knowledge_miner / task_rewrite", true, null),
                new ParamDef("taskInput", "任务输入描述", true, null),
                new ParamDef("parentAgentInstanceId", "父 Agent 实例 ID（从当前对话上下文获取）", false, null)
            );
        }

        @Override
        public String execute(List<Object> args) {
            if (args == null || args.isEmpty()) {
                return "参数错误：缺少 agentType";
            }

            String agentType = args.get(0) != null ? args.get(0).toString() : null;
            if (agentType == null || agentType.isEmpty()) {
                return "参数错误：agentType 不能为空";
            }

            String taskInput = args.size() > 1 && args.get(1) != null ? args.get(1).toString() : "";
            String parentInstanceId = args.size() > 2 && args.get(2) != null ? args.get(2).toString() : null;

            // 如果未提供 parentInstanceId，尝试从第一个对话 sessionId 推断（取内存中任意一个）
            if (parentInstanceId == null || parentInstanceId.isEmpty()) {
                if (!memoryStore.isEmpty()) {
                    parentInstanceId = memoryStore.keySet().iterator().next();
                } else {
                    return "参数错误：缺少父 Agent 实例 ID";
                }
            }

            try {
                // 从 parentInstanceId 构造 AgentContext（从 MongoDB 中获取）
                AgentContext context = buildContextFromInstance(parentInstanceId);
                String childInstanceId = subAgentSpawner.delegateTask(parentInstanceId, agentType, taskInput, context);

                // 将子 Agent 的 context 注册到 contextStore（注入推荐工具）
                registerContext(childInstanceId, context);

                return "子 Agent 孵化成功，实例 ID: " + childInstanceId + "\n"
                    + "类型: " + agentType + "\n"
                    + "任务: " + taskInput + "\n"
                    + "推荐工具: " + (context.getRecommendedTools() != null
                        ? String.join(", ", context.getRecommendedTools())
                        : "无");
            } catch (IllegalStateException e) {
                return "子 Agent 孵化失败（嵌套深度超限）: " + e.getMessage();
            } catch (IllegalArgumentException e) {
                return "子 Agent 孵化失败（参数错误）: " + e.getMessage();
            } catch (Exception e) {
                log.error("子 Agent 孵化异常: agentType={}, parentId={}", agentType, parentInstanceId, e);
                return "子 Agent 孵化失败: " + e.getMessage();
            }
        }

        private AgentContext buildContextFromInstance(String parentInstanceId) {
            AgentContext context = new AgentContext();
            context.setSessionId(parentInstanceId);

            // 从 contextStore 获取父 Agent 的推荐工具列表
            AgentContext parentCtx = LangChainAgentService.this.getContext(parentInstanceId);
            if (parentCtx != null && parentCtx.getRecommendedTools() != null
                    && !parentCtx.getRecommendedTools().isEmpty()) {
                context.setRecommendedTools(parentCtx.getRecommendedTools());
                log.debug("子 Agent 继承父 Agent 推荐工具: parentId={}, tools={}",
                    parentInstanceId, parentCtx.getRecommendedTools());
            }

            // 同时从 agent_definition 表读取该类型的工具列表作为兜底推荐
            try {
                java.util.List<String> defTools = LangChainAgentService.this.agentRegistry.parseTools("default");
                if (context.getRecommendedTools() == null || context.getRecommendedTools().isEmpty()) {
                    context.setRecommendedTools(defTools);
                }
            } catch (Exception e) {
                log.debug("读取默认工具推荐失败: {}", e.getMessage());
            }

            return context;
        }
    }
}