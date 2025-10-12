# Knowledge RAG Agent Platform

面向知识付费场景的企业级 RAG 对话平台。该目录是独立项目，未修改当前正在运行的 Java/Python 服务文件。

Milvus、Elasticsearch、Neo4j、Qwen、OCR、ASR 等外部能力均以 adapter 形式接入。项目可以在不启动这些外部服务的情况下阅读整体链路；生产部署时替换对应 adapter 即可。

## Directory

```text
knowledge-rag-agent-platform/
  java-gateway/          Java 业务网关：用户积分、资料购买、支付宝/微信支付、RAG 转发
  frontend/              Vue 最小前端：资料列表/上传/购买/订单/资料对话
  src/rag_agent_platform/
    api/                 FastAPI 应用入口
    ingestion/           文档解析、切片、RAPTOR 建树、索引写入流水线
    documents/           PDF/Word/PPT/Excel/图片/音视频解析与切片
    raptor/              RAPTOR 树状层级索引与下钻检索
    routing/             三级意图路由、控制指令、查询改写
    retrieval/           Milvus、Elasticsearch、Neo4j、RRF、BGE rerank、Agentic 检索
    generation/          Agent 编排、Qwen 调用适配、引用生成
    evaluation/          规则评测、LLM-as-Judge、A/B、Bad Case
    memory/              三层用户记忆与权重遗忘
    knowledge/           静态知识库时效巡检
    cost/                轻量模型和调用降本策略
    storage/             离线内存适配器
  legacy/
    python-rag-service/  从现有 /home/sun/javabackend/rag-service 保留的服务模块
    java-material/       从现有 Java 资料系统保留的控制器/服务/SQL
    java-user/           从现有 Java 用户系统保留的用户、OAuth、积分服务和持久层
    java-rest-user/      从现有 Java REST 层保留的用户、OAuth、积分入口
    frontend-material/   从现有 vue3web 保留的资料页面和 service
  docs/
    ARCHITECTURE.md
    CAPABILITIES.md
    DEPLOYMENT.md
```

## Main Capabilities

- Java 网关：`java-gateway/` 负责资料购买、积分扣减、支付宝/微信支付创建与回调、资料订单金额校验、RAG 转发。
- 前端闭环：`frontend/` 覆盖资料列表、详情、上传、订单、资料对话与支付入口。
- 多格式解析：`documents/loaders.py` 覆盖 PDF、Word、PPT、Excel、图片 OCR、音视频 ASR。
- 入库流水线：`ingestion/pipeline.py` 串联解析、切片、RAPTOR 建树、Milvus/Elasticsearch/Neo4j 写入。
- RAPTOR：`raptor/tree_index.py` 自底向上构建树状摘要节点，`raptor/retriever.py` 做粗召回和精准下钻。
- 三级意图路由：`routing/intent_router.py` 按“规则指令 -> 关键词匹配 -> Qwen 轻量模型”分发。
- 控制指令：`routing/commands.py` 支持 `/clear`、`/context`、`/agentic`。
- 精准块检索：`retrieval/precise.py` 面向简单问答返回紧凑叶子块。
- 混合检索：`retrieval/hybrid.py` 融合 Milvus 稠密检索和 Elasticsearch BM25，`retrieval/rrf.py` 做 RRF。
- 精排：`retrieval/reranker.py` 对接 BGE-Reranker。
- 知识图谱：`retrieval/neo4j_adapter.py` 保留 Neo4j 多跳查询 Cypher 调用位置。
- 复杂推理：`retrieval/agentic.py` 实现迭代检索。
- 评测闭环：`evaluation/` 包含规则过滤、LLM-as-Judge、Pairwise A/B、Bad Case 归因。
- 降本与记忆：`cost/model_policy.py`、`memory/layered_memory.py`、`knowledge/freshness.py`。

## Imported Modules

既有资料系统相关模块放在 `legacy/` 中，用于保留前后端接口和服务实现的上下文：

- Python RAG：`legacy/python-rag-service/`
- Java 资料系统：`legacy/java-material/`
- Java 用户/积分系统：`legacy/java-user/`、`legacy/java-rest-user/`
- 前端资料系统：`legacy/frontend-material/`

这些文件没有被原地修改，也不会影响当前运行服务。

## Deployment

部署步骤见 `docs/DEPLOYMENT.md`。本地 API 入口：

```bash
cd /home/sun/knowledge-rag-agent-platform
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
pip install -e .
cp .env.example .env
uvicorn rag_agent_platform.api.run:app --host 0.0.0.0 --port 8080

cd /home/sun/knowledge-rag-agent-platform/java-gateway
mvn spring-boot:run

cd /home/sun/knowledge-rag-agent-platform/frontend
npm install
npm run serve
```

## Static Check

```bash
python3 -m py_compile $(find src -name '*.py')
```

## Notes

`examples/offline_flow.py` 展示无外部依赖的离线流程。实际生产部署时，将 adapter 接入真实 Milvus、Elasticsearch、Neo4j、Qwen/vLLM、OCR、ASR 服务即可。

