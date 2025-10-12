"""独立项目依赖装配。"""

import json
from typing import Any, Tuple
import urllib.request

from rag_agent_platform.documents.chunking import SemanticChunker
from rag_agent_platform.config import load_config
from rag_agent_platform.documents.loaders import (
    AudioVideoLoader,
    DocumentIngestionService,
    ImageLoader,
    LocalOfficeConverter,
    PDFLoader,
    PlainTextLoader,
    PowerPointLoader,
    SpreadsheetLoader,
    WordLoader,
)
from rag_agent_platform.generation.agent import AgentDependencies, KnowledgeRagAgent
from rag_agent_platform.generation.answer_service import AnswerService
from rag_agent_platform.generation.llm_client import QwenChatAdapter
from rag_agent_platform.ingestion.entity_extractor import HeuristicEntityExtractor, LLMEntityExtractor
from rag_agent_platform.ingestion.pipeline import KnowledgeIngestionPipeline
from rag_agent_platform.raptor.retriever import RaptorRetriever
from rag_agent_platform.raptor.tree_index import (
    ExtractiveSummarizer,
    InMemoryRaptorStore,
    QwenRaptorSummarizer,
    RaptorTreeBuilder,
)
from rag_agent_platform.retrieval.agentic import AgenticRetriever, HeuristicQueryPlanner
from rag_agent_platform.retrieval.elasticsearch_adapter import ElasticsearchBM25Retriever
from rag_agent_platform.retrieval.embedding import BGEEmbeddingClient, HTTPEmbeddingClient
from rag_agent_platform.retrieval.hybrid import HybridRetriever
from rag_agent_platform.retrieval.milvus_adapter import MilvusDenseRetriever
from rag_agent_platform.retrieval.neo4j_adapter import LocalGraphRetriever, Neo4jGraphRetriever
from rag_agent_platform.retrieval.precise import PreciseBlockRetriever
from rag_agent_platform.retrieval.reranker import BGEReranker, HTTPRerankClient
from rag_agent_platform.routing.intent_router import IntentRouter, QwenIntentClassifier
from rag_agent_platform.routing.query_optimizer import QueryOptimizer, QwenRewriteAdapter
from rag_agent_platform.storage.in_memory import HashEmbeddingClient, InMemoryDenseBackend, InMemorySparseBackend


