"""视频 RAPTOR 树状索引。

提供两种构建策略：

1. Sequential RAPTOR（序列索引，默认推荐）：
   - 按时间顺序分块，保持视频的线性结构
   - 层级：L0（事件）→ L1（场景30s）→ L2（章节2min）→ L3（整体）
   - 适合：时间导航、章节查询、连续播放

2. Clustering RAPTOR（聚类索引）：
   - 按语义相似度聚类，发现跨时间的主题关联
   - 层级：L0（事件）→ L1（场景主题）→ L2（章节主题）→ L3（整体）
   - 适合：主题查询、跨章节检索

优势：
- 支持多粒度检索（粗查找 + 细下钻）
- 自动发现视频的层次结构
- 适应不同长度的视频
"""

from dataclasses import dataclass
from typing import List, Dict, Any, Literal

import numpy as np
from sklearn.cluster import KMeans

from rag_agent_platform.video.timeline import VideoEvent, VideoTimeline


@dataclass
class VideoRaptorNode:
    """RAPTOR 树节点。"""

    node_id: str
    level: int  # 0=叶子, 1+=摘要层
    summary: str  # 节点摘要文本
    time_range: tuple[float, float]  # (start, end) 时间范围
    children_ids: List[str]  # 子节点 ID
    embeddings: np.ndarray | None = None  # 节点向量
    metadata: Dict[str, Any] | None = None

    def to_dict(self) -> dict:
        return {
            "node_id": self.node_id,
            "level": self.level,
            "summary": self.summary,
            "time_range": self.time_range,
            "children_ids": self.children_ids,
            "metadata": self.metadata or {}
        }


