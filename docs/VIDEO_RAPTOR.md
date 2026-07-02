# 视频 RAPTOR 树状索引

## 概述

将 **RAPTOR（Recursive Abstractive Processing for Tree-Organized Retrieval）** 应用到视频上，构建层次化的树状索引。

提供两种构建策略：
- **Sequential RAPTOR**（时间序列，默认推荐）：按时间顺序分块，保持线性结构
- **Clustering RAPTOR**（语义聚类）：按语义相似度聚类，发现主题关联

详细对比请参考：[VIDEO_RAPTOR_STRATEGIES.md](VIDEO_RAPTOR_STRATEGIES.md)

---

## 为什么需要视频 RAPTOR？

### 问题

**传统方式**：只检索叶子节点（原始事件片段）

```
用户查询："这个视频讲了什么"
  ↓
检索 50 个事件片段
  → 返回：[00:15] 打开终端
  → 返回：[00:45] 输入命令
  → 返回：[01:20] 查看输出
  ...
```

❌ 问题：
- 返回的都是细碎片段，看不到整体
- 粗粒度查询（"讲了什么"）只能匹配到细节
- 无法快速定位到相关章节

### 解决方案：RAPTOR 树

**层次化索引**：从细节到整体的多层抽象

```
L3 (根节点) ─ 视频总结："Nginx 配置教程，演示安装、配置、测试全流程"
    │
    ├─ L2 (章节) ─ "环境准备与安装" [0-150s]
    │     │
    │     ├─ L1 (场景) ─ "检查系统环境" [0-30s]
    │     │     │
    │     │     ├─ L0 ─ [5s] 打开终端
    │     │     ├─ L0 ─ [10s] 查看系统版本
    │     │     └─ L0 ─ [15s] 检查端口占用
    │     │
    │     └─ L1 (场景) ─ "安装 Nginx" [30-150s]
    │           └─ ...
    │
    ├─ L2 (章节) ─ "反向代理配置" [150-300s]
    │     └─ ...
    │
    └─ L2 (章节) ─ "测试与验证" [300-420s]
          └─ ...
```

✅ 优势：
- **多粒度检索**：粗查找（L2章节）+ 细下钻（L0事件）
- **自动发现结构**：k-means 聚类自动识别章节
- **适应不同视频**：短视频2层，长视频4层

---

## 树结构

### 层级定义

| 层级 | 名称 | 粒度 | 时间跨度 | 数量（10分钟视频） |
|------|------|------|----------|-------------------|
| **L0** | 叶子节点 | 原始事件 | 2-5秒 | ~50个 |
| **L1** | 场景摘要 | 多个相关事件 | 30-60秒 | ~10个 |
| **L2** | 章节摘要 | 多个场景 | 2-3分钟 | ~3个 |
| **L3** | 视频总结 | 全视频 | 完整视频 | 1个（根节点）|

### 节点结构

```python
@dataclass
class VideoRaptorNode:
    node_id: str              # "video:vid:L2:0"
    level: int                # 0=叶子, 1+=摘要层
    summary: str              # 节点摘要文本
    time_range: (float, float) # (start, end) 时间范围
    children_ids: List[str]   # 子节点 ID
    embeddings: np.ndarray    # 节点向量
    metadata: dict            # 元数据
```

---

## 构建流程

### 1. 创建叶子节点（L0）

```python
# 每个事件 → 一个叶子节点
L0 节点:
  - summary: "语音: 打开终端 | 画面: 终端界面"
  - time_range: (5.0, 8.0)
  - embeddings: [0.1, 0.2, ...]
```

### 2. 聚类 + 摘要（L1）

```python
# 1. 对 L0 节点向量进行 k-means 聚类
kmeans = KMeans(n_clusters=10)
labels = kmeans.fit_predict(embeddings)

# 2. 每个簇生成一个 L1 场景节点
簇0: [L0:0, L0:1, L0:2] → L1:0 "检查系统环境"
簇1: [L0:3, L0:4, L0:5] → L1:1 "安装 Nginx"
...

# 3. 用 LLM 生成场景摘要
prompt = "以下事件发生在同一场景，用1-2句话总结："
summary = llm.generate(prompt + children_texts)
```

### 3. 递归构建（L2, L3...）

```python
# 重复聚类 + 摘要过程
L1 节点 → k-means → L2 章节节点
L2 节点 → k-means → L3 总结节点（根）

# 终止条件：
# - 节点数 <= min_cluster_size (默认2)
# - 达到 max_levels (默认3)
```

---

## 使用方法

### 构建索引

#### 方式1：Sequential RAPTOR（推荐）

