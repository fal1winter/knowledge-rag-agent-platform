"""独立项目运行时配置。"""

from dataclasses import dataclass, field
import os
from typing import List


@dataclass
class ModelConfig:
    intent_endpoint: str = os.getenv("QWEN_INTENT_ENDPOINT", "http://127.0.0.1:8001/v1")
    rewrite_endpoint: str = os.getenv("QWEN_REWRITE_ENDPOINT", "http://127.0.0.1:8000/v1")
    answer_endpoint: str = os.getenv("QWEN_ANSWER_ENDPOINT", "http://127.0.0.1:8002/v1")
    intent_model: str = os.getenv("QWEN_INTENT_MODEL", "Qwen2.5-1.5B-Instruct-QLoRA")
    rewrite_model: str = os.getenv("QWEN_REWRITE_MODEL", "Qwen2.5-7B-Instruct-QLoRA")
    answer_model: str = os.getenv("QWEN_ANSWER_MODEL", "Qwen2.5-7B-Instruct")
    embedding_endpoint: str = os.getenv("QWEN_EMBEDDING_ENDPOINT", "http://127.0.0.1:8003/v1")
    embedding_model: str = os.getenv("EMBEDDING_MODEL", "BAAI/bge-large-zh-v1.5")
    rerank_endpoint: str = os.getenv("RERANK_ENDPOINT", "http://127.0.0.1:8004/rerank")
    reranker_model: str = os.getenv("RERANKER_MODEL", "BAAI/bge-reranker-v2-m3")
    summarizer_endpoint: str = os.getenv("QWEN_SUMMARIZER_ENDPOINT", "http://127.0.0.1:8002/v1")
    summarizer_model: str = os.getenv("QWEN_SUMMARIZER_MODEL", "Qwen2.5-7B-Instruct")
    embedding_batch_size: int = 64


@dataclass
class RetrievalConfig:
    top_k_dense: int = 30
    top_k_sparse: int = 30
    final_top_k: int = 8
    rrf_k: int = 60
    raptor_branching_factor: int = 6
    raptor_max_levels: int = 5
    raptor_min_cluster_size: int = 3
    iterative_max_rounds: int = 3


@dataclass
class ServiceConfig:
    app_name: str = "knowledge-rag-agent-platform"
    environment: str = os.getenv("APP_ENV", "development")
    milvus_uri: str = os.getenv("MILVUS_URI", "http://127.0.0.1:19530")
    elasticsearch_url: str = os.getenv("ELASTICSEARCH_URL", "http://127.0.0.1:9200")
    neo4j_uri: str = os.getenv("NEO4J_URI", "bolt://127.0.0.1:7687")
    redis_url: str = os.getenv("REDIS_URL", "redis://127.0.0.1:6379/0")
    # 当生产中间件未连通时，是否启用进程内兜底实现（保证链路可独立运行/演示）。
    enable_local_fallback: bool = os.getenv("ENABLE_LOCAL_FALLBACK", "1") not in {"0", "false", "False"}
    ocr_endpoint: str = os.getenv("OCR_ENDPOINT", "http://127.0.0.1:8090/ocr")
    asr_endpoint: str = os.getenv("ASR_ENDPOINT", "http://127.0.0.1:8091/transcribe")
    allowed_formats: List[str] = field(
        default_factory=lambda: [
            "pdf",
            "doc",
            "docx",
            "ppt",
            "pptx",
            "xls",
            "xlsx",
            "csv",
            "txt",
            "md",
            "html",
            "png",
            "jpg",
            "jpeg",
            "wav",
            "mp3",
            "mp4",
            "mov",
        ]
    )
    models: ModelConfig = field(default_factory=ModelConfig)
    retrieval: RetrievalConfig = field(default_factory=RetrievalConfig)


def load_config() -> ServiceConfig:
    """从环境变量加载配置，使用确定性默认值。"""

    return ServiceConfig()

