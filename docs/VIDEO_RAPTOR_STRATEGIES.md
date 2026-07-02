# 视频 RAPTOR 两种构建策略

## 概述

提供两种 RAPTOR 树构建策略，分别适用于不同的检索场景。

---

## 1. Sequential RAPTOR（时间序列索引）⭐ 推荐

### 核心思想

**按时间顺序分块，保持视频的线性结构和连续性。**

### 构建方式

```python
from rag_agent_platform.video import build_video_raptor_index

nodes = build_video_raptor_index(
    timeline=timeline,
    video_id="video_001",
    embedding_client=embedding_client,
    summarizer_llm=llm,
    strategy="sequential",  # 时间序列策略（默认）
    scene_window=30.0,      # L1: 每30秒一个场景
    chapter_window=120.0    # L2: 每2分钟一个章节
)
```

### 层级结构

```
L3 (根) ─ 整个视频总结 [0-420s]
  │
  ├─ L2 (章节1) ─ [0-120s] 环境准备与安装
  │     │
  │     ├─ L1 (场景1) ─ [0-30s] 检查系统环境
  │     │     ├─ L0 ─ [5s] 打开终端
  │     │     ├─ L0 ─ [10s] 查看系统版本
  │     │     └─ L0 ─ [15s] 检查端口
  │     │
  │     ├─ L1 (场景2) ─ [30-60s] 下载安装包
  │     ├─ L1 (场景3) ─ [60-90s] 安装 Nginx
  │     └─ L1 (场景4) ─ [90-120s] 启动服务
  │
  ├─ L2 (章节2) ─ [120-240s] 反向代理配置
  │     ├─ L1 (场景1) ─ [120-150s] 打开配置文件
  │     ├─ L1 (场景2) ─ [150-180s] 编辑 proxy_pass
  │     └─ ...
  │
  └─ L2 (章节3) ─ [240-420s] 测试与验证
```

### 核心特点

- ✅ **时间连续性**：每个节点的时间范围是连续的
- ✅ **固定窗口**：按固定时间窗口划分（可配置）
- ✅ **线性结构**：符合视频从头到尾播放的特点
- ✅ **章节清晰**：第1章、第2章对应明确的时间段

### 适用场景

| 查询类型 | 示例 | 为什么适合 |
|---------|------|-----------|
| **时间范围查询** | "前5分钟讲了什么" | 直接定位到 L2:0 |
| **章节导航** | "第2章的内容" | L2:1 对应第2个连续时间段 |
| **连续流程** | "安装到配置的完整流程" | 按时间顺序返回 L2:0 → L2:1 |
| **时间点跳转** | "2:30-3:00 做了什么" | 快速定位到对应场景 |
| **上下文理解** | "配置之前做了什么" | 时间连续，前后关系清晰 |

### 检索示例

```python
retriever = VideoRaptorRetriever(embedding_client)

# 查询：前2分钟讲了什么
results = retriever.search("前2分钟", nodes, top_k=1, level=2)
# 返回: L2:0 [0-120s] 环境准备与安装

# 查询：如何配置反向代理
results = retriever.search_with_drill_down(
    "如何配置反向代理", nodes, start_level=2
)
# 路径: L2:1 [120-240s] → L1:2 [150-180s] → L0 事件
```

### 参数调优

| 视频时长 | scene_window | chapter_window | 说明 |
|---------|-------------|---------------|------|
| < 5分钟 | 15-20s | 60s | 短视频，细粒度场景 |
| 5-15分钟 | 30s | 120s | 标准配置（默认）|
| 15-60分钟 | 45-60s | 180-300s | 长视频，粗粒度章节 |
| > 1小时 | 60-90s | 300-600s | 超长视频 |

---

## 2. Clustering RAPTOR（语义聚类索引）

### 核心思想

**按语义相似度聚类，发现跨时间的主题关联。**

### 构建方式

```python
nodes = build_video_raptor_index(
    timeline=timeline,
    video_id="video_001",
    embedding_client=embedding_client,
    summarizer_llm=llm,
    strategy="clustering",  # 聚类策略
    cluster_size=5,         # 每层聚类大小
    max_levels=3
)
```

### 层级结构

