"""
统一 Embedding 服务
优先使用 SiliconFlow API (512维)，失败时降级到本地模型
"""
import os
import logging
import time
import requests
from typing import List, Optional
from dotenv import load_dotenv

# 加载环境变量
load_dotenv()

# 配置日志
logger = logging.getLogger(__name__)

# ==================== 配置 ====================

# SiliconFlow API 配置
SILICONFLOW_API_KEY = os.getenv("SILICONFLOW_API_KEY", "")
SILICONFLOW_API_URL = "https://api.siliconflow.cn/v1/embeddings"
SILICONFLOW_MODEL = "BAAI/bge-small-zh-v1.5"  # 512维，与本地模型一致

# 本地模型配置
os.environ['HF_ENDPOINT'] = 'https://hf-mirror.com'
from sentence_transformers import SentenceTransformer

LOCAL_MODEL_NAME = "BAAI/bge-small-zh-v1.5"
EMBEDDING_DIM = 512

# 重试配置
API_TIMEOUT = 10  # API 超时时间（秒）
API_MAX_RETRIES = 2  # API 最大重试次数

# 全局变量
_local_model = None
_api_available = True  # API 可用性标志
_api_fail_count = 0  # API 连续失败次数
_last_api_check_time = 0  # 上次检查 API 的时间

# ==================== 本地模型管理 ====================

def get_local_model():
    """获取本地模型（懒加载）"""
    global _local_model
    if _local_model is None:
        logger.info(f"Loading local embedding model: {LOCAL_MODEL_NAME}")
        _local_model = SentenceTransformer(LOCAL_MODEL_NAME)
        logger.info("Local embedding model loaded")
    return _local_model


def get_model():
    """预加载模型（供 model_service.py 启动时调用）"""
    return get_local_model()


def embed_local(text: str) -> List[float]:
    """使用本地模型生成 embedding"""
    if not text or not text.strip():
        return [0.0] * EMBEDDING_DIM

    try:
        model = get_local_model()
        vector = model.encode(text, normalize_embeddings=True)
        return vector.tolist()
    except Exception as e:
        logger.error(f"Local embedding failed: {e}", exc_info=True)
        raise


def embed_batch_local(texts: List[str]) -> List[List[float]]:
    """使用本地模型批量生成 embedding"""
    if not texts:
        return []

    try:
        # 处理空文本
        valid_texts = [t if t and t.strip() else " " for t in texts]

        model = get_local_model()
        logger.info(f"Local batch embedding {len(valid_texts)} texts")

        vectors = model.encode(
            valid_texts,
            normalize_embeddings=True,
            batch_size=64,
            show_progress_bar=False
        )

        return [v.tolist() for v in vectors]
    except Exception as e:
        logger.error(f"Local batch embedding failed: {e}", exc_info=True)
        raise


# ==================== API 调用 ====================

def check_api_available() -> bool:
    """检查 API 是否可用"""
    global _api_available, _last_api_check_time

    # 如果最近检查过（5分钟内），直接返回缓存结果
    current_time = time.time()
    if current_time - _last_api_check_time < 300:  # 5分钟
        return _api_available

    # 检查 API Key
    if not SILICONFLOW_API_KEY or SILICONFLOW_API_KEY == "":
        logger.warning("SiliconFlow API key not configured, using local model")
        _api_available = False
        _last_api_check_time = current_time
        return False

    _last_api_check_time = current_time
    return True


def embed_api(text: str) -> Optional[List[float]]:
    """使用 SiliconFlow API 生成 embedding"""
    if not check_api_available():
        return None

    try:
        response = requests.post(
            SILICONFLOW_API_URL,
            headers={
                "Authorization": f"Bearer {SILICONFLOW_API_KEY}",
                "Content-Type": "application/json"
            },
            json={
                "model": SILICONFLOW_MODEL,
                "input": [text]
            },
            timeout=API_TIMEOUT
        )

        if response.status_code == 200:
            result = response.json()
            vector = result["data"][0]["embedding"]
            return vector
        else:
            logger.warning(f"API embedding failed: {response.status_code} - {response.text}")
            return None

    except requests.exceptions.Timeout:
        logger.warning(f"API embedding timeout after {API_TIMEOUT}s")
        return None
    except Exception as e:
        logger.warning(f"API embedding error: {e}")
        return None


