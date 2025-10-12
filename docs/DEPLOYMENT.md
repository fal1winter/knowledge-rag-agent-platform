# Deployment

This service is adapter-driven. Local deployment can start the API and inspect the full request path without Milvus, Elasticsearch, Neo4j, Qwen, OCR, or ASR being live. Production deployment keeps the same entrypoint and replaces adapter implementations or points them to real services.

## Local API

```bash
cd /home/sun/knowledge-rag-agent-platform
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
pip install -e .
cp .env.example .env
uvicorn rag_agent_platform.api.run:app --host 0.0.0.0 --port 8080
```

Health check:

```bash
curl http://127.0.0.1:8080/health
```

Offline flow without external services:

```bash
PYTHONPATH=src python3 examples/offline_flow.py
```

## Runtime Configuration

Configure service addresses with environment variables or a process manager:

```bash
export MILVUS_URI=http://127.0.0.1:19530
export ELASTICSEARCH_URL=http://127.0.0.1:9200
export NEO4J_URI=bolt://127.0.0.1:7687
export QWEN_REWRITE_ENDPOINT=http://127.0.0.1:8000/v1
export QWEN_INTENT_ENDPOINT=http://127.0.0.1:8001/v1
export QWEN_ANSWER_ENDPOINT=http://127.0.0.1:8002/v1
export OCR_ENDPOINT=http://127.0.0.1:8090/ocr
export ASR_ENDPOINT=http://127.0.0.1:8091/transcribe
```

The default bootstrap registers parser, chunker, RAPTOR tree builder, Milvus dense adapter, Elasticsearch BM25 adapter, Neo4j adapter, Qwen adapters, reranker, router, and answer service. Image OCR and media ASR are wired through HTTP clients in `bootstrap.py`. Legacy `.doc` and `.ppt` files are converted through LibreOffice `soffice` before extraction.

## External Services

- Milvus: vector index for leaf and RAPTOR summary nodes.
- Elasticsearch: BM25 sparse index for leaf chunks.
- Neo4j: entity and relationship graph for multi-hop retrieval.
- Qwen/vLLM: intent classification, query rewriting, and answer generation endpoints.
- OCR/ASR: parser-side adapters for image, audio, and video assets.

The adapter files already show the call sites and request shapes. For a real deployment, wire `retrieval/*_adapter.py`, `generation/llm_client.py`, and the OCR/ASR clients to the concrete SDKs or HTTP services used in the target environment.

## Process Manager

Example systemd unit:

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

## Ingestion And Query APIs

Ingest one document:

```bash
curl -X POST http://127.0.0.1:8080/api/knowledge/ingest \
  -H 'Content-Type: application/json' \
  -d '{"document_id":"doc-1","tenant_id":"tenant-a","uri":"/path/to/doc.md","file_type":"md","title":"doc"}'
```

Ask a question:

```bash
curl -X POST http://127.0.0.1:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"tenant_id":"tenant-a","user_id":"user-a","message":"/agentic 比较 RAPTOR 和混合检索"}'
```


## Java Gateway

The Java gateway is the browser-facing business entrypoint for user credits, material purchase, Alipay/WeChat payment creation and payment callback handling, material-order amount validation, and RAG forwarding.

```bash
cd /home/sun/knowledge-rag-agent-platform/java-gateway
export RAG_API_BASE_URL=http://127.0.0.1:8080
export USER_CREDIT_ENDPOINT=http://127.0.0.1:7013
export MATERIAL_ORDER_ENDPOINT=http://127.0.0.1:7010
export ALIPAY_ENABLED=false
export WECHAT_PAY_ENABLED=false
mvn spring-boot:run
```

Payment environment variables:

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

## Frontend

```bash
cd /home/sun/knowledge-rag-agent-platform/frontend
cp .env.example .env.local
npm install
npm run serve
```

The frontend calls the Java gateway through `/api`. It includes material listing, material upload, material detail purchase, order list payment, and material chat views.
