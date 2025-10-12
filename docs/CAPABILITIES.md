# Capabilities

| Capability | Modules |
| --- | --- |
| Knowledge-payment RAG conversation platform | `src/rag_agent_platform/api/app.py`, `generation/agent.py`, `java-gateway/`, `frontend/` |
| Java business gateway | `java-gateway/src/main/java/com/liang/knowledge/gateway` |
| Material purchase and payment | `java-gateway/.../payment`, `frontend/src/service/paymentService.js`, `frontend/src/components/material/MaterialDetail.vue` |
| PDF/Word/PPT/Excel/image/audio/video parsing | `documents/loaders.py`, `bootstrap.py` OCR/ASR clients |
| Ingestion pipeline from parser to indexes | `ingestion/pipeline.py`, `api/app.py`, `bootstrap.py` |
| RAPTOR tree hierarchical retrieval | `raptor/tree_index.py`, `raptor/retriever.py` |
| Coarse recall + precise drill-down | `raptor/retriever.py` |
| Three-stage intent routing | `routing/intent_router.py` |
| `/clear`, `/context`, `/agentic` control commands | `routing/commands.py`, `generation/agent.py` |
| Colloquial rewrite and coreference fill | `routing/query_optimizer.py` |
| Qwen2.5 lightweight model integration | `routing/intent_router.py`, `routing/query_optimizer.py`, `generation/llm_client.py`, `cost/model_policy.py` |
| Simple QA precise block retrieval | `retrieval/precise.py`, `generation/agent.py` |
| Entity relation query via Neo4j multi-hop | `retrieval/neo4j_adapter.py` |
| Complex reasoning iterative retrieval | `retrieval/agentic.py` |
| Milvus dense + Elasticsearch BM25 | `retrieval/milvus_adapter.py`, `retrieval/elasticsearch_adapter.py`, `retrieval/hybrid.py` |
| RRF fusion | `retrieval/rrf.py` |
| BGE-Reranker precision rerank | `retrieval/reranker.py` |
| RAG evaluation closed loop | `evaluation/pipeline.py` |
| Rule filters for empty recall and citation problems | `evaluation/rules.py` |
| LLM-as-Judge scoring | `evaluation/llm_judge.py` |
| Pairwise A/B gray validation | `evaluation/pairwise_ab.py` |
| Bad Case attribution and manual review queue | `evaluation/badcase.py` |
| Cost reduction by lightweight models and embedding skip | `cost/model_policy.py` |
| Static knowledge vs dynamic memory separation | `knowledge/freshness.py`, `memory/layered_memory.py` |
| Three-layer memory and weighted forgetting | `memory/layered_memory.py` |

