"""视频 RAPTOR 使用示例。"""

from rag_agent_platform.bootstrap import build_embedding_client
from rag_agent_platform.config import load_config
from rag_agent_platform.generation.llm_client import DeepSeekChatAdapter
from rag_agent_platform.models import DocumentAsset
from rag_agent_platform.video.bootstrap import build_smart_video_loader
from rag_agent_platform.video.raptor import (
    build_video_raptor_index,
    VideoRaptorRetriever
)
from rag_agent_platform.video.timeline import VideoTimeline, VideoEvent


def demo_video_raptor_sequential():
    """演示 Sequential RAPTOR（时间序列，推荐）。"""
    print("🌲 Sequential RAPTOR 演示（时间序列索引）\n")

    # 1. 初始化
    config = load_config()
    from rag_agent_platform.bootstrap import HTTPASRClient
    asr_client = HTTPASRClient(config.asr_endpoint)
    video_loader = build_smart_video_loader(asr_client)

    embedding_client = build_embedding_client()
    summarizer_llm = DeepSeekChatAdapter(
        endpoint=config.models.deepseek_endpoint,
        api_key=config.models.deepseek_api_key,
        model=config.models.answer_model
    )

    # 2. 加载视频
    video_path = "/data/videos/nginx_tutorial.mp4"
    print(f"📹 加载视频: {video_path}")

    asset = DocumentAsset(uri=video_path, file_type="mp4")
    parsed_doc = video_loader.load(asset)

    # 3. 构建时间线
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
    timeline = VideoTimeline(events=events, duration=parsed_doc.metadata.get("video_duration", 0))

    print(f"✓ 事件数: {len(timeline.events)}")

    # 4. 构建 Sequential RAPTOR 树（默认）
    print("\n🌲 构建 Sequential RAPTOR 树...")
    nodes = build_video_raptor_index(
        timeline=timeline,
        video_id="nginx_tutorial_001",
        embedding_client=embedding_client,
        summarizer_llm=summarizer_llm,
        strategy="sequential",  # 默认策略
        scene_window=30.0,      # 30秒一个场景
        chapter_window=120.0    # 2分钟一个章节
    )

    # 5. 展示树结构
    print("\n=== RAPTOR 树结构 ===")
    for level in range(max(n.level for n in nodes) + 1):
        level_nodes = [n for n in nodes if n.level == level]
        print(f"\nL{level} ({len(level_nodes)} 个节点):")

        for node in level_nodes[:3]:  # 只展示前3个
            time_str = f"[{int(node.time_range[0])}s-{int(node.time_range[1])}s]"
            summary_preview = node.summary[:80] + "..." if len(node.summary) > 80 else node.summary
            print(f"  {node.node_id} {time_str}: {summary_preview}")

        if len(level_nodes) > 3:
            print(f"  ... 还有 {len(level_nodes) - 3} 个节点")

    # 6. Sequential 特有查询：时间范围查询
    print("\n\n🔍 Sequential RAPTOR 特有查询")
    retriever = VideoRaptorRetriever(embedding_client)

    # 示例1：章节查询（连续时间段）
    query1 = "前2分钟讲了什么"
    print(f"\n查询1: \"{query1}\" (L2 章节层)")
    results = retriever.search(query1, nodes, top_k=3, level=2)

    for i, node in enumerate(results, 1):
        time_str = f"[{int(node.time_range[0])}s-{int(node.time_range[1])}s]"
        print(f"{i}. {time_str} {node.summary[:100]}")

    # 示例2：场景查询（30秒片段）
    query2 = "如何配置反向代理"
    print(f"\n查询2: \"{query2}\" (L1 场景层)")
    results = retriever.search(query2, nodes, top_k=3, level=1)

    for i, node in enumerate(results, 1):
        time_str = f"[{int(node.time_range[0])}s-{int(node.time_range[1])}s]"
        print(f"{i}. {time_str} {node.summary[:100]}")

    # 示例3：Drill-down（时间导航）
    query3 = "Nginx 配置"
    print(f"\n查询3: \"{query3}\" (Drill-down: 章节→场景→事件)")
    results = retriever.search_with_drill_down(
        query3, nodes, start_level=2, drill_down_top_k=2
    )

    print("时间线下钻:")
    for i, node in enumerate(results, 1):
        time_str = f"[{int(node.time_range[0])}s-{int(node.time_range[1])}s]"
        print(f"{i}. {time_str} {node.summary[:100]}")

    # 7. 存储索引（用于后续检索）
    print("\n\n💾 存储 RAPTOR 索引...")

    # 方式1：存入向量数据库（所有层级）
    for node in nodes:
        # 每个节点都可以被检索
        print(f"  入库: {node.node_id} (L{node.level})")
        # dense_index.insert(...)

    print("✓ RAPTOR 索引已存储")

    return nodes


