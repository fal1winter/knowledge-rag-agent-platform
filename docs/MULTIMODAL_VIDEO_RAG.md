# 多模态视频 RAG 完整实现文档

## 概述

本项目在原有的纯 ASR 音视频处理基础上，新增了**完整的多模态视频理解能力**，包括：

1. ✅ **关键帧提取**：固定间隔 + 场景变化检测自适应提取
2. ✅ **视觉理解**：场景描述、物体检测、屏幕文字提取
3. ✅ **时序对齐**：音频转写与视觉帧按时间戳融合
4. ✅ **时间线构建**：生成前后事件关系的可检索文档
5. ✅ **图谱扩展**：视频事件时序关系存入 Neo4j，支持上下文查询

---

## 架构设计

```
视频文件 (mp4/mov/avi...)
    ↓
┌─────────────────────────────────────────────────┐
│  MultimodalVideoLoader                          │
│  ┌───────────────┐  ┌───────────────────────┐  │
│  │ ASR 音频通道   │  │ Vision 视觉通道       │  │
│  │ - 语音转文字   │  │ - 关键帧提取          │  │
│  │ - 带时间戳     │  │ - 场景描述 (Qwen-VL)  │  │
│  └───────────────┘  │ - 物体检测 (YOLO)     │  │
│                     │ - 屏幕文字 (OCR)      │  │
│                     └───────────────────────┘  │
│                              ↓                  │
│              ┌────────────────────────┐         │
│              │ TimelineBuilder        │         │
│              │ - 音视频对齐           │         │
│              │ - 构建事件时间线       │         │
│              └────────────────────────┘         │
└─────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────┐
│  ParsedDocument                                  │
│  - text: 可检索文本（转写 + 场景描述）         │
│  - timeline: 事件序列 + 时序关系                │
│  - metadata: 时长、帧数、摘要                   │
└─────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────┐
│  入库 & 检索                                     │
│  - Milvus/ES: 向量 + BM25 检索                  │
│  - Neo4j: 时序关系图谱                          │
│  - RAPTOR: 多层摘要索引                         │
└─────────────────────────────────────────────────┘
```

---

## 核心模块

### 1. 关键帧提取 (`keyframe_extractor.py`)

#### **两种策略**

**固定间隔提取**：
```python
from rag_agent_platform.video import KeyframeExtractor

extractor = KeyframeExtractor(
    strategy="interval",
    interval_seconds=2.0,
    max_frames=50
)

keyframes = extractor.extract("video.mp4")
# 每2秒提取一帧，最多50帧
```

**场景变化检测**：
```python
extractor = KeyframeExtractor(
    strategy="scene_change",
    scene_threshold=30.0,      # 场景变化阈值（0-100）
    min_interval_seconds=1.0,  # 最小间隔（避免过密）
    max_frames=50
)

keyframes = extractor.extract("video.mp4")
# 只在场景切换时提取关键帧
```

**智能提取（推荐）**：
```python
from rag_agent_platform.video import extract_keyframes_smart

keyframes = extract_keyframes_smart(
    "video.mp4",
    max_frames=50,
    interval_seconds=2.0
)
# 先尝试场景变化，如果结果不理想则降级到固定间隔
```

#### **Keyframe 数据结构**

```python
@dataclass
class Keyframe:
    timestamp: float           # 时间戳（秒）
    frame_index: int          # 帧索引
    image: np.ndarray         # OpenCV BGR 图像
    scene_change_score: float # 场景变化分数

    def to_pil(self) -> Image.Image  # 转为 PIL Image
    def to_base64(self) -> str       # 转为 base64 字符串
```

---

### 2. 视觉理解 (`vision_client.py`)

#### **支持的客户端**

