"""
检索服务 - FastAPI（异步版本）
专注于检索和 RAG 对话
端口: 5051
"""
import os
import json
import logging
import asyncio
from typing import List, Dict, Optional
from fastapi import FastAPI, HTTPException
from fastapi.responses import StreamingResponse
from pydantic import BaseModel
import uvicorn
import httpx

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# 导入服务
import vector_service
import es_service
from document_parser import DocumentParser

# 创建 FastAPI 应用
app = FastAPI(
    title="RAG Search Service",
    description="混合检索和 RAG 对话服务",
    version="2.0.0"
)

# 模型服务地址
MODEL_SERVICE_URL = os.getenv("MODEL_SERVICE_URL", "http://localhost:5052")

# HTTP 客户端（复用连接）
http_client = httpx.AsyncClient(timeout=120.0)

# 文档解析器
doc_parser = DocumentParser()

# ==================== 请求模型 ====================

class VectorizeRequest(BaseModel):
    file_url: str
    file_type: str

class SearchRequest(BaseModel):
    query: str
    top_k: int = 5
    strategy: str = "hybrid"  # hybrid, vector, keyword

class ChatRequest(BaseModel):
    question: str
    history: List[Dict] = []
    user_id: Optional[int] = None
    session_id: Optional[str] = None
    material_ids: Optional[List[int]] = None

# ==================== 模型服务客户端 ====================

class ModelServiceClient:
    """模型服务客户端"""

    @staticmethod
    async def embed(text: str) -> List[float]:
        """调用模型服务获取 Embedding"""
        try:
            response = await http_client.post(
                f"{MODEL_SERVICE_URL}/embed",
                json={"text": text}
            )
            response.raise_for_status()
            data = response.json()
            return data["vector"]
        except Exception as e:
            logger.error(f"Model service embed error: {e}")
            raise HTTPException(status_code=500, detail=f"Embedding failed: {e}")

    @staticmethod
    async def embed_batch(texts: List[str]) -> List[List[float]]:
        """批量 Embedding"""
        try:
            response = await http_client.post(
                f"{MODEL_SERVICE_URL}/embed/batch",
                json={"texts": texts}
            )
            response.raise_for_status()
            data = response.json()
            return data["vectors"]
        except Exception as e:
            logger.error(f"Model service embed_batch error: {e}")
            raise HTTPException(status_code=500, detail=f"Batch embedding failed: {e}")

    @staticmethod
    async def rerank(query: str, documents: List[str], top_k: int = 5) -> List[float]:
        """重排序"""
        try:
            response = await http_client.post(
                f"{MODEL_SERVICE_URL}/rerank",
                json={"query": query, "documents": documents, "top_k": top_k}
            )
            response.raise_for_status()
            data = response.json()
            return data["scores"]
        except Exception as e:
            logger.error(f"Model service rerank error: {e}")
            # 降级：返回空列表
            return []

model_client = ModelServiceClient()

# ==================== 混合检索实现 ====================

