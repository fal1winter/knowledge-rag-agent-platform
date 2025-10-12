"""FastAPI 应用工厂。

提供文档摄入、对话问答和评测的 HTTP 接口。
"""

import os
from typing import Dict, List

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from rag_agent_platform.generation.agent import KnowledgeRagAgent
from rag_agent_platform.ingestion.pipeline import KnowledgeIngestionPipeline
from rag_agent_platform.models import DocumentAsset, QueryRequest


class ChatPayload(BaseModel):
    tenant_id: str
    user_id: str
    message: str
    session_id: str | None = None
    material_ids: List[str] | None = None
    force_agentic: bool = False


class IngestPayload(BaseModel):
    document_id: str
    tenant_id: str
    uri: str
    file_type: str
    title: str = ""
    metadata: Dict = Field(default_factory=dict)


def create_app(
    agent: KnowledgeRagAgent | None = None,
    ingestion_pipeline: KnowledgeIngestionPipeline | None = None,
) -> FastAPI:
    app = FastAPI(title="Knowledge RAG Agent Platform", version="0.1.0")

    # 网关信任校验：仅接受携带内部共享密钥的请求，防止绕过网关直连
    _gateway_secret = os.getenv("GATEWAY_INTERNAL_SECRET", "")

    @app.middleware("http")
    async def verify_gateway_token(request: Request, call_next):
        # 健康检查放行
        if request.url.path == "/health":
            return await call_next(request)
        # 未配置密钥时跳过校验（本地开发模式）
        if not _gateway_secret:
            return await call_next(request)
        token = request.headers.get("X-Gateway-Secret", "")
        if token != _gateway_secret:
            return JSONResponse(
                status_code=403,
                content={"code": 403, "message": "禁止直连，请通过网关访问"},
            )
        return await call_next(request)

    @app.get("/health")
    def health() -> Dict[str, str]:
        return {"status": "ok", "service": "knowledge-rag-agent-platform"}

    @app.post("/api/knowledge/ingest")
    def ingest(payload: IngestPayload) -> Dict[str, str]:
        asset = DocumentAsset(
            document_id=payload.document_id,
            uri=payload.uri,
            file_type=payload.file_type,
            tenant_id=payload.tenant_id,
            title=payload.title,
            metadata=payload.metadata,
        )
        if ingestion_pipeline is None:
            return {"status": "accepted", "document_id": asset.document_id}
        result = ingestion_pipeline.ingest(asset)
        return {
            "status": "indexed",
            "document_id": result.document_id,
            "leaf_chunks": str(result.leaf_chunks),
            "raptor_nodes": str(result.raptor_nodes),
        }

    @app.post("/api/chat")
    def chat(payload: ChatPayload) -> Dict:
        if agent is None:
            return {"error": "agent not wired; use bootstrap.py to assemble dependencies"}
        request = QueryRequest(
            tenant_id=payload.tenant_id,
            user_id=payload.user_id,
            message=payload.message,
            session_id=payload.session_id,
            material_ids=payload.material_ids,
            force_agentic=payload.force_agentic,
        )
        answer = agent.handle(request)
        return {
            "answer": answer.text,
            "route": answer.route.__dict__,
            "citations": answer.citations,
            "debug": answer.debug,
        }

    @app.post("/api/evaluation/run")
    def run_evaluation() -> Dict[str, str]:
        return {"status": "accepted", "pipeline": "rules + llm-as-judge + pairwise-ab + badcase"}

    return app
