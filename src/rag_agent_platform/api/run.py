"""ASGI entrypoint for uvicorn."""

from rag_agent_platform.api.app import create_app
from rag_agent_platform.bootstrap import build_runtime


agent, ingestion_pipeline = build_runtime()
app = create_app(agent=agent, ingestion_pipeline=ingestion_pipeline)
