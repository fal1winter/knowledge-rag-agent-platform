"""RAG 平台跨模块共享的领域模型。"""

from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum
from typing import Any, Dict, List, Optional


class IntentType(str, Enum):
    CONTROL = "control"
    DIRECT_QA = "direct_qa"
    ENTITY_RELATION = "entity_relation"
    COMPLEX_REASONING = "complex_reasoning"
    MATERIAL_SEARCH = "material_search"
    UNKNOWN = "unknown"


class RetrievalStrategy(str, Enum):
    PRECISE_BLOCK = "precise_block"
    GRAPH_MULTI_HOP = "graph_multi_hop"
    ITERATIVE = "iterative"
    RAPTOR = "raptor"
    HYBRID = "hybrid"


@dataclass
class DocumentAsset:
    document_id: str
    uri: str
    file_type: str
    tenant_id: str
    title: str = ""
    metadata: Dict[str, Any] = field(default_factory=dict)


@dataclass
class ParsedDocument:
    asset: DocumentAsset
    text: str
    pages: List[Dict[str, Any]] = field(default_factory=list)
    media_transcripts: List[Dict[str, Any]] = field(default_factory=list)
    extracted_tables: List[Dict[str, Any]] = field(default_factory=list)


@dataclass
class Chunk:
    chunk_id: str
    document_id: str
    text: str
    tenant_id: str
    page: Optional[int] = None
    level: int = 0
    parent_id: Optional[str] = None
    child_ids: List[str] = field(default_factory=list)
    vector: Optional[List[float]] = None
    metadata: Dict[str, Any] = field(default_factory=dict)


@dataclass
class RetrievalHit:
    chunk_id: str
    document_id: str
    text: str
    score: float
    source: str
    tenant_id: str
    citation: Dict[str, Any] = field(default_factory=dict)
    metadata: Dict[str, Any] = field(default_factory=dict)


@dataclass
class QueryRequest:
    tenant_id: str
    user_id: str
    message: str
    session_id: Optional[str] = None
    material_ids: Optional[List[str]] = None
    persistent_context: bool = False
    force_agentic: bool = False
    metadata: Dict[str, Any] = field(default_factory=dict)


@dataclass
class RouteDecision:
    intent: IntentType
    strategy: RetrievalStrategy
    normalized_query: str
    confidence: float
    command: Optional[str] = None
    entities: List[str] = field(default_factory=list)
    reasoning: str = ""


@dataclass
class Answer:
    text: str
    route: RouteDecision
    citations: List[Dict[str, Any]]
    retrieval_hits: List[RetrievalHit]
    debug: Dict[str, Any] = field(default_factory=dict)


@dataclass
class EvalCase:
    case_id: str
    query: str
    answer: str
    hits: List[RetrievalHit]
    expected_facts: List[str] = field(default_factory=list)
    metadata: Dict[str, Any] = field(default_factory=dict)


@dataclass
class EvalScore:
    case_id: str
    relevance: float
    faithfulness: float
    completeness: float
    citation_quality: float
    passed: bool
    issues: List[str] = field(default_factory=list)


@dataclass
class MemoryItem:
    memory_id: str
    tenant_id: str
    user_id: str
    content: str
    layer: str
    weight: float
    created_at: datetime = field(default_factory=datetime.utcnow)
    updated_at: datetime = field(default_factory=datetime.utcnow)
    metadata: Dict[str, Any] = field(default_factory=dict)

