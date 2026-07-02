# 视频智能总结功能

## 概述

新增 **VideoSummarizer** 模块，提供视频级别的智能总结功能：

1. ✅ **多帧视觉融合总结** - 将多个关键帧发送给 VLM，生成整体视觉描述
2. ✅ **基于 LLM 的时间线总结** - 将事件序列抽象为结构化章节
3. ✅ **综合总结** - 融合视觉和时间线分析，生成完整介绍

---

## 核心功能

### 1. 多帧视觉融合总结

**问题**：原方案逐帧处理，缺少整体视觉理解

**解决方案**：将多个关键帧一起发送给 VLM

```python
from rag_agent_platform.video import VideoSummarizer

summarizer = VideoSummarizer(vision_client, llm_client)

# 输入：10个关键帧
# 输出：视觉总结
visual_summary = summarizer.summarize_visual_content(keyframes)

# 返回：
{
    "theme": "Nginx 配置教程",
    "scene_type": "室内教学视频，电脑屏幕录制",
    "participants": "讲师（画外音），无出镜",
    "activity": "终端操作演示、配置文件编辑",
    "style": "技术教学视频"
}
```

**工作原理**：
1. 均匀采样 5-10 个关键帧
2. 将所有帧一起发送给 VLM（Qwen-VL / GPT-4V 支持多图）
3. VLM 综合分析所有帧，输出整体描述

**优势**：
- ✅ 理解视频整体主题（不是单帧片段）
- ✅ 识别场景变化和主要活动
- ✅ 判断视频类型（教学/会议/演示）

---

### 2. 基于 LLM 的时间线总结

**问题**：事件列表只是罗列，缺少抽象和结构化

**解决方案**：用 LLM 将事件序列总结为章节

```python
# 输入：50个事件的时间线
timeline_summary = summarizer.summarize_timeline(timeline)

# 返回：
{
    "title": "Nginx 反向代理配置完整教程",
    "summary": "本视频演示如何在 Linux 系统上安装、配置和测试 Nginx 反向代理",
    "chapters": [
        {
            "start": 0,
            "end": 150,
            "title": "环境准备与 Nginx 安装",
            "content": "介绍系统环境，使用 apt 安装 Nginx，启动服务"
        },
        {
            "start": 150,
            "end": 300,
            "title": "反向代理配置",
            "content": "编辑 nginx.conf，添加 proxy_pass 指令，配置转发规则"
        },
        {
            "start": 300,
            "end": 420,
            "title": "测试与验证",
            "content": "重启 Nginx，使用 curl 测试代理，验证配置正确性"
        }
    ],
    "key_points": [
        "使用 apt install nginx 安装",
        "配置文件位于 /etc/nginx/nginx.conf",
        "proxy_pass 指令用于设置转发目标",
        "测试时注意防火墙设置"
    ]
}
```

**工作原理**：
1. 将所有事件（场景 + 音频）组织为时间线文本
2. 发送给 LLM（DeepSeek-V3）
3. LLM 识别逻辑章节，提取关键要点

**优势**：
- ✅ 结构化章节划分（自动识别逻辑断点）
- ✅ 提取核心要点（知识点、操作步骤）
- ✅ 生成可读标题和摘要

---

### 3. 综合总结

**融合视觉和时间线分析，生成完整介绍**

```python
# 输入：关键帧 + 时间线
full_summary = summarizer.generate_full_summary(keyframes, timeline)

# 返回：
{
    "visual": {...},              # 视觉总结
    "timeline": {...},            # 时间线总结
    "combined_summary": """
这是一个 7 分钟的 Nginx 配置教学视频，通过屏幕录制的方式演示了完整的反向代理配置流程。
视频分为三个部分：首先在 Ubuntu 系统上安装 Nginx，然后详细讲解如何编辑配置文件添加 proxy_pass 指令，
最后通过 curl 命令验证配置是否生效。整个过程操作清晰，适合初学者学习。
"""
}
```

---

## 使用场景

### 场景 1：视频检索（粗粒度）

**用户查询**："有没有 Nginx 教程视频"

**检索流程**：
1. 搜索视频级别总结（`combined_summary`）
2. 返回匹配的视频卡片

**返回结果**：
```
📹 Nginx 反向代理配置完整教程 (7分钟)
主题：技术教学视频，演示 Nginx 配置
章节：
  1. 环境准备与安装 (0:00-2:30)
  2. 反向代理配置 (2:30-5:00)
  3. 测试与验证 (5:00-7:00)

[观看视频]
```

### 场景 2：精准定位（细粒度）

**用户查询**："如何添加 proxy_pass 指令"

**检索流程**：
1. 先匹配章节（"反向代理配置" 章节）
2. 再匹配具体事件（时间戳）
3. 返回跳转链接

**返回结果**：
```
找到相关片段（位于第2章节）：

[02:35] 编辑 nginx.conf 配置文件
画面：终端显示配置文件内容
语音：现在添加 proxy_pass 指令，指向后端服务器
屏幕：server { location / { proxy_pass http://backend:8080; } }

[▶️ 跳转播放 (02:35)]  [📖 查看完整章节]
```

---

## 检索策略

### 两层检索架构

```
用户查询
    ↓
┌─────────────────────────────────────┐
│ 第1层：视频级别总结检索（粗粒度）    │
│  - 匹配 combined_summary             │
│  - 匹配 chapters 标题                │
│  - 匹配 key_points                   │
│  → 返回相关视频列表                  │
└─────────────────────────────────────┘
    ↓（用户选择视频或需要精准定位）
┌─────────────────────────────────────┐
│ 第2层：事件级别检索（细粒度）        │
│  - 匹配具体事件（场景+音频）          │
│  - 匹配 screen_text                  │
│  → 返回精准时间戳 + 跳转链接          │
└─────────────────────────────────────┘
```