def embed_batch_api(texts: List[str]) -> Optional[List[List[float]]]:
    """使用 SiliconFlow API 批量生成 embedding"""
    if not check_api_available():
        return None

    try:
        response = requests.post(
            SILICONFLOW_API_URL,
            headers={
                "Authorization": f"Bearer {SILICONFLOW_API_KEY}",
                "Content-Type": "application/json"
            },
            json={
                "model": SILICONFLOW_MODEL,
                "input": texts
            },
            timeout=API_TIMEOUT * 2  # 批量请求给更多时间
        )

        if response.status_code == 200:
            result = response.json()
            vectors = [item["embedding"] for item in result["data"]]
            return vectors
        else:
            logger.warning(f"API batch embedding failed: {response.status_code} - {response.text}")
            return None

    except requests.exceptions.Timeout:
        logger.warning(f"API batch embedding timeout after {API_TIMEOUT * 2}s")
        return None
    except Exception as e:
        logger.warning(f"API batch embedding error: {e}")
        return None


# ==================== 统一接口（API 优先 + 本地降级）====================

def embed(text: str) -> List[float]:
    """
    生成 embedding（API 优先，失败时降级到本地）

    Args:
        text: 输入文本

    Returns:
        512维向量
    """
    global _api_fail_count

    # 尝试使用 API
    if check_api_available():
        start_time = time.time()
        vector = embed_api(text)

        if vector is not None:
            elapsed = time.time() - start_time
            logger.info(f"✅ API embedding success ({elapsed:.3f}s)")
            _api_fail_count = 0  # 重置失败计数
            return vector
        else:
            _api_fail_count += 1
            logger.warning(f"⚠️ API embedding failed (count: {_api_fail_count}), falling back to local")

    # 降级到本地模型
    start_time = time.time()
    vector = embed_local(text)
    elapsed = time.time() - start_time
    logger.info(f"🔄 Local embedding used ({elapsed:.3f}s)")

    return vector


def embed_batch(texts: List[str]) -> List[List[float]]:
    """
    批量生成 embedding（API 优先，失败时降级到本地）

    Args:
        texts: 文本列表

    Returns:
        向量列表
    """
    global _api_fail_count

    if not texts:
        return []

    # 尝试使用 API
    if check_api_available():
        start_time = time.time()
        vectors = embed_batch_api(texts)

        if vectors is not None:
            elapsed = time.time() - start_time
            logger.info(f"✅ API batch embedding success: {len(texts)} texts ({elapsed:.3f}s)")
            _api_fail_count = 0  # 重置失败计数
            return vectors
        else:
            _api_fail_count += 1
            logger.warning(f"⚠️ API batch embedding failed (count: {_api_fail_count}), falling back to local")

    # 降级到本地模型
    start_time = time.time()
    vectors = embed_batch_local(texts)
    elapsed = time.time() - start_time
    logger.info(f"🔄 Local batch embedding used: {len(texts)} texts ({elapsed:.3f}s)")

    return vectors


def get_dimension() -> int:
    """获取向量维度"""
    return EMBEDDING_DIM


def get_status() -> dict:
    """获取服务状态"""
    return {
        "dimension": EMBEDDING_DIM,
        "api_configured": bool(SILICONFLOW_API_KEY),
        "api_available": _api_available,
        "api_fail_count": _api_fail_count,
        "local_model_loaded": _local_model is not None,
        "model": SILICONFLOW_MODEL
    }
