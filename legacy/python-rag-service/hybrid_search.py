"""
RAG服务 - 混合检索实现
结合向量检索和关键词检索，使用 RRF 算法融合结果
"""
import logging
from typing import List, Dict, Optional
import embedding_service
import vector_service
import es_service
import rerank_service

logger = logging.getLogger(__name__)

def reciprocal_rank_fusion(
    vector_results: List[Dict],
    keyword_results: List[Dict],
    k: int = 60
) -> List[Dict]:
    """
    RRF (Reciprocal Rank Fusion) 算法

    公式: RRF_score = sum(1 / (k + rank_i))

    Args:
        vector_results: 向量检索结果
        keyword_results: 关键词检索结果
        k: RRF 参数，通常取 60

    Returns:
        融合后的结果列表
    """
    scores = {}
    chunk_map = {}

    # 处理向量检索结果
    for rank, result in enumerate(vector_results):
        chunk_key = (result.get("material_id", 0), result["chunk_index"])
        rrf_score = 1.0 / (k + rank + 1)

        if chunk_key not in scores:
            scores[chunk_key] = 0.0
            chunk_map[chunk_key] = result

        scores[chunk_key] += rrf_score

        # 保存原始向量分数
        if "vector_score" not in chunk_map[chunk_key]:
            chunk_map[chunk_key]["vector_score"] = result.get("score", 0.0)

    # 处理关键词检索结果
    for rank, result in enumerate(keyword_results):
        chunk_key = (result.get("material_id", 0), result["chunk_index"])
        rrf_score = 1.0 / (k + rank + 1)

        if chunk_key not in scores:
            scores[chunk_key] = 0.0
            chunk_map[chunk_key] = result

        scores[chunk_key] += rrf_score

        # 保存原始关键词分数
        if "keyword_score" not in chunk_map[chunk_key]:
            chunk_map[chunk_key]["keyword_score"] = result.get("score", 0.0)

    # 按 RRF 分数排序
    sorted_chunks = sorted(scores.items(), key=lambda x: x[1], reverse=True)

    # 构建最终结果
    merged_results = []
    for chunk_key, rrf_score in sorted_chunks:
        result = chunk_map[chunk_key].copy()
        result["rrf_score"] = rrf_score
        result["score"] = rrf_score  # 使用 RRF 分数作为主分数
        merged_results.append(result)

    logger.info(f"RRF merged {len(vector_results)} vector + {len(keyword_results)} keyword results into {len(merged_results)} results")
    return merged_results

def hybrid_search(
    material_id: Optional[int],
    query: str,
    top_k: int = 5,
    material_ids: Optional[List[int]] = None,
    vector_weight: float = 0.5,
    keyword_weight: float = 0.5,
    use_rerank: bool = True
) -> List[Dict]:
    """
    混合检索：向量检索 + 关键词检索 + RRF 融合 + 重排序

    Args:
        material_id: 单个资料 ID（优先使用）
        query: 查询文本
        top_k: 最终返回的结果数量
        material_ids: 多个资料 ID 列表
        vector_weight: 向量检索权重（暂未使用，保留用于加权融合）
        keyword_weight: 关键词检索权重（暂未使用，保留用于加权融合）
        use_rerank: 是否使用重排序

    Returns:
        混合检索结果列表
    """
    try:
        # 1. 向量检索（召回 top-20）
        query_emb = embedding_service.embed(query)
        vector_results = vector_service.search(
            material_id=material_id,
            query_embedding=query_emb,
            top_k=20,
            material_ids=material_ids
        )
        logger.info(f"Vector search returned {len(vector_results)} results")

        # 2. 关键词检索（召回 top-20）
        keyword_results = []

        # 检查 ES 是否可用
        if es_service.is_available():
            if material_id is not None:
                keyword_results = es_service.search(material_id, query, top_k=20)
            elif material_ids:
                keyword_results = es_service.search_multi_materials(material_ids, query, top_k=20)

            logger.info(f"Keyword search returned {len(keyword_results)} results")
        else:
            logger.warning("ElasticSearch not available, using vector search only")

        # 3. 如果没有关键词检索结果，直接使用向量检索
        if not keyword_results:
            logger.info("No keyword results, using vector search only")
            if use_rerank and rerank_service.is_available():
                # 重排序 top-10，返回 top-k
                reranked = rerank_service.rerank(query, vector_results[:10], top_k=top_k)
                return reranked
            else:
                return vector_results[:top_k]

        # 4. RRF 融合
        merged_results = reciprocal_rank_fusion(vector_results, keyword_results, k=60)

        # 5. 重排序（可选）
        if use_rerank and rerank_service.is_available():
            # 对融合后的 top-10 进行重排序，返回 top-k
            candidates = merged_results[:10]
            reranked = rerank_service.rerank(query, candidates, top_k=top_k)
            logger.info(f"Reranked {len(candidates)} candidates, returning top {len(reranked)}")
            return reranked
        else:
            # 不使用重排序，直接返回 top-k
            return merged_results[:top_k]

    except Exception as e:
        logger.error(f"Hybrid search error: {e}", exc_info=True)
        # 降级：只使用向量检索
        try:
            query_emb = embedding_service.embed(query)
            vector_results = vector_service.search(
                material_id=material_id,
                query_embedding=query_emb,
                top_k=top_k,
                material_ids=material_ids
            )
            return vector_results
        except Exception as fallback_error:
            logger.error(f"Fallback vector search also failed: {fallback_error}")
            return []

def search_with_strategy(
    material_id: Optional[int],
    query: str,
    top_k: int = 5,
    material_ids: Optional[List[int]] = None,
    strategy: str = "hybrid"
) -> List[Dict]:
    """
    根据策略选择检索方式

    Args:
        material_id: 资料 ID
        query: 查询文本
        top_k: 返回结果数量
        material_ids: 多资料 ID 列表
        strategy: 检索策略 ("hybrid", "vector", "keyword")

    Returns:
        检索结果列表
    """
    if strategy == "vector":
        # 纯向量检索
        query_emb = embedding_service.embed(query)
        results = vector_service.search(
            material_id=material_id,
            query_embedding=query_emb,
            top_k=top_k,
            material_ids=material_ids
        )
        return results

    elif strategy == "keyword":
        # 纯关键词检索
        if material_id is not None:
            results = es_service.search(material_id, query, top_k=top_k)
        elif material_ids:
            results = es_service.search_multi_materials(material_ids, query, top_k=top_k)
        else:
            results = []
        return results

    else:  # strategy == "hybrid" (default)
        # 混合检索
        return hybrid_search(
            material_id=material_id,
            query=query,
            top_k=top_k,
            material_ids=material_ids
        )
