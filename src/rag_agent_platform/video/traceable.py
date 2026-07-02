"""视频检索结果可追溯扩展。

在检索结果中添加视频溯源信息，支持跳转到具体时间点。
"""

from dataclasses import dataclass
from typing import Any, Dict, List

from rag_agent_platform.models import RetrievalHit


@dataclass
class VideoReference:
    """视频引用信息。"""

    video_id: str
    video_uri: str  # 原始视频路径/URL
    timestamp: float  # 秒
    duration: float = 5.0  # 片段时长
    event_index: int = 0
    thumbnail_uri: str | None = None  # 关键帧缩略图

    def to_playback_url(self, base_url: str = "") -> str:
        """生成可播放的视频链接（带时间戳）。"""
        # HTML5 video fragment: #t=start,end
        return f"{base_url}{self.video_uri}#t={self.timestamp},{self.timestamp + self.duration}"

    def to_dict(self) -> Dict[str, Any]:
        return {
            "video_id": self.video_id,
            "video_uri": self.video_uri,
            "timestamp": self.timestamp,
            "duration": self.duration,
            "event_index": self.event_index,
            "thumbnail_uri": self.thumbnail_uri,
            "playback_url": self.to_playback_url(),
        }


@dataclass
class TraceableVideoChunk:
    """可追溯的视频片段。"""

    chunk_id: str
    video_id: str
    video_uri: str
    text: str  # 检索文本（转写 + 场景描述）
    timestamp: float
    event_index: int
    scene_description: str = ""
    audio_text: str = ""
    screen_text: str = ""
    objects: List[str] = None
    thumbnail_base64: str | None = None

    def to_retrieval_hit(self, score: float, tenant_id: str) -> RetrievalHit:
        """转换为标准检索结果，携带完整溯源信息。"""
        return RetrievalHit(
            chunk_id=self.chunk_id,
            document_id=self.video_id,
            text=self.text,
            score=score,
            source="video_multimodal",
            tenant_id=tenant_id,
            citation={
                "video_id": self.video_id,
                "video_uri": self.video_uri,
                "timestamp": self.timestamp,
                "event_index": self.event_index,
                "playback_url": f"{self.video_uri}#t={self.timestamp}",
            },
            metadata={
                "type": "video_event",
                "scene": self.scene_description,
                "audio": self.audio_text,
                "screen_text": self.screen_text,
                "objects": self.objects or [],
                "thumbnail": self.thumbnail_base64,
            },
        )


def build_traceable_chunks_from_timeline(
    video_id: str,
    video_uri: str,
    timeline_events: List[Dict[str, Any]],
    save_thumbnails: bool = True,
) -> List[TraceableVideoChunk]:
    """
    从时间线构建可追溯的检索块。

    Args:
        video_id: 视频唯一标识
        video_uri: 视频原始路径/URL
        timeline_events: 时间线事件列表
        save_thumbnails: 是否保存关键帧缩略图

    Returns:
        可追溯的检索块列表
    """
    chunks = []

    for i, event in enumerate(timeline_events):
        # 合并文本
        text_parts = []
        if event.get("audio"):
            text_parts.append(f"语音: {event['audio']}")
        if event.get("scene"):
            text_parts.append(f"画面: {event['scene']}")
        if event.get("screen_text"):
            text_parts.append(f"文字: {event['screen_text']}")

        combined_text = " | ".join(text_parts)

        # 提取物体标签
        objects = [obj.get("label", "") for obj in event.get("objects", [])]

        chunk = TraceableVideoChunk(
            chunk_id=f"video:{video_id}:event:{i}",
            video_id=video_id,
            video_uri=video_uri,
            text=combined_text,
            timestamp=event["timestamp"],
            event_index=i,
            scene_description=event.get("scene", ""),
            audio_text=event.get("audio", ""),
            screen_text=event.get("screen_text", ""),
            objects=objects,
            thumbnail_base64=event.get("thumbnail_base64") if save_thumbnails else None,
        )

        chunks.append(chunk)

    return chunks