```
L3 (根) ─ 整个视频总结
  │
  ├─ L2 (主题簇1) ─ 安装相关（包含 0-30s, 85-90s, 150-160s）
  │     ├─ L1 ─ 安装准备
  │     └─ L1 ─ 实际安装
  │
  ├─ L2 (主题簇2) ─ 配置相关（包含 40-80s, 100-140s, 200-250s）
  │     ├─ L1 ─ 基础配置
  │     └─ L1 ─ 高级配置
  │
  └─ L2 (主题簇3) ─ 测试验证（包含 160-180s, 300-350s）
```

### 核心特点

- ✅ **主题聚合**：语义相似的内容聚到一起
- ✅ **跨时间关联**：发现分散在不同时间点的相关内容
- ✅ **自动发现主题**：k-means 自动识别主题
- ⚠️ **时间不连续**：同一簇的内容可能跨越多个时间段

### 适用场景

| 查询类型 | 示例 | 为什么适合 |
|---------|------|-----------|
| **主题查询** | "所有关于配置的片段" | 返回配置主题簇（跨时间）|
| **主题概览** | "视频讲了哪几个主题" | 直接查看 L2 各个簇 |
| **跨章节检索** | "错误排查的所有内容" | 聚合分散的相关片段 |
| **内容分类** | "安装、配置、测试各讲了什么" | 自动主题分类 |

### 检索示例

```python
retriever = VideoRaptorRetriever(embedding_client)

# 查询：所有关于配置的内容
results = retriever.search("配置", nodes, top_k=1, level=2)
# 返回: L2:1 配置主题簇（包含多个不连续的时间段）

# 查询：视频的主题结构
level2_nodes = [n for n in nodes if n.level == 2]
for node in level2_nodes:
    print(f"主题: {node.summary}")
# 输出:
#   主题: 环境检查与 Nginx 安装
#   主题: 配置文件编辑与代理设置
#   主题: 服务重启与验证测试
```

### 参数调优

| 视频长度 | cluster_size | 说明 |
|---------|-------------|------|
| < 5分钟 | 3-4 | 少量聚类 |
| 5-15分钟 | 5 | 标准配置 |
| 15-60分钟 | 8-10 | 增加聚类数 |

---

## 对比总结

| 维度 | Sequential RAPTOR | Clustering RAPTOR |
|------|------------------|-------------------|
| **构建依据** | 时间顺序 | 语义相似度 |
| **时间连续性** | ✅ 连续 | ❌ 可能不连续 |
| **章节定义** | 固定时间窗口 | 语义主题簇 |
| **前后关系** | ✅ 清晰 | ⚠️ 可能跳跃 |
| **主题发现** | ❌ 需要遍历 | ✅ 自动聚合 |
| **适合查询** | 时间范围、章节导航 | 主题查询、跨章节检索 |
| **用户习惯** | ✅ 符合线性播放 | ⚠️ 需要理解主题 |
| **构建速度** | 快（无聚类） | 慢（k-means） |
| **推荐程度** | ⭐⭐⭐⭐⭐ 默认 | ⭐⭐⭐ 补充 |

---

## 使用建议

### 方案1：只用 Sequential（推荐）

**大部分场景足够**：

```python
nodes = build_video_raptor_index(
    timeline, video_id, embedding_client, llm,
    strategy="sequential"  # 默认
)
```

**优势**：
- 构建快
- 符合用户观看习惯
- 时间导航清晰

### 方案2：同时构建两种索引

**同时入库，根据查询类型选择**：

```python
# 构建两种索引
nodes_seq = build_video_raptor_index(..., strategy="sequential")
nodes_cluster = build_video_raptor_index(..., strategy="clustering")

# 时间查询 → Sequential
if is_time_query(query):
    results = retriever.search(query, nodes_seq, ...)

# 主题查询 → Clustering
elif is_theme_query(query):
    results = retriever.search(query, nodes_cluster, ...)
```

**判断逻辑**：

```python
def is_time_query(query: str) -> bool:
    """判断是否为时间范围查询。"""
    time_keywords = ["前", "后", "开始", "结束", "章", "分钟", "秒"]
    return any(kw in query for kw in time_keywords)

def is_theme_query(query: str) -> bool:
    """判断是否为主题查询。"""
    theme_keywords = ["所有", "全部", "哪些", "主题", "内容", "相关"]
    return any(kw in query for kw in theme_keywords)
```

