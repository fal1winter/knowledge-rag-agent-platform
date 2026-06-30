"""静态公开知识时效性巡检。

功能点：
- 按 TTL 检测过期知识记录
- 多级过期严重度（warning / stale / critical）
- 后台定时巡检调度（可选）
- 巡检结果通知回调
- 巡检报告统计摘要
- 基于 source_url 的主动拉取变更检测（无需 web search）
"""

from dataclasses import dataclass, field
from datetime import datetime, timedelta
import logging
import threading
from typing import Callable, Dict, Iterable, List, Optional, Protocol

logger = logging.getLogger(__name__)


@dataclass
class KnowledgeRecord:
    """知识库中的一条文档记录。

    Attributes:
        source_uri: 可访问的更新源链接，巡检时直接拉取对比内容变化
        content_hash: 入库时计算的内容指纹（SHA-256），用于判断源是否有更新
        check_interval_hours: 该文档的检查间隔（小时），不同文档可设置不同频率
    """
    document_id: str
    tenant_id: str
    title: str
    updated_at: datetime
    source_uri: str
    content_hash: str = ""
    check_interval_hours: float = 24.0
    last_checked_at: Optional[datetime] = None
    metadata: Dict = field(default_factory=dict)


class Severity:
    """过期严重度等级。"""
    WARNING = "warning"       # 即将过期（>80% TTL）
    STALE = "stale"           # 已过期
    CRITICAL = "critical"     # 严重过期（>2x TTL）


@dataclass
class FreshnessIssue:
    """一条时效性问题记录。"""
    document_id: str
    tenant_id: str
    title: str
    reason: str
    age_days: int
    ttl_days: int
    severity: str
    action: str
    source_uri: str = ""


@dataclass
class InspectionReport:
    """巡检报告摘要。"""
    inspected_at: datetime
    total_records: int
    healthy_count: int
    warning_count: int
    stale_count: int
    critical_count: int
    issues: List[FreshnessIssue] = field(default_factory=list)

    @property
    def health_ratio(self) -> float:
        """健康比例 (0.0 ~ 1.0)。"""
        if self.total_records == 0:
            return 1.0
        return self.healthy_count / self.total_records

    def summary(self) -> str:
        return (
            f"巡检完成: {self.total_records} 条记录, "
            f"健康 {self.healthy_count}, 警告 {self.warning_count}, "
            f"过期 {self.stale_count}, 严重 {self.critical_count} "
            f"(健康率 {self.health_ratio:.1%})"
        )


class KnowledgeRepository(Protocol):
    """知识库存储协议。"""

    def list_public_records(self) -> Iterable[KnowledgeRecord]:
        """列出静态公开知识记录。"""

    def mark_stale(self, document_id: str, reason: str) -> None:
        """标记记录需要刷新。"""


# 通知回调类型：接收巡检报告
NotifyCallback = Callable[[InspectionReport], None]