```python
from rag_agent_platform.video import build_video_raptor_index

# 时间序列索引（默认）
nodes = build_video_raptor_index(
    timeline=timeline,
    video_id="tutorial_001",
    embedding_client=embedding_client,
    summarizer_llm=llm_client,
    strategy="sequential",  # 默认策略
    scene_window=30.0,      # 30秒一个场景
    chapter_window=120.0    # 2分钟一个章节
)

# 输出：
# 🌲 构建 Sequential RAPTOR 树（时间序列）...
# ✓ RAPTOR 树构建完成 (sequential):
#   L0: 50 个节点
#   L1: 10 个节点
#   L2: 3 个节点
#   L3: 1 个节点
```

#### 方式2：Clustering RAPTOR（可选）

```python
# 语义聚类索引
nodes = build_video_raptor_index(
    timeline=timeline,
    video_id="tutorial_001",
    embedding_client=embedding_client,
    summarizer_llm=llm_client,
    strategy="clustering",  # 聚类策略
    cluster_size=5,
    max_levels=3
)

# 输出：
# 🌲 构建 Clustering RAPTOR 树（语义聚类）...
# ✓ RAPTOR 树构建完成 (clustering):
#   L0: 50 个节点
#   L1: 10 个节点
#   L2: 3 个节点
#   L3: 1 个节点
```

### 检索策略

#### 策略1：单层检索

```python
from rag_agent_platform.video import VideoRaptorRetriever

retriever = VideoRaptorRetriever(embedding_client)

# 只搜索章节层（粗粒度）
results = retriever.search(
    query="视频讲了什么",
    nodes=nodes,
    top_k=3,
    level=2  # L2 章节
)

# 返回：
# 1. [0-150s] 环境准备与 Nginx 安装
# 2. [150-300s] 反向代理配置
# 3. [300-420s] 测试与验证
```

#### 策略2：Drill-down 下钻（推荐）⭐

```python
# 从高层开始，逐层下钻到叶子
results = retriever.search_with_drill_down(
    query="如何配置反向代理",
    nodes=nodes,
    start_level=2,        # 从章节层开始
    drill_down_top_k=2    # 每层返回top2
)

# 流程：
# L2: 匹配到 "反向代理配置" 章节
#   ↓
# L1: 在该章节的子节点中匹配 "编辑配置文件" 场景
#   ↓
# L0: 在该场景的子节点中匹配 [155s] "添加 proxy_pass 指令"

# 返回：L0 叶子节点（经过章节过滤，更精准）
```

#### 策略3：混合检索（多粒度）

```python
# 同时检索多个层级
results = []
for level in [2, 1, 0]:  # 章节 → 场景 → 事件
    level_results = retriever.search(query, nodes, top_k=2, level=level)
    results.extend(level_results)

# 返回：
# - L2: 章节概览（用户想了解整体）
# - L1: 场景细节（用户想了解步骤）
# - L0: 精准时刻（用户想跳转播放）
```

---

## 检索对比

### 场景：用户查询 "如何配置 Nginx"

#### 传统方式（平铺检索）

```
检索 L0 所有事件 (50个节点)
  ↓
返回 top-5:
  1. [155s] 打开 nginx.conf 文件
  2. [165s] 添加 proxy_pass 指令
  3. [180s] 设置转发规则
  4. [35s] 安装 Nginx
  5. [200s] 保存配置文件
```

❌ 问题：
- 缺少上下文（不知道属于哪个章节）
- 片段跳跃（35s 和 155s 混在一起）
- 无法快速预览

#### RAPTOR 方式（层次检索）

```
Drill-down 检索:
  L2 匹配: "反向代理配置" 章节 [150-300s]
    ↓
  L1 匹配: "编辑配置文件" 场景 [150-180s]
    ↓
  L0 匹配:
    1. [155s] 打开 nginx.conf
    2. [165s] 添加 proxy_pass 指令
    3. [180s] 设置转发规则
```

✅ 优势：
- ✅ 有上下文（知道在"反向代理配置"章节）
- ✅ 连贯性（都在同一场景内）
- ✅ 可快速预览（展示章节 + 场景 + 事件三层）

---

## 前端展示

### 多层级结果展示

```html
<!-- L2: 章节卡片 -->
<div class="chapter-card">
  <h3>第2章：反向代理配置 [2:30-5:00]</h3>
  <p>本章节演示如何编辑 nginx.conf 配置文件，添加 proxy_pass 指令...</p>
  <button @click="expandChapter(2)">展开场景 ▼</button>
</div>

<!-- L1: 场景列表（折叠） -->
<div v-if="expandedChapter === 2" class="scenes">
  <div class="scene">
    <h4>场景1：编辑配置文件 [2:30-3:00]</h4>
    <button @click="expandScene(1)">展开事件 ▼</button>
  </div>
  
  <!-- L0: 事件列表（折叠） -->
  <div v-if="expandedScene === 1" class="events">
    <div class="event">
      <span class="time">[2:35]</span>
      <span class="desc">打开 nginx.conf</span>
      <button @click="jumpTo(155)">▶️</button>
    </div>
    <div class="event">
      <span class="time">[2:45]</span>
      <span class="desc">添加 proxy_pass 指令</span>
      <button @click="jumpTo(165)">▶️</button>
    </div>
  </div>
</div>
```