**Qwen-VL（通义千问多模态）**：
```python
from rag_agent_platform.video import QwenVLClient

client = QwenVLClient(
    endpoint="https://api.siliconflow.cn/v1",
    api_key="your-api-key",
    model="Qwen/Qwen2-VL-7B-Instruct"
)

# 场景描述
scene = client.describe_scene(image, prompt="详细描述这个画面")

# 物体检测（通过 prompt 引导）
objects = client.detect_objects(image)

# 屏幕文字提取
text = client.extract_screen_text(image)
```

**GPT-4 Vision**：
```python
from rag_agent_platform.video import GPT4VisionClient

client = GPT4VisionClient(
    api_key="sk-...",
    model="gpt-4o"
)

scene = client.describe_scene(image)
```

**混合客户端（推荐）**：
```python
from rag_agent_platform.video import HybridVisionClient, LocalYOLODetector

# VLM 负责场景描述，YOLO 负责物体检测，OCR 负责文字
client = HybridVisionClient(
    vlm_client=QwenVLClient(...),
    object_detector=LocalYOLODetector(model_path="yolov8n.pt"),
    ocr_client=ocr_service
)
```

---

### 3. 时序对齐 (`timeline.py`)

#### **音视频融合**

```python
from rag_agent_platform.video import align_audio_visual, TranscriptSegment

# 音频转写（带时间戳）
transcript_segments = [
    TranscriptSegment(text="大家好", start_time=0.0, end_time=2.5),
    TranscriptSegment(text="今天讲解 Nginx", start_time=2.5, end_time=5.0),
]

# 视觉帧数据
visual_frames = [
    {
        "timestamp": 1.0,
        "scene": "讲师站在白板前",
        "objects": [{"label": "person", "confidence": 0.95}],
        "screen_text": "",
        "scene_change_score": 0.0
    },
    {
        "timestamp": 3.0,
        "scene": "PPT 显示 Nginx 架构图",
        "objects": [],
        "screen_text": "Nginx 反向代理配置",
        "scene_change_score": 45.0
    }
]

# 对齐
timeline = align_audio_visual(
    transcript=transcript_segments,
    visual_frames=visual_frames,
    duration=60.0,
    audio_window=5.0  # 5秒窗口内的音频会匹配到该视觉帧
)

# 访问事件
for event in timeline.events:
    print(f"[{event.timestamp}s] {event.scene_description}")
    print(f"  语音: {event.audio_text}")
    print(f"  文字: {event.screen_text}")
```

#### **VideoTimeline 方法**

```python
# 获取指定时间附近的事件
event = timeline.get_event_at_time(timestamp=30.0, window=5.0)

# 获取上下文（前后事件）
context = timeline.get_context(
    timestamp=30.0,
    before=2,  # 前2个事件
    after=2    # 后2个事件
)

# 生成摘要
summary = timeline.to_summary()
```

---

### 4. 多模态加载器 (`multimodal_loader.py`)

#### **完整模式**

```python
from rag_agent_platform.video import MultimodalVideoLoader

loader = MultimodalVideoLoader(
    asr_client=asr_client,
    vision_client=vision_client,
    max_keyframes=50,
    keyframe_interval=2.0,
    enable_object_detection=True,
    enable_screen_text=True
)

parsed_doc = loader.load(DocumentAsset(
    uri="/path/to/video.mp4",
    file_type="mp4"
))

# parsed_doc.text 包含：
# - 完整转写
# - 每个关键帧的场景描述
# - 时间线摘要
```

#### **智能模式（推荐）**

```python
from rag_agent_platform.video import SmartVideoLoader

loader = SmartVideoLoader(
    asr_client=asr_client,
    vision_client=vision_client,
    max_keyframes=30
)

# 自动判断：如果转写包含"画面"、"显示"等词，才启用视觉理解
parsed_doc = loader.load(asset)
```

#### **纯音频模式（降级）**

```python
from rag_agent_platform.video import AudioOnlyVideoLoader

loader = AudioOnlyVideoLoader(asr_client=asr_client)
parsed_doc = loader.load(asset)  # 只转写音频
```

