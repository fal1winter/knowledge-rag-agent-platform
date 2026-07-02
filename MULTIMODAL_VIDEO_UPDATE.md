# 🎉 多模态视频 RAG 功能更新

## 新增功能

在原有的音频 ASR 基础上，新增**完整的多模态视频理解与可追溯检索**能力：

### ⭐ 核心亮点

1. **视觉理解** - 不仅知道"讲了什么"，还能理解"画面展示了什么"
2. **时序关系** - 理解视频前后事件的因果关系（"配置完 Nginx 后做什么"）
3. **精准定位** - 检索结果包含时间戳，一键跳转到视频具体时刻
4. **完整溯源** - 每个检索结果都能追溯到源视频 + 缩略图 + 上下文

---

## 功能对比

| 能力 | 原方案（仅 ASR） | 新方案（多模态 + 溯源） |
|------|-----------------|----------------------|
| 音频理解 | ✅ 语音转文字 | ✅ 语音转文字（带时间戳） |
| 视觉理解 | ❌ | ✅ 场景描述 + 物体检测 + OCR |
| 时序关系 | ❌ | ✅ 前后事件关系图谱 |
| 精准定位 | ❌ | ✅ 秒级时间戳 + 跳转链接 |
| 检索溯源 | ❌ | ✅ 视频ID + URL + 缩略图 |
| 上下文查询 | ❌ | ✅ 查看前后步骤 |

---

## 技术实现

### 1. 关键帧提取
- 固定间隔策略（每N秒一帧）
- 场景变化检测（自适应提取）
- 智能混合策略（自动降级）

### 2. 视觉理解
- **Qwen-VL / GPT-4V**：场景描述
- **YOLO（可选）**：物体检测
- **OCR（可选）**：屏幕文字提取
- **混合客户端**：降低成本

### 3. 时序对齐
- 音频转写与视觉帧按时间戳融合
- 构建事件时间线（VideoTimeline）
- 生成可检索文本

### 4. 时序关系图谱
- 将事件存入 Neo4j
- 建立 FOLLOWED_BY 关系
- 支持上下文查询

### 5. 可追溯性 ⭐
- 每个检索块包含：
  - `video_id`（视频唯一标识）
  - `video_uri`（原始文件路径/URL）
  - `timestamp`（精确时间戳）
  - `playback_url`（HTML5 跳转链接）
  - `thumbnail`（关键帧缩略图）

---

## 使用示例

### 入库视频

```python
from rag_agent_platform.video import VideoChunkIndexer

indexer = VideoChunkIndexer(dense_index, sparse_index, embedding_client)

indexer.index_video_timeline(
    tenant_id="user_123",
    video_id="tutorial_nginx",
    video_uri="https://cdn.example.com/videos/nginx.mp4",
    timeline_events=parsed_doc.extra_data["timeline"],
    metadata=parsed_doc.metadata
)
```

### 检索并返回溯源

```python
from rag_agent_platform.video import enrich_retrieval_results_with_video_refs

# 检索
hits = dense_index.search(query_embedding, top_k=5)

# 添加溯源信息
results = enrich_retrieval_results_with_video_refs(hits)

# 返回结果包含：
# {
#   "text": "语音: 配置反向代理 | 画面: 终端显示配置文件",
#   "score": 0.95,
#   "video_reference": {
#     "video_id": "tutorial_nginx",
#     "video_uri": "https://cdn.../nginx.mp4",
#     "timestamp": 125.5,
#     "playback_url": "https://cdn.../nginx.mp4#t=125.5,130.5"
#   },
#   "scene": "终端界面显示 nginx.conf",
#   "audio": "现在配置反向代理",
#   "screen_text": "server { listen 80; ... }",
#   "thumbnail": "base64..."
# }
```

### 查看上下文

```python
from rag_agent_platform.video import expand_retrieval_with_timeline

context = expand_retrieval_with_timeline(
    graph=timeline_graph,
    tenant_id="user_123",
    matched_event_id="video:tutorial_nginx:event:25",
    context_size=2
)

# 返回前后事件：
# {
#   "before": ["安装 Nginx", "启动服务"],
#   "current": "配置反向代理",
#   "after": ["重启 Nginx", "验证配置"]
# }
```

---

## 成本优化

### 智能加载器（按需启用）

```python
from rag_agent_platform.video import SmartVideoLoader

loader = SmartVideoLoader(asr_client, vision_client)

# 如果转写包含"画面"、"显示"等词 → 完整多模态
# 否则 → 仅音频转写
```

### 混合客户端（降低成本）

```python
from rag_agent_platform.video import HybridVisionClient, LocalYOLODetector

client = HybridVisionClient(
    vlm_client=qwen_vl,              # VLM 负责场景描述
    object_detector=LocalYOLODetector(),  # 本地 YOLO 负责物体检测（免费）
    ocr_client=local_ocr              # 本地 OCR（免费）
)
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
```

### 依赖安装

```bash
pip install opencv-python pillow numpy
pip install ultralytics  # 可选：本地物体检测
```

---

## 文档

- [完整实现文档](docs/MULTIMODAL_VIDEO_RAG.md)
- [可追溯性文档](docs/VIDEO_TRACEABILITY.md)
- [实现总结](docs/VIDEO_RAG_IMPLEMENTATION_SUMMARY.md)

---

## 使用场景

### 1. 在线教育平台
学生搜索"矩阵乘法" → 跳转到讲解板书的时刻 + 查看前后课程内容

### 2. 企业培训视频
员工搜索"如何提交报销" → 跳转到演示操作的片段 + 显示屏幕截图

### 3. 视频素材库
编辑搜索"蓝天白云" → 找到所有包含该场景的镜头

### 4. 会议录像管理
搜索"Q1销售数据" → 找到展示图表的时刻 + 自动识别演讲者切换

---

## 性能

| 模式 | 处理时间 | API 成本 | 检索能力 |
|------|---------|---------|---------|
| 仅 ASR（原方案） | 1x | $0.001/min | 只能搜语音 |
| ASR + 智能加载器 | 2-3x | $0.02/video | 音频+画面 |
| 完整多模态 | 5x | $0.08/video | 音频+画面+物体+文字 |

---

## 总结

✅ **新增模块**：
- `video/keyframe_extractor.py` - 关键帧提取
- `video/vision_client.py` - 视觉理解客户端
- `video/timeline.py` - 时序对齐与融合
- `video/multimodal_loader.py` - 多模态加载器
- `video/timeline_graph.py` - 时序关系图谱
- `video/traceable.py` - 可追溯性 ⭐
- `video/bootstrap.py` - 依赖装配

✅ **核心价值**：
- 从"只能搜索讲了什么"到"能搜索画面展示了什么"
- 支持跨模态检索：音频 + 视觉 + 文字统一检索
- 完整的可追溯性：视频ID + 时间戳 + 播放链接
- 理解前后关系：某个操作之后发生了什么
- 精准定位：返回具体时间戳，直接跳转

**总代码量**：~1,500 行核心代码 + 完整文档 + 示例
