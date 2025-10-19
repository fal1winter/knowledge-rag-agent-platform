# 架构说明

本项目为独立的知识付费 RAG Agent 平台。外部基础设施均以 adapter 形式接入，
Milvus、Elasticsearch、Neo4j、Qwen、OCR、ASR 等服务在本地审查时无需实际启动。

## 主流程

0. `frontend` 通过 `java-gateway` 处理用户身份、积分、素材购买、支付和 RAG 对话转发。
1. `documents.loaders` 解析 PDF、Word、PPT、Excel、图片、音频和视频。
2. `documents.chunking` 生成段落感知的语义切片。
3. `ingestion.pipeline` 将叶子 chunk 和 RAPTOR 节点写入检索存储。
4. `raptor.tree_index` 自底向上聚类构建摘要节点树。
5. `routing.intent_router` 执行三级路由：
   控制指令 -> 关键词规则 -> 轻量模型。
6. `retrieval.precise` 面向简单问答提供紧凑叶子块检索。
7. `retrieval.hybrid` 调用 Milvus 稠密检索和 Elasticsearch BM25，
   经 RRF 融合后由 BGE 精排。
8. `retrieval.neo4j_adapter` 处理实体关系多跳查询。
9. `retrieval.agentic` 执行复杂推理的迭代检索。
10. `generation.agent` 编排路由、检索与有据回答生成。
11. `evaluation.pipeline` 运行规则过滤、LLM-as-Judge、A/B 对比和 Bad Case 归因。
12. `memory.layered_memory` 分离短期记忆、会话摘要和长期用户记忆，支持权重遗忘。
13. `knowledge.freshness` 巡检过期公共知识并标记刷新/重索引。

## 引入的既有模块

`legacy/` 目录保留了现有素材/RAG 源模块作为集成上下文。
它们默认不被导入，也不会修改正在运行的服务。

## 部署

参见 `DEPLOYMENT.md` 了解本地 API 启动、环境变量、外部服务接入和进程管理配置。

## 网关与前端

`java-gateway` 保持 Java 业务入口与原有系统对齐：用户积分委托给用户服务上下文，
素材购买通过素材订单回调完成，支付宝/微信支付通过 SDK 暴露创建和回调路径。
`frontend` 为最小 Vue 壳，覆盖素材列表、上传、购买、订单和素材对话。
