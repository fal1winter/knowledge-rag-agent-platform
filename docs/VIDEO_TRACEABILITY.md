# 视频检索可追溯性实现

## 核心需求

用户检索视频内容时，需要能够：
1. **精准定位**：知道匹配内容在视频的哪个时间点
2. **快速跳转**：点击结果直接跳转到视频的对应时刻
3. **上下文查看**：看到匹配片段的前后内容
4. **源文件访问**：能够访问原始视频文件

---

## 数据结构设计

### 1. 视频引用信息 (VideoReference)

```python
@dataclass
class VideoReference:
    video_id: str           # 视频唯一标识
    video_uri: str          # 视频文件路径/CDN URL
    timestamp: float        # 匹配片段的时间戳（秒）
    duration: float = 5.0   # 片段时长
    event_index: int = 0    # 事件索引
    thumbnail_uri: str | None = None  # 缩略图

    def to_playback_url(self) -> str:
        # HTML5 视频片段格式
        return f"{self.video_uri}#t={self.timestamp},{self.timestamp + self.duration}"
```

### 2. 可追溯的检索块 (TraceableVideoChunk)

```python
@dataclass
class TraceableVideoChunk:
    chunk_id: str                    # video:vid:event:5
    video_id: str                    # 视频 ID
    video_uri: str                   # 视频 URL
    text: str                        # 检索文本
    timestamp: float                 # 时间戳
    event_index: int                 # 事件索引
    scene_description: str = ""      # 场景描述
    audio_text: str = ""             # 音频文本
    screen_text: str = ""            # 屏幕文字
    objects: List[str] = None        # 物体列表
    thumbnail_base64: str | None = None  # 缩略图
```

---

## 入库流程（保留溯源）

### 完整流程图

```
视频文件
    ↓
[加载] MultimodalVideoLoader
    ↓
时间线事件 (timeline_events)
    ↓
[构建] build_traceable_chunks_from_timeline()
    ↓
TraceableVideoChunk[]
    ├─ chunk_id: "video:vid:event:5"
    ├─ video_uri: "/videos/tutorial.mp4"
    ├─ timestamp: 30.5
    ├─ text: "语音: 配置 Nginx | 画面: 终端界面"
    └─ thumbnail: "base64..."
    ↓
[转换] to_retrieval_hit()
    ↓
RetrievalHit (带 citation 和 metadata)
    ├─ citation: {video_id, video_uri, timestamp, playback_url}
    └─ metadata: {scene, audio, screen_text, thumbnail}
    ↓
[入库]
    ├─ Milvus: 向量检索（保留 citation）
    ├─ Elasticsearch: BM25 检索（保留 citation）
    └─ Neo4j: 时序关系图谱
```

### 代码实现

```python
from rag_agent_platform.video.traceable import VideoChunkIndexer

indexer = VideoChunkIndexer(dense_index, sparse_index, embedding_client)

# 入库（自动保留溯源信息）
num_chunks = indexer.index_video_timeline(
    tenant_id="user_123",
    video_id="tutorial_001",
    video_uri="https://cdn.example.com/videos/nginx_tutorial.mp4",
    timeline_events=timeline_events,
    metadata={"duration": 600, "num_events": 50}
)
```

---

## 检索流程（返回溯源）

### 数据流

```
用户查询: "如何配置 Nginx 反向代理"
    ↓
[向量化] embedding_client.embed()
    ↓
[检索] Milvus/ES search
    ↓
RetrievalHit[]
    ├─ text: "语音: 现在配置反向代理 | 画面: 终端显示配置文件"
    ├─ citation: {
    │     video_id: "tutorial_001",
    │     video_uri: "https://cdn.../nginx.mp4",
    │     timestamp: 125.5,
    │     playback_url: "https://cdn.../nginx.mp4#t=125.5,130.5"
    │  }
    └─ metadata: {scene, audio, thumbnail}
    ↓
[增强] enrich_retrieval_results_with_video_refs()
    ↓
前端展示结果
    ├─ 文本摘要
    ├─ 视频缩略图
    ├─ 播放按钮（跳转到 125.5s）
    └─ 前后上下文按钮
```

### 代码实现

```python
from rag_agent_platform.video.traceable import enrich_retrieval_results_with_video_refs

# 1. 检索
hits = dense_index.search(
    tenant_id="user_123",
    query_embedding=query_embedding,
    top_k=5,
    filter_dict={"source": "video_multimodal"}  # 只检索视频
)

# 2. 添加溯源信息
enriched_results = enrich_retrieval_results_with_video_refs(
    hits,
    base_playback_url="https://cdn.example.com/"
)

# 3. 返回结果
for result in enriched_results:
    print(f"场景: {result['scene']}")
    print(f"语音: {result['audio']}")
    print(f"播放: {result['playback_url']}")
    print(f"缩略图: {result['video_reference']['thumbnail_uri']}")
```