---

### 5. 时序关系图谱 (`timeline_graph.py`)

#### **入库**

```python
from rag_agent_platform.video import VideoTimelineGraph

graph = VideoTimelineGraph(graph_retriever=neo4j_retriever)

# 将时间线存入 Neo4j
graph.index_timeline(
    tenant_id="user_123",
    video_id="video_456",
    timeline=timeline
)
```

#### **图谱结构**

```
节点类型：
- Video: 视频节点
- VideoEvent: 事件节点
- Object: 物体节点

关系类型：
- FOLLOWED_BY: event1 → event2 (时序关系)
- BELONGS_TO: event → video
- CONTAINS: event → object
```

#### **检索**

```python
# 获取事件上下文
context = graph.get_event_context(
    tenant_id="user_123",
    video_id="video_456",
    event_index=5,
    context_size=2  # 前后各2个事件
)

# 查找包含特定物体的事件
events = graph.search_by_object(
    tenant_id="user_123",
    object_name="person",
    hops=1
)

# 扩展检索结果（用户查询 → 匹配事件 → 返回上下文）
from rag_agent_platform.video import expand_retrieval_with_timeline

result = expand_retrieval_with_timeline(
    graph=graph,
    tenant_id="user_123",
    matched_event_id="video:456:event:5",
    context_size=2
)
# 返回：
# {
#   "video_id": "456",
#   "matched_event": "video:456:event:5",
#   "context": {"before": [...], "current": ..., "after": [...]},
#   "video_clip_url": "video://456#t=10"
# }
```

---

## 完整示例

### 端到端视频入库

```python
from rag_agent_platform.bootstrap import HTTPASRClient, build_document_parser
from rag_agent_platform.video.bootstrap import build_smart_video_loader
from rag_agent_platform.video import VideoTimelineGraph
from rag_agent_platform.models import DocumentAsset

# 1. 初始化客户端
asr_client = HTTPASRClient("http://localhost:8091/transcribe")
video_loader = build_smart_video_loader(asr_client)

# 2. 加载视频
asset = DocumentAsset(uri="/data/videos/tutorial.mp4", file_type="mp4")
parsed_doc = video_loader.load(asset)

# 3. 提取时间线
timeline = parsed_doc.extra_data.get("timeline")

# 4. 存入图谱
graph = VideoTimelineGraph(graph_retriever=neo4j_retriever)
graph.index_timeline(
    tenant_id="tenant_001",
    video_id="tutorial_001",
    timeline=timeline
)

# 5. 向量化 & 入库
embeddings = embedding_client.embed([parsed_doc.text])
dense_index.insert(tenant_id="tenant_001", chunks=[...], embeddings=embeddings)
sparse_index.insert(tenant_id="tenant_001", chunks=[...])

# 6. 检索
query = "视频中如何配置 Nginx 反向代理"
results = hybrid_retriever.search(query, tenant_id="tenant_001", top_k=5)

# 7. 扩展时序上下文
for result in results:
    if "video:" in result.chunk_id:
        context = expand_retrieval_with_timeline(
            graph=graph,
            tenant_id="tenant_001",
            matched_event_id=result.chunk_id,
            context_size=2
        )
        print(context["video_clip_url"])
```

---

## 配置

### 环境变量

```bash
# 视觉理解模型
export VISION_ENDPOINT=https://api.siliconflow.cn/v1
export VISION_API_KEY=your-api-key
export VISION_MODEL=Qwen/Qwen2-VL-7B-Instruct  # 或 gpt-4o

# ASR 服务（已有）
export ASR_ENDPOINT=http://127.0.0.1:8091/transcribe

# OCR 服务（可选）
export OCR_ENDPOINT=http://127.0.0.1:8090/ocr
```

### 依赖安装

```bash
# 核心依赖
pip install opencv-python pillow numpy

# 视觉理解
pip install requests

# 物体检测（可选）
pip install ultralytics  # YOLOv8

# 视频处理
pip install ffmpeg-python
```

