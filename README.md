# Knowledge RAG Agent Platform

面向知识付费场景的企业级 RAG 对话平台，支持多格式文档入库、RAPTOR 树状层级检索、多路召回融合、Agentic 迭代推理和端到端评测闭环。

## 项目亮点

相较于常见的开源 RAG 项目（如 LangChain-based chatbot、Dify、FastGPT 等），本平台在以下方面有显著差异化：

### 1. 检索架构深度

| 维度 | 常见 RAG 方案 | 本项目 |
|------|-------------|--------|
| 索引结构 | 单层 flat 向量 | RAPTOR 树状层级（k-means 聚类多层摘要） |
| 召回策略 | 单一向量相似度 | Milvus 稠密 + ES BM25 双路 + RRF 融合 + 可配权重 |
| 精排 | 无 / 简单分数阈值 | BGE cross-encoder 重排序 |
| 图谱检索 | 无 | Neo4j 多跳实体关系查询 |
| 复杂问题 | 单轮检索 | Agentic 迭代检索（质量评估 → 策略切换 → 多轮自适应） |
| 精准模式 | 无区分 | 叶子块精准检索 + 上下文窗口扩展 |

### 2. 模型调度策略

不依赖单一大模型暴力覆盖，而是按任务复杂度分层路由：

- **本地 Qwen QLoRA**（1.5B/7B）：意图分类、查询改写、简单命令 — 低延迟低成本
- **DeepSeek-V3（硅基流动）**：回答生成、RAPTOR 摘要、质量评估 — 复杂推理
- 三级意图路由（规则 → 关键词 → 模型）逐步升级，>60% 的请求不需要调用大模型

### 3. 生产级工程实现

- **Java 网关层**：JWT 鉴权 + 租户隔离 + 滑动窗口限流 + 支付宝/微信支付闭环 + 会话 TTL 自动回收
- **入库管线**：SHA-256 指纹去重 + 批量异步 ThreadPool + 死信队列重试
- **知识时效管理**：多级过期告警 + source_url 主动拉取对比 + 增量重入库触发
- **端到端评测**：规则过滤 → LLM-Judge → A/B 灰度 → Bad Case 归因分析 → 审核队列

### 4. 完整的业务闭环

不仅仅是一个"问答 demo"，而是从素材购买 → 支付 → 权限校验 → RAG 对话 → 流式输出 → 评测优化的完整链路：

```
用户 → Vue 前端 → Java 网关(鉴权/支付/限流) → Python RAG 引擎 → 多源检索 → 模型生成 → SSE 流式返回
                                                                    ↑
                                                           评测反馈 → 持续优化
```

### 5. 记忆机制

三层记忆分离 + 指数衰减遗忘（非简单 sliding window）：
- 短期对话记忆（当前会话上下文）
- 会话级摘要（跨轮次压缩）
- 长期用户画像（偏好/知识盲区追踪）

## 技术栈

| 层级 | 技术选型 |
|------|----------|
| 业务网关 | Spring Boot 2.7 / JWT 鉴权 / 支付宝 & 微信支付 SDK |
| RAG 引擎 | Python 3.10+ / FastAPI / SSE 流式输出 |
| 向量检索 | Milvus（稠密） + Elasticsearch（BM25 稀疏） |
| 知识图谱 | Neo4j（多跳实体关系查询） |
| 大模型 | DeepSeek (硅基流动 SiliconFlow) 复杂推理/生成 + 本地 Qwen QLoRA 轻量任务 |
| 精排 | BGE-Reranker |
| 索引结构 | RAPTOR 自底向上 k-means 聚类树状摘要 |
| 前端 | Vue 3 + Axios |

## 系统架构

