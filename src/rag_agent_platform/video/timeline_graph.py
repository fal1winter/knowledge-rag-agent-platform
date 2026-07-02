"""视频时序关系图谱扩展。

将视频事件时间线存入 Neo4j，支持时序查询和上下文扩展检索。
"""

from dataclasses import dataclass
from typing import Any, Dict, List

from rag_agent_platform.retrieval.neo4j_adapter import Neo4jGraphRetriever, LocalGraphRetriever
from rag_agent_platform.video.timeline import VideoEvent, VideoTimeline


@dataclass
class VideoTimelineGraph:
    """视频时序关系图谱管理器。"""

    graph_retriever: Neo4jGraphRetriever | LocalGraphRetriever

    def index_timeline(self, tenant_id: str, video_id: str, timeline: VideoTimeline) -> None:
        """
        将视频时间线存入图谱。

        节点类型：
        - VideoEvent: 事件节点
        - Video: 视频节点

        关系类型：
        - FOLLOWED_BY: 时序关系（event1 -> event2）
        - BELONGS_TO: 事件属于视频（event -> video）
        """
        entities = []

        # 创建视频节点
        video_entity = {
            "name": f"video:{video_id}",
            "properties": {
                "type": "Video",
                "video_id": video_id,
                "duration": timeline.duration,
                "num_events": len(timeline.events),
            },
            "relations": [],
        }

        # 创建事件节点并建立时序关系
        for i, event in enumerate(timeline.events):
            event_id = f"video:{video_id}:event:{i}"

            relations = [
                # 事件属于视频
                {
                    "target": f"video:{video_id}",
                    "type": "BELONGS_TO",
                    "properties": {"timestamp": event.timestamp},
                }
            ]

            # 时序关系：当前事件 -> 下一个事件
            if i < len(timeline.events) - 1:
                next_event_id = f"video:{video_id}:event:{i+1}"
                duration_to_next = timeline.events[i + 1].timestamp - event.timestamp
                relations.append(
                    {
                        "target": next_event_id,
                        "type": "FOLLOWED_BY",
                        "properties": {
                            "duration": duration_to_next,
                            "order": i,
                        },
                    }
                )

            # 物体关系（如果有）
            for obj in event.objects[:5]:  # 限制数量
                obj_name = obj.get("label", "")
                if obj_name:
                    relations.append(
                        {
                            "target": f"object:{obj_name}",
                            "type": "CONTAINS",
                            "properties": {"confidence": obj.get("confidence", 1.0)},
                        }
                    )

            event_entity = {
                "name": event_id,
                "properties": {
                    "type": "VideoEvent",
                    "video_id": video_id,
                    "timestamp": event.timestamp,
                    "scene": event.scene_description,
                    "audio": event.audio_text,
                    "screen_text": event.screen_text,
                    "scene_change_score": event.scene_change_score,
                },
                "relations": relations,
            }

            entities.append(event_entity)

        entities.append(video_entity)

        # 批量入库
        self.graph_retriever.upsert_entities(tenant_id, entities)

    def get_event_context(
        self, tenant_id: str, video_id: str, event_index: int, context_size: int = 2
    ) -> Dict[str, Any]:
        """
        获取事件的上下文（前后事件）。

        Args:
            tenant_id: 租户 ID
            video_id: 视频 ID
            event_index: 事件索引
            context_size: 上下文大小（前后各 N 个事件）

        Returns:
            包含 before、current、after 的字典
        """
        event_id = f"video:{video_id}:event:{event_index}"

        # 通过图谱查询前后关系
        paths = self.graph_retriever.multi_hop_search(
            tenant_id=tenant_id, entities=[event_id], hops=context_size
        )

        # 解析路径
        before_events = []
        after_events = []

        for path in paths:
            if "FOLLOWED_BY" in path.relationships:
                # 后续事件
                after_events.append(
                    {"event_id": path.nodes[-1], "evidence": path.evidence, "score": path.score}
                )

        return {"before": before_events, "current": event_id, "after": after_events}

    def search_by_scene(self, tenant_id: str, scene_keywords: List[str]) -> List[Dict[str, Any]]:
        """
        根据场景关键词搜索事件。

        Note: 这需要在 Neo4j 中建立全文索引，这里提供接口定义。
        """
        # 简化实现：通过实体名称匹配
        # 生产环境应使用 Neo4j 全文索引
        pass

    def search_by_object(self, tenant_id: str, object_name: str, hops: int = 1) -> List[Dict[str, Any]]:
        """
        查找包含指定物体的所有事件。

        Args:
            tenant_id: 租户 ID
            object_name: 物体名称
            hops: 跳数

        Returns:
            事件列表
        """
        object_id = f"object:{object_name}"
        paths = self.graph_retriever.multi_hop_search(tenant_id=tenant_id, entities=[object_id], hops=hops)

        events = []
        for path in paths:
            if "CONTAINS" in path.relationships:
                # 反向关系：物体 <- 事件
                for node in path.nodes:
                    if node.startswith("video:") and ":event:" in node:
                        events.append({"event_id": node, "path": path.evidence, "score": path.score})

        return events


def expand_retrieval_with_timeline(
    graph: VideoTimelineGraph,
    tenant_id: str,
    matched_event_id: str,
    context_size: int = 2,
) -> Dict[str, Any]:
    """
    扩展检索结果：为匹配的事件添加时序上下文。

    使用场景：
    用户查询匹配到某个视频片段，返回该片段的前后上下文，
    并生成带时间戳的视频链接。

    Args:
        graph: 图谱管理器
        tenant_id: 租户 ID
        matched_event_id: 匹配到的事件 ID（格式: "video:xxx:event:N"）
        context_size: 上下文大小

    Returns:
        扩展后的检索结果，包含时序上下文和视频跳转链接
    """
    # 解析事件 ID
    parts = matched_event_id.split(":")
    if len(parts) < 4:
        return {}

    video_id = parts[1]
    event_index = int(parts[3])

    # 获取上下文
    context = graph.get_event_context(tenant_id, video_id, event_index, context_size)

    return {
        "video_id": video_id,
        "matched_event": matched_event_id,
        "context": context,
        "video_clip_url": f"video://{video_id}#t={event_index * 2}",  # 假设每2秒一个事件
    }