class SequentialVideoRaptorBuilder:
    """基于时间序列的 RAPTOR 构建器（推荐）。

    按时间顺序分块，保持视频的线性结构和连续性。
    """

    def __init__(
        self,
        embedding_client,
        summarizer_llm,
        scene_window: float = 30.0,    # L1 场景窗口（秒）
        chapter_window: float = 120.0,  # L2 章节窗口（秒）
        max_levels: int = 3
    ):
        self.embedding_client = embedding_client
        self.summarizer_llm = summarizer_llm
        self.scene_window = scene_window
        self.chapter_window = chapter_window
        self.max_levels = max_levels

    def build(self, timeline: VideoTimeline, video_id: str) -> List[VideoRaptorNode]:
        """
        构建基于时间序列的 RAPTOR 树。

        流程：
        1. L0: 将每个事件作为叶子节点（按时间排列）
        2. L1: 按固定时间窗口合并为场景（如每30秒）
        3. L2: 按固定时间窗口合并为章节（如每2分钟）
        4. L3: 合并为整个视频总结

        Returns:
            所有层级的节点列表
        """
        all_nodes = []

        # L0: 叶子节点（原始事件，按时间排列）
        leaf_nodes = self._create_leaf_nodes(timeline.events, video_id)
        all_nodes.extend(leaf_nodes)

        if not leaf_nodes:
            return all_nodes

        current_level_nodes = leaf_nodes
        level = 1

        # L1: 场景层（按时间窗口）
        if level <= self.max_levels and len(current_level_nodes) > 1:
            scene_nodes = self._merge_by_time_window(
                current_level_nodes,
                window_size=self.scene_window,
                level=level,
                video_id=video_id
            )
            all_nodes.extend(scene_nodes)
            current_level_nodes = scene_nodes
            level += 1

        # L2: 章节层（按时间窗口）
        if level <= self.max_levels and len(current_level_nodes) > 1:
            chapter_nodes = self._merge_by_time_window(
                current_level_nodes,
                window_size=self.chapter_window,
                level=level,
                video_id=video_id
            )
            all_nodes.extend(chapter_nodes)
            current_level_nodes = chapter_nodes
            level += 1

        # L3: 根节点（整个视频）
        if level <= self.max_levels and len(current_level_nodes) > 1:
            root = self._merge_all(current_level_nodes, level, video_id)
            all_nodes.append(root)

        return all_nodes

    def _create_leaf_nodes(
        self, events: List[VideoEvent], video_id: str
    ) -> List[VideoRaptorNode]:
        """创建叶子节点（L0），与 Clustering 版本相同。"""
        leaf_nodes = []

        for i, event in enumerate(events):
            # 合并事件文本
            text_parts = []
            if event.audio_text:
                text_parts.append(f"语音: {event.audio_text}")
            if event.scene_description:
                text_parts.append(f"画面: {event.scene_description}")
            if event.screen_text:
                text_parts.append(f"文字: {event.screen_text}")

            summary = " | ".join(text_parts) if text_parts else f"事件 {i}"

            # 时间范围（当前事件到下一事件）
            time_range = (
                event.timestamp,
                events[i + 1].timestamp if i < len(events) - 1 else event.timestamp + 5
            )

            node = VideoRaptorNode(
                node_id=f"{video_id}:L0:{i}",
                level=0,
                summary=summary,
                time_range=time_range,
                children_ids=[],
                metadata={
                    "event_index": i,
                    "scene_change_score": event.scene_change_score,
                    "strategy": "sequential"
                }
            )

            leaf_nodes.append(node)

        # 向量化
        texts = [node.summary for node in leaf_nodes]
        embeddings = self.embedding_client.embed(texts)

        for node, emb in zip(leaf_nodes, embeddings):
            node.embeddings = emb

        return leaf_nodes

    def _merge_by_time_window(
        self,
        nodes: List[VideoRaptorNode],
        window_size: float,
        level: int,
        video_id: str
    ) -> List[VideoRaptorNode]:
        """按时间窗口合并节点（保持时间连续性）。"""
        merged = []
        current_window = []
        window_start_time = 0.0
        window_id = 0

        for node in nodes:
            node_start = node.time_range[0]

            # 检查是否超过时间窗口
            if current_window and (node_start - window_start_time >= window_size):
                # 当前窗口满了，生成摘要节点
                parent = self._merge_sequence(
                    current_window, level, video_id, window_id
                )
                merged.append(parent)

                # 开启新窗口
                current_window = [node]
                window_start_time = node_start
                window_id += 1
            else:
                if not current_window:
                    window_start_time = node_start
                current_window.append(node)

        # 最后一个窗口
        if current_window:
            parent = self._merge_sequence(
                current_window, level, video_id, window_id
            )
            merged.append(parent)

        # 向量化父节点
        texts = [node.summary for node in merged]
        embeddings = self.embedding_client.embed(texts)

        for node, emb in zip(merged, embeddings):
            node.embeddings = emb

        return merged

    def _merge_all(
        self,
        nodes: List[VideoRaptorNode],
        level: int,
        video_id: str
    ) -> VideoRaptorNode:
        """合并所有节点为根节点（整个视频）。"""
        root = self._merge_sequence(nodes, level, video_id, cluster_id=0)

        # 向量化
        root.embeddings = self.embedding_client.embed([root.summary])[0]

        return root

    def _merge_sequence(
        self,
        nodes: List[VideoRaptorNode],
        level: int,
        video_id: str,
        cluster_id: int
    ) -> VideoRaptorNode:
        """合并一个序列的节点为摘要节点。"""
        # 时间范围：从第一个节点开始到最后一个节点结束
        time_range = (
            nodes[0].time_range[0],
            nodes[-1].time_range[1]
        )

        # 收集子节点文本（按时间顺序）
        children_texts = [node.summary for node in nodes]

        # 生成摘要
        summary = self._generate_summary(children_texts, level, time_range)

        # 创建父节点
        parent = VideoRaptorNode(
            node_id=f"{video_id}:L{level}:{cluster_id}",
            level=level,
            summary=summary,
            time_range=time_range,
            children_ids=[node.node_id for node in nodes],
            metadata={
                "num_children": len(nodes),
                "duration": time_range[1] - time_range[0],
                "strategy": "sequential"
            }
        )

        return parent

    def _generate_summary(
        self,
        children_texts: List[str],
        level: int,
        time_range: tuple[float, float]
    ) -> str:
        """使用 LLM 生成序列摘要。"""
        duration = time_range[1] - time_range[0]
        start_min = int(time_range[0] // 60)
        start_sec = int(time_range[0] % 60)
        end_min = int(time_range[1] // 60)
        end_sec = int(time_range[1] % 60)

        # 根据层级调整提示词
        if level == 1:
            # L1: 场景摘要
            prompt = f"""
以下是视频中 [{start_min}:{start_sec:02d}-{end_min}:{end_sec:02d}] 时间段按顺序发生的事件：

{chr(10).join(f"{i+1}. {text}" for i, text in enumerate(children_texts))}

请用1-2句话总结这个场景中**按顺序**发生了什么。重点是流程，不是罗列。
"""
        elif level == 2:
            # L2: 章节摘要
            prompt = f"""
以下是视频中 [{start_min}:{start_sec:02d}-{end_min}:{end_sec:02d}] 时间段按顺序发生的场景：

{chr(10).join(f"{i+1}. {text}" for i, text in enumerate(children_texts))}

请用2-3句话总结这个章节。给出一个章节标题，并概括这段时间内完成了什么。
"""
        else:
            # L3+: 整体摘要
            prompt = f"""
以下是视频按时间顺序的章节内容：

{chr(10).join(f"{i+1}. {text}" for i, text in enumerate(children_texts))}

请用3-5句话总结整个视频，突出主线和关键步骤。
"""

        try:
            summary = self.summarizer_llm.generate(prompt)
            return summary.strip()
        except Exception:
            # 降级：简单拼接前几个
            return " → ".join(children_texts[:3]) + ("..." if len(children_texts) > 3 else "")


class ClusteringVideoRaptorBuilder:
    """基于语义聚类的 RAPTOR 构建器。

    按语义相似度聚类，发现跨时间的主题关联。
    适合主题查询和跨章节检索。
    """

    def __init__(
        self,
        embedding_client,
        summarizer_llm,
        cluster_size: int = 5,  # 每层聚类大小
        max_levels: int = 3,    # 最大层数
        min_cluster_size: int = 2
    ):
        self.embedding_client = embedding_client
        self.summarizer_llm = summarizer_llm
        self.cluster_size = cluster_size
        self.max_levels = max_levels
        self.min_cluster_size = min_cluster_size

    def build(self, timeline: VideoTimeline, video_id: str) -> List[VideoRaptorNode]:
        """
        构建基于聚类的 RAPTOR 树。

        流程：
        1. L0: 将每个事件作为叶子节点
        2. L1: k-means 聚类相似事件 → 生成主题摘要
        3. L2: k-means 聚类场景 → 生成章节摘要
        4. L3: 聚类章节 → 生成视频总结

        Returns:
            所有层级的节点列表
        """
        all_nodes = []

        # L0: 叶子节点（原始事件）
        leaf_nodes = self._create_leaf_nodes(timeline.events, video_id)
        all_nodes.extend(leaf_nodes)

        current_level_nodes = leaf_nodes
        level = 1

        # 逐层构建
        while len(current_level_nodes) > 1 and level <= self.max_levels:
            # 聚类 + 摘要
            parent_nodes = self._build_next_level(
                current_level_nodes,
                level,
                video_id
            )

            if not parent_nodes:
                break

            all_nodes.extend(parent_nodes)
            current_level_nodes = parent_nodes
            level += 1

        return all_nodes

    def _create_leaf_nodes(
        self, events: List[VideoEvent], video_id: str
    ) -> List[VideoRaptorNode]:
        """创建叶子节点（L0）。"""
        leaf_nodes = []

        for i, event in enumerate(events):
            # 合并事件文本
            text_parts = []
            if event.audio_text:
                text_parts.append(f"语音: {event.audio_text}")
            if event.scene_description:
                text_parts.append(f"画面: {event.scene_description}")
            if event.screen_text:
                text_parts.append(f"文字: {event.screen_text}")

            summary = " | ".join(text_parts) if text_parts else f"事件 {i}"

            # 时间范围（当前事件到下一事件）
            time_range = (
                event.timestamp,
                events[i + 1].timestamp if i < len(events) - 1 else event.timestamp + 5
            )

            node = VideoRaptorNode(
                node_id=f"{video_id}:L0:{i}",
                level=0,
                summary=summary,
                time_range=time_range,
                children_ids=[],
                metadata={
                    "event_index": i,
                    "scene_change_score": event.scene_change_score,
                    "strategy": "clustering"
                }
            )

            leaf_nodes.append(node)

        # 向量化
        texts = [node.summary for node in leaf_nodes]
        embeddings = self.embedding_client.embed(texts)

        for node, emb in zip(leaf_nodes, embeddings):
            node.embeddings = emb

        return leaf_nodes

    def _build_next_level(
        self,
        nodes: List[VideoRaptorNode],
        level: int,
        video_id: str
    ) -> List[VideoRaptorNode]:
        """构建下一层级（聚类 + 摘要）。"""
        if len(nodes) < self.min_cluster_size:
            # 节点太少，直接合并为一个根节点
            return [self._merge_nodes(nodes, level, video_id, cluster_id=0)]

        # 1. 聚类
        embeddings = np.array([node.embeddings for node in nodes])
        n_clusters = max(self.min_cluster_size, len(nodes) // self.cluster_size)

        if n_clusters >= len(nodes):
            # 聚类数量 >= 节点数，直接合并
            return [self._merge_nodes(nodes, level, video_id, cluster_id=0)]

        kmeans = KMeans(n_clusters=n_clusters, random_state=42, n_init=10)
        labels = kmeans.fit_predict(embeddings)

        # 2. 按聚类分组
        clusters = {}
        for node, label in zip(nodes, labels):
            clusters.setdefault(int(label), []).append(node)

        # 3. 为每个簇生成摘要节点
        parent_nodes = []
        for cluster_id, cluster_nodes in clusters.items():
            parent = self._merge_nodes(cluster_nodes, level, video_id, cluster_id)
            parent_nodes.append(parent)

        # 4. 向量化父节点
        texts = [node.summary for node in parent_nodes]
        embeddings = self.embedding_client.embed(texts)

        for node, emb in zip(parent_nodes, embeddings):
            node.embeddings = emb

        return parent_nodes

    def _merge_nodes(
        self,
        nodes: List[VideoRaptorNode],
        level: int,
        video_id: str,
        cluster_id: int
    ) -> VideoRaptorNode:
        """合并多个节点为一个摘要节点。"""
        # 时间范围：从第一个节点开始到最后一个节点结束
        time_range = (
            min(node.time_range[0] for node in nodes),
            max(node.time_range[1] for node in nodes)
        )

        # 收集子节点文本
        children_texts = [node.summary for node in nodes]

        # 生成摘要
        summary = self._generate_summary(children_texts, level, time_range)

        # 创建父节点
        parent = VideoRaptorNode(
            node_id=f"{video_id}:L{level}:{cluster_id}",
            level=level,
            summary=summary,
            time_range=time_range,
            children_ids=[node.node_id for node in nodes],
            metadata={
                "num_children": len(nodes),
                "duration": time_range[1] - time_range[0],
                "strategy": "clustering"
            }
        )

        return parent

    def _generate_summary(
        self,
        children_texts: List[str],
        level: int,
        time_range: tuple[float, float]
    ) -> str:
        """使用 LLM 生成摘要。"""
        duration = time_range[1] - time_range[0]
        start_min = int(time_range[0] // 60)
        start_sec = int(time_range[0] % 60)
        end_min = int(time_range[1] // 60)
        end_sec = int(time_range[1] % 60)

        # 根据层级调整提示词
        if level == 1:
            # L1: 场景摘要
            prompt = f"""
以下是视频中 [{start_min}:{start_sec:02d}-{end_min}:{end_sec:02d}] 时间段的多个事件：

{chr(10).join(f"- {text}" for text in children_texts)}

请用1-2句话总结这个场景中发生了什么。重点是**做了什么**，而不是逐条罗列。
"""
        elif level == 2:
            # L2: 章节摘要
            prompt = f"""
以下是视频中 [{start_min}:{start_sec:02d}-{end_min}:{end_sec:02d}] 时间段的多个场景摘要：

{chr(10).join(f"- {text}" for text in children_texts)}

请用2-3句话总结这个章节的主要内容。给出一个章节标题，并概括核心内容。
"""
        else:
            # L3+: 高层摘要
            prompt = f"""
以下是视频的多个章节摘要：

{chr(10).join(f"- {text}" for text in children_texts)}

请用3-5句话总结整个视频的核心内容。
"""

        try:
            summary = self.summarizer_llm.generate(prompt)
            return summary.strip()
        except Exception as e:
            # 降级：简单拼接
            return " → ".join(children_texts[:3]) + ("..." if len(children_texts) > 3 else "")


class VideoRaptorRetriever:
    """视频 RAPTOR 检索器。"""

    def __init__(self, embedding_client):
        self.embedding_client = embedding_client

    def search(
        self,
        query: str,
        nodes: List[VideoRaptorNode],
        top_k: int = 5,
        level: int | None = None
    ) -> List[VideoRaptorNode]:
        """
        在 RAPTOR 树中检索。

        Args:
            query: 查询文本
            nodes: 所有节点
            top_k: 返回数量
            level: 指定层级（None=所有层级）

        Returns:
            匹配的节点列表
        """
        # 过滤层级
        if level is not None:
            candidate_nodes = [n for n in nodes if n.level == level]
        else:
            candidate_nodes = nodes

        if not candidate_nodes:
            return []

        # 向量化查询
        query_emb = self.embedding_client.embed([query])[0]

        # 计算相似度
        scores = []
        for node in candidate_nodes:
            if node.embeddings is not None:
                sim = np.dot(query_emb, node.embeddings) / (
                    np.linalg.norm(query_emb) * np.linalg.norm(node.embeddings)
                )
                scores.append((node, float(sim)))

        # 排序
        scores.sort(key=lambda x: x[1], reverse=True)

        return [node for node, score in scores[:top_k]]

    def search_with_drill_down(
        self,
        query: str,
        nodes: List[VideoRaptorNode],
        start_level: int = 2,
        drill_down_top_k: int = 3
    ) -> List[VideoRaptorNode]:
        """
        粗到细检索（drill-down）。

        流程：
        1. 先在高层（L2/L3）检索，找到相关章节
        2. 在匹配章节的子节点中细查，找到具体片段

        Args:
            query: 查询
            nodes: 所有节点
            start_level: 起始层级（默认从章节层开始）
            drill_down_top_k: 每层下钻返回数量

        Returns:
            叶子节点列表
        """
        # 构建节点索引
        nodes_by_id = {n.node_id: n for n in nodes}

        # 从高层开始检索
        current_nodes = self.search(query, nodes, top_k=drill_down_top_k, level=start_level)

        # 下钻到叶子
        while current_nodes and current_nodes[0].level > 0:
            # 收集所有子节点
            children = []
            for node in current_nodes:
                for child_id in node.children_ids:
                    if child_id in nodes_by_id:
                        children.append(nodes_by_id[child_id])

            if not children:
                break

            # 在子节点中再次检索
            current_nodes = self.search(query, children, top_k=drill_down_top_k)

        return current_nodes


def build_video_raptor_index(
    timeline: VideoTimeline,
    video_id: str,
    embedding_client,
    summarizer_llm,
    strategy: Literal["sequential", "clustering"] = "sequential",
    **kwargs
) -> List[VideoRaptorNode]:
    """
    快捷函数：构建视频 RAPTOR 索引。

    Args:
        timeline: 视频时间线
        video_id: 视频 ID
        embedding_client: 向量化客户端
        summarizer_llm: 摘要 LLM
        strategy: 构建策略
            - "sequential" (默认): 按时间序列，保持线性结构
            - "clustering": 按语义聚类，发现主题关联
        **kwargs: 策略特定参数
            Sequential: scene_window, chapter_window, max_levels
            Clustering: cluster_size, max_levels, min_cluster_size

    Returns:
        所有层级的节点列表
    """
    if strategy == "sequential":
        builder = SequentialVideoRaptorBuilder(
            embedding_client=embedding_client,
            summarizer_llm=summarizer_llm,
            scene_window=kwargs.get("scene_window", 30.0),
            chapter_window=kwargs.get("chapter_window", 120.0),
            max_levels=kwargs.get("max_levels", 3)
        )
        print(f"🌲 构建 Sequential RAPTOR 树（时间序列）...")
    else:  # clustering
        builder = ClusteringVideoRaptorBuilder(
            embedding_client=embedding_client,
            summarizer_llm=summarizer_llm,
            cluster_size=kwargs.get("cluster_size", 5),
            max_levels=kwargs.get("max_levels", 3),
            min_cluster_size=kwargs.get("min_cluster_size", 2)
        )
        print(f"🌲 构建 Clustering RAPTOR 树（语义聚类）...")

    nodes = builder.build(timeline, video_id)

    print(f"✓ RAPTOR 树构建完成 ({strategy}):")
    for level in range(max(n.level for n in nodes) + 1):
        level_nodes = [n for n in nodes if n.level == level]
        print(f"  L{level}: {len(level_nodes)} 个节点")

    return nodes