```
┌───────────┐       ┌──────────────────────┐       ┌─────────────────────────────┐
│  Vue 前端  │──────▶│     Java 网关         │──────▶│      Python RAG 引擎        │
└───────────┘       │  ┌────────────────┐  │       │  ┌───────┐  ┌───────────┐  │
                    │  │ JWT 鉴权       │  │       │  │Router │─▶│ Retrieval │  │
                    │  │ 限流(令牌桶)   │  │       │  └───────┘  └─────┬─────┘  │
                    │  │ 会话管理       │  │       │                   │        │
                    │  │ 支付闭环       │  │       │  ┌────────────────▼─────┐  │
                    │  │ 素材权限       │  │       │  │ Milvus│ES│Neo4j│RRF  │  │
                    │  └────────────────┘  │       │  └────────────────┬─────┘  │
                    └──────────────────────┘       │  ┌────────────────▼─────┐  │
                                                   │  │  BGE Rerank + Agent  │  │
                                                   │  └────────────────┬─────┘  │
                                                   │  ┌────────────────▼─────┐  │
                                                   │  │  Generation (DeepSeek)│  │
                                                   │  └──────────────────────┘  │
                                                   └─────────────────────────────┘
```

## 核心特性

- **RAPTOR 树状层级检索** — 自底向上 k-means 聚类构建多层摘要节点，支持粗召回 + 精准下钻
- **三级意图路由** — 规则指令 → 关键词匹配 → Qwen 轻量模型，分层递进降低推理开销
- **混合检索 + RRF 融合** — Milvus 稠密向量 + Elasticsearch BM25 双路召回，Reciprocal Rank Fusion 排序
- **BGE 精排** — 对融合结果做 cross-encoder 重排序，提升 top-k 精准度
- **Neo4j 知识图谱** — 实体关系建模，支持多跳 Cypher 查询
- **Agentic 迭代检索** — 对复杂推理类问题执行多轮检索-反思循环，自动策略切换
- **端到端评测** — 规则过滤 + LLM-as-Judge + Pairwise A/B 灰度 + Bad Case 归因
- **三层记忆机制** — 短期对话 / 会话摘要 / 长期用户画像，加权指数衰减遗忘
- **多格式文档解析** — PDF / Word / PPT / Excel / 图片 OCR / 音视频 ASR
- **入库去重与容错** — SHA-256 指纹去重 + 批量异步入库 + 死信重试
- **知识时效管理** — TTL 多级告警 + source_url 主动拉取对比 + 增量触发
- **JWT 鉴权 + 租户隔离** — 网关层统一身份校验，检索层强制 tenant_id 过滤
- **滑动窗口限流** — 令牌桶算法 + 自动桶清理，防止单用户冲击后端
- **素材购买权限** — 对话前校验用户是否持有目标素材的访问权
- **流式输出** — SSE 逐 token 推送，降低首字等待时间
- **支付闭环** — 支付宝 / 微信支付创建与异步回调，积分扣减与充值

## 目录结构

```text
java-gateway/                  Java 业务网关
  auth/                        JWT 鉴权、权限校验、全局异常处理
  controller/                  REST 接口（RAG 对话、会话管理、用户、支付、健康检查）
  ratelimit/                   滑动窗口限流器 + Spring 拦截器
  payment/                     支付宝 / 微信支付 Provider + 订单仓储
  rag/                         RAG 服务调用客户端、会话存储、流式客户端
  order/                       素材订单网关
  user/                        用户积分网关
  config/                      RestTemplate 配置（超时、日志、重试）

frontend/                      Vue 前端
  src/components/material/     资料列表 / 详情 / 上传 / 订单 / 对话

src/rag_agent_platform/        Python RAG 引擎
  api/                         FastAPI 应用入口 + 网关信任中间件
  ingestion/                   文档解析 → 去重 → 批量入库 → 死信重试
  documents/                   多格式 Loader + 语义切片
  raptor/                      RAPTOR 树状索引构建与检索
  routing/                     意图路由 / 控制指令 / 查询改写
  retrieval/                   Milvus / Elasticsearch / Neo4j / RRF / Reranker / Agentic / 精准块
  generation/                  Agent 编排 + DeepSeek/Qwen 双模型 LLM 调用适配
  evaluation/                  规则 / LLM-Judge / A-B / Bad Case 归因
  memory/                      三层记忆与遗忘策略
  knowledge/                   静态知识时效巡检 + source_url 更新检测
  cost/                        轻量模型调度与降本策略
  integrations/                HTTP 客户端（重试 / 流式 / token 统计）

datasets/                      训练数据（意图分类 / 查询改写样本）
benchmarks/                    评测数据集与指标验证脚本
docs/                          架构、能力矩阵、部署文档
tests/                         单元测试
```

