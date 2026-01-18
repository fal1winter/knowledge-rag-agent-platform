# 部署指南

本服务采用 adapter 驱动架构。本地部署可以在不启动 Milvus、Elasticsearch、Neo4j、
DeepSeek (硅基流动)、本地 Qwen、OCR、ASR 的情况下启动 API 并检视完整请求链路。
生产部署保持相同入口，替换 adapter 实现或将其指向真实服务即可。

## 本地 API 启动

```bash
cd /home/sun/knowledge-rag-agent-platform
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
pip install -e .
cp .env.example .env
uvicorn rag_agent_platform.api.run:app --host 0.0.0.0 --port 8080
```

健康检查：

```bash
curl http://127.0.0.1:8080/health
```

无外部依赖的离线流程：

```bash
PYTHONPATH=src python3 examples/offline_flow.py
```

## 运行时配置

通过环境变量或进程管理器配置服务地址：

```bash
export MILVUS_URI=http://127.0.0.1:19530
export ELASTICSEARCH_URL=http://127.0.0.1:9200
export NEO4J_URI=bolt://127.0.0.1:7687
# 本地 Qwen（简单任务：意图分类、查询改写）
export QWEN_REWRITE_ENDPOINT=http://127.0.0.1:8000/v1
export QWEN_INTENT_ENDPOINT=http://127.0.0.1:8001/v1
# DeepSeek via 硅基流动（复杂任务：回答生成、RAPTOR 摘要、质量评估）
export DEEPSEEK_ENDPOINT=https://api.siliconflow.cn/v1
export DEEPSEEK_API_KEY=sk-your-siliconflow-key
export DEEPSEEK_MODEL=deepseek-ai/DeepSeek-V3
# 其他服务
export OCR_ENDPOINT=http://127.0.0.1:8090/ocr
export ASR_ENDPOINT=http://127.0.0.1:8091/transcribe
```

默认的 bootstrap 注册了解析器、切片器、RAPTOR 树构建器、Milvus 稠密 adapter、
Elasticsearch BM25 adapter、Neo4j adapter、DeepSeek/Qwen 双模型 adapter、精排器、路由器和回答服务。
图片 OCR 和媒体 ASR 通过 `bootstrap.py` 中的 HTTP 客户端接入。
旧版 `.doc` 和 `.ppt` 文件通过 LibreOffice `soffice` 转换后再提取。

## 外部服务

- Milvus：叶子 chunk 和 RAPTOR 摘要节点的向量索引。
- Elasticsearch：叶子 chunk 的 BM25 稀疏索引。
- Neo4j：实体和关系图，用于多跳检索。
- DeepSeek (硅基流动)：回答生成、RAPTOR 摘要、质量评估等复杂推理任务。
- Qwen/vLLM (本地部署)：意图分类 (1.5B QLoRA) 和查询改写 (7B QLoRA)。
- OCR/ASR：解析侧 adapter，用于图片、音频和视频资产。

adapter 文件已展示调用位置和请求格式。实际部署时，将
`retrieval/*_adapter.py`、`generation/llm_client.py` 及 OCR/ASR 客户端
接入目标环境的 SDK 或 HTTP 服务即可。

## 进程管理

systemd unit 示例：

```ini
[Unit]
Description=Knowledge RAG Agent Platform
After=network.target

[Service]
WorkingDirectory=/home/sun/knowledge-rag-agent-platform
EnvironmentFile=/home/sun/knowledge-rag-agent-platform/.env
ExecStart=/home/sun/knowledge-rag-agent-platform/.venv/bin/uvicorn rag_agent_platform.api.run:app --host 0.0.0.0 --port 8080
Restart=always
RestartSec=3

[Install]
WantedBy=multi-user.target
```

## 入库与查询接口

摄入一个文档：

```bash
curl -X POST http://127.0.0.1:8080/api/knowledge/ingest \
  -H 'Content-Type: application/json' \
  -d '{"document_id":"doc-1","tenant_id":"tenant-a","uri":"/path/to/doc.md","file_type":"md","title":"doc"}'
```

提问：

```bash
curl -X POST http://127.0.0.1:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"tenant_id":"tenant-a","user_id":"user-a","message":"/agentic 比较 RAPTOR 和混合检索"}'
```

## Java 网关

Java 网关是面向浏览器的业务入口，负责用户积分、素材购买、支付宝/微信支付创建与回调、
素材订单金额校验和 RAG 转发。

```bash
cd /home/sun/knowledge-rag-agent-platform/java-gateway
export RAG_API_BASE_URL=http://127.0.0.1:8080
export USER_CREDIT_ENDPOINT=http://127.0.0.1:7013
export MATERIAL_ORDER_ENDPOINT=http://127.0.0.1:7010
export ALIPAY_ENABLED=false
export WECHAT_PAY_ENABLED=false
mvn spring-boot:run
```

支付环境变量：

```bash
export ALIPAY_APP_ID=
export ALIPAY_MERCHANT_PRIVATE_KEY=
export ALIPAY_PUBLIC_KEY=
export ALIPAY_NOTIFY_URL=https://your-domain/api/payments/alipay/notify
export ALIPAY_RETURN_URL=https://your-domain/payments/alipay/return
export WECHAT_PAY_APP_ID=
export WECHAT_PAY_MCH_ID=
export WECHAT_PAY_MCH_SERIAL_NO=
export WECHAT_PAY_API_V3_KEY=
export WECHAT_PAY_PRIVATE_KEY_PATH=/secure/path/apiclient_key.pem
export WECHAT_PAY_NOTIFY_URL=https://your-domain/api/payments/wechat/notify
```

## 前端

```bash
cd /home/sun/knowledge-rag-agent-platform/frontend
cp .env.example .env.local
npm install
npm run serve
```

前端通过 `/api` 调用 Java 网关，包含素材列表、素材上传、素材详情购买、订单列表支付和素材对话视图。