### 方案3：根据视频类型选择

| 视频类型 | 推荐策略 | 原因 |
|---------|---------|------|
| **教程视频** | Sequential | 线性流程，按步骤讲解 |
| **会议录像** | Clustering | 主题跳跃，需要主题聚合 |
| **直播回放** | Sequential | 时间线性，用户想看某时段 |
| **知识合集** | Clustering | 多主题混合 |
| **剪辑视频** | Clustering | 时间线被打乱 |

---

## 实现细节

### Sequential 实现

```python
class SequentialVideoRaptorBuilder:
    def _merge_by_time_window(self, nodes, window_size):
        """按时间窗口合并（保持顺序）。"""
        merged = []
        current_window = []
        window_start_time = 0.0

        for node in nodes:
            if node.time_range[0] - window_start_time >= window_size:
                # 窗口满了，生成摘要
                if current_window:
                    merged.append(self._merge_sequence(current_window))
                current_window = [node]
                window_start_time = node.time_range[0]
            else:
                current_window.append(node)

        # 最后一个窗口
        if current_window:
            merged.append(self._merge_sequence(current_window))

        return merged
```

### Clustering 实现

```python
class ClusteringVideoRaptorBuilder:
    def _build_next_level(self, nodes, level):
        """k-means 聚类 + LLM 摘要。"""
        # 1. 提取向量
        embeddings = np.array([node.embeddings for node in nodes])

        # 2. k-means 聚类
        n_clusters = len(nodes) // self.cluster_size
        kmeans = KMeans(n_clusters=n_clusters)
        labels = kmeans.fit_predict(embeddings)

        # 3. 按簇分组
        clusters = {}
        for node, label in zip(nodes, labels):
            clusters.setdefault(label, []).append(node)

        # 4. 为每个簇生成摘要
        parent_nodes = []
        for cluster_id, cluster_nodes in clusters.items():
            parent = self._merge_nodes(cluster_nodes, level, cluster_id)
            parent_nodes.append(parent)

        return parent_nodes
```

---

## 性能对比

| 指标 | Sequential | Clustering |
|------|-----------|-----------|
| **构建时间（10分钟视频）** | ~2分钟 | ~3分钟 |
| **LLM 调用次数** | ~15次 | ~15次 |
| **构建成本** | $0.03 | $0.03 |
| **检索延迟** | ~50ms | ~50ms |
| **存储空间** | 相同 | 相同 |

**结论**：两者性能差异不大，主要区别在于索引结构。

---

## FAQ

### Q1: 能否自适应窗口大小？

**A**: 可以基于场景变化自适应：

```python
def adaptive_window(events):
    """基于场景变化得分自适应窗口。"""
    for i, event in enumerate(events):
        if event.scene_change_score > 0.7:
            # 场景变化大，这里切分
            yield events[last_cut:i]
            last_cut = i
```

### Q2: Sequential 能否发现主题？

**A**: 可以，但需要后处理：

```python
# 先构建 Sequential
nodes_seq = build_video_raptor_index(..., strategy="sequential")

# 对 L2 节点再做一次聚类
l2_nodes = [n for n in nodes_seq if n.level == 2]
# k-means 找主题
```

### Q3: 两种索引能否混合检索？

**A**: 可以：

```python
# 时间粗查（Sequential L2）
time_results = retriever.search(query, nodes_seq, level=2)

# 主题精查（Clustering L1）
theme_results = retriever.search(query, nodes_cluster, level=1)

# 合并去重
final_results = merge_and_deduplicate(time_results, theme_results)
```

---

## 总结

### 默认选择：Sequential ⭐

- 符合视频线性特点
- 时间导航清晰
- 用户理解成本低

### 补充选择：Clustering

- 需要跨时间主题查询时启用
- 作为辅助索引，提升召回率

### 最佳实践

**对于大多数视频 RAG 系统**：
1. 默认构建 Sequential 索引
2. 当用户明确查询"所有X的内容"时，按需构建 Clustering 索引
3. 或者同时维护两种索引，查询时智能选择

**从时间线性到主题聚合，灵活应对不同检索需求！** 🌲⏱️🎯