class FreshnessInspector:
    """知识时效性巡检器。

    支持多级过期判定和后台定时巡检。

    过期判定逻辑：
    - age > ttl * 2.0 → CRITICAL（建议立即下线或重新索引）
    - age > ttl       → STALE（建议刷新源文档）
    - age > ttl * 0.8 → WARNING（即将过期，提前预警）
    """

    def __init__(
        self,
        repository: KnowledgeRepository,
        max_age_days: int = 90,
        warning_threshold: float = 0.8,
        critical_multiplier: float = 2.0,
        notify_callback: Optional[NotifyCallback] = None,
    ):
        self.repository = repository
        self.max_age_days = max_age_days
        self.warning_threshold = warning_threshold
        self.critical_multiplier = critical_multiplier
        self.notify_callback = notify_callback
        self._scheduler_timer: Optional[threading.Timer] = None
        self._running = False

    def inspect(self, now: Optional[datetime] = None) -> InspectionReport:
        """执行一次巡检，返回报告。"""
        now = now or datetime.utcnow()
        issues: List[FreshnessIssue] = []
        total = 0
        warning_count = 0
        stale_count = 0
        critical_count = 0

        for record in self.repository.list_public_records():
            total += 1
            age_days = (now - record.updated_at).days
            declared_ttl = int(record.metadata.get("ttl_days", self.max_age_days))

            severity = self._classify_severity(age_days, declared_ttl)
            if severity is None:
                continue

            action = self._recommend_action(severity)
            issue = FreshnessIssue(
                document_id=record.document_id,
                tenant_id=record.tenant_id,
                title=record.title,
                reason=f"知识文档已 {age_days} 天未更新 (TTL={declared_ttl}d)",
                age_days=age_days,
                ttl_days=declared_ttl,
                severity=severity,
                action=action,
                source_uri=record.source_uri,
            )
            issues.append(issue)

            if severity == Severity.CRITICAL:
                critical_count += 1
                self.repository.mark_stale(record.document_id, issue.reason)
            elif severity == Severity.STALE:
                stale_count += 1
                self.repository.mark_stale(record.document_id, issue.reason)
            else:
                warning_count += 1

        healthy_count = total - warning_count - stale_count - critical_count

        report = InspectionReport(
            inspected_at=now,
            total_records=total,
            healthy_count=healthy_count,
            warning_count=warning_count,
            stale_count=stale_count,
            critical_count=critical_count,
            issues=issues,
        )

        logger.info(report.summary())
        if self.notify_callback and issues:
            try:
                self.notify_callback(report)
            except Exception as e:
                logger.warning("巡检通知回调失败: %s", e)

        return report

    def _classify_severity(self, age_days: int, ttl_days: int) -> Optional[str]:
        """根据年龄和 TTL 判定严重度。"""
        if age_days > ttl_days * self.critical_multiplier:
            return Severity.CRITICAL
        elif age_days > ttl_days:
            return Severity.STALE
        elif age_days > ttl_days * self.warning_threshold:
            return Severity.WARNING
        return None

    def _recommend_action(self, severity: str) -> str:
        """根据严重度推荐处理动作。"""
        actions = {
            Severity.WARNING: "schedule_refresh",
            Severity.STALE: "refresh_or_reindex",
            Severity.CRITICAL: "offline_and_reindex",
        }
        return actions.get(severity, "review")

    # --- 后台定时巡检 ---

    def start_scheduled(self, interval_hours: float = 6.0) -> None:
        """启动后台定时巡检。

        Args:
            interval_hours: 巡检间隔（小时）
        """
        if self._running:
            logger.warning("定时巡检已在运行")
            return
        self._running = True
        logger.info("启动知识时效性定时巡检, 间隔 %.1f 小时", interval_hours)
        self._schedule_next(interval_hours)

    def stop_scheduled(self) -> None:
        """停止后台定时巡检。"""
        self._running = False
        if self._scheduler_timer:
            self._scheduler_timer.cancel()
            self._scheduler_timer = None
        logger.info("已停止知识时效性定时巡检")

    def _schedule_next(self, interval_hours: float) -> None:
        """安排下次巡检。"""
        if not self._running:
            return
        self._scheduler_timer = threading.Timer(
            interval_hours * 3600,
            self._run_scheduled,
            args=(interval_hours,),
        )
        self._scheduler_timer.daemon = True
        self._scheduler_timer.start()

    def _run_scheduled(self, interval_hours: float) -> None:
        """执行定时巡检并重新调度。"""
        try:
            self.inspect()
        except Exception as e:
            logger.error("定时巡检执行失败: %s", e)
        finally:
            self._schedule_next(interval_hours)

    def inspect_tenant(self, tenant_id: str, now: Optional[datetime] = None) -> InspectionReport:
        """对单个租户执行巡检（过滤其他租户记录）。"""
        now = now or datetime.utcnow()
        issues: List[FreshnessIssue] = []
        total = 0
        warning_count = stale_count = critical_count = 0

        for record in self.repository.list_public_records():
            if record.tenant_id != tenant_id:
                continue
            total += 1
            age_days = (now - record.updated_at).days
            declared_ttl = int(record.metadata.get("ttl_days", self.max_age_days))

            severity = self._classify_severity(age_days, declared_ttl)
            if severity is None:
                continue

            issue = FreshnessIssue(
                document_id=record.document_id,
                tenant_id=record.tenant_id,
                title=record.title,
                reason=f"知识文档已 {age_days} 天未更新 (TTL={declared_ttl}d)",
                age_days=age_days,
                ttl_days=declared_ttl,
                severity=severity,
                action=self._recommend_action(severity),
                source_uri=record.source_uri,
            )
            issues.append(issue)
            if severity == Severity.CRITICAL:
                critical_count += 1
            elif severity == Severity.STALE:
                stale_count += 1
            else:
                warning_count += 1

        return InspectionReport(
            inspected_at=now,
            total_records=total,
            healthy_count=total - warning_count - stale_count - critical_count,
            warning_count=warning_count,
            stale_count=stale_count,
            critical_count=critical_count,
            issues=issues,
        )


# ---------------------------------------------------------------------------
# 基于 source_url 的主动更新检测
# ---------------------------------------------------------------------------


@dataclass
class SourceUpdateResult:
    """单条文档的源更新检测结果。"""
    document_id: str
    source_url: str
    changed: bool
    old_fingerprint: str
    new_fingerprint: str = ""
    error: str = ""


