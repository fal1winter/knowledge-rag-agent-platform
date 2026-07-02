"""增强型多模态视频加载器。

集成关键帧提取、视觉理解、时序对齐，生成可检索的视频文档。
"""

import hashlib
import tempfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict, List

import cv2

from rag_agent_platform.documents.loaders import ASRClient
from rag_agent_platform.models import DocumentAsset, ParsedDocument
from rag_agent_platform.video.keyframe_extractor import extract_keyframes_smart, Keyframe
from rag_agent_platform.video.vision_client import VisionClient
from rag_agent_platform.video.timeline import align_audio_visual, TranscriptSegment, VideoTimeline


@dataclass
class MultimodalVideoLoader:
    """多模态视频加载器。"""

    asr_client: ASRClient
    vision_client: VisionClient
    max_keyframes: int = 50
    keyframe_interval: float = 2.0
    enable_object_detection: bool = True
    enable_screen_text: bool = True
    cache_keyframes: bool = True  # 缓存关键帧，避免重复处理

    supported_types = {"mp4", "mov", "avi", "mkv", "webm", "flv"}

    def load(self, asset: DocumentAsset) -> ParsedDocument:
        """
        加载并处理视频。

        处理流程：
        1. ASR 转写音频
        2. 提取关键帧
        3. 视觉理解（场景、物体、文字）
        4. 时序对齐
        5. 生成可检索文档
        """
        # 1. 音频转写
        transcript = self.asr_client.transcribe(asset.uri)

        # 2. 提取关键帧
        keyframes = extract_keyframes_smart(
            asset.uri, max_frames=self.max_keyframes, interval_seconds=self.keyframe_interval
        )

        if not keyframes:
            # 降级：只使用音频
            return ParsedDocument(
                asset=asset,
                text=transcript,
                media_transcripts=[{"uri": asset.uri, "text": transcript}],
            )

        # 3. 视觉理解
        visual_frames = self._process_keyframes(keyframes)

        # 4. 获取视频时长
        duration = self._get_video_duration(asset.uri)

        # 5. 构建时间线
        timeline = align_audio_visual(
            transcript=transcript, visual_frames=visual_frames, duration=duration, audio_window=5.0
        )

        # 6. 生成可检索文本
        searchable_text = self._build_searchable_text(transcript, timeline)

        # 7. 构建 ParsedDocument
        return ParsedDocument(
            asset=asset,
            text=searchable_text,
            media_transcripts=[{"uri": asset.uri, "text": transcript}],
            metadata={
                "video_duration": duration,
                "num_keyframes": len(keyframes),
                "num_events": len(timeline.events),
                "timeline_summary": timeline.to_summary(),
            },
            # 扩展字段（可选）
            extra_data={
                "timeline": [event.to_dict() for event in timeline.events],
                "keyframe_timestamps": [kf.timestamp for kf in keyframes],
            },
        )

    def _process_keyframes(self, keyframes: List[Keyframe]) -> List[Dict[str, Any]]:
        """处理关键帧：视觉理解。"""
        visual_frames = []

        for kf in keyframes:
            frame_data = {
                "timestamp": kf.timestamp,
                "scene": "",
                "objects": [],
                "screen_text": "",
                "scene_change_score": kf.scene_change_score,
            }

            # 场景描述
            try:
                frame_data["scene"] = self.vision_client.describe_scene(kf.image)
            except Exception as e:
                frame_data["scene"] = f"[场景描述失败: {str(e)}]"

            # 物体检测
            if self.enable_object_detection:
                try:
                    frame_data["objects"] = self.vision_client.detect_objects(kf.image)
                except Exception:
                    frame_data["objects"] = []

            # 屏幕文字提取
            if self.enable_screen_text:
                try:
                    frame_data["screen_text"] = self.vision_client.extract_screen_text(kf.image)
                except Exception:
                    frame_data["screen_text"] = ""

            visual_frames.append(frame_data)

        return visual_frames

    def _get_video_duration(self, video_path: str) -> float:
        """获取视频时长。"""
        cap = cv2.VideoCapture(video_path)
        if not cap.isOpened():
            return 0.0

        fps = cap.get(cv2.CAP_PROP_FPS)
        total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
        cap.release()

        return total_frames / fps if fps > 0 else 0.0

    def _build_searchable_text(self, transcript: str, timeline: VideoTimeline) -> str:
        """构建可检索的文本。"""
        parts = [
            "=== 完整转写 ===",
            transcript,
            "",
            "=== 关键事件时间线 ===",
        ]

        for event in timeline.events:
            parts.append(event.to_searchable_text())

        parts.append("")
        parts.append("=== 时间线摘要 ===")
        parts.append(timeline.to_summary())

        return "\n".join(parts)


@dataclass
class SmartVideoLoader:
    """
    智能视频加载器：根据转写内容决定是否需要视觉理解。

    策略：
    - 如果转写中包含"画面"、"显示"、"演示"等词，启用完整多模态处理
    - 否则只使用 ASR，节省成本
    """

    asr_client: ASRClient
    vision_client: VisionClient
    max_keyframes: int = 30

    supported_types = {"mp4", "mov", "avi", "mkv", "webm", "flv"}

    def load(self, asset: DocumentAsset) -> ParsedDocument:
        # 1. 先转写
        transcript = self.asr_client.transcribe(asset.uri)

        # 2. 判断是否需要视觉理解
        if self._needs_visual_understanding(transcript):
            # 完整多模态处理
            loader = MultimodalVideoLoader(
                asr_client=self.asr_client,
                vision_client=self.vision_client,
                max_keyframes=self.max_keyframes,
            )
            return loader.load(asset)
        else:
            # 仅使用音频
            return ParsedDocument(
                asset=asset,
                text=transcript,
                media_transcripts=[{"uri": asset.uri, "text": transcript}],
            )

    def _needs_visual_understanding(self, transcript: str) -> bool:
        """判断是否需要视觉理解。"""
        visual_keywords = [
            "画面",
            "显示",
            "演示",
            "界面",
            "屏幕",
            "看到",
            "展示",
            "图示",
            "操作",
            "点击",
            "视频",
            "PPT",
            "幻灯片",
        ]

        transcript_lower = transcript.lower()
        return any(kw in transcript_lower for kw in visual_keywords)


@dataclass
class AudioOnlyVideoLoader:
    """纯音频模式（降级方案）。"""

    asr_client: ASRClient
    supported_types = {"mp4", "mov", "avi", "mkv", "webm", "flv", "wav", "mp3", "m4a"}

    def load(self, asset: DocumentAsset) -> ParsedDocument:
        """仅转写音频，不处理视觉。"""
        transcript = self.asr_client.transcribe(asset.uri)
        return ParsedDocument(
            asset=asset, text=transcript, media_transcripts=[{"uri": asset.uri, "text": transcript}]
        )
