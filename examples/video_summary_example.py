"""使用示例：视频智能总结。"""

from rag_agent_platform.bootstrap import build_embedding_client
from rag_agent_platform.config import load_config
from rag_agent_platform.generation.llm_client import DeepSeekChatAdapter
from rag_agent_platform.models import DocumentAsset
from rag_agent_platform.video.bootstrap import build_smart_video_loader
from rag_agent_platform.video.summarizer import VideoSummarizer
from rag_agent_platform.video.keyframe_extractor import extract_keyframes_smart
from rag_agent_platform.video.timeline import VideoTimeline, VideoEvent


def demo_video_summary():
    """演示视频智能总结功能。"""
    print("🎬 视频智能总结演示\n")

    # 1. 初始化
    config = load_config()
    asr_client = HTTPASRClient(config.asr_endpoint)
    video_loader = build_smart_video_loader(asr_client)

    llm_client = DeepSeekChatAdapter(
        endpoint=config.models.deepseek_endpoint,
        api_key=config.models.deepseek_api_key,
        model=config.models.answer_model
    )

    from rag_agent_platform.video.bootstrap import build_vision_client
    vision_client = build_vision_client()

    summarizer = VideoSummarizer(vision_client=vision_client, llm_client=llm_client)

    # 2. 加载视频
    video_path = "/data/videos/nginx_tutorial.mp4"
    print(f"📹 加载视频: {video_path}")

    asset = DocumentAsset(uri=video_path, file_type="mp4")
    parsed_doc = video_loader.load(asset)

    # 3. 提取关键帧
    keyframes = extract_keyframes_smart(video_path, max_frames=10)
    print(f"✓ 提取关键帧: {len(keyframes)} 帧\n")

    # 4. 构建时间线
    timeline_events = parsed_doc.extra_data.get("timeline", [])
    events = [
        VideoEvent(
            timestamp=e["timestamp"],
            scene_description=e.get("scene", ""),
            audio_text=e.get("audio", ""),
            objects=e.get("objects", []),
            screen_text=e.get("screen_text", "")
        )
        for e in timeline_events
    ]
    timeline = VideoTimeline(
        events=events,
        duration=parsed_doc.metadata.get("video_duration", 0)
    )

    # 5. 生成视觉总结
    print("👁️  生成视觉总结...")
    visual_summary = summarizer.summarize_visual_content(keyframes)
    print("\n=== 视觉总结 ===")
    print(f"视频主题: {visual_summary.get('theme', 'N/A')}")
    print(f"场景类型: {visual_summary.get('scene_type', 'N/A')}")
    print(f"主要人物: {visual_summary.get('participants', 'N/A')}")
    print(f"主要活动: {visual_summary.get('activity', 'N/A')}")
    print(f"视频风格: {visual_summary.get('style', 'N/A')}")

    # 6. 生成时间线总结
    print("\n⏱️  生成时间线总结...")
    timeline_summary = summarizer.summarize_timeline(timeline)
    print("\n=== 时间线总结 ===")
    print(f"标题: {timeline_summary.get('title', 'N/A')}")
    print(f"总结: {timeline_summary.get('summary', 'N/A')}")

    print("\n章节划分:")
    for i, chapter in enumerate(timeline_summary.get('chapters', []), 1):
        start_min = chapter['start'] // 60
        start_sec = chapter['start'] % 60
        end_min = chapter['end'] // 60
        end_sec = chapter['end'] % 60
        print(f"{i}. [{start_min}:{start_sec:02d}-{end_min}:{end_sec:02d}] {chapter['title']}")

    print("\n核心要点:")
    for i, point in enumerate(timeline_summary.get('key_points', []), 1):
        print(f"{i}. {point}")

    # 7. 生成完整总结
    print("\n🎯 生成完整总结...")
    full_summary = summarizer.generate_full_summary(keyframes, timeline)

    print("\n=== 综合总结 ===")
    print(full_summary.get('combined_summary', 'N/A'))

    # 8. 保存总结（用于检索）
    print("\n💾 保存总结用于检索...")

    # 将总结作为元数据添加到文档
    enhanced_doc = {
        "document_id": f"video:{asset.uri}",
        "text": parsed_doc.text,
        "summary": full_summary['combined_summary'],
        "visual_theme": visual_summary.get('theme', ''),
        "chapters": timeline_summary.get('chapters', []),
        "key_points": timeline_summary.get('key_points', []),
        "timeline_events": timeline_events
    }

    print("✓ 总结已生成并可用于检索")

    return enhanced_doc


def demo_search_with_summary():
    """演示基于总结的检索增强。"""
    print("\n🔍 基于总结的检索增强\n")

    # 用户查询
    query = "这个视频讲了什么"

    # 检索策略1：先搜索视频总结（粗粒度）
    print("策略1: 搜索视频级别总结")
    print(f"查询: {query}")
    print("匹配到视频总结:")
    print("  - 标题: Nginx 反向代理配置教程")
    print("  - 总结: 本视频演示如何在 Linux 系统上配置 Nginx 反向代理...")
    print("  - 章节: 3个（安装、配置、测试）")

    # 检索策略2：再搜索具体事件（细粒度）
    query2 = "如何配置反向代理"
    print(f"\n策略2: 搜索具体事件")
    print(f"查询: {query2}")
    print("匹配到具体片段:")
    print("  - [02:15] 打开 nginx.conf 配置文件")
    print("  - [02:30] 添加 proxy_pass 指令")
    print("  - 跳转链接: video.mp4#t=135")


if __name__ == "__main__":
    from rag_agent_platform.bootstrap import HTTPASRClient

    # 演示视频总结
    enhanced_doc = demo_video_summary()

    # 演示检索增强
    demo_search_with_summary()

    print("\n✅ 演示完成!")