def enrich_retrieval_results_with_video_refs(
    hits: List[RetrievalHit], base_playback_url: str = ""
) -> List[Dict[str, Any]]:
    """
    为检索结果添加视频引用信息。

    Args:
        hits: 原始检索结果
        base_playback_url: 视频播放基础 URL

    Returns:
        增强后的结果，包含 video_reference 字段
    """
    enriched = []

    for hit in hits:
        result = {
            "chunk_id": hit.chunk_id,
            "text": hit.text,
            "score": hit.score,
            "source": hit.source,
        }

        # 如果是视频来源，添加引用信息
        if hit.source == "video_multimodal" and hit.citation:
            video_ref = VideoReference(
                video_id=hit.citation.get("video_id", ""),
                video_uri=hit.citation.get("video_uri", ""),
                timestamp=hit.citation.get("timestamp", 0),
                event_index=hit.citation.get("event_index", 0),
                thumbnail_uri=hit.metadata.get("thumbnail") if hit.metadata else None,
            )

            result["video_reference"] = video_ref.to_dict()
            result["playback_url"] = video_ref.to_playback_url(base_playback_url)

            # 添加元数据
            if hit.metadata:
                result["scene"] = hit.metadata.get("scene", "")
                result["audio"] = hit.metadata.get("audio", "")
                result["screen_text"] = hit.metadata.get("screen_text", "")
                result["objects"] = hit.metadata.get("objects", [])

        enriched.append(result)

    return enriched


class VideoChunkIndexer:
    """视频块索引器：将时间线事件分块入库，保留溯源信息。"""

    def __init__(self, dense_index, sparse_index, embedding_client):
        self.dense_index = dense_index
        self.sparse_index = sparse_index
        self.embedding_client = embedding_client

    def index_video_timeline(
        self,
        tenant_id: str,
        video_id: str,
        video_uri: str,
        timeline_events: List[Dict[str, Any]],
        metadata: Dict[str, Any] | None = None,
    ) -> int:
        """
        索引视频时间线。

        Args:
            tenant_id: 租户 ID
            video_id: 视频 ID
            video_uri: 视频路径/URL
            timeline_events: 时间线事件
            metadata: 视频级别元数据

        Returns:
            入库的块数量
        """
        # 1. 构建可追溯块
        traceable_chunks = build_traceable_chunks_from_timeline(
            video_id=video_id, video_uri=video_uri, timeline_events=timeline_events
        )

        if not traceable_chunks:
            return 0

        # 2. 向量化
        texts = [chunk.text for chunk in traceable_chunks]
        embeddings = self.embedding_client.embed(texts)

        # 3. 转换为标准检索块
        hits = [chunk.to_retrieval_hit(score=1.0, tenant_id=tenant_id) for chunk in traceable_chunks]

        # 4. 入库
        # Milvus 稠密检索
        self.dense_index.insert(
            tenant_id=tenant_id,
            chunks=hits,
            embeddings=embeddings,
        )

        # Elasticsearch BM25 稀疏检索
        self.sparse_index.insert(tenant_id=tenant_id, chunks=hits)

        return len(hits)


def create_video_summary_document(
    video_id: str,
    video_uri: str,
    timeline_events: List[Dict[str, Any]],
    full_transcript: str,
    metadata: Dict[str, Any],
) -> Dict[str, Any]:
    """
    创建视频概述文档（用于粗粒度检索）。

    结构：
    - 视频级别摘要
    - 完整转写文本
    - 关键事件列表（带时间戳）
    - 溯源信息

    Args:
        video_id: 视频 ID
        video_uri: 视频 URI
        timeline_events: 时间线事件
        full_transcript: 完整转写
        metadata: 元数据（时长、帧数等）

    Returns:
        概述文档
    """
    # 提取关键事件
    key_events = []
    for i, event in enumerate(timeline_events[::5]):  # 每5个取1个
        key_events.append(
            {
                "timestamp": event["timestamp"],
                "event_index": i * 5,
                "scene": event.get("scene", "")[:100],  # 截断
                "audio": event.get("audio", "")[:100],
            }
        )

    summary_doc = {
        "document_id": f"video_summary:{video_id}",
        "video_id": video_id,
        "video_uri": video_uri,
        "type": "video_summary",
        "text": f"""
视频ID: {video_id}
时长: {metadata.get('video_duration', 0):.1f}秒
事件数: {metadata.get('num_events', 0)}

完整转写:
{full_transcript}

关键时刻:
{chr(10).join(f"[{e['timestamp']:.1f}s] {e['scene']}" for e in key_events)}
        """.strip(),
        "metadata": metadata,
        "key_events": key_events,
        "citation": {"video_id": video_id, "video_uri": video_uri, "type": "summary"},
    }

    return summary_doc