### 时间轴导航

```
视频进度条：
├─────────────┼─────────────┼─────────────┤
0:00        2:30         5:00         7:00

章节标记：
  │            │             │
  L2:0         L2:1          L2:2
  安装         配置          测试

场景标记（鼠标悬停显示）：
  │ │ │        │ │           │ │
  L1:0-2      L1:3-4        L1:5-6
```

---

## 入库与检索

### 入库策略

```python
# 所有层级都入库
for node in nodes:
    chunk = {
        "chunk_id": node.node_id,
        "text": node.summary,
        "embeddings": node.embeddings,
        "metadata": {
            "level": node.level,
            "time_range": node.time_range,
            "children_ids": node.children_ids,
            "video_id": "tutorial_001"
        }
    }
    dense_index.insert(chunk)
```

### 检索策略对比

| 策略 | 适用场景 | 优势 | 缺点 |
|------|---------|------|------|
| **L0 单层** | "如何添加指令"（具体操作） | 精准 | 缺少上下文 |
| **L2 单层** | "视频讲了什么"（整体了解） | 快速预览 | 不够具体 |
| **Drill-down** | 大部分场景（推荐）⭐ | 自动下钻到合适粒度 | 需要多次检索 |
| **混合检索** | 复杂查询 | 多粒度结果 | 返回数量较多 |

---

## 性能与成本

### 构建成本

| 视频时长 | L0节点数 | L1节点数 | L2节点数 | LLM调用次数 | 成本 |
|---------|---------|---------|---------|-----------|------|
| 3分钟 | 18 | 4 | 1 | 5 | $0.01 |
| 10分钟 | 50 | 10 | 3 | 14 | $0.03 |
| 30分钟 | 180 | 36 | 9 | 46 | $0.10 |

**公式**：`LLM调用次数 ≈ (L1节点数 + L2节点数 + ...)`

### 检索性能

| 检索策略 | 向量检索次数 | 响应时间 |
|---------|------------|---------|
| L0 单层 | 1次 | ~50ms |
| Drill-down (L2→L0) | 3次 | ~150ms |
| 混合检索 (L2+L1+L0) | 3次 | ~150ms |

---

## 优化建议

### 1. 聚类参数调优

```python
builder = VideoRaptorBuilder(
    cluster_size=5,      # 每层聚类大小（越大越粗粒度）
    max_levels=3,        # 最大层数
    min_cluster_size=2   # 最小聚类大小
)

# 短视频（<5分钟）：cluster_size=3, max_levels=2
# 长视频（>30分钟）：cluster_size=10, max_levels=4
```

### 2. 降级策略

```python
# 超短视频（<2分钟）：跳过 RAPTOR，直接用事件列表
if video_duration < 120:
    skip_raptor = True

# 事件过少（<10个）：只构建1层
if len(events) < 10:
    max_levels = 1
```

### 3. 缓存策略

```python
# 缓存 RAPTOR 树（避免重复构建）
cache_key = f"raptor:{video_id}"
if redis.exists(cache_key):
    nodes = pickle.loads(redis.get(cache_key))
else:
    nodes = build_video_raptor_index(...)
    redis.set(cache_key, pickle.dumps(nodes))
```

---

## 对比：文档 RAPTOR vs 视频 RAPTOR

| 维度 | 文档 RAPTOR | 视频 RAPTOR |
|------|------------|-----------|
| **输入** | 文本块 | 视频事件（场景+音频） |
| **聚类依据** | 语义相似度 | 语义相似度 + 时序连续性 |
| **摘要目标** | 提炼关键信息 | 描述"做了什么" |
| **层级名称** | 段落 → 小节 → 章节 | 事件 → 场景 → 章节 |
| **时间维度** | 无 | 有（time_range） |
| **检索方式** | 语义匹配 | 语义匹配 + 时间跳转 |

---

## 总结

### ✅ 核心价值

1. **多粒度检索**：从整体到细节的自然下钻
2. **自动章节划分**：k-means 聚类自动发现结构
3. **改善检索体验**：有上下文、有连贯性、可预览
4. **适应长视频**：层级随视频长度自动调整

### 📈 效果对比

| 查询类型 | 传统检索 | RAPTOR 检索 |
|---------|---------|------------|
| "视频讲了什么" | 返回细碎片段 | 返回章节概览 ✅ |
| "如何配置" | 混杂无关片段 | Drill-down 精准定位 ✅ |
| "第二部分讲什么" | 无法理解"第二部分" | 直接返回 L2:1 章节 ✅ |

### 🚀 使用建议

- **短视频（<5分钟）**：只构建 L0+L1 两层
- **中等视频（5-15分钟）**：L0+L1+L2 三层（推荐）
- **长视频（>15分钟）**：L0+L1+L2+L3 四层
- **超长视频（>1小时）**：考虑先分段，再对每段构建 RAPTOR

**从"平铺检索"到"层次理解"！** 🌲
