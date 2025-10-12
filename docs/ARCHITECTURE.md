# Architecture

This project is an isolated knowledge-payment RAG Agent platform. External
infrastructure is represented by adapters, so Milvus, Elasticsearch, Neo4j,
Qwen, OCR, and ASR can be wired in without requiring those services during
local inspection.

## Main Flow

0. `frontend` calls `java-gateway` for user identity, credits, material purchase, payment, and RAG chat forwarding.
1. `documents.loaders` parses PDF, Word, PPT, Excel, images, audio, and video.
2. `documents.chunking` creates paragraph-aware semantic chunks.
3. `ingestion.pipeline` writes leaf chunks and RAPTOR nodes to retrieval stores.
4. `raptor.tree_index` builds bottom-up clustered summary nodes.
5. `routing.intent_router` applies three-stage routing:
   control command -> keyword rule -> lightweight model.
6. `retrieval.precise` serves simple QA with compact leaf-block retrieval.
7. `retrieval.hybrid` calls Milvus dense retrieval and Elasticsearch BM25,
   fuses with RRF, then reranks with BGE.
8. `retrieval.neo4j_adapter` handles entity-relation multi-hop queries.
9. `retrieval.agentic` performs iterative retrieval for complex reasoning.
10. `generation.agent` orchestrates route, retrieval, and grounded answer.
11. `evaluation.pipeline` runs rule filters, LLM-as-Judge, A/B comparison, and
    bad-case attribution.
12. `memory.layered_memory` separates short-term, session summary, and long-term
    user memory with weighted forgetting.
13. `knowledge.freshness` inspects stale public knowledge and marks it for
    refresh/reindex.

## Imported Existing Modules

The `legacy/` folder contains existing material/RAG source modules kept as
integration context. They are not imported by default and do not modify running
services.


## Deployment

See `DEPLOYMENT.md` for local API startup, environment variables, external service wiring, and process manager setup.

## Gateway And Frontend

`java-gateway` keeps the Java business entrypoint aligned with the original system: user credits are delegated to the copied user service context, material purchase is completed through material-order callbacks, and Alipay/WeChat payment providers expose SDK-based create and notify paths. `frontend` contains the minimal Vue shell for material list, upload, purchase, orders, and material chat.
