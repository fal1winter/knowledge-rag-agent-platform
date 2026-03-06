"""RAPTOR 自底向上向量聚类构建树状索引。"""

from dataclasses import dataclass
import hashlib
import json
import math
from typing import Dict, Iterable, List, Protocol

from rag_agent_platform.integrations.http_json import OpenAICompatibleClient
from rag_agent_platform.models import Chunk


class EmbeddingClient(Protocol):
    def embed(self, text: str) -> List[float]:
        """返回文本的稠密向量表示。"""


class Summarizer(Protocol):
    def summarize(self, texts: List[str], max_words: int = 180) -> str:
        """将聚类后的子块摘要为父节点主题。"""


@dataclass
class RaptorConfig:
    branching_factor: int = 6
    max_levels: int = 5
    min_cluster_size: int = 3
    kmeans_iterations: int = 18


class RaptorTreeBuilder:
    """自底向上构建层次化切片树。"""

    def __init__(self, embeddings: EmbeddingClient, summarizer: Summarizer, config: RaptorConfig | None = None):
        self.embeddings = embeddings
        self.summarizer = summarizer
        self.config = config or RaptorConfig()

    def build(self, leaf_chunks: Iterable[Chunk]) -> List[Chunk]:
        """自底向上迭代构建：叶子节点 → 聚类 → 摘要生成父节点 → 重复至顶。"""
        all_nodes: List[Chunk] = []
        current_level = [self._with_vector(chunk) for chunk in leaf_chunks]
        all_nodes.extend(current_level)

        level = 1
        while len(current_level) >= self.config.min_cluster_size and level <= self.config.max_levels:
            clusters = self._cluster(current_level)
            parents: List[Chunk] = []
            for cluster_index, children in enumerate(clusters):
                if len(children) < self.config.min_cluster_size:
                    continue
                summary = self.summarizer.summarize([child.text for child in children])
                parent_id = self._parent_id(children, level, cluster_index)
                parent = Chunk(
                    chunk_id=parent_id,
                    document_id=children[0].document_id,
                    tenant_id=children[0].tenant_id,
                    text=summary,
                    level=level,
                    child_ids=[child.chunk_id for child in children],
                    metadata={
                        "node_type": "raptor_summary",
                        "child_count": len(children),
                        "source_documents": sorted({c.document_id for c in children}),
                        "cluster_method": "deterministic_kmeans_cosine",
                        "summarizer": self.summarizer.__class__.__name__,
                    },
                )
                parent.vector = self.embeddings.embed(parent.text)
                for child in children:
                    child.parent_id = parent.chunk_id
                parents.append(parent)
            if not parents:
                break
            all_nodes.extend(parents)
            current_level = parents
            level += 1
        return all_nodes

    def _with_vector(self, chunk: Chunk) -> Chunk:
        if chunk.vector is None:
            chunk.vector = self.embeddings.embed(chunk.text)
        return chunk

    def _cluster(self, chunks: List[Chunk]) -> List[List[Chunk]]:
        """基于余弦相似度的确定性 k-means 聚类（不依赖随机初始化）。"""
        if len(chunks) <= self.config.branching_factor:
            return [chunks]
        k = max(1, math.ceil(len(chunks) / max(1, self.config.branching_factor)))
        k = min(k, max(1, len(chunks) // self.config.min_cluster_size))
        vectors = [self._normalize(chunk.vector or []) for chunk in chunks]
        centroids = self._initial_centroids(vectors, k)
        assignments = [0] * len(chunks)
        for _ in range(self.config.kmeans_iterations):
            changed = False
            for idx, vector in enumerate(vectors):
                cluster_id = max(range(k), key=lambda cid: self._dot(vector, centroids[cid]))
                if assignments[idx] != cluster_id:
                    assignments[idx] = cluster_id
                    changed = True
            centroids = self._recompute_centroids(vectors, assignments, k)
            if not changed:
                break
        clusters = [[] for _ in range(k)]
        for chunk, cluster_id in zip(chunks, assignments):
            clusters[cluster_id].append(chunk)
        return self._rebalance([cluster for cluster in clusters if cluster])

    def _initial_centroids(self, vectors: List[List[float]], k: int) -> List[List[float]]:
        centroids = [vectors[0]]
        while len(centroids) < k:
            next_vector = max(vectors, key=lambda vector: min(1.0 - self._dot(vector, c) for c in centroids))
            centroids.append(next_vector)
        return centroids

    def _recompute_centroids(self, vectors: List[List[float]], assignments: List[int], k: int) -> List[List[float]]:
        grouped: List[List[List[float]]] = [[] for _ in range(k)]
        for vector, cluster_id in zip(vectors, assignments):
            grouped[cluster_id].append(vector)
        centroids = []
        for group in grouped:
            if not group:
                centroids.append([0.0] * (len(vectors[0]) if vectors else 0))
                continue
            width = max(len(v) for v in group)
            summed = [0.0] * width
            for vector in group:
                for idx, value in enumerate(vector):
                    summed[idx] += value
            centroids.append(self._normalize([value / len(group) for value in summed]))
        return centroids

    def _rebalance(self, clusters: List[List[Chunk]]) -> List[List[Chunk]]:
        small = [chunk for cluster in clusters if len(cluster) < self.config.min_cluster_size for chunk in cluster]
        kept = [cluster for cluster in clusters if len(cluster) >= self.config.min_cluster_size]
        for chunk in small:
            if not kept:
                kept.append([chunk])
            else:
                min(kept, key=len).append(chunk)
        return kept

    def _normalize(self, vector: List[float]) -> List[float]:
        if not vector:
            return []
        norm = sum(value * value for value in vector) ** 0.5
        if norm == 0:
            return [0.0 for _ in vector]
        return [value / norm for value in vector]

    def _dot(self, left: List[float], right: List[float]) -> float:
        return sum(a * b for a, b in zip(left, right))

    def _parent_id(self, children: List[Chunk], level: int, cluster_index: int) -> str:
        digest = hashlib.sha1("|".join(c.chunk_id for c in children).encode("utf-8")).hexdigest()[:12]
        return f"{children[0].document_id}:raptor:L{level}:{cluster_index}:{digest}"


class DeepSeekRaptorSummarizer:
    """通过 DeepSeek（硅基流动 SiliconFlow）生成 RAPTOR 父节点摘要。

    RAPTOR 摘要属于复杂生成任务，路由到 DeepSeek 大模型。
    API 不可达时降级到抽取式摘要。
    """

    def __init__(self, endpoint: str, api_key: str = "", model: str = "deepseek-ai/DeepSeek-V3", fallback: Summarizer | None = None):
        self.endpoint = endpoint
        self.model = model
        self.client = OpenAICompatibleClient(endpoint, api_key=api_key or None, timeout=90.0) if endpoint else None
        self.fallback = fallback or ExtractiveSummarizer()

    def summarize(self, texts: List[str], max_words: int = 180) -> str:
        if self.client is None:
            return self.fallback.summarize(texts, max_words)
        payload = {
            "max_words": max_words,
            "chunks": texts,
            "instruction": "Summarize the shared topic of these document chunks for hierarchical retrieval. Preserve entities, numbers, and section themes.",
        }
        messages = [
            {"role": "system", "content": "You create concise RAPTOR parent-node summaries for RAG retrieval."},
            {"role": "user", "content": json.dumps(payload, ensure_ascii=False)},
        ]
        try:
            summary = self.client.chat(self.model, messages, temperature=0.0)
        except Exception:
            return self.fallback.summarize(texts, max_words)
        words = summary.split()
        return summary if len(words) <= max_words else " ".join(words[:max_words])


class QwenRaptorSummarizer:
    """通过本地 Qwen 模型生成 RAPTOR 父节点摘要（备用，仅离线测试使用）。"""

    def __init__(self, endpoint: str, model: str = "Qwen2.5-7B-Instruct", api_key: str | None = None, fallback: Summarizer | None = None):
        self.endpoint = endpoint
        self.model = model
        self.client = OpenAICompatibleClient(endpoint, api_key=api_key, timeout=60.0) if endpoint else None
        self.fallback = fallback or ExtractiveSummarizer()

    def summarize(self, texts: List[str], max_words: int = 180) -> str:
        if self.client is None:
            return self.fallback.summarize(texts, max_words)
        payload = {
            "max_words": max_words,
            "chunks": texts,
            "instruction": "Summarize the shared topic of these document chunks for hierarchical retrieval. Preserve entities, numbers, and section themes.",
        }
        messages = [
            {"role": "system", "content": "You create concise RAPTOR parent-node summaries for RAG retrieval."},
            {"role": "user", "content": json.dumps(payload, ensure_ascii=False)},
        ]
        try:
            summary = self.client.chat(self.model, messages, temperature=0.0)
        except Exception:
            return self.fallback.summarize(texts, max_words)
        words = summary.split()
        return summary if len(words) <= max_words else " ".join(words[:max_words])


class ExtractiveSummarizer:
    """无模型端点时的本地抽取式摘要降级。"""

    def summarize(self, texts: List[str], max_words: int = 180) -> str:
        sentences: List[str] = []
        for text in texts:
            normalized = text.replace("\n", " ").replace("。", "。|").replace(".", ".|")
            for sentence in normalized.split("|"):
                cleaned = sentence.strip()
                if cleaned:
                    sentences.append(cleaned)
        selected = sentences[: min(6, len(sentences))]
        joined = " ".join(selected) if selected else " ".join(t.strip() for t in texts if t.strip())
        words = joined.split()
        return joined if len(words) <= max_words else " ".join(words[:max_words])


class InMemoryRaptorStore:
    """用于测试和离线演示的内存树存储。"""

    def __init__(self):
        self.nodes: Dict[str, Chunk] = {}
        self.children_by_parent: Dict[str, List[str]] = {}

    def upsert_many(self, chunks: Iterable[Chunk]) -> None:
        for chunk in chunks:
            self.nodes[chunk.chunk_id] = chunk
            if chunk.child_ids:
                self.children_by_parent[chunk.chunk_id] = list(chunk.child_ids)

    def get(self, chunk_id: str) -> Chunk | None:
        return self.nodes.get(chunk_id)

    def children(self, parent_id: str) -> List[Chunk]:
        return [self.nodes[cid] for cid in self.children_by_parent.get(parent_id, []) if cid in self.nodes]
