"""Document ingestion pipeline."""

from rag_agent_platform.ingestion.dedup import DocumentDeduplicator, FingerprintRecord  # noqa: F401
from rag_agent_platform.ingestion.pipeline import (  # noqa: F401
    BatchIngestionReport,
    DeadLetter,
    IngestionResult,
    KnowledgeIngestionPipeline,
)