def demo_video_raptor_clustering():
    """演示 Clustering RAPTOR（语义聚类）。"""
    print("\n\n🌲 Clustering RAPTOR 演示（语义聚类索引）\n")

    # 1. 初始化（复用 Sequential 的配置）
    config = load_config()
    from rag_agent_platform.bootstrap import HTTPASRClient
    asr_client = HTTPASRClient(config.asr_endpoint)
    video_loader = build_smart_video_loader(asr_client)

    embedding_client = build_embedding_client()
    summarizer_llm = DeepSeekChatAdapter(
        endpoint=config.models.deepseek_endpoint,
        api_key=config.models.deepseek_api_key,
        model=config.models.answer_model
    )

    # 2. 加载视频（同上）
    video_path = "/data/videos/nginx_tutorial.mp4"
    asset = DocumentAsset(uri=video_path, file_type="mp4")
    parsed_doc = video_loader.load(asset)

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
    timeline = VideoTimeline(events=events, duration=parsed_doc.metadata.get("video_duration", 0))

    # 3. 构建 Clustering RAPTOR 树
    print("🌲 构建 Clustering RAPTOR 树...")
    nodes = build_video_raptor_index(
        timeline=timeline,
        video_id="nginx_tutorial_002",
        embedding_client=embedding_client,
        summarizer_llm=summarizer_llm,
        strategy="clustering",  # 聚类策略
        cluster_size=5,
        max_levels=3
    )

    # 4. 展示树结构（按主题聚类）
    print("\n=== Clustering RAPTOR 树结构（按主题聚类）===")
    for level in range(max(n.level for n in nodes) + 1):
        level_nodes = [n for n in nodes if n.level == level]
        print(f"\nL{level} ({len(level_nodes)} 个节点):")

        for node in level_nodes[:3]:
            # 注意：时间范围可能不连续
            time_str = f"[{int(node.time_range[0])}s-{int(node.time_range[1])}s]"
            summary_preview = node.summary[:80] + "..." if len(node.summary) > 80 else node.summary
            print(f"  {node.node_id} {time_str}: {summary_preview}")

        if len(level_nodes) > 3:
            print(f"  ... 还有 {len(level_nodes) - 3} 个节点")

    # 5. Clustering 特有查询：跨时间主题查询
    print("\n\n🔍 Clustering RAPTOR 特有查询（跨时间主题）")
    retriever = VideoRaptorRetriever(embedding_client)

    query = "所有关于配置的内容"
    print(f"\n查询: \"{query}\" (L2 主题簇)")
    results = retriever.search(query, nodes, top_k=3, level=2)

    print("主题簇（可能包含不连续的时间段）:")
    for i, node in enumerate(results, 1):
        time_str = f"[{int(node.time_range[0])}s-{int(node.time_range[1])}s]"
        print(f"{i}. {time_str} {node.summary[:100]}")

    return nodes