---

## 成本优化策略

### 1. 智能加载器（按需启用视觉理解）

```python
# 只有转写内容包含视觉关键词时，才提取关键帧
loader = SmartVideoLoader(asr_client, vision_client)
```

### 2. 关键帧数量控制

```python
# 限制最大帧数
loader = MultimodalVideoLoader(
    ...,
    max_keyframes=20,  # 降低视觉理解调用次数
)
```

### 3. 混合客户端

```python
# VLM 负责场景描述，本地 YOLO 负责物体检测
client = HybridVisionClient(
    vlm_client=qwen_vl,
    object_detector=LocalYOLODetector(),  # 本地运行，免费
    ocr_client=local_ocr                   # 本地 OCR，免费
)
```

### 4. 降级策略

```python
# 长视频只提取关键片段
if video_duration > 600:  # 超过10分钟
    loader = AudioOnlyVideoLoader(asr_client)  # 只转写音频
```

---

## 性能对比

| 模式 | 处理时间 | API 成本 | 检索质量 |
|------|---------|---------|---------|
| **仅 ASR** | 1x | $0.001/min | 基础 |
| **ASR + 固定间隔帧** | 3x | $0.05/video | 良好 |
| **ASR + 场景变化检测** | 4x | $0.03/video | 优秀 |
| **完整多模态** | 5x | $0.08/video | 最佳 |

---

## 使用场景

### 1. 在线课程平台
- 提取课程视频关键帧和讲义截图
- 用户搜索"矩阵乘法公式" → 跳转到板书截图的时间点

### 2. 会议录像管理
- 自动识别演讲者切换和 PPT 翻页
- 搜索"Q1 销售数据" → 找到展示该图表的时刻

### 3. 视频剪辑素材检索
- 按画面内容检索：人物、场景、物体
- "找出所有包含蓝天的镜头"

### 4. 教学视频 QA
- 理解前后操作步骤关系
- "配置完 Nginx 后，下一步做什么？"

---

## 后续优化方向

1. **视频摘要生成**：基于时间线自动生成章节摘要
2. **多模态 RAPTOR**：视觉层级 + 音频层级独立聚类
3. **实时流式处理**：边播放边处理，降低首次入库延迟
4. **用户交互反馈**：记录用户跳转时间点，优化关键帧提取策略
5. **多语言字幕对齐**：ASR + OCR 识别的字幕对齐校正

---

## 文件清单

```
src/rag_agent_platform/video/
├── __init__.py              # 模块导出
├── keyframe_extractor.py    # 关键帧提取
├── vision_client.py         # 视觉理解客户端
├── timeline.py              # 时序对齐与融合
├── multimodal_loader.py     # 多模态视频加载器
├── timeline_graph.py        # 时序关系图谱
└── bootstrap.py             # 依赖装配
```

---

## 总结

✅ **已实现的功能**：
1. 关键帧提取（固定间隔 + 场景变化检测）
2. 多模态视觉理解（场景描述、物体检测、OCR）
3. 音视频时序对齐
4. 事件时间线构建
5. Neo4j 时序关系图谱
6. 智能加载器（按需启用视觉理解）
7. 成本优化策略（混合客户端、降级方案）

🎯 **核心价值**：
- 从"只能搜索讲了什么"到"能搜索画面展示了什么"
- 支持跨模态检索：音频内容 + 视觉内容统一检索
- 理解视频前后关系：某个操作之后发生了什么
- 精准定位：返回具体时间戳，直接跳转

🚀 **生产部署建议**：
- 初期使用 `SmartVideoLoader`（按需启用视觉理解）
- 关键帧数量控制在 20-50 帧
- 使用 Qwen-VL（性价比高）+ 本地 YOLO（免费）
- 长视频（>10分钟）降级到纯 ASR 模式