## 快速开始

### 环境要求

- Python 3.10+
- Java 8+ / Maven 3.6+
- Node.js 16+

### Python RAG 引擎

```bash
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt && pip install -e .
cp .env.example .env
# 编辑 .env 配置 Milvus / ES / Neo4j / Qwen 端点
uvicorn rag_agent_platform.api.run:app --host 0.0.0.0 --port 8080
```

### Java 网关

```bash
cd java-gateway
# 配置环境变量或修改 application.yml
mvn spring-boot:run
```

### 前端

```bash
cd frontend
cp .env.example .env.local
npm install && npm run serve
```

### Docker 一键启动

```bash
docker-compose up -d
```

## 配置项

主要环境变量参考 `.env.example` 和 `java-gateway/src/main/resources/application.yml`：

| 变量 | 说明 |
|------|------|
| `MILVUS_URI` | Milvus 向量数据库地址 |
| `ELASTICSEARCH_URL` | Elasticsearch BM25 索引地址 |
| `NEO4J_URI` | Neo4j 图数据库 Bolt 地址 |
| `DEEPSEEK_ENDPOINT` | DeepSeek 推理/生成 API 端点（硅基流动） |
| `DEEPSEEK_API_KEY` | 硅基流动 API 密钥 |
| `QWEN_INTENT_ENDPOINT` | 本地 Qwen 意图分类 vLLM 端点 |
| `JWT_SECRET` | 网关 JWT 签名密钥 |
| `GATEWAY_INTERNAL_SECRET` | 网关→RAG 引擎内部通信密钥 |

完整配置说明见 [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)。

## 评测

```bash
# 运行检索评测
PYTHONPATH=src python3 scripts/run_rag_benchmark.py \
  --dataset benchmarks/sample_eval.jsonl \
  --output artifacts/benchmarks/candidate.json

# 对比基线
python3 scripts/compare_retrieval_ab.py \
  --baseline artifacts/benchmarks/baseline.json \
  --candidate artifacts/benchmarks/candidate.json
```

评测指标包括 recall@k、意图路由准确率、P50/P99 延迟、LLM-Judge 评分。

## Roadmap

### 短期可落地（1-2 周）

- [ ] 接入真实 Milvus/ES 实例，验证端到端检索质量
- [ ] 补充前端对话页面的 Markdown 渲染和引文高亮
- [ ] 限流配置接入 application.yml，支持运行时热更新
- [ ] 添加 Prometheus metrics 端点（QPS、延迟分位数、模型调用成本）

### 中期增强（1-2 月）

- [ ] Redis 替换内存会话/订单存储，支持多实例水平扩展
- [ ] 接入向量数据库增量同步（CDC），实现近实时知识更新
- [ ] 实现 RAG 上下文压缩（LongContext Compression），降低长上下文 token 消耗
- [ ] 前端增加管理后台：知识库管理、巡检状态面板、评测报表可视化
- [ ] 模型网关层 A/B 灰度：按流量比例切换不同模型/prompt 策略

### 长期方向

- [ ] 多模态 RAG：图片内容理解 + 表格结构化检索
- [ ] 基于用户反馈的在线学习：点赞/踩 → reranker 微调 → 检索质量闭环
- [ ] Graph RAG 增强：基于知识图谱的推理链路可视化
- [ ] 分布式入库管线：Kafka + Worker 架构，支持大规模并发入库
- [ ] 端侧模型缓存：高频问答本地 KV Cache 加速，减少 API 调用

## 文档

- [系统架构](docs/ARCHITECTURE.md)
- [能力矩阵](docs/CAPABILITIES.md)
- [部署指南](docs/DEPLOYMENT.md)

## License

MIT
