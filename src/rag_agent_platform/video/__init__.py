"""多模态视频处理模块。"""

from .keyframe_extractor import KeyframeExtractor, Keyframe, SceneChangeDetector, extract_keyframes_smart
from .vision_client import VisionClient, QwenVLClient, GPT4VisionClient, HybridVisionClient
from .multimodal_loader import MultimodalVideoLoader, SmartVideoLoader, AudioOnlyVideoLoader
from .timeline import VideoEvent, VideoTimeline, TimelineBuilder, align_audio_visual
from .timeline_graph import VideoTimelineGraph, expand_retrieval_with_timeline
from .traceable import (
    VideoReference,
    TraceableVideoChunk,
    VideoChunkIndexer,
    build_traceable_chunks_from_timeline,
    enrich_retrieval_results_with_video_refs,
    create_video_summary_document,
)
from .summarizer import VideoSummarizer
from .raptor import (
    VideoRaptorNode,
    SequentialVideoRaptorBuilder,
    ClusteringVideoRaptorBuilder,
    VideoRaptorRetriever,
    build_video_raptor_index,
)

__all__ = [
    # 关键帧提取
    "KeyframeExtractor",
    "Keyframe",
    "SceneChangeDetector",
    "extract_keyframes_smart",
    # 视觉理解
    "VisionClient",
    "QwenVLClient",
    "GPT4VisionClient",
    "HybridVisionClient",
    # 视频加载器
    "MultimodalVideoLoader",
    "SmartVideoLoader",
    "AudioOnlyVideoLoader",
    # 时序对齐
    "VideoEvent",
    "VideoTimeline",
    "TimelineBuilder",
    "align_audio_visual",
    # 图谱扩展
    "VideoTimelineGraph",
    "expand_retrieval_with_timeline",
    # 可追溯性
    "VideoReference",
    "TraceableVideoChunk",
    "VideoChunkIndexer",
    "build_traceable_chunks_from_timeline",
    "enrich_retrieval_results_with_video_refs",
    "create_video_summary_document",
    # 智能总结
    "VideoSummarizer",
    # RAPTOR 树状索引
    "VideoRaptorNode",
    "SequentialVideoRaptorBuilder",
    "ClusteringVideoRaptorBuilder",
    "VideoRaptorRetriever",
    "build_video_raptor_index",
]
