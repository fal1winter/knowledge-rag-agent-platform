# Knowledge RAG Agent Platform

面向知识付费场景的企业级 RAG 对话平台，支持多格式文档入库、RAPTOR 树状层级检索、多路召回融合、Agentic 迭代推理和端到端评测闭环。

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
┌───────────┐       ┌──────────────┐       ┌─────────────────────────────┐
│  Vue 前端  │──────▶│  Java 网关   │──────▶│      Python RAG 引擎        │
└───────────┘       │  (鉴权/支付/ │       │  ┌───────┐  ┌───────────┐  │
                    │   会话管理)   │       │  │Router │─▶│ Retrieval │  │
                    └──────────────┘       │  └───────┘  └─────┬─────┘  │
                                           │                   │        │
                                           │  ┌────────────────▼─────┐  │
                                           │  │ Milvus│ES│Neo4j│RRF  │  │
                                           │  └────────────────┬─────┘  │
                                           │  ┌────────────────▼─────┐  │
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
- **Agentic 迭代检索** — 对复杂推理类问题执行多轮检索-反思循环
- **端到端评测** — 规则过滤 + LLM-as-Judge + Pairwise A/B 灰度 + Bad Case 归因
- **三层记忆机制** — 短期对话 / 会话摘要 / 长期用户画像，加权指数衰减遗忘
- **多格式文档解析** — PDF / Word / PPT / Excel / 图片 OCR / 音视频 ASR
- **JWT 鉴权 + 租户隔离** — 网关层统一身份校验，检索层强制 tenant_id 过滤
- **素材购买权限** — 对话前校验用户是否持有目标素材的访问权
- **流式输出** — SSE 逐 token 推送，降低首字等待时间
- **支付闭环** — 支付宝 / 微信支付创建与异步回调，积分扣减与充值

## 目录结构

```text
java-gateway/                  Java 业务网关
  auth/                        JWT 鉴权、权限校验
  controller/                  REST 接口（RAG 对话、会话管理、用户、支付）
  payment/                     支付宝 / 微信支付 Provider
  rag/                         RAG 服务调用客户端、会话存储、流式客户端
  order/                       素材订单网关
  user/                        用户积分网关

frontend/                      Vue 前端
  src/components/material/     资料列表 / 详情 / 上传 / 订单 / 对话

src/rag_agent_platform/        Python RAG 引擎
  api/                         FastAPI 应用入口 + 网关信任中间件
  ingestion/                   文档解析 → 切片 → RAPTOR 建树 → 索引写入
  documents/                   多格式 Loader + 语义切片
  raptor/                      RAPTOR 树状索引构建与检索
  routing/                     意图路由 / 控制指令 / 查询改写
  retrieval/                   Milvus / Elasticsearch / Neo4j / RRF / Reranker / Agentic
  generation/                  Agent 编排 + DeepSeek/Qwen 双模型 LLM 调用适配
  evaluation/                  规则 / LLM-Judge / A-B / Bad Case
  memory/                      三层记忆与遗忘策略
  knowledge/                   静态知识时效巡检
  cost/                        轻量模型调度与降本策略

datasets/                      训练数据（意图分类 / 查询改写样本）
benchmarks/                    评测数据集与指标验证脚本
docs/                          架构、能力矩阵、部署文档
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

## 文档

- [系统架构](docs/ARCHITECTURE.md)
- [能力矩阵](docs/CAPABILITIES.md)
- [部署指南](docs/DEPLOYMENT.md)

## License

MIT
