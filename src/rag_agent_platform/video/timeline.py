"""时序对齐与融合模块。

将音频转写文本与视觉帧按时间戳对齐，构建视频事件时间线。
"""

from dataclasses import dataclass, field
from typing import Any, Dict, List


@dataclass
class TranscriptSegment:
    """带时间戳的转写片段。"""

    text: str
    start_time: float  # 秒
    end_time: float
    confidence: float = 1.0


@dataclass
class VideoEvent:
    """视频事件（音频+视觉融合）。"""

    timestamp: float  # 秒
    scene_description: str
    audio_text: str
    objects: List[Dict[str, Any]] = field(default_factory=list)
    screen_text: str = ""
    scene_change_score: float = 0.0

    def to_dict(self) -> Dict[str, Any]:
        return {
            "timestamp": self.timestamp,
            "scene": self.scene_description,
            "audio": self.audio_text,
            "objects": self.objects,
            "screen_text": self.screen_text,
            "scene_change_score": self.scene_change_score,
        }

    def to_searchable_text(self) -> str:
        """生成可检索的文本。"""
        parts = [
            f"[{self.timestamp:.1f}s]",
            f"画面: {self.scene_description}",
        ]

        if self.audio_text:
            parts.append(f"语音: {self.audio_text}")

        if self.screen_text:
            parts.append(f"文字: {self.screen_text}")

        if self.objects:
            obj_labels = [obj.get("label", "") for obj in self.objects]
            parts.append(f"物体: {', '.join(obj_labels)}")

        return " | ".join(parts)


@dataclass
class VideoTimeline:
    """视频时间线（事件序列）。"""

    events: List[VideoEvent]
    duration: float  # 总时长（秒）

    def get_event_at_time(self, timestamp: float, window: float = 5.0) -> VideoEvent | None:
        """获取指定时间附近的事件。"""
        for event in self.events:
            if abs(event.timestamp - timestamp) <= window:
                return event
        return None

    def get_context(self, timestamp: float, before: int = 1, after: int = 1) -> Dict[str, Any]:
        """获取指定时间点的上下文（前后事件）。"""
        target_idx = None
        for i, event in enumerate(self.events):
            if event.timestamp >= timestamp:
                target_idx = i
                break

        if target_idx is None:
            target_idx = len(self.events) - 1

        context = {
            "current": self.events[target_idx].to_dict(),
            "before": [],
            "after": [],
        }

        # 前 N 个事件
        for i in range(max(0, target_idx - before), target_idx):
            context["before"].append(self.events[i].to_dict())

        # 后 N 个事件
        for i in range(target_idx + 1, min(len(self.events), target_idx + after + 1)):
            context["after"].append(self.events[i].to_dict())

        return context

    def to_summary(self) -> str:
        """生成时间线摘要。"""
        lines = [f"视频时长: {self.duration:.1f}秒", f"关键事件: {len(self.events)} 个", ""]

        for i, event in enumerate(self.events, 1):
            lines.append(f"{i}. [{event.timestamp:.1f}s] {event.scene_description}")
            if event.audio_text:
                lines.append(f"   语音: {event.audio_text[:50]}...")

        return "\n".join(lines)


class TimelineBuilder:
    """时间线构建器：对齐音频与视觉。"""

    def __init__(self, audio_window: float = 5.0):
        """
        Args:
            audio_window: 查找音频片段的时间窗口（秒）
        """
        self.audio_window = audio_window

    def build(
        self,
        transcript_segments: List[TranscriptSegment],
        visual_frames: List[Dict[str, Any]],  # 来自关键帧 + 视觉理解
        duration: float,
    ) -> VideoTimeline:
        """
        构建时间线。

        Args:
            transcript_segments: 带时间戳的转写片段
            visual_frames: 视觉帧数据，每个包含:
                - timestamp: float
                - scene: str
                - objects: List[Dict]
                - screen_text: str
                - scene_change_score: float
            duration: 视频总时长

        Returns:
            VideoTimeline
        """
        events = []

        for frame in visual_frames:
            timestamp = frame["timestamp"]

            # 查找时间窗口内的音频
            audio_text = self._find_audio_at_time(transcript_segments, timestamp, self.audio_window)

            event = VideoEvent(
                timestamp=timestamp,
                scene_description=frame.get("scene", ""),
                audio_text=audio_text,
                objects=frame.get("objects", []),
                screen_text=frame.get("screen_text", ""),
                scene_change_score=frame.get("scene_change_score", 0.0),
            )

            events.append(event)

        return VideoTimeline(events=events, duration=duration)

    def _find_audio_at_time(
        self, segments: List[TranscriptSegment], timestamp: float, window: float
    ) -> str:
        """查找指定时间窗口内的音频文本。"""
        matching_segments = []

        for seg in segments:
            # 片段与时间窗口有重叠
            if seg.start_time <= timestamp + window and seg.end_time >= timestamp - window:
                matching_segments.append(seg)

        if not matching_segments:
            return ""

        # 按时间排序并拼接
        matching_segments.sort(key=lambda s: s.start_time)
        return " ".join(seg.text for seg in matching_segments)


def align_audio_visual(
    transcript: str | List[TranscriptSegment],
    visual_frames: List[Dict[str, Any]],
    duration: float,
    audio_window: float = 5.0,
) -> VideoTimeline:
    """
    快捷函数：对齐音频与视觉。

    Args:
        transcript: 完整转写文本（无时间戳）或带时间戳的片段列表
        visual_frames: 视觉帧数据
        duration: 视频时长
        audio_window: 音频查找窗口

    Returns:
        VideoTimeline
    """
    # 如果是纯文本，转为单个片段
    if isinstance(transcript, str):
        transcript_segments = [TranscriptSegment(text=transcript, start_time=0, end_time=duration)]
    else:
        transcript_segments = transcript

    builder = TimelineBuilder(audio_window=audio_window)
    return builder.build(transcript_segments, visual_frames, duration)