### 示例：对比

**查询**："学习 Nginx"

| 检索层级 | 匹配内容 | 返回结果 |
|---------|---------|---------|
| **视频级别** | `combined_summary` | "Nginx 配置教程（7分钟）" |
| **章节级别** | `chapters[0].title` | "环境准备与安装 (0:00-2:30)" |
| **事件级别** | `events[5].audio` | "[00:45] 使用 apt install nginx" |

---

## 入库流程

### 增强的入库管线

```python
# 1. 加载视频（多模态）
parsed_doc = video_loader.load(asset)

# 2. 提取关键帧
keyframes = extract_keyframes_smart(video_path)

# 3. 构建时间线
timeline = build_timeline(parsed_doc)

# 4. 生成智能总结 ⭐
summarizer = VideoSummarizer(vision_client, llm_client)
full_summary = summarizer.generate_full_summary(keyframes, timeline)

# 5. 入库（三层粒度）
# 层1：视频级别总结
index_video_summary({
    "video_id": "video_001",
    "summary": full_summary['combined_summary'],
    "visual_theme": full_summary['visual']['theme'],
    "chapters": full_summary['timeline']['chapters'],
    "key_points": full_summary['timeline']['key_points']
})

# 层2：章节级别
for chapter in full_summary['timeline']['chapters']:
    index_chapter({
        "video_id": "video_001",
        "chapter_id": f"video_001:chapter:{i}",
        "title": chapter['title'],
        "start": chapter['start'],
        "end": chapter['end'],
        "content": chapter['content']
    })

# 层3：事件级别（已有）
indexer.index_video_timeline(...)
```

---

## 前端展示

### 视频卡片（粗粒度）

```html
<div class="video-card">
  <img src="{{ thumbnail }}" />
  <div class="video-info">
    <h3>{{ title }}</h3>
    <p class="summary">{{ combined_summary }}</p>
    
    <!-- 章节列表 -->
    <div class="chapters">
      <h4>内容章节</h4>
      <ul>
        <li v-for="chapter in chapters">
          <span class="time">[{{ chapter.start }}-{{ chapter.end }}]</span>
          <span class="title">{{ chapter.title }}</span>
          <button @click="jumpTo(chapter.start)">▶️</button>
        </li>
      </ul>
    </div>
    
    <!-- 关键要点 -->
    <div class="key-points">
      <h4>核心要点</h4>
      <ul>
        <li v-for="point in key_points">{{ point }}</li>
      </ul>
    </div>
  </div>
</div>
```

### 精准片段（细粒度）

```html
<div class="video-segment">
  <div class="thumbnail-with-timestamp">
    <img src="{{ thumbnail }}" />
    <span class="timestamp">02:35</span>
  </div>
  
  <div class="segment-info">
    <p class="context">位于：第2章节 - 反向代理配置</p>
    <p class="scene">{{ scene_description }}</p>
    <p class="audio">{{ audio_text }}</p>
    <code>{{ screen_text }}</code>
    
    <button @click="playFrom(155)">▶️ 跳转播放</button>
    <button @click="showChapter(2)">📖 查看完整章节</button>
  </div>
</div>
```

---

## 性能优化

### 成本控制

| 操作 | API 调用 | 成本 | 优化策略 |
|------|---------|------|---------|
| 多帧视觉总结 | 1次 VLM（5-10帧） | ~$0.01 | 采样关键帧 |
| 时间线总结 | 1次 LLM | ~$0.005 | 只总结长视频（>5分钟）|
| 综合总结 | 1次 LLM | ~$0.002 | 可选 |

**总成本**：每个视频增加 ~$0.02（相比原方案）

### 降级策略

```python
# 短视频（<3分钟）：跳过总结
if video_duration < 180:
    skip_summary = True

# 长视频（>10分钟）：只做章节划分
if video_duration > 600:
    only_chapter_summary = True
```

---

## 使用示例

```python
from rag_agent_platform.video import VideoSummarizer

# 初始化
summarizer = VideoSummarizer(vision_client, llm_client)

# 生成总结
full_summary = summarizer.generate_full_summary(keyframes, timeline)

# 使用总结
print(f"标题: {full_summary['timeline']['title']}")
print(f"总结: {full_summary['combined_summary']}")

for chapter in full_summary['timeline']['chapters']:
    print(f"  - [{chapter['start']}-{chapter['end']}] {chapter['title']}")
```

详细示例：`examples/video_summary_example.py`

---

## 总结

### ✅ 新增能力

1. **多帧视觉融合** - VLM 综合分析多帧，理解整体主题
2. **智能章节划分** - LLM 自动识别逻辑断点
3. **关键要点提取** - 提取知识点和操作步骤
4. **三层检索粒度** - 视频 → 章节 → 事件

### 🎯 核心价值

- **提升检索准召率**：粗粒度总结 + 细粒度事件双保险
- **改善用户体验**：结构化展示 + 章节导航
- **支持快速预览**：无需观看全片即可了解内容

### 📈 效果对比

| 场景 | 原方案 | 新方案 |
|------|--------|--------|
| "找 Nginx 教程" | 返回事件片段列表 | 返回视频卡片 + 章节目录 |
| "学习代理配置" | 只匹配转写文本 | 匹配章节标题 + 关键要点 |
| 用户体验 | 需要逐个点开片段 | 章节导航 + 一键跳转 |

**从"找到片段"到"理解整体"！** 🎉
