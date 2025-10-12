"""
模型服务 - FastAPI
专注于模型推理：Embedding 和 Reranker
端口: 5052
"""
import os
import logging
from typing import List, Dict
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
import uvicorn

# 加载 .env 文件
from dotenv import load_dotenv
load_dotenv()

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# 导入服务
import embedding_service
import rerank_service

# 创建 FastAPI 应用
app = FastAPI(
    title="RAG Model Service",
    description="Embedding 和 Reranker 模型推理服务",
    version="1.0.0"
)

# ==================== 请求模型 ====================

class EmbedRequest(BaseModel):
    text: str

class EmbedBatchRequest(BaseModel):
    texts: List[str]

class RerankRequest(BaseModel):
    query: str
    documents: List[str]
    top_k: int = 5

# ==================== 健康检查 ====================

@app.get("/health")
async def health():
    """健康检查"""
    return {
        "status": "ok",
        "service": "model-service",
        "embedding_loaded": embedding_service.get_model() is not None,
        "reranker_loaded": rerank_service.get_model() is not None
    }

@app.get("/model/info")
async def model_info():
    """模型信息"""
    return {
        "embedding": embedding_service.get_status() if hasattr(embedding_service, 'get_status') else {
            "model": "BAAI/bge-small-zh-v1.5",
            "dimension": 512,
            "loaded": True
        },
        "reranker": rerank_service.get_model_info() if hasattr(rerank_service, 'get_model_info') else {
            "model": "BAAI/bge-reranker-base",
            "loaded": rerank_service.get_model() is not None
        }
    }

# ==================== Embedding 接口 ====================

@app.post("/embed")
async def embed(request: EmbedRequest):
    """
    单文本 Embedding

    Args:
        text: 输入文本

    Returns:
        vector: 512 维向量
    """
    try:
        vector = embedding_service.embed(request.text)
        return {
            "success": True,
            "vector": vector,
            "dimension": len(vector)
        }
    except Exception as e:
        logger.error(f"Embedding error: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/embed/batch")
async def embed_batch(request: EmbedBatchRequest):
    """
    批量 Embedding

    Args:
        texts: 文本列表

    Returns:
        vectors: 向量列表
    """
    try:
        if not request.texts:
            raise HTTPException(status_code=400, detail="texts cannot be empty")

        vectors = embedding_service.embed_batch(request.texts)
        return {
            "success": True,
            "vectors": vectors,
            "count": len(vectors),
            "dimension": len(vectors[0]) if vectors else 0
        }
    except Exception as e:
        logger.error(f"Batch embedding error: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))

# ==================== Reranker 接口 ====================

@app.post("/rerank")
async def rerank(request: RerankRequest):
    """
    重排序

    Args:
        query: 查询文本
        documents: 文档列表
        top_k: 返回前 k 个结果

    Returns:
        scores: 重排序分数列表
        indices: 排序后的索引列表
    """
    try:
        if not request.documents:
            raise HTTPException(status_code=400, detail="documents cannot be empty")

        # 构建结果列表
        results = [{"content": doc, "chunk_index": i} for i, doc in enumerate(request.documents)]

        # 重排序
        reranked = rerank_service.rerank(request.query, results, top_k=request.top_k)

        # 提取分数和索引
        scores = [r.get("rerank_score", 0) for r in reranked]
        indices = [r.get("chunk_index", 0) for r in reranked]

        return {
            "success": True,
            "scores": scores,
            "indices": indices,
            "count": len(scores)
        }
    except Exception as e:
        logger.error(f"Rerank error: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/rerank/available")
async def rerank_available():
    """检查重排序服务是否可用"""
    return {
        "available": rerank_service.is_available(),
        "info": rerank_service.get_model_info() if hasattr(rerank_service, 'get_model_info') else {}
    }

# ==================== 启动服务 ====================

if __name__ == "__main__":
    port = int(os.getenv("MODEL_SERVICE_PORT", "5052"))
    workers = int(os.getenv("MODEL_SERVICE_WORKERS", "2"))

    logger.info(f"Starting Model Service on port {port} with {workers} workers")

    # 预加载模型
    logger.info("Preloading models...")
    embedding_service.get_model()
    rerank_service.get_model()
    logger.info("Models loaded successfully")

    # 启动服务
    uvicorn.run(
        "model_service:app",
        host="0.0.0.0",
        port=port,
        workers=workers,
        log_level="info",
        access_log=True
    )