@dataclass
class SourceUpdateReport:
    """批量源更新检测报告。"""
    checked_at: datetime
    total_checked: int
    changed_count: int
    error_count: int
    results: List[SourceUpdateResult] = field(default_factory=list)

    def summary(self) -> str:
        return (
            f"源更新检测: {self.total_checked} 条, "
            f"有变更 {self.changed_count}, 错误 {self.error_count}"
        )


class ContentFingerprintStore(Protocol):
    """文档内容指纹存储协议。"""

    def get_fingerprint(self, document_id: str) -> Optional[str]:
        """获取文档上次入库时的内容指纹。"""

    def set_fingerprint(self, document_id: str, fingerprint: str) -> None:
        """更新文档内容指纹。"""


class SourceUpdateChecker:
    """基于 source_url 的知识更新检测器。

    核心思路：
    - 用户上传文档时附带一个可访问的 source_url（如内部文档系统 API 链接）
    - 入库时计算内容 SHA-256 指纹并存储
    - 巡检时直接 HTTP GET 该 URL 获取最新内容，对比指纹
    - 指纹变化 → 标记需要增量重入库；无变化 → 更新 last_checked_at

    优势：
    - 不依赖 web search（费用高、结果不稳定）
    - 精准对比同一来源的变更，不会误判
    - 可针对不同文档设置不同检查频率
    """

    def __init__(
        self,
        repository: KnowledgeRepository,
        fingerprint_store: ContentFingerprintStore,
        http_timeout: float = 15.0,
        on_change_callback: Optional[Callable[[KnowledgeRecord, str], None]] = None,
    ):
        """
        Args:
            repository: 知识库存储
            fingerprint_store: 内容指纹存储
            http_timeout: 拉取源 URL 的超时时间
            on_change_callback: 检测到变更时的回调，参数为 (record, new_content)
        """
        self.repository = repository
        self.fingerprint_store = fingerprint_store
        self.http_timeout = http_timeout
        self.on_change_callback = on_change_callback

    def check_updates(
        self,
        records: Optional[Iterable[KnowledgeRecord]] = None,
    ) -> SourceUpdateReport:
        """批量检测文档源是否有更新。

        仅检查 metadata 中包含 source_url 的记录。
        没有 source_url 的记录跳过（退化为基于 TTL 的时间巡检）。

        Args:
            records: 待检测的记录列表，默认为全部公开记录
        """
        import hashlib
        import urllib.request
        import urllib.error

        if records is None:
            records = self.repository.list_public_records()

        results: List[SourceUpdateResult] = []
        changed_count = 0
        error_count = 0

        for record in records:
            source_url = record.metadata.get("source_url") or record.source_uri
            if not source_url or not source_url.startswith(("http://", "https://")):
                continue

            old_fingerprint = self.fingerprint_store.get_fingerprint(record.document_id) or ""

            try:
                content = self._fetch_content(source_url)
                new_fingerprint = hashlib.sha256(content.encode("utf-8")).hexdigest()
            except Exception as e:
                logger.warning(
                    "拉取源 URL 失败: document_id=%s, url=%s, error=%s",
                    record.document_id, source_url, e,
                )
                results.append(SourceUpdateResult(
                    document_id=record.document_id,
                    source_url=source_url,
                    changed=False,
                    old_fingerprint=old_fingerprint,
                    error=str(e),
                ))
                error_count += 1
                continue

            changed = bool(old_fingerprint) and new_fingerprint != old_fingerprint

            if changed:
                changed_count += 1
                self.fingerprint_store.set_fingerprint(record.document_id, new_fingerprint)
                self.repository.mark_stale(record.document_id, "源内容已变更，需重新入库")
                logger.info(
                    "检测到源更新: document_id=%s, title=%s",
                    record.document_id, record.title,
                )
                if self.on_change_callback:
                    try:
                        self.on_change_callback(record, content)
                    except Exception as e:
                        logger.warning("变更回调执行失败: %s", e)
            elif not old_fingerprint:
                # 首次记录指纹（新入库文档）
                self.fingerprint_store.set_fingerprint(record.document_id, new_fingerprint)

            results.append(SourceUpdateResult(
                document_id=record.document_id,
                source_url=source_url,
                changed=changed,
                old_fingerprint=old_fingerprint,
                new_fingerprint=new_fingerprint,
            ))

        report = SourceUpdateReport(
            checked_at=datetime.utcnow(),
            total_checked=len(results),
            changed_count=changed_count,
            error_count=error_count,
            results=results,
        )
        logger.info(report.summary())
        return report

    def _fetch_content(self, url: str) -> str:
        """拉取 URL 内容并返回文本。"""
        import urllib.request
        import urllib.error

        req = urllib.request.Request(url, method="GET")
        with urllib.request.urlopen(req, timeout=self.http_timeout) as resp:
            return resp.read().decode("utf-8", errors="replace")
