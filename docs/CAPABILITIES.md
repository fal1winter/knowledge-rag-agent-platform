# 能力清单

| 能力 | 对应模块 |
| --- | --- |
| 知识付费 RAG 对话平台 | `src/rag_agent_platform/api/app.py`、`generation/agent.py`、`java-gateway/`、`frontend/` |
| Java 业务网关 | `java-gateway/src/main/java/com/liang/knowledge/gateway` |
| 素材购买与支付 | `java-gateway/.../payment`、`frontend/src/service/paymentService.js`、`frontend/src/components/material/MaterialDetail.vue` |
| PDF/Word/PPT/Excel/图片/音频/视频解析 | `documents/loaders.py`、`bootstrap.py` OCR/ASR 客户端 |
| 从解析到索引的入库流水线 | `ingestion/pipeline.py`、`api/app.py`、`bootstrap.py` |
| RAPTOR 树状层级检索 | `raptor/tree_index.py`、`raptor/retriever.py` |
| 粗召回 + 精准下钻 | `raptor/retriever.py` |
| 三级意图路由 | `routing/intent_router.py` |
| `/clear`、`/context`、`/agentic` 控制指令 | `routing/commands.py`、`generation/agent.py` |
| 口语化改写与共指填充 | `routing/query_optimizer.py` |
| 双模型分层路由（本地 Qwen QLoRA + DeepSeek 硅基流动） | `routing/intent_router.py`、`routing/query_optimizer.py`、`generation/llm_client.py`、`cost/model_policy.py` |
| 简单问答精准块检索 | `retrieval/precise.py`、`generation/agent.py` |
| Neo4j 实体关系多跳查询 | `retrieval/neo4j_adapter.py` |
| 复杂推理迭代检索 | `retrieval/agentic.py` |
| Milvus 稠密 + Elasticsearch BM25 | `retrieval/milvus_adapter.py`、`retrieval/elasticsearch_adapter.py`、`retrieval/hybrid.py` |
| RRF 融合 | `retrieval/rrf.py` |
| BGE-Reranker 精排 | `retrieval/reranker.py` |
| RAG 评测闭环 | `evaluation/pipeline.py` |
| 空召回与引用问题规则过滤 | `evaluation/rules.py` |
| LLM-as-Judge 打分 | `evaluation/llm_judge.py` |
| Pairwise A/B 灰度验证 | `evaluation/pairwise_ab.py` |
| Bad Case 归因与人工审核队列 | `evaluation/badcase.py` |
| 轻量模型与 Embedding 跳过降本 | `cost/model_policy.py` |
| 静态知识与动态记忆分离 | `knowledge/freshness.py`、`memory/layered_memory.py` |
| 三层记忆与权重遗忘 | `memory/layered_memory.py` |
