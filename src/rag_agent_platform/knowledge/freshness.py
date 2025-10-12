"""静态公开知识时效性巡检。"""

from dataclasses import dataclass, field
from datetime import datetime, timedelta
import threading
from typing import Callable, Dict, Iterable, List, Optional, Protocol


@dataclass
class KnowledgeRecord:
    document_id: str
    tenant_id: str
    title: str
    updated_at: datetime
    source_uri: str
    metadata: Dict


@dataclass
class FreshnessIssue:
    document_id: str
    reason: str
    age_days: int
    action: str


class KnowledgeRepository(Protocol):
    def list_public_records(self) -> Iterable[KnowledgeRecord]:
        """列出静态公开知识记录。"""

    def mark_stale(self, document_id: str, reason: str) -> None:
        """标记记录需要刷新。"""


class FreshnessInspector:
    def __init__(self, repository: KnowledgeRepository, max_age_days: int = 90):
        self.repository = repository
        self.max_age_days = max_age_days

    def inspect(self, now: datetime | None = None) -> List[FreshnessIssue]:
        now = now or datetime.utcnow()
        issues: List[FreshnessIssue] = []
        for record in self.repository.list_public_records():
            age_days = (now - record.updated_at).days
            declared_ttl = int(record.metadata.get("ttl_days", self.max_age_days))
            if age_days > declared_ttl:
                issue = FreshnessIssue(
                    document_id=record.document_id,
                    reason=f"public knowledge older than {declared_ttl} days",
                    age_days=age_days,
                    action="refresh_or_reindex",
                )
                self.repository.mark_stale(record.document_id, issue.reason)
                issues.append(issue)
        return issues

