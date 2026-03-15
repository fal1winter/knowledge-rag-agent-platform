"""文档指纹去重，避免重复入库。

基于文件内容 SHA-256 指纹判断文档是否已入库：
- 相同内容的文件无论 URI 变化都不会重复摄入
- 内容变更后自动触发覆盖更新（upsert 语义）

存储后端可切换：内存（测试/单机）或 Redis（分布式部署）。
"""

from __future__ import annotations

import hashlib
import time
from dataclasses import dataclass, field
from typing import Dict, Optional, Protocol


@dataclass
class FingerprintRecord:
    """已入库文档的指纹记录。"""
    document_id: str
    fingerprint: str
    ingested_at: float  # Unix timestamp
    chunk_count: int = 0
    metadata: Dict = field(default_factory=dict)


class FingerprintStore(Protocol):
    """指纹存储抽象接口。"""

    def get(self, document_id: str) -> Optional[FingerprintRecord]:
        """按 document_id 查找已有指纹。"""

    def put(self, record: FingerprintRecord) -> None:
        """写入或更新指纹记录。"""

    def exists_by_fingerprint(self, fingerprint: str) -> Optional[str]:
        """按内容指纹查找是否存在相同内容的文档，返回 document_id 或 None。"""


class InMemoryFingerprintStore:
    """内存指纹存储，适合单机测试和开发。"""

    def __init__(self):
        self._by_id: Dict[str, FingerprintRecord] = {}
        self._by_fp: Dict[str, str] = {}  # fingerprint -> document_id

    def get(self, document_id: str) -> Optional[FingerprintRecord]:
        return self._by_id.get(document_id)

    def put(self, record: FingerprintRecord) -> None:
        self._by_id[record.document_id] = record
        self._by_fp[record.fingerprint] = record.document_id

    def exists_by_fingerprint(self, fingerprint: str) -> Optional[str]:
        return self._by_fp.get(fingerprint)


class RedisFingerprintStore:
    """Redis 指纹存储，适合分布式多实例部署。

    键设计：
    - ``dedup:id:{document_id}`` → JSON FingerprintRecord
    - ``dedup:fp:{fingerprint}`` → document_id
    TTL 默认 30 天，避免历史文档永久占用内存。
    """

    def __init__(self, redis_url: str = "redis://localhost:6379/0", ttl_seconds: int = 2592000):
        self.redis_url = redis_url
        self.ttl_seconds = ttl_seconds
        self._client = self._connect()

    def _connect(self):
        try:
            import redis
            return redis.from_url(self.redis_url, decode_responses=True)
        except Exception:
            return None

    def get(self, document_id: str) -> Optional[FingerprintRecord]:
        if self._client is None:
            return None
        import json
        raw = self._client.get(f"dedup:id:{document_id}")
        if raw is None:
            return None
        data = json.loads(raw)
        return FingerprintRecord(**data)

    def put(self, record: FingerprintRecord) -> None:
        if self._client is None:
            return
        import json
        payload = json.dumps({
            "document_id": record.document_id,
            "fingerprint": record.fingerprint,
            "ingested_at": record.ingested_at,
            "chunk_count": record.chunk_count,
            "metadata": record.metadata,
        })
        self._client.setex(f"dedup:id:{record.document_id}", self.ttl_seconds, payload)
        self._client.setex(f"dedup:fp:{record.fingerprint}", self.ttl_seconds, record.document_id)

    def exists_by_fingerprint(self, fingerprint: str) -> Optional[str]:
        if self._client is None:
            return None
        return self._client.get(f"dedup:fp:{fingerprint}")


class DocumentDeduplicator:
    """文档去重器：基于内容指纹判断是否需要入库。

    使用场景：
    1. 新文档 → 计算指纹 → 不存在 → 放行入库
    2. 已有文档内容未变 → 跳过，返回已有记录
    3. 已有文档内容变更 → 放行入库（覆盖更新）
    """

    def __init__(self, store: FingerprintStore | None = None):
        self.store = store or InMemoryFingerprintStore()

    def compute_fingerprint(self, content: str) -> str:
        """计算文本内容的 SHA-256 指纹。"""
        return hashlib.sha256(content.encode("utf-8")).hexdigest()

    def should_ingest(self, document_id: str, content: str) -> tuple[bool, Optional[FingerprintRecord]]:
        """判断文档是否需要入库。

        Returns:
            (should_ingest, existing_record)
            - (True, None): 全新文档
            - (True, record): 内容变更，需覆盖更新
            - (False, record): 内容未变，跳过
        """
        fingerprint = self.compute_fingerprint(content)

        # 检查同 ID 文档是否存在
        existing = self.store.get(document_id)
        if existing is not None:
            if existing.fingerprint == fingerprint:
                return False, existing  # 内容未变
            return True, existing  # 内容变更

        # 检查不同 ID 但相同内容
        dup_id = self.store.exists_by_fingerprint(fingerprint)
        if dup_id is not None:
            dup_record = self.store.get(dup_id)
            return False, dup_record  # 重复内容

        return True, None  # 全新文档

    def record_ingestion(self, document_id: str, content: str, chunk_count: int, metadata: Dict | None = None) -> FingerprintRecord:
        """入库完成后记录指纹。"""
        record = FingerprintRecord(
            document_id=document_id,
            fingerprint=self.compute_fingerprint(content),
            ingested_at=time.time(),
            chunk_count=chunk_count,
            metadata=metadata or {},
        )
        self.store.put(record)
        return record