---

## 前端展示

### HTML 示例

```html
<!-- 检索结果卡片 -->
<div class="video-result-card">
  <!-- 缩略图 + 播放按钮 -->
  <div class="thumbnail" onclick="playVideo('https://cdn.../video.mp4#t=125.5')">
    <img src="{{ result.video_reference.thumbnail_uri }}" />
    <div class="play-icon">▶</div>
    <span class="timestamp">02:05</span>
  </div>

  <!-- 内容摘要 -->
  <div class="content">
    <h3>找到相关片段</h3>
    <p class="scene">{{ result.scene }}</p>
    <p class="audio">{{ result.audio }}</p>
    
    <!-- 操作按钮 -->
    <div class="actions">
      <button onclick="jumpToVideo('{{ result.playback_url }}')">
        🎬 跳转播放
      </button>
      <button onclick="showContext('{{ result.video_reference.video_id }}', {{ result.video_reference.event_index }})">
        📖 查看上下文
      </button>
    </div>
  </div>
</div>

<script>
function jumpToVideo(playbackUrl) {
  // HTML5 video 支持 #t=start,end 格式
  const videoPlayer = document.getElementById('video-player');
  videoPlayer.src = playbackUrl;
  videoPlayer.play();
}

function showContext(videoId, eventIndex) {
  // 请求前后事件
  fetch(`/api/video/${videoId}/context?event=${eventIndex}`)
    .then(res => res.json())
    .then(data => {
      // 显示前序事件
      renderContextTimeline(data.before, data.current, data.after);
    });
}
</script>
```

---

## 多层检索粒度

### 1. 粗粒度：视频概述

```python
# 创建视频级别概述文档
summary_doc = create_video_summary_document(
    video_id="tutorial_001",
    video_uri="https://cdn.../nginx.mp4",
    timeline_events=timeline_events,
    full_transcript=full_transcript,
    metadata={"duration": 600, "num_events": 50}
)

# 用途：
# - 用户搜索"Nginx 教程" → 返回整个视频
# - 展示：视频标题、总时长、关键时刻列表
```

### 2. 细粒度：事件片段

```python
# 每个事件单独索引
for event in timeline_events:
    chunk = TraceableVideoChunk(
        chunk_id=f"video:{video_id}:event:{i}",
        timestamp=event["timestamp"],
        text=f"语音: {event['audio']} | 画面: {event['scene']}",
        ...
    )

# 用途：
# - 精准定位："反向代理配置" → 第5分20秒处
# - 展示：具体场景描述 + 跳转按钮
```

---

## 溯源信息存储位置

### RetrievalHit 结构

```python
RetrievalHit(
    chunk_id="video:tutorial_001:event:25",
    document_id="tutorial_001",
    text="语音: 配置反向代理 | 画面: 终端显示配置文件",
    score=0.95,
    source="video_multimodal",
    tenant_id="user_123",
    
    # 核心：溯源信息存在 citation
    citation={
        "video_id": "tutorial_001",
        "video_uri": "https://cdn.example.com/videos/nginx.mp4",
        "timestamp": 125.5,
        "event_index": 25,
        "playback_url": "https://cdn.../nginx.mp4#t=125.5,130.5"
    },
    
    # 扩展：元数据
    metadata={
        "type": "video_event",
        "scene": "终端界面显示 nginx.conf 文件",
        "audio": "现在我们配置反向代理，打开配置文件",
        "screen_text": "server { listen 80; location / { proxy_pass ... } }",
        "objects": ["terminal", "text_editor"],
        "thumbnail": "data:image/jpeg;base64,..."
    }
)
```

### Milvus 存储映射

```python
# Milvus Collection Schema
{
    "chunk_id": "video:tutorial_001:event:25",
    "embedding": [0.1, 0.2, ...],  # 768维向量
    "text": "...",
    "tenant_id": "user_123",
    "source": "video_multimodal",
    
    # JSON 字段存储溯源
    "citation": "{\"video_id\": \"tutorial_001\", ...}",
    "metadata": "{\"scene\": \"...\", ...}"
}
```

---

## 上下文扩展检索

### 场景

用户搜索到某个片段后，想看：
- **前面发生了什么**（前序步骤）
- **后面发生了什么**（后续步骤）

### 实现

```python
from rag_agent_platform.video import expand_retrieval_with_timeline

# 用户检索到的片段
matched_chunk_id = "video:tutorial_001:event:25"

# 扩展上下文（前后各2个事件）
context = expand_retrieval_with_timeline(
    graph=timeline_graph,
    tenant_id="user_123",
    matched_event_id=matched_chunk_id,
    context_size=2
)

# 返回：
{
    "video_id": "tutorial_001",
    "matched_event": "video:tutorial_001:event:25",
    "context": {
        "before": [
            {"event_id": "...:event:23", "scene": "安装 Nginx", "timestamp": 110.0},
            {"event_id": "...:event:24", "scene": "启动服务", "timestamp": 118.0}
        ],
        "current": "video:tutorial_001:event:25",
        "after": [
            {"event_id": "...:event:26", "scene": "重启 Nginx", "timestamp": 133.0},
            {"event_id": "...:event:27", "scene": "验证配置", "timestamp": 140.0}
        ]
    },
    "video_clip_url": "https://cdn.../nginx.mp4#t=125.5"
}
```

