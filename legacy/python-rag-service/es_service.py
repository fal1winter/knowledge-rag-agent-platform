"""
RAG服务 - ElasticSearch 服务
用于关键词检索，与向量检索配合实现混合检索
"""
import logging
import os
from typing import List, Dict
from elasticsearch import Elasticsearch
from elasticsearch.exceptions import NotFoundError

logger = logging.getLogger(__name__)

# ElasticSearch 配置
ES_HOST = os.getenv("ES_HOST", "http://127.0.0.1:9200")
ES_USERNAME = os.getenv("ES_USERNAME", "")
ES_PASSWORD = os.getenv("ES_PASSWORD", "")

_client = None

def get_client():
    """获取 ElasticSearch 客户端"""
    global _client
    if _client is None:
        if ES_USERNAME and ES_PASSWORD:
            _client = Elasticsearch(
                [ES_HOST],
                basic_auth=(ES_USERNAME, ES_PASSWORD)
            )
        else:
            _client = Elasticsearch([ES_HOST])
        logger.info(f"ElasticSearch connected at {ES_HOST}")
    return _client

def _get_index_name(material_id: int) -> str:
    """获取资料对应的索引名"""
    return f"material_{material_id}"

def create_index(material_id: int):
    """为资料创建索引"""
    client = get_client()
    index_name = _get_index_name(material_id)

    if client.indices.exists(index=index_name):
        logger.info(f"Index {index_name} already exists")
        return

    # 创建索引，使用中文分词器
    client.indices.create(
        index=index_name,
        body={
            "settings": {
                "number_of_shards": 1,
                "number_of_replicas": 0,
                "analysis": {
                    "analyzer": {
                        "default": {
                            "type": "standard"
                        }
                    }
                }
            },
            "mappings": {
                "properties": {
                    "chunk_index": {"type": "integer"},
                    "content": {
                        "type": "text",
                        "analyzer": "standard",  # 使用标准分词器（支持中文）
                        "fields": {
                            "keyword": {
                                "type": "keyword",
                                "ignore_above": 256
                            }
                        }
                    },
                    "metadata": {
                        "type": "object",
                        "enabled": True
                    }
                }
            }
        }
    )
    logger.info(f"Created index: {index_name}")

def insert_chunks(material_id: int, chunks: List[Dict]):
    """批量插入切块到 ElasticSearch"""
    client = get_client()
    index_name = _get_index_name(material_id)

    # 确保索引存在
    create_index(material_id)

    # 批量插入
    from elasticsearch.helpers import bulk

    actions = []
    for chunk in chunks:
        actions.append({
            "_index": index_name,
            "_source": {
                "chunk_index": chunk["chunk_index"],
                "content": chunk["content"],
                "metadata": chunk.get("metadata", {})
            }
        })

    if actions:
        success, failed = bulk(client, actions, refresh=True)
        logger.info(f"Inserted {success} chunks to ES for material {material_id}")
        if failed:
            logger.warning(f"Failed to insert {len(failed)} chunks")

def search(material_id: int, query: str, top_k: int = 10) -> List[Dict]:
    """关键词检索"""
    client = get_client()
    index_name = _get_index_name(material_id)

    try:
        # 使用 multi_match 查询，支持多字段匹配
        result = client.search(
            index=index_name,
            body={
                "query": {
                    "multi_match": {
                        "query": query,
                        "fields": ["content^2"],  # content 字段权重为 2
                        "type": "best_fields",
                        "operator": "or",
                        "fuzziness": "AUTO"  # 支持模糊匹配
                    }
                },
                "size": top_k,
                "_source": ["chunk_index", "content", "metadata"]
            }
        )

        # 格式化结果
        formatted = []
        for hit in result["hits"]["hits"]:
            formatted.append({
                "chunk_index": hit["_source"]["chunk_index"],
                "content": hit["_source"]["content"],
                "metadata": hit["_source"].get("metadata", {}),
                "score": float(hit["_score"])
            })

        logger.info(f"ES search returned {len(formatted)} results for material {material_id}")
        return formatted

    except NotFoundError:
        logger.warning(f"Index {index_name} not found")
        return []
    except Exception as e:
        logger.error(f"ES search error: {e}", exc_info=True)
        return []

def search_multi_materials(material_ids: List[int], query: str, top_k: int = 10) -> List[Dict]:
    """跨多个资料检索"""
    client = get_client()

    # 构建索引列表
    indices = [_get_index_name(mid) for mid in material_ids]

    # 过滤掉不存在的索引
    existing_indices = []
    for idx in indices:
        if client.indices.exists(index=idx):
            existing_indices.append(idx)

    if not existing_indices:
        logger.warning(f"No valid indices found for materials: {material_ids}")
        return []

    try:
        result = client.search(
            index=existing_indices,
            body={
                "query": {
                    "multi_match": {
                        "query": query,
                        "fields": ["content^2"],
                        "type": "best_fields",
                        "operator": "or",
                        "fuzziness": "AUTO"
                    }
                },
                "size": top_k,
                "_source": ["chunk_index", "content", "metadata"]
            }
        )

        # 格式化结果（需要从索引名中提取 material_id）
        formatted = []
        for hit in result["hits"]["hits"]:
            index_name = hit["_index"]
            material_id = int(index_name.split("_")[1])

            formatted.append({
                "material_id": material_id,
                "chunk_index": hit["_source"]["chunk_index"],
                "content": hit["_source"]["content"],
                "metadata": hit["_source"].get("metadata", {}),
                "score": float(hit["_score"])
            })

        logger.info(f"ES multi-material search returned {len(formatted)} results")
        return formatted

    except Exception as e:
        logger.error(f"ES multi-material search error: {e}", exc_info=True)
        return []

def delete_by_material_id(material_id: int):
    """删除资料的索引"""
    client = get_client()
    index_name = _get_index_name(material_id)

    try:
        if client.indices.exists(index=index_name):
            client.indices.delete(index=index_name)
            logger.info(f"Deleted ES index: {index_name}")
    except Exception as e:
        logger.error(f"Failed to delete ES index {index_name}: {e}")

def get_chunk_count(material_id: int) -> int:
    """获取资料的切块数量"""
    client = get_client()
    index_name = _get_index_name(material_id)

    try:
        if not client.indices.exists(index=index_name):
            return 0

        result = client.count(index=index_name)
        return result["count"]
    except Exception as e:
        logger.error(f"Failed to get chunk count: {e}")
        return 0

def is_available() -> bool:
    """检查 ElasticSearch 是否可用"""
    try:
        client = get_client()
        return client.ping()
    except:
        return False