async def hybrid_search_async(
    material_id: Optional[int],
    query: str,
    top_k: int = 5,
    material_ids: Optional[List[int]] = None,
    use_rerank: bool = True
) -> List[Dict]:
    """
    异步混合检索
    """
    try:
        # 1. 并发执行：Embedding + 向量检索 + 关键词检索
        async def vector_search():
            query_emb = await model_client.embed(query)
            return vector_service.search(
                material_id=material_id,
                query_embedding=query_emb,
                top_k=20,
                material_ids=material_ids
            )

        async def keyword_search():
            if not es_service.is_available():
                return []
            if material_id is not None:
                return es_service.search(material_id, query, top_k=20)
            elif material_ids:
                return es_service.search_multi_materials(material_ids, query, top_k=20)
            return []

        # 并发执行
        vector_results, keyword_results = await asyncio.gather(
            vector_search(),
            keyword_search()
        )

        logger.info(f"Vector: {len(vector_results)}, Keyword: {len(keyword_results)}")

        # 2. 如果没有关键词结果，只用向量检索
        if not keyword_results:
            if use_rerank and len(vector_results) > 0:
                # 重排序
                contents = [r["content"] for r in vector_results[:10]]
                scores = await model_client.rerank(query, contents, top_k=top_k)

                if scores:
                    for i, score in enumerate(scores):
                        vector_results[i]["rerank_score"] = score
                    vector_results = sorted(vector_results[:len(scores)],
                                          key=lambda x: x.get("rerank_score", 0),
                                          reverse=True)
                    return vector_results[:top_k]

            return vector_results[:top_k]

        # 3. RRF 融合
        merged = reciprocal_rank_fusion(vector_results, keyword_results, k=60)

        # 4. 重排序
        if use_rerank and len(merged) > 0:
            candidates = merged[:10]
            contents = [r["content"] for r in candidates]
            scores = await model_client.rerank(query, contents, top_k=top_k)

            if scores:
                for i, score in enumerate(scores):
                    candidates[i]["rerank_score"] = score
                candidates = sorted(candidates,
                                  key=lambda x: x.get("rerank_score", 0),
                                  reverse=True)
                return candidates[:top_k]

        return merged[:top_k]

    except Exception as e:
        logger.error(f"Hybrid search error: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))

def reciprocal_rank_fusion(
    vector_results: List[Dict],
    keyword_results: List[Dict],
    k: int = 60
) -> List[Dict]:
    """RRF 融合算法"""
    scores = {}
    chunk_map = {}

    # 向量检索结果
    for rank, result in enumerate(vector_results):
        chunk_key = (result.get("material_id", 0), result["chunk_index"])
        rrf_score = 1.0 / (k + rank + 1)

        if chunk_key not in scores:
            scores[chunk_key] = 0.0
            chunk_map[chunk_key] = result

        scores[chunk_key] += rrf_score

    # 关键词检索结果
    for rank, result in enumerate(keyword_results):
        chunk_key = (result.get("material_id", 0), result["chunk_index"])
        rrf_score = 1.0 / (k + rank + 1)

        if chunk_key not in scores:
            scores[chunk_key] = 0.0
            chunk_map[chunk_key] = result

        scores[chunk_key] += rrf_score

    # 排序
    sorted_chunks = sorted(scores.items(), key=lambda x: x[1], reverse=True)

    # 构建结果
    merged = []
    for chunk_key, rrf_score in sorted_chunks:
        result = chunk_map[chunk_key].copy()
        result["rrf_score"] = rrf_score
        result["score"] = rrf_score
        merged.append(result)

    return merged

# ==================== API 接口 ====================

@app.get("/health")
async def health():
    """健康检查"""
    # 检查模型服务
    try:
        response = await http_client.get(f"{MODEL_SERVICE_URL}/health", timeout=5.0)
        model_service_ok = response.status_code == 200
    except:
        model_service_ok = False

    return {
        "status": "ok",
        "service": "search-service",
        "model_service": "ok" if model_service_ok else "unavailable",
        "milvus": "ok" if vector_service.get_client() else "unavailable",
        "elasticsearch": "ok" if es_service.is_available() else "unavailable"
    }

@app.post("/api/material/{material_id}/vectorize")
async def vectorize_material(material_id: int, request: VectorizeRequest):
    """解析文档并向量化"""
    try:
        logger.info(f"Vectorize material {material_id}, type={request.file_type}")

        # 1. 解析文档
        chunks = doc_parser.parse(request.file_url, request.file_type)
        if not chunks:
            raise HTTPException(status_code=400, detail="文档解析失败")

        # 2. 批量 Embedding（调用模型服务）
        contents = [c['content'] for c in chunks]
        embeddings = await model_client.embed_batch(contents)

        # 3. 存入 Milvus
        vector_service.insert_chunks(material_id, chunks, embeddings)

        # 4. 存入 ElasticSearch
        try:
            if es_service.is_available():
                es_service.insert_chunks(material_id, chunks)
                logger.info(f"Chunks inserted to ES for material {material_id}")
        except Exception as es_error:
            logger.error(f"ES insert failed: {es_error}")

        return {
            "success": True,
            "chunk_count": len(chunks),
            "material_id": material_id
        }

    except Exception as e:
        logger.error(f"Vectorize error: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/api/material/{material_id}/search")
async def search_material(material_id: int, request: SearchRequest):
    """混合检索"""
    try:
        if request.strategy == "hybrid":
            results = await hybrid_search_async(
                material_id=material_id,
                query=request.query,
                top_k=request.top_k,
                use_rerank=True
            )
        elif request.strategy == "vector":
            query_emb = await model_client.embed(request.query)
            results = vector_service.search(material_id, query_emb, request.top_k)
        elif request.strategy == "keyword":
            results = es_service.search(material_id, request.query, request.top_k)
        else:
            raise HTTPException(status_code=400, detail="Invalid strategy")

        return {
            "success": True,
            "results": results,
            "strategy": request.strategy
        }

    except Exception as e:
        logger.error(f"Search error: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/api/material/{material_id}/chat/stream")
async def chat_stream(material_id: int, request: ChatRequest):
    """流式 RAG 对话"""

    async def generate():
        try:
            # 1. 混合检索
            actual_material_id = None if material_id == 0 else material_id
            results = await hybrid_search_async(
                material_id=actual_material_id,
                query=request.question,
                top_k=5,
                material_ids=request.material_ids,
                use_rerank=True
            )

            if not results:
                yield f"data: {json.dumps({'type': 'complete', 'data': {'answer': '抱歉，在资料中没有找到相关内容。', 'sources': []}}, ensure_ascii=False)}\n\n"
                return

            # 2. 构建上下文
            context = "\n\n".join([f"[片段{i+1}] {r['content']}" for i, r in enumerate(results)])

            # 3. 调用 LLM（这里简化，实际应该调用 OpenRouter）
            # TODO: 实现 LLM 流式调用
            answer = f"基于检索到的 {len(results)} 个相关片段，这里是回答..."

            # 4. 返回结果
            sources = [
                {
                    "chunk_index": r["chunk_index"],
                    "content": r["content"][:200] + "..." if len(r["content"]) > 200 else r["content"],
                    "relevance": round(r.get("rerank_score", r.get("rrf_score", r.get("score", 0))), 3),
                    "material_id": r.get("material_id")
                }
                for r in results
            ]

            yield f"data: {json.dumps({'type': 'complete', 'data': {'answer': answer, 'sources': sources}}, ensure_ascii=False)}\n\n"

        except Exception as e:
            logger.error(f"Chat stream error: {e}", exc_info=True)
            yield f"data: {json.dumps({'type': 'error', 'message': str(e)}, ensure_ascii=False)}\n\n"

    return StreamingResponse(generate(), media_type="text/event-stream")

@app.delete("/api/material/{material_id}")
async def delete_material(material_id: int):
    """删除资料"""
    try:
        vector_service.delete_by_material_id(material_id)

        if es_service.is_available():
            es_service.delete_by_material_id(material_id)

        return {"success": True}
    except Exception as e:
        logger.error(f"Delete error: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/api/material/{material_id}/stats")
async def get_stats(material_id: int):
    """获取统计信息"""
    try:
        count = vector_service.get_chunk_count(material_id)
        return {
            "success": True,
            "material_id": material_id,
            "chunk_count": count
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

# ==================== 启动服务 ====================

@app.on_event("startup")
async def startup_event():
    """启动时初始化"""
    logger.info("Initializing services...")
    vector_service.get_client()
    logger.info("Services initialized")

@app.on_event("shutdown")
async def shutdown_event():
    """关闭时清理"""
    await http_client.aclose()
    logger.info("HTTP client closed")

if __name__ == "__main__":
    port = int(os.getenv("SEARCH_SERVICE_PORT", "5051"))
    workers = int(os.getenv("SEARCH_SERVICE_WORKERS", "4"))

    logger.info(f"Starting Search Service on port {port} with {workers} workers")

    uvicorn.run(
        "search_service:app",
        host="0.0.0.0",
        port=port,
        workers=workers,
        log_level="info",
        access_log=True
    )
