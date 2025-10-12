"""BGE-large-zh 稠密向量客户端。

三层设计，与重排器一致：

- ``HTTPEmbeddingClient`` 调用兼容 OpenAI 格式的 ``/embeddings`` 端点
  （vLLM / TEI / Xinference 均暴露此接口）。
- ``LocalBGEEmbeddingClient`` 通过 ``FlagEmbedding`` 在进程内加载
  ``BAAI/bge-large-zh-v1.5`` 做本地推理。
- ``BGEEmbeddingClient`` 为 bootstrap 装配使用，优先调真实后端，归一化输出；
  向量服务不可达时降级到确定性 hash embedding 保证链路可离线运行。
"""

from __future__ import annotations

from typing import List, Protocol

from rag_agent_platform.integrations.http_json import OpenAICompatibleClient
from rag_agent_platform.storage.in_memory import HashEmbeddingClient


class EmbeddingBackend(Protocol):
    def embed_batch(self, texts: List[str]) -> List[List[float]]:
        """批量返回文本的稠密向量。"""


class HTTPEmbeddingClient:
    """兼容 OpenAI 格式的向量化端点（vLLM / TEI / Xinference）。"""

    def __init__(self, endpoint: str, model: str = "BAAI/bge-large-zh-v1.5", api_key: str | None = None):
        self.endpoint = endpoint
        self.model = model
        self.client = OpenAICompatibleClient(endpoint, api_key=api_key, timeout=30.0)

    def embed_batch(self, texts: List[str]) -> List[List[float]]:
        if not texts:
            return []
        return self.client.embeddings(self.model, texts)


class LocalBGEEmbeddingClient:
    """通过 FlagEmbedding 做进程内 BGE 向量化。"""

    def __init__(self, model: str = "BAAI/bge-large-zh-v1.5", use_fp16: bool = True):
        from FlagEmbedding import FlagModel

        self.model = FlagModel(
            model,
            query_instruction_for_retrieval="为这个句子生成表示以用于检索相关文章：",
            use_fp16=use_fp16,
        )

    def embed_batch(self, texts: List[str]) -> List[List[float]]:
        if not texts:
            return []
        vectors = self.model.encode(texts)
        return [list(map(float, vector)) for vector in vectors]


class BGEEmbeddingClient:
    """检索和 RAPTOR 索引统一使用的向量客户端。

    对外暴露 ``embed(text) -> List[float]`` 接口，内部通过真实后端批量计算；
    向量服务不可用时降级到确定性 hash embedding。
    """

    def __init__(self, backend: EmbeddingBackend | None = None, fallback: HashEmbeddingClient | None = None):
        self.backend = backend
        self.fallback = fallback or HashEmbeddingClient()

    def embed(self, text: str) -> List[float]:
        return self.embed_documents([text])[0]

    def embed_documents(self, texts: List[str]) -> List[List[float]]:
        if not texts:
            return []
        if self.backend is not None:
            try:
                vectors = self.backend.embed_batch(texts)
                if vectors and len(vectors) == len(texts):
                    return [self._normalize(vector) for vector in vectors]
            except Exception:
                pass
        return [self.fallback.embed(text) for text in texts]

    @staticmethod
    def _normalize(vector: List[float]) -> List[float]:
        norm = sum(value * value for value in vector) ** 0.5
        if norm == 0:
            return vector
        return [value / norm for value in vector]
