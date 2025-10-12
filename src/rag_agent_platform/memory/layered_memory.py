"""三层用户记忆与加权遗忘机制。"""

from dataclasses import replace
from datetime import datetime, timedelta
import hashlib
from typing import Dict, List

from rag_agent_platform.models import MemoryItem


class LayeredMemoryStore:
    """分离短期、会话摘要、长期三层用户记忆。"""

    LAYERS = ("short_term", "session_summary", "long_term")

    def __init__(self):
        self.items: Dict[str, MemoryItem] = {}

    def remember(self, tenant_id: str, user_id: str, content: str, layer: str, weight: float, metadata: Dict | None = None) -> MemoryItem:
        if layer not in self.LAYERS:
            raise ValueError(f"Unknown memory layer: {layer}")
        memory_id = hashlib.sha1(f"{tenant_id}:{user_id}:{layer}:{content}".encode("utf-8")).hexdigest()
        item = MemoryItem(
            memory_id=memory_id,
            tenant_id=tenant_id,
            user_id=user_id,
            content=content,
            layer=layer,
            weight=max(0.0, min(1.0, weight)),
            metadata=metadata or {},
        )
        self.items[memory_id] = item
        return item

    def recall(self, tenant_id: str, user_id: str, limit: int = 12) -> List[MemoryItem]:
        candidates = [
            item
            for item in self.items.values()
            if item.tenant_id == tenant_id and item.user_id == user_id and item.weight > 0.1
        ]
        return sorted(candidates, key=lambda item: (item.weight, item.updated_at), reverse=True)[:limit]

    def decay(self, now: datetime | None = None) -> None:
        now = now or datetime.utcnow()
        for memory_id, item in list(self.items.items()):
            age_days = max(0, (now - item.updated_at).days)
            layer_factor = {"short_term": 0.22, "session_summary": 0.08, "long_term": 0.02}[item.layer]
            decayed_weight = item.weight * ((1 - layer_factor) ** age_days)
            if decayed_weight < 0.05:
                del self.items[memory_id]
            else:
                self.items[memory_id] = replace(item, weight=decayed_weight)

    def promote_high_value(self, tenant_id: str, user_id: str, threshold: float = 0.82) -> List[MemoryItem]:
        promoted = []
        for item in list(self.items.values()):
            if item.tenant_id == tenant_id and item.user_id == user_id and item.layer == "session_summary" and item.weight >= threshold:
                promoted.append(
                    self.remember(
                        tenant_id,
                        user_id,
                        item.content,
                        layer="long_term",
                        weight=min(1.0, item.weight + 0.08),
                        metadata={**item.metadata, "promoted_from": item.memory_id},
                    )
                )
        return promoted

    def clear_session(self, tenant_id: str, user_id: str) -> None:
        for memory_id, item in list(self.items.items()):
            if item.tenant_id == tenant_id and item.user_id == user_id and item.layer in {"short_term", "session_summary"}:
                del self.items[memory_id]

    def prune_context_window(self, tenant_id: str, user_id: str, max_chars: int = 5000) -> List[MemoryItem]:
        selected = []
        total = 0
        for item in self.recall(tenant_id, user_id, limit=100):
            if total + len(item.content) > max_chars:
                continue
            selected.append(item)
            total += len(item.content)
        return selected