class HTTPOCRClient:
    def __init__(self, endpoint: str):
        self.endpoint = endpoint

    def extract_text(self, image_uri: str) -> str:
        payload = json.dumps({"uri": image_uri}).encode("utf-8")
        request = urllib.request.Request(
            self.endpoint,
            data=payload,
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        with urllib.request.urlopen(request, timeout=30) as response:
            body = json.loads(response.read().decode("utf-8"))
        return body.get("text", "")


class HTTPASRClient:
    def __init__(self, endpoint: str):
        self.endpoint = endpoint

    def transcribe(self, media_uri: str) -> str:
        payload = json.dumps({"uri": media_uri}).encode("utf-8")
        request = urllib.request.Request(
            self.endpoint,
            data=payload,
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        with urllib.request.urlopen(request, timeout=120) as response:
            body = json.loads(response.read().decode("utf-8"))
        return body.get("text", body.get("transcript", ""))


def build_document_parser() -> DocumentIngestionService:
    config = load_config()
    office_converter = LocalOfficeConverter()
    return DocumentIngestionService(
        [
            PlainTextLoader(),
            PDFLoader(),
            WordLoader(office_converter),
            PowerPointLoader(office_converter),
            SpreadsheetLoader(),
            ImageLoader(HTTPOCRClient(config.ocr_endpoint)),
            AudioVideoLoader(HTTPASRClient(config.asr_endpoint)),
        ]
    )


def _local_dense_fallback() -> InMemoryDenseBackend | None:
    return InMemoryDenseBackend() if load_config().enable_local_fallback else None


def _local_sparse_fallback() -> InMemorySparseBackend | None:
    return InMemorySparseBackend() if load_config().enable_local_fallback else None


def _graph_fallback() -> LocalGraphRetriever | None:
    return LocalGraphRetriever() if load_config().enable_local_fallback else None


def _build_raptor_summarizer() -> QwenRaptorSummarizer:
    config = load_config()
    return QwenRaptorSummarizer(
        endpoint=config.models.summarizer_endpoint,
        model=config.models.summarizer_model,
        fallback=ExtractiveSummarizer(),
    )


def build_embedding_client() -> BGEEmbeddingClient:
    """通过兼容 OpenAI 格式的端点调用 BGE-large-zh 向量化。

    向量服务不可达时降级到确定性 hash embedding，保证检索链路可离线运行。
    """
    config = load_config()
    backend = HTTPEmbeddingClient(
        endpoint=config.models.embedding_endpoint,
        model=config.models.embedding_model,
    )
    fallback = HashEmbeddingClient() if config.enable_local_fallback else None
    return BGEEmbeddingClient(backend=backend, fallback=fallback)


def build_reranker() -> BGEReranker:
    """BGE-reranker 精排，调用兼容 OpenAI 格式的重排端点。

    重排服务不可达且启用本地降级时，BGEReranker 退化为词汇打分保证管线可运行。
    """
    config = load_config()
    client = HTTPRerankClient(
        endpoint=config.models.rerank_endpoint,
        model=config.models.reranker_model,
    )
    return BGEReranker(client=client, model=config.models.reranker_model)


def build_entity_extractor():
    """向 Neo4j 知识图谱供给实体/关系的抽取器。

    优先使用 LLM 抽取器，模型不可达时降级为基于共现的启发式抽取。
    """
    config = load_config()
    heuristic = HeuristicEntityExtractor()
    return LLMEntityExtractor(
        endpoint=config.models.rewrite_endpoint,
        model=config.models.rewrite_model,
        fallback=heuristic,
    )


def build_agent(
    embeddings: HashEmbeddingClient | None = None,
    dense: Any | None = None,
    sparse: Any | None = None,
    tree_store: InMemoryRaptorStore | None = None,
    graph: Neo4jGraphRetriever | None = None,
) -> KnowledgeRagAgent:
    config = load_config()
    embeddings = embeddings or build_embedding_client()
    dense = dense or MilvusDenseRetriever(uri=config.milvus_uri, fallback=_local_dense_fallback())
    sparse = sparse or ElasticsearchBM25Retriever(url=config.elasticsearch_url, fallback=_local_sparse_fallback())
    tree_store = tree_store or InMemoryRaptorStore()
    graph = graph or Neo4jGraphRetriever(uri=config.neo4j_uri, fallback=_graph_fallback())

    reranker = build_reranker()
    hybrid = HybridRetriever(embeddings, dense, sparse, reranker)
    precise = PreciseBlockRetriever(hybrid)
    raptor = RaptorRetriever(embeddings, dense, tree_store)
    agentic = AgenticRetriever(hybrid, HeuristicQueryPlanner())
    router = IntentRouter(
        optimizer=QueryOptimizer(
            QwenRewriteAdapter(endpoint=config.models.rewrite_endpoint, model=config.models.rewrite_model)
        ),
        intent_model=QwenIntentClassifier(endpoint=config.models.intent_endpoint, model=config.models.intent_model),
    )
    answer_service = AnswerService(
        QwenChatAdapter(endpoint=config.models.answer_endpoint, model=config.models.answer_model)
    )
    return KnowledgeRagAgent(
        AgentDependencies(
            router=router,
            hybrid_retriever=hybrid,
            precise_retriever=precise,
            raptor_retriever=raptor,
            graph_retriever=graph,
            agentic_retriever=agentic,
            answer_service=answer_service,
        )
    )


def build_ingestion_pipeline(
    embeddings: HashEmbeddingClient | None = None,
    dense: Any | None = None,
    sparse: Any | None = None,
    tree_store: InMemoryRaptorStore | None = None,
    graph: Neo4jGraphRetriever | None = None,
) -> KnowledgeIngestionPipeline:
    config = load_config()
    embeddings = embeddings or build_embedding_client()
    dense = dense or MilvusDenseRetriever(uri=config.milvus_uri, fallback=_local_dense_fallback())
    sparse = sparse or ElasticsearchBM25Retriever(url=config.elasticsearch_url, fallback=_local_sparse_fallback())
    tree_store = tree_store or InMemoryRaptorStore()
    graph = graph or Neo4jGraphRetriever(uri=config.neo4j_uri, fallback=_graph_fallback())

    return KnowledgeIngestionPipeline(
        parser=build_document_parser(),
        chunker=SemanticChunker(),
        raptor_builder=RaptorTreeBuilder(embeddings, _build_raptor_summarizer()),
        raptor_store=tree_store,
        dense_index=dense,
        sparse_index=sparse,
        graph_index=graph,
        entity_extractor=build_entity_extractor(),
    )


def build_runtime() -> Tuple[KnowledgeRagAgent, KnowledgeIngestionPipeline]:
    config = load_config()
    embeddings = build_embedding_client()
    dense = MilvusDenseRetriever(uri=config.milvus_uri, fallback=_local_dense_fallback())
    sparse = ElasticsearchBM25Retriever(url=config.elasticsearch_url, fallback=_local_sparse_fallback())
    tree_store = InMemoryRaptorStore()
    graph = Neo4jGraphRetriever(uri=config.neo4j_uri, fallback=_graph_fallback())

    agent = build_agent(embeddings=embeddings, dense=dense, sparse=sparse, tree_store=tree_store, graph=graph)
    ingestion_pipeline = build_ingestion_pipeline(
        embeddings=embeddings,
        dense=dense,
        sparse=sparse,
        tree_store=tree_store,
        graph=graph,
    )
    return agent, ingestion_pipeline
