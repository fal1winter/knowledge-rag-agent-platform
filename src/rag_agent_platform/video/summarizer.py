"""视频整体总结与多帧融合模块。

提供视频级别的智能总结功能，包括：
1. 多帧视觉融合总结
2. 基于 LLM 的事件抽象总结
3. 分章节结构化摘要
"""

from typing import List

from rag_agent_platform.video.keyframe_extractor import Keyframe
from rag_agent_platform.video.vision_client import VisionClient
from rag_agent_platform.video.timeline import VideoTimeline


class VideoSummarizer:
    """视频智能总结器。"""

    def __init__(self, vision_client: VisionClient, llm_client):
        self.vision_client = vision_client
        self.llm_client = llm_client

    def summarize_visual_content(
        self, keyframes: List[Keyframe], max_frames: int = 5
    ) -> dict:
        """
        多帧视觉融合总结。

        使用 VLM 分析多个关键帧，生成整体视觉描述。

        Args:
            keyframes: 关键帧列表
            max_frames: 最多使用的帧数（避免token过多）

        Returns:
            {
                "theme": "视频主题",
                "scene_type": "场景类型",
                "participants": "主要人物",
                "activity": "主要活动",
                "style": "视频风格"
            }
        """
        if not keyframes:
            return {"theme": "无内容", "scene_type": "未知"}

        # 均匀采样关键帧
        step = max(1, len(keyframes) // max_frames)
        sampled_frames = keyframes[::step][:max_frames]

        prompt = """
分析这些视频关键帧，回答以下问题：

1. **视频主题**：这个视频主要讲什么？一句话概括。
2. **场景类型**：室内还是室外？是教室、办公室、会议室还是其他？
3. **主要人物**：有哪些人物？他们在做什么？
4. **主要活动**：视频中发生了什么活动或演示？
5. **视频风格**：是教学视频、会议录像、产品演示还是其他类型？

请用简洁的语言回答，每个问题1-2句话。
"""

        # 注意：这里需要 VLM 支持多图输入
        # Qwen-VL 和 GPT-4V 都支持
        try:
            # 如果 VisionClient 支持多图
            summary_text = self._describe_multiple_frames(sampled_frames, prompt)

            # 解析结果（简单版本，生产环境应该用结构化输出）
            return self._parse_visual_summary(summary_text)
        except Exception as e:
            # 降级：逐帧描述后拼接
            descriptions = [
                self.vision_client.describe_scene(kf.image)
                for kf in sampled_frames
            ]
            return {
                "theme": "多场景视频",
                "frames_count": len(sampled_frames),
                "descriptions": descriptions
            }

    def _describe_multiple_frames(self, frames: List[Keyframe], prompt: str) -> str:
        """
        使用 VLM 描述多个帧（需要模型支持多图输入）。

        对于 Qwen-VL / GPT-4V，可以一次发送多张图。
        """
        # 这里简化实现，实际需要根据具体 API 调整
        # 例如 GPT-4V 支持：
        # messages = [{
        #     "role": "user",
        #     "content": [
        #         {"type": "text", "text": prompt},
        #         {"type": "image_url", "image_url": frame1_base64},
        #         {"type": "image_url", "image_url": frame2_base64},
        #         ...
        #     ]
        # }]

        # 降级实现：逐帧描述后汇总
        frame_descriptions = []
        for i, frame in enumerate(frames, 1):
            desc = self.vision_client.describe_scene(
                frame.image,
                prompt=f"[第{i}帧] 简要描述这个画面（1-2句话）"
            )
            frame_descriptions.append(f"第{i}帧: {desc}")

        # 用 LLM 汇总
        combined = "\n".join(frame_descriptions)
        summary_prompt = f"""
基于以下关键帧描述，回答之前的问题：

{combined}

{prompt}
"""
        return self.llm_client.generate(summary_prompt)

    def _parse_visual_summary(self, summary_text: str) -> dict:
        """解析视觉总结文本。"""
        # 简单解析（生产环境应该用结构化输出）
        lines = summary_text.split("\n")
        result = {
            "theme": "",
            "scene_type": "",
            "participants": "",
            "activity": "",
            "style": ""
        }

        for line in lines:
            line = line.strip()
            if "主题" in line or "theme" in line.lower():
                result["theme"] = line.split("：")[-1] if "：" in line else line
            elif "场景" in line or "scene" in line.lower():
                result["scene_type"] = line.split("：")[-1] if "：" in line else line
            elif "人物" in line or "participant" in line.lower():
                result["participants"] = line.split("：")[-1] if "：" in line else line
            elif "活动" in line or "activity" in line.lower():
                result["activity"] = line.split("：")[-1] if "：" in line else line
            elif "风格" in line or "style" in line.lower():
                result["style"] = line.split("：")[-1] if "：" in line else line

        return result

    def summarize_timeline(self, timeline: VideoTimeline) -> dict:
        """
        基于 LLM 的时间线抽象总结。

        将事件序列总结为结构化的章节。

        Args:
            timeline: 视频时间线

        Returns:
            {
                "title": "视频标题",
                "summary": "一句话总结",
                "chapters": [
                    {"start": 0, "end": 150, "title": "...", "content": "..."},
                    ...
                ],
                "key_points": ["要点1", "要点2", ...]
            }
        """
        # 构建事件列表
        events_text = []
        for event in timeline.events:
            timestamp_str = f"[{int(event.timestamp // 60)}:{int(event.timestamp % 60):02d}]"
            scene = event.scene_description[:100] if event.scene_description else ""
            audio = event.audio_text[:100] if event.audio_text else ""

            event_line = f"{timestamp_str} 画面: {scene}"
            if audio:
                event_line += f" | 语音: {audio}"

            events_text.append(event_line)

        events_str = "\n".join(events_text)

        prompt = f"""
你是视频内容分析专家。以下是一个视频的事件时间线，请提供结构化总结。

## 事件时间线
{events_str}

## 视频信息
- 总时长: {timeline.duration:.0f}秒（{timeline.duration/60:.1f}分钟）
- 事件数: {len(timeline.events)}个

请按以下格式输出：

# 视频标题
[给视频起一个合适的标题]

# 一句话总结
[用一句话概括视频的核心内容]

# 内容章节
按时间顺序将视频分为3-5个逻辑章节，每个章节包括：
- 时间范围
- 章节标题
- 主要内容（2-3句话）

格式：
1. [起始时间-结束时间] 章节标题
   主要内容描述...

# 核心要点
提取3-5个关键要点（知识点、操作步骤或重要信息）

请用简洁专业的语言，避免冗余。
"""

        summary_text = self.llm_client.generate(prompt)

        # 解析结果
        return self._parse_timeline_summary(summary_text, timeline)

    def _parse_timeline_summary(self, summary_text: str, timeline: VideoTimeline) -> dict:
        """解析时间线总结。"""
        result = {
            "title": "",
            "summary": "",
            "chapters": [],
            "key_points": []
        }

        current_section = None
        lines = summary_text.split("\n")

        for line in lines:
            line = line.strip()
            if not line:
                continue

            # 识别标题
            if line.startswith("# 视频标题") or line.startswith("# 标题"):
                current_section = "title"
            elif line.startswith("# 一句话总结") or line.startswith("# 总结"):
                current_section = "summary"
            elif line.startswith("# 内容章节") or line.startswith("# 章节"):
                current_section = "chapters"
            elif line.startswith("# 核心要点") or line.startswith("# 要点"):
                current_section = "key_points"
            elif line.startswith("#"):
                current_section = None
            else:
                # 填充内容
                if current_section == "title" and not result["title"]:
                    result["title"] = line
                elif current_section == "summary" and not result["summary"]:
                    result["summary"] = line
                elif current_section == "chapters" and line[0].isdigit():
                    # 解析章节 "1. [0:00-2:30] 章节标题"
                    chapter = self._parse_chapter_line(line)
                    if chapter:
                        result["chapters"].append(chapter)
                elif current_section == "key_points" and (line.startswith("-") or line.startswith("•")):
                    result["key_points"].append(line.lstrip("-• "))

        return result

    def _parse_chapter_line(self, line: str) -> dict | None:
        """解析章节行。"""
        import re

        # 匹配 "1. [0:00-2:30] 章节标题"
        match = re.match(r"\d+\.\s*\[(\d+):(\d+)-(\d+):(\d+)\]\s*(.+)", line)
        if match:
            start_min, start_sec, end_min, end_sec, title = match.groups()
            return {
                "start": int(start_min) * 60 + int(start_sec),
                "end": int(end_min) * 60 + int(end_sec),
                "title": title.strip(),
                "content": ""  # 需要从后续行提取
            }
        return None

    def generate_full_summary(
        self, keyframes: List[Keyframe], timeline: VideoTimeline
    ) -> dict:
        """
        生成完整的视频总结（视觉 + 时间线）。

        Returns:
            {
                "visual": {...},       # 视觉总结
                "timeline": {...},     # 时间线总结
                "combined_summary": "" # 综合总结
            }
        """
        visual_summary = self.summarize_visual_content(keyframes)
        timeline_summary = self.summarize_timeline(timeline)

        # 生成综合总结
        combined_prompt = f"""
基于视觉分析和时间线分析，生成一段完整的视频介绍（3-5句话）：

视觉分析：
- 主题: {visual_summary.get('theme', '')}
- 场景: {visual_summary.get('scene_type', '')}
- 风格: {visual_summary.get('style', '')}

时间线分析：
- 标题: {timeline_summary.get('title', '')}
- 总结: {timeline_summary.get('summary', '')}
- 章节数: {len(timeline_summary.get('chapters', []))}

请写一段自然流畅的视频介绍。
"""

        combined_summary = self.llm_client.generate(combined_prompt)

        return {
            "visual": visual_summary,
            "timeline": timeline_summary,
            "combined_summary": combined_summary
        }
