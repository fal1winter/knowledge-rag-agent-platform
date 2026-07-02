# 多模态视频 RAG - 快速开始示例

## 1. 基础用法：提取关键帧

```python
from rag_agent_platform.video import extract_keyframes_smart

# 智能提取关键帧（推荐）
keyframes = extract_keyframes_smart(
    video_path="tutorial.mp4",
    max_frames=30,
    interval_seconds=2.0
)

for kf in keyframes:
    print(f"[{kf.timestamp}s] Frame {kf.frame_index}")
    # kf.image 是 OpenCV BGR 格式
    # kf.to_pil() 转为 PIL Image
    # kf.to_base64() 转为 base64 字符串
```

## 2. 视觉理解

```python
from rag_agent_platform.video import QwenVLClient

client = QwenVLClient(
    endpoint="https://api.siliconflow.cn/v1",
    api_key="your-key",
    model="Qwen/Qwen2-VL-7B-Instruct"
)

# 描述场景
scene = client.describe_scene(keyframes[0].image)
print(scene)

# 检测物体
objects = client.detect_objects(keyframes[0].image)
for obj in objects:
    print(f"  - {obj['label']} (confidence: {obj['confidence']})")

# 提取屏幕文字
text = client.extract_screen_text(keyframes[0].image)
print(f"Screen text: {text}")
```

## 3. 完整视频处理

```python
from rag_agent_platform.bootstrap import HTTPASRClient
from rag_agent_platform.video.bootstrap import build_smart_video_loader
from rag_agent_platform.models import DocumentAsset

# 初始化
asr_client = HTTPASRClient("http://localhost:8091/transcribe")
loader = build_smart_video_loader(asr_client)

# 加载视频
asset = DocumentAsset(uri="tutorial.mp4", file_type="mp4")
parsed_doc = loader.load(asset)

# 查看结果
print("=== 可检索文本 ===")
print(parsed_doc.text[:500])

print("\n=== 时间线 ===")
timeline_data = parsed_doc.extra_data.get("timeline", [])
for event in timeline_data[:3]:
    print(f"[{event['timestamp']}s] {event['scene']}")
    print(f"  Audio: {event['audio']}")
```

## 4. 时序关系图谱

```python
from rag_agent_platform.video import VideoTimelineGraph, VideoTimeline, VideoEvent
from rag_agent_platform.retrieval.neo4j_adapter import Neo4jGraphRetriever

# 初始化图谱
neo4j = Neo4jGraphRetriever(uri="bolt://localhost:7687")
graph = VideoTimelineGraph(graph_retriever=neo4j)

# 构建时间线（从 parsed_doc 提取）
events = [
    VideoEvent(
        timestamp=e["timestamp"],
        scene_description=e["scene"],
        audio_text=e["audio"],
        objects=e["objects"],
        screen_text=e["screen_text"]
    )
    for e in timeline_data
]
timeline = VideoTimeline(events=events, duration=parsed_doc.metadata["video_duration"])

# 入库
graph.index_timeline(
    tenant_id="user_001",
    video_id="tutorial_001",
    timeline=timeline
)

# 检索：获取事件上下文
context = graph.get_event_context(
    tenant_id="user_001",
    video_id="tutorial_001",
    event_index=5,
    context_size=2
)

print("前序事件:", context["before"])
print("当前事件:", context["current"])
print("后续事件:", context["after"])
```

## 5. 检索增强示例

```python
from rag_agent_platform.video import expand_retrieval_with_timeline

# 用户查询："如何配置 Nginx"
# 假设检索到匹配的事件 ID
matched_event_id = "video:tutorial_001:event:8"

# 扩展上下文
result = expand_retrieval_with_timeline(
    graph=graph,
    tenant_id="user_001",
    matched_event_id=matched_event_id,
    context_size=2
)

# 返回视频跳转链接
print(f"找到相关片段: {result['video_clip_url']}")
print(f"当前事件: {result['context']['current']}")
print(f"前序步骤: {result['context']['before']}")
print(f"后续步骤: {result['context']['after']}")
```

## 6. 成本优化示例

### 智能加载器（按需启用视觉理解）

```python
from rag_agent_platform.video import SmartVideoLoader

loader = SmartVideoLoader(asr_client, vision_client, max_keyframes=30)

# 如果转写包含"画面"、"显示"等词 → 完整多模态
# 否则 → 仅音频转写
parsed_doc = loader.load(asset)
```

### 混合客户端（降低成本）

```python
from rag_agent_platform.video import HybridVisionClient, LocalYOLODetector

# VLM 负责场景描述，本地 YOLO 负责物体检测
client = HybridVisionClient(
    vlm_client=QwenVLClient(...),
    object_detector=LocalYOLODetector(model_path="yolov8n.pt"),  # 免费
    ocr_client=local_ocr_service  # 可选，本地 OCR
)
```

### 降级方案（长视频）

```python
from rag_agent_platform.video import AudioOnlyVideoLoader
import cv2

# 判断视频时长
cap = cv2.VideoCapture("long_video.mp4")
fps = cap.get(cv2.CAP_PROP_FPS)
total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
duration = total_frames / fps
cap.release()

# 超过10分钟 → 仅音频
if duration > 600:
    loader = AudioOnlyVideoLoader(asr_client)
else:
    loader = build_smart_video_loader(asr_client)

parsed_doc = loader.load(asset)
```

## 7. 批量处理示例

```python
import os
from pathlib import Path

# 批量处理视频文件夹
video_dir = Path("/data/videos")
for video_file in video_dir.glob("*.mp4"):
    try:
        print(f"Processing {video_file.name}...")

        asset = DocumentAsset(uri=str(video_file), file_type="mp4")
        parsed_doc = loader.load(asset)

        # 入库
        # ... (向量化、存储等)

        print(f"✓ Done: {len(parsed_doc.extra_data['timeline'])} events")
    except Exception as e:
        print(f"✗ Failed: {e}")
```

## 环境配置

```bash
# .env
VISION_ENDPOINT=https://api.siliconflow.cn/v1
VISION_API_KEY=your-api-key
VISION_MODEL=Qwen/Qwen2-VL-7B-Instruct

ASR_ENDPOINT=http://127.0.0.1:8091/transcribe
OCR_ENDPOINT=http://127.0.0.1:8090/ocr

NEO4J_URI=bolt://127.0.0.1:7687
MILVUS_URI=http://127.0.0.1:19530
ELASTICSEARCH_URL=http://127.0.0.1:9200
```

## 依赖安装

```bash
pip install opencv-python pillow numpy requests

# 可选：物体检测
pip install ultralytics

# 可选：视频处理
pip install ffmpeg-python
```

## 测试运行

```bash
# 1. 启动 ASR 服务（假设已有）
# python -m asr_service

# 2. 启动 Neo4j
docker run -p 7687:7687 -p 7474:7474 neo4j:latest

# 3. 运行示例
python examples/video_rag_demo.py
```