def demo_strategy_comparison():
    """对比两种策略的差异。"""
    print("\n\n📊 Sequential vs Clustering 策略对比\n")

    print("=" * 60)
    print("Sequential RAPTOR（时间序列）")
    print("=" * 60)
    print("✓ 保持时间连续性")
    print("✓ 章节 = 连续时间段（如 0-120s, 120-240s）")
    print("✓ 适合查询：")
    print("  - '前5分钟讲了什么'")
    print("  - '第2章的内容'")
    print("  - '2:30-3:00 这段做了什么'")
    print("\n结构示例:")
    print("  L2: [0-120s] 环境准备与安装")
    print("    L1: [0-30s] 检查系统")
    print("    L1: [30-60s] 下载安装包")
    print("    L1: [60-90s] 安装 Nginx")
    print("    L1: [90-120s] 启动服务")

    print("\n" + "=" * 60)
    print("Clustering RAPTOR（语义聚类）")
    print("=" * 60)
    print("✓ 发现跨时间的主题关联")
    print("✓ 章节 = 语义相似的内容（时间可能不连续）")
    print("✓ 适合查询：")
    print("  - '所有关于配置的片段'")
    print("  - '视频中讲了哪几个主题'")
    print("  - '错误排查相关的所有内容'")
    print("\n结构示例:")
    print("  L2: 安装主题（包含 0-30s, 85-90s, 150-160s）")
    print("  L2: 配置主题（包含 40-80s, 100-140s, 200-250s）")
    print("  L2: 测试主题（包含 160-180s, 300-350s）")

    print("\n" + "=" * 60)
    print("推荐用法")
    print("=" * 60)
    print("• 默认使用 Sequential（符合视频线性特点）")
    print("• 当需要跨章节主题查询时，使用 Clustering")
    print("• 可以同时构建两种索引，各取所长")


def demo_multi_level_retrieval(nodes):
    """演示多层级检索策略。"""
    print("\n\n📊 多层级检索策略对比\n")

    query = "如何配置 Nginx"

    from rag_agent_platform.bootstrap import build_embedding_client
    embedding_client = build_embedding_client()
    retriever = VideoRaptorRetriever(embedding_client)

    # 策略1：只搜索叶子节点（传统方式）
    print("策略1: 只搜索 L0 叶子节点")
    results_l0 = retriever.search(query, nodes, top_k=5, level=0)
    print(f"  返回: {len(results_l0)} 个事件片段（可能过于细碎）")

    # 策略2：只搜索高层节点（粗粒度）
    print("\n策略2: 只搜索 L2 章节节点")
    results_l2 = retriever.search(query, nodes, top_k=5, level=2)
    print(f"  返回: {len(results_l2)} 个章节（可能太宽泛）")

    # 策略3：混合检索（推荐）
    print("\n策略3: 混合检索（L2 + L1 + L0）")
    results_all = []
    for level in [2, 1, 0]:
        level_results = retriever.search(query, nodes, top_k=2, level=level)
        results_all.extend(level_results)

    print(f"  返回: {len(results_all)} 个节点（多粒度）")
    print("  - L2: 章节概览")
    print("  - L1: 场景细节")
    print("  - L0: 精准时刻")

    # 策略4：Drill-down（智能下钻）
    print("\n策略4: Drill-down 智能下钻（推荐）")
    results_drill = retriever.search_with_drill_down(
        query, nodes, start_level=2, drill_down_top_k=2
    )
    print(f"  返回: {len(results_drill)} 个叶子节点（经过章节过滤）")
    print("  流程: L2章节匹配 → L1场景匹配 → L0事件匹配")


if __name__ == "__main__":
    print("🎬 视频 RAPTOR 完整演示\n")

    # 1. 演示 Sequential RAPTOR（推荐）
    nodes_seq = demo_video_raptor_sequential()

    # 2. 演示 Clustering RAPTOR
    nodes_cluster = demo_video_raptor_clustering()

    # 3. 策略对比
    demo_strategy_comparison()

    # 4. 多层级检索策略对比（使用 Sequential 节点）
    demo_multi_level_retrieval(nodes_seq)

    print("\n✅ 完整演示完成!")
