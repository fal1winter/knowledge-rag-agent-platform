"""
统一 Rerank 服务
优先使用 SiliconFlow API，失败时降级到本地 ONNX 模型
"""
import os
import logging
import time
import requests
from typing import List, Dict, Optional
from dotenv import load_dotenv

# 加载环境变量
load_dotenv()

# 配置日志
logger = logging.getLogger(__name__)

# ==================== 配置 ====================

# SiliconFlow API 配置
SILICONFLOW_API_KEY = os.getenv("SILICONFLOW_API_KEY", "")
SILICONFLOW_RERANK_URL = "https://api.siliconflow.cn/v1/rerank"
SILICONFLOW_RERANK_MODEL = "BAAI/bge-reranker-v2-m3"

# 本地模型配置
USE_ONNX = os.getenv("USE_ONNX", "true").lower() == "true"
ONNX_MODEL_PATH = os.getenv("ONNX_MODEL_PATH", "./models/bge-reranker-base-onnx")

# 重试配置
API_TIMEOUT = 15  # API 超时时间（秒）
API_MAX_RETRIES = 2  # API 最大重试次数

# 全局变量
_local_model = None
_model_type = None  # "onnx" or "pytorch"
_api_available = True
_api_fail_count = 0
_last_api_check_time = 0
_loading = False
_load_failed = False

# ==================== 本地模型管理 ====================

def _load_onnx_model():
    """加载 ONNX 模型"""
    global _local_model, _model_type
    try:
        import onnxruntime as ort
        from transformers import AutoTokenizer

        logger.info(f"Loading ONNX rerank model from: {ONNX_MODEL_PATH}")

        onnx_file = os.path.join(ONNX_MODEL_PATH, "model.onnx")
        if not os.path.exists(onnx_file):
            logger.error(f"ONNX model file not found: {onnx_file}")
            return False

        session = ort.InferenceSession(onnx_file)
        tokenizer = AutoTokenizer.from_pretrained(ONNX_MODEL_PATH)

        _local_model = {
            "session": session,
            "tokenizer": tokenizer
        }
        _model_type = "onnx"
        logger.info("ONNX rerank model loaded successfully")
        return True

    except Exception as e:
        logger.error(f"Failed to load ONNX model: {e}", exc_info=True)
        return False


def _load_pytorch_model():
    """加载 PyTorch 模型"""
    global _local_model, _model_type
    try:
        from sentence_transformers import CrossEncoder
        logger.info("Loading PyTorch rerank model: BAAI/bge-reranker-base")
        _local_model = CrossEncoder("BAAI/bge-reranker-base", max_length=512)
        _model_type = "pytorch"
        logger.info("PyTorch rerank model loaded successfully")
        return True
    except Exception as e:
        logger.error(f"Failed to load PyTorch model: {e}")
        return False


def get_local_model():
    """获取本地模型（懒加载）"""
    global _local_model, _loading, _load_failed

    if _local_model is not None:
        return _local_model

    if _load_failed:
        logger.warning("Local rerank model load failed previously")
        return None

    if _loading:
        logger.info("Local rerank model is loading...")
        return None

    _loading = True

    # 优先尝试 ONNX
    if USE_ONNX:
        success = _load_onnx_model()
        if not success:
            logger.warning("ONNX loading failed, falling back to PyTorch")
            success = _load_pytorch_model()
    else:
        success = _load_pytorch_model()

    if not success:
        _load_failed = True

    _loading = False
    return _local_model


def rerank_local(query: str, results: List[Dict], top_k: int = 5) -> List[Dict]:
    """使用本地模型重排序"""
    model = get_local_model()

    if model is None:
        logger.warning("Local rerank model not available, returning original order")
        return results[:top_k]

    try:
        if _model_type == "onnx":
            # ONNX 推理
            import numpy as np

            pairs = [[query, r["content"]] for r in results]
            tokenizer = model["tokenizer"]
            session = model["session"]

            # Tokenize
            encoded = tokenizer(
                pairs,
                padding=True,
                truncation=True,
                max_length=512,
                return_tensors="np"
            )

            # 推理
            ort_inputs = {
                "input_ids": encoded["input_ids"].astype(np.int64),
                "attention_mask": encoded["attention_mask"].astype(np.int64)
            }
            ort_outputs = session.run(None, ort_inputs)
            scores = ort_outputs[0].flatten().tolist()

        else:
            # PyTorch 推理
            pairs = [[query, r["content"]] for r in results]
            scores = model.predict(pairs).tolist()

        # 添加分数并排序
        for i, result in enumerate(results):
            result["rerank_score"] = float(scores[i])

        reranked = sorted(results, key=lambda x: x["rerank_score"], reverse=True)
        return reranked[:top_k]

    except Exception as e:
        logger.error(f"Local rerank failed: {e}", exc_info=True)
        return results[:top_k]