### 前端时间线组件

```html
<div class="context-timeline">
  <!-- 前序步骤 -->
  <div class="before">
    <div class="event" onclick="jumpTo(110.0)">
      <span class="time">01:50</span>
      <span class="desc">安装 Nginx</span>
    </div>
    <div class="event" onclick="jumpTo(118.0)">
      <span class="time">01:58</span>
      <span class="desc">启动服务</span>
    </div>
  </div>

  <!-- 当前匹配 -->
  <div class="current">
    <div class="event active">
      <span class="time">02:05</span>
      <span class="desc">配置反向代理 ✓</span>
    </div>
  </div>

  <!-- 后续步骤 -->
  <div class="after">
    <div class="event" onclick="jumpTo(133.0)">
      <span class="time">02:13</span>
      <span class="desc">重启 Nginx</span>
    </div>
    <div class="event" onclick="jumpTo(140.0)">
      <span class="time">02:20</span>
      <span class="desc">验证配置</span>
    </div>
  </div>
</div>
```

---

## 完整示例

### 入库代码

```python
from rag_agent_platform.video.traceable import VideoChunkIndexer

indexer = VideoChunkIndexer(dense_index, sparse_index, embedding_client)

# 入库视频（自动保留溯源）
indexer.index_video_timeline(
    tenant_id="user_123",
    video_id="tutorial_nginx",
    video_uri="https://cdn.example.com/videos/nginx_tutorial.mp4",
    timeline_events=parsed_doc.extra_data["timeline"],
    metadata=parsed_doc.metadata
)
```

### 检索代码

```python
from rag_agent_platform.video.traceable import enrich_retrieval_results_with_video_refs

# 检索
hits = hybrid_retriever.search("配置 Nginx 反向代理", tenant_id="user_123", top_k=5)

# 添加溯源
results = enrich_retrieval_results_with_video_refs(hits, base_playback_url="https://cdn.example.com/")

# 返回给前端
return {
    "results": results,
    "total": len(results)
}
```

### 前端展示

```json
{
  "results": [
    {
      "text": "语音: 现在配置反向代理 | 画面: 终端显示配置文件",
      "score": 0.95,
      "video_reference": {
        "video_id": "tutorial_nginx",
        "video_uri": "https://cdn.../nginx.mp4",
        "timestamp": 125.5,
        "playback_url": "https://cdn.../nginx.mp4#t=125.5,130.5",
        "thumbnail_uri": "data:image/jpeg;base64,..."
      },
      "scene": "终端界面显示 nginx.conf 文件",
      "audio": "现在我们配置反向代理，打开配置文件",
      "screen_text": "server { listen 80; ... }"
    }
  ]
}
```

---

## 总结

### ✅ 已实现的可追溯性

1. **视频 ID 绑定**：每个检索块都记录 `video_id` 和 `video_uri`
2. **时间戳定位**：每个片段记录 `timestamp` 和 `event_index`
3. **播放链接生成**：自动生成 `#t=start,end` 格式的跳转链接
4. **缩略图关联**：可选保存关键帧缩略图
5. **上下文查询**：通过 Neo4j 图谱查询前后事件
6. **多粒度检索**：概述文档 + 事件片段两层粒度

### 🎯 关键设计点

1. **citation 字段**：RetrievalHit 的 citation 字段存储完整溯源信息
2. **chunk_id 设计**：`video:vid:event:N` 格式便于解析和定位
3. **metadata 扩展**：场景、音频、文字、物体、缩略图全部保留
4. **图谱关系**：时序关系 (FOLLOWED_BY) 支持上下文扩展
5. **HTML5 兼容**：播放链接使用标准的 `#t=` 格式

### 📊 检索结果包含的信息

```python
{
    "text": "检索匹配的文本",
    "score": 0.95,
    "video_reference": {
        "video_id": "唯一标识",
        "video_uri": "原始文件路径/CDN URL",
        "timestamp": 125.5,
        "playback_url": "跳转链接",
        "thumbnail_uri": "缩略图"
    },
    "scene": "画面描述",
    "audio": "语音内容",
    "screen_text": "屏幕文字",
    "objects": ["检测到的物体"]
}
```

这样用户就能：
- ✅ 知道内容来自哪个视频
- ✅ 精准定位到具体时间点
- ✅ 一键跳转播放
- ✅ 查看前后上下文
- ✅ 访问原始视频文件
