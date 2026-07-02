"""视频关键帧提取服务。

支持两种策略：
1. 固定间隔提取（简单可控）
2. 场景变化检测（自适应精准）
"""

import base64
from dataclasses import dataclass
from io import BytesIO
from pathlib import Path
import subprocess
import tempfile
from typing import List, Protocol

import cv2
import numpy as np
from PIL import Image


@dataclass
class Keyframe:
    """关键帧数据结构。"""

    timestamp: float  # 秒
    frame_index: int
    image: np.ndarray  # BGR format from OpenCV
    scene_change_score: float = 0.0  # 场景变化分数（0-1）

    def to_pil(self) -> Image.Image:
        """转换为 PIL Image（RGB）。"""
        return Image.fromarray(cv2.cvtColor(self.image, cv2.COLOR_BGR2RGB))

    def to_base64(self, format: str = "JPEG") -> str:
        """转换为 base64 编码字符串。"""
        pil_img = self.to_pil()
        buffer = BytesIO()
        pil_img.save(buffer, format=format)
        return base64.b64encode(buffer.getvalue()).decode("utf-8")


class SceneChangeDetector:
    """基于帧差异的场景变化检测。"""

    def __init__(self, threshold: float = 30.0):
        """
        Args:
            threshold: 场景变化阈值（0-100），越小越敏感
        """
        self.threshold = threshold
        self.prev_frame = None

    def detect(self, frame: np.ndarray) -> float:
        """
        检测当前帧与前一帧的差异度。

        Returns:
            差异分数（0-100），超过阈值表示场景变化
        """
        # 转为灰度图
        gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)

        if self.prev_frame is None:
            self.prev_frame = gray
            return 0.0

        # 计算帧间差异
        diff = cv2.absdiff(self.prev_frame, gray)
        score = np.mean(diff)

        self.prev_frame = gray
        return float(score)

    def reset(self):
        """重置状态。"""
        self.prev_frame = None


class KeyframeExtractor:
    """视频关键帧提取器。"""

    def __init__(
        self,
        strategy: str = "interval",  # "interval" or "scene_change"
        interval_seconds: float = 2.0,
        scene_threshold: float = 30.0,
        min_interval_seconds: float = 1.0,  # 场景变化模式下的最小间隔
        max_frames: int = 100,
    ):
        """
        Args:
            strategy: 提取策略（"interval" 固定间隔 / "scene_change" 场景变化）
            interval_seconds: 固定间隔模式下的间隔（秒）
            scene_threshold: 场景变化检测阈值
            min_interval_seconds: 场景变化模式下两帧的最小间隔
            max_frames: 最大提取帧数（防止过长视频）
        """
        self.strategy = strategy
        self.interval_seconds = interval_seconds
        self.scene_threshold = scene_threshold
        self.min_interval_seconds = min_interval_seconds
        self.max_frames = max_frames

    def extract(self, video_path: str) -> List[Keyframe]:
        """
        提取视频关键帧。

        Args:
            video_path: 视频文件路径

        Returns:
            关键帧列表
        """
        if self.strategy == "interval":
            return self._extract_by_interval(video_path)
        elif self.strategy == "scene_change":
            return self._extract_by_scene_change(video_path)
        else:
            raise ValueError(f"Unknown strategy: {self.strategy}")

    def _extract_by_interval(self, video_path: str) -> List[Keyframe]:
        """按固定间隔提取关键帧。"""
        cap = cv2.VideoCapture(video_path)
        if not cap.isOpened():
            raise RuntimeError(f"Failed to open video: {video_path}")

        fps = cap.get(cv2.CAP_PROP_FPS)
        total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
        duration = total_frames / fps if fps > 0 else 0

        keyframes = []
        frame_interval = int(fps * self.interval_seconds)

        frame_index = 0
        while True:
            ret, frame = cap.read()
            if not ret:
                break

            if frame_index % frame_interval == 0:
                timestamp = frame_index / fps
                keyframes.append(Keyframe(
                    timestamp=timestamp,
                    frame_index=frame_index,
                    image=frame.copy()
                ))

                if len(keyframes) >= self.max_frames:
                    break

            frame_index += 1

        cap.release()
        return keyframes

    def _extract_by_scene_change(self, video_path: str) -> List[Keyframe]:
        """基于场景变化检测提取关键帧。"""
        cap = cv2.VideoCapture(video_path)
        if not cap.isOpened():
            raise RuntimeError(f"Failed to open video: {video_path}")

        fps = cap.get(cv2.CAP_PROP_FPS)
        detector = SceneChangeDetector(threshold=self.scene_threshold)

        keyframes = []
        last_keyframe_time = -self.min_interval_seconds
        frame_index = 0

        while True:
            ret, frame = cap.read()
            if not ret:
                break

            timestamp = frame_index / fps
            score = detector.detect(frame)

            # 场景变化 + 满足最小间隔
            if score > self.scene_threshold and (timestamp - last_keyframe_time) >= self.min_interval_seconds:
                keyframes.append(Keyframe(
                    timestamp=timestamp,
                    frame_index=frame_index,
                    image=frame.copy(),
                    scene_change_score=score
                ))
                last_keyframe_time = timestamp

                if len(keyframes) >= self.max_frames:
                    break

            frame_index += 1

        cap.release()

        # 如果没提取到任何帧（视频太静态），降级到固定间隔
        if len(keyframes) == 0:
            detector.reset()
            return self._extract_by_interval(video_path)

        return keyframes


def extract_keyframes_smart(
    video_path: str,
    max_frames: int = 50,
    interval_seconds: float = 2.0
) -> List[Keyframe]:
    """
    智能关键帧提取（推荐使用）。

    先尝试场景变化检测，如果结果太少或太多，则降级到固定间隔。

    Args:
        video_path: 视频路径
        max_frames: 最大帧数
        interval_seconds: 降级时的间隔

    Returns:
        关键帧列表
    """
    # 先尝试场景变化
    extractor_scene = KeyframeExtractor(
        strategy="scene_change",
        scene_threshold=25.0,
        min_interval_seconds=1.5,
        max_frames=max_frames
    )
    keyframes = extractor_scene.extract(video_path)

    # 场景变化提取的帧数合理，直接返回
    if 5 <= len(keyframes) <= max_frames:
        return keyframes

    # 降级到固定间隔
    extractor_interval = KeyframeExtractor(
        strategy="interval",
        interval_seconds=interval_seconds,
        max_frames=max_frames
    )
    return extractor_interval.extract(video_path)