# ==================== API 调用 ====================

def check_api_available() -> bool:
    """检查 API 是否可用"""
    global _api_available, _last_api_check_time

    current_time = time.time()
    if current_time - _last_api_check_time < 300:  # 5分钟
        return _api_available

    if not SILICONFLOW_API_KEY or SILICONFLOW_API_KEY == "":
        logger.warning("SiliconFlow API key not configured, using local model")
        _api_available = False
        _last_api_check_time = current_time
        return False

    _last_api_check_time = current_time
    return True


def rerank_api(query: str, results: List[Dict], top_k: int = 5) -> Optional[List[Dict]]:
    """使用 SiliconFlow API 重排序"""
    if not check_api_available():
        return None

    try:
        documents = [r["content"] for r in results]

        response = requests.post(
            SILICONFLOW_RERANK_URL,
            headers={
                "Authorization": f"Bearer {SILICONFLOW_API_KEY}",
                "Content-Type": "application/json"
            },
            json={
                "model": SILICONFLOW_RERANK_MODEL,
                "query": query,
                "documents": documents,
                "top_n": top_k
            },
            timeout=API_TIMEOUT
        )

        if response.status_code == 200:
            result = response.json()
            reranked = []

            for item in result["results"]:
                idx = item["index"]
                doc = results[idx].copy()
                doc["rerank_score"] = item["relevance_score"]
                reranked.append(doc)

            return reranked
        else:
            logger.warning(f"API rerank failed: {response.status_code} - {response.text}")
            return None

    except requests.exceptions.Timeout:
        logger.warning(f"API rerank timeout after {API_TIMEOUT}s")
        return None
    except Exception as e:
        logger.warning(f"API rerank error: {e}")
        return None


# ==================== 统一接口（API 优先 + 本地降级）====================

def rerank(query: str, results: List[Dict], top_k: int = 5) -> List[Dict]:
    """
    重排序（API 优先，失败时降级到本地）

    Args:
        query: 查询文本
        results: 检索结果列表
        top_k: 返回前 k 个结果

    Returns:
        重排序后的结果列表
    """
    global _api_fail_count

    if not results:
        return []

    # 尝试使用 API
    if check_api_available():
        start_time = time.time()
        reranked = rerank_api(query, results, top_k)

        if reranked is not None:
            elapsed = time.time() - start_time
            logger.info(f"✅ API rerank success: {len(results)} -> {len(reranked)} ({elapsed:.3f}s)")
            _api_fail_count = 0
            return reranked
        else:
            _api_fail_count += 1
            logger.warning(f"⚠️ API rerank failed (count: {_api_fail_count}), falling back to local")

    # 降级到本地模型
    start_time = time.time()
    reranked = rerank_local(query, results, top_k)
    elapsed = time.time() - start_time
    logger.info(f"🔄 Local rerank used: {len(results)} -> {len(reranked)} ({elapsed:.3f}s)")

    return reranked


def is_available() -> bool:
    """检查重排序服务是否可用"""
    return check_api_available() or get_local_model() is not None


def get_model_info() -> dict:
    """获取模型信息"""
    return {
        "api_model": SILICONFLOW_RERANK_MODEL,
        "local_model_type": _model_type,
        "api_configured": bool(SILICONFLOW_API_KEY),
        "api_available": _api_available,
        "api_fail_count": _api_fail_count,
        "local_model_loaded": _local_model is not None,
        "local_load_failed": _load_failed
    }


# 兼容旧接口
def get_model():
    """兼容旧接口"""
    return get_local_model()
