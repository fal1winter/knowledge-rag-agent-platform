"""视频 RAG 完整入库示例（带溯源）。"""

from pathlib import Path

from rag_agent_platform.bootstrap import HTTPASRClient, build_embedding_client
from rag_agent_platform.config import load_config
from rag_agent_platform.models import DocumentAsset
from rag_agent_platform.retrieval.milvus_adapter import MilvusDenseRetriever
from rag_agent_platform.retrieval.elasticsearch_adapter import ElasticsearchBM25Retriever
from rag_agent_platform.retrieval.neo4j_adapter import Neo4jGraphRetriever
from rag_agent_platform.video.bootstrap import build_smart_video_loader
from rag_agent_platform.video.timeline import VideoTimeline, VideoEvent
from rag_agent_platform.video.timeline_graph import VideoTimelineGraph
from rag_agent_platform.video.traceable import (
    VideoChunkIndexer,
    create_video_summary_document,
    enrich_retrieval_results_with_video_refs,
)


def index_video_with_traceability(video_path: str, video_id: str, tenant_id: str):
    """
    完整的视频入库流程（带溯源）。

    流程：
    1. 加载视频（ASR + 关键帧 + 视觉理解）
    2. 构建时间线
    3. 生成可追溯的检索块
    4. 入库：Milvus + Elasticsearch + Neo4j
    5. 创建视频概述文档
    """
    print(f"📹 处理视频: {video_path}")

    # 1. 初始化服务
    config = load_config()
    asr_client = HTTPASRClient(config.asr_endpoint)
    video_loader = build_smart_video_loader(asr_client)

    embedding_client = build_embedding_client()
    dense_index = MilvusDenseRetriever(uri=config.milvus_uri)
    sparse_index = ElasticsearchBM25Retriever(url=config.elasticsearch_url)
    neo4j = Neo4jGraphRetriever(uri=config.neo4j_uri)
    graph = VideoTimelineGraph(graph_retriever=neo4j)

    # 2. 加载视频
    asset = DocumentAsset(uri=video_path, file_type=Path(video_path).suffix.strip("."))
    parsed_doc = video_loader.load(asset)

    print(f"✓ 转写完成: {len(parsed_doc.text)} 字符")
    print(f"✓ 关键帧: {parsed_doc.metadata.get('num_keyframes', 0)} 帧")
    print(f"✓ 事件数: {parsed_doc.metadata.get('num_events', 0)} 个")

    # 3. 提取时间线
    timeline_events = parsed_doc.extra_data.get("timeline", [])
    if not timeline_events:
        print("⚠️  无时间线数据，跳过")
        return

    # 4. 索引事件块（精细粒度）
    indexer = VideoChunkIndexer(
        dense_index=dense_index,
        sparse_index=sparse_index,
        embedding_client=embedding_client,
    )

    num_chunks = indexer.index_video_timeline(
        tenant_id=tenant_id,
        video_id=video_id,
        video_uri=video_path,  # 生产环境应该是 CDN URL
        timeline_events=timeline_events,
        metadata=parsed_doc.metadata,
    )

    print(f"✓ 入库事件块: {num_chunks} 个")

    # 5. 创建视频概述文档（粗粒度）
    summary_doc = create_video_summary_document(
        video_id=video_id,
        video_uri=video_path,
        timeline_events=timeline_events,
        full_transcript=parsed_doc.media_transcripts[0]["text"],
        metadata=parsed_doc.metadata,
    )

    # 入库概述文档
    summary_embedding = embedding_client.embed([summary_doc["text"]])[0]
    # ... (入库逻辑)

    print(f"✓ 概述文档已创建")

    # 6. 存入时序图谱
    events = [
        VideoEvent(
            timestamp=e["timestamp"],
            scene_description=e.get("scene", ""),
            audio_text=e.get("audio", ""),
            objects=e.get("objects", []),
            screen_text=e.get("screen_text", ""),
            scene_change_score=e.get("scene_change_score", 0),
        )
        for e in timeline_events
    ]

    timeline = VideoTimeline(events=events, duration=parsed_doc.metadata.get("video_duration", 0))

    graph.index_timeline(tenant_id=tenant_id, video_id=video_id, timeline=timeline)

    print(f"✓ 时序图谱已建立")
    print(f"\n🎉 视频入库完成!")


def search_video_with_traceability(query: str, tenant_id: str, top_k: int = 5):
    """
    检索视频并返回可追溯结果。

    返回结构：
    {
        "text": "...",
        "score": 0.95,
        "video_reference": {
            "video_id": "...",
            "video_uri": "...",
            "timestamp": 30.5,
            "playback_url": "video.mp4#t=30.5,35.5"
        },
        "scene": "...",
        "audio": "...",
        "thumbnail": "base64..."
    }
    """
    print(f"🔍 查询: {query}")

    # 1. 初始化
    config = load_config()
    embedding_client = build_embedding_client()
    dense_index = MilvusDenseRetriever(uri=config.milvus_uri)

    # 2. 向量检索
    query_embedding = embedding_client.embed([query])[0]
    hits = dense_index.search(
        tenant_id=tenant_id, query_embedding=query_embedding, top_k=top_k, filter_dict={"source": "video_multimodal"}
    )

    # 3. 添加溯源信息
    enriched_results = enrich_retrieval_results_with_video_refs(
        hits, base_playback_url="https://cdn.example.com/"
    )

    # 4. 展示结果
    print(f"\n✓ 找到 {len(enriched_results)} 个相关片段:\n")
    for i, result in enumerate(enriched_results, 1):
        print(f"{i}. [分数: {result['score']:.3f}]")
        print(f"   场景: {result.get('scene', 'N/A')[:80]}")
        print(f"   语音: {result.get('audio', 'N/A')[:80]}")

        if "video_reference" in result:
            ref = result["video_reference"]
            print(f"   📍 视频: {ref['video_id']} @ {ref['timestamp']:.1f}s")
            print(f"   🎬 播放: {result['playback_url']}")
        print()

    return enriched_results


def demo():
    """演示完整流程。"""
    tenant_id = "demo_tenant"
    video_id = "tutorial_nginx_001"
    video_path = "/data/videos/nginx_tutorial.mp4"

    # 入库
    index_video_with_traceability(video_path, video_id, tenant_id)

    # 检索
    results = search_video_with_traceability("如何配置 Nginx 反向代理", tenant_id, top_k=3)

    # 前端展示
    print("\n=== 前端展示格式 ===")
    for result in results[:1]:
        if "video_reference" in result:
            ref = result["video_reference"]
            print(
                f"""
<div class="video-result">
  <h3>找到相关片段</h3>
  <p>{result.get('scene', '')}</p>
  <video src="{ref['video_uri']}#t={ref['timestamp']}" controls></video>
  <p>时间戳: {ref['timestamp']:.1f}秒</p>
  <p>语音内容: {result.get('audio', '')}</p>
</div>
            """
            )


if __name__ == "__main__":
    demo()
