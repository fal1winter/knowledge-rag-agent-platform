"""
RAG服务 - 向量存储服务
使用 MilvusClient 新API，512维向量，COSINE相似度
"""
import json
import logging
from pymilvus import MilvusClient, DataType

logger = logging.getLogger(__name__)

MILVUS_URI = "http://127.0.0.1:19530"
COLLECTION_NAME = "material_chunks"
EMBEDDING_DIM = 512

_client = None

def get_client():
    global _client
    if _client is None:
        _client = MilvusClient(uri=MILVUS_URI)
        logger.info(f"Milvus connected at {MILVUS_URI}")
        _init_collection()
    return _client

def _init_collection():
    client = _client
    if not client.has_collection(COLLECTION_NAME):
        schema = client.create_schema(auto_id=True, enable_dynamic_field=False)
        schema.add_field(field_name="id", datatype=DataType.INT64, is_primary=True, auto_id=True)
        schema.add_field(field_name="material_id", datatype=DataType.INT64)
        schema.add_field(field_name="chunk_index", datatype=DataType.INT32)
        schema.add_field(field_name="content", datatype=DataType.VARCHAR, max_length=65535)
        schema.add_field(field_name="metadata", datatype=DataType.VARCHAR, max_length=2000)
        schema.add_field(field_name="is_free", datatype=DataType.INT8)
        schema.add_field(field_name="vector", datatype=DataType.FLOAT_VECTOR, dim=EMBEDDING_DIM)

        index_params = client.prepare_index_params()
        index_params.add_index(field_name="vector", metric_type="COSINE", index_type="IVF_FLAT", params={"nlist": 128})
        index_params.add_index(field_name="material_id", index_type="INVERTED")
        index_params.add_index(field_name="is_free", index_type="INVERTED")

        client.create_collection(
            collection_name=COLLECTION_NAME,
            schema=schema,
            index_params=index_params,
        )
        logger.info(f"Collection {COLLECTION_NAME} created (dim={EMBEDDING_DIM})")
    else:
        logger.info(f"Collection {COLLECTION_NAME} exists")

def insert_chunks(material_id: int, chunks: list, embeddings: list, is_free: int = 0):
    """插入切片向量

    Args:
        material_id: 资料ID
        chunks: 切片列表
        embeddings: 向量列表
        is_free: 是否免费（0=付费，1=免费）
    """
    client = get_client()
    data = []
    for i, (chunk, emb) in enumerate(zip(chunks, embeddings)):
        data.append({
            "material_id": material_id,
            "chunk_index": chunk["chunk_index"],
            "content": chunk["content"],
            "metadata": json.dumps(chunk.get("metadata", {}), ensure_ascii=False),
            "is_free": is_free,
            "vector": emb,
        })
    client.insert(collection_name=COLLECTION_NAME, data=data)
    logger.info(f"Inserted {len(data)} chunks for material {material_id} (is_free={is_free})")

def search(material_id: int = None, query_embedding: list = None, top_k: int = 5,
           material_ids: list = None, only_paid: bool = False) -> list:
    """检索相关切片

    Args:
        material_id: 单个资料ID（优先使用）
        query_embedding: 查询向量
        top_k: 返回数量
        material_ids: 资料ID列表（当material_id为None时使用）
        only_paid: 是否只检索付费资料（True时过滤掉免费资料）
    """
    client = get_client()

    # 构建过滤条件
    filter_parts = []

    if material_id is not None:
        filter_parts.append(f"material_id == {material_id}")
    elif material_ids and len(material_ids) > 0:
        ids_str = ", ".join(str(mid) for mid in material_ids)
        filter_parts.append(f"material_id in [{ids_str}]")

    # 只检索付费资料
    if only_paid:
        filter_parts.append("is_free == 0")

    filter_expr = " && ".join(filter_parts) if filter_parts else None

    results = client.search(
        collection_name=COLLECTION_NAME,
        data=[query_embedding],
        limit=top_k,
        filter=filter_expr,
        output_fields=["material_id", "chunk_index", "content", "metadata", "is_free"],
    )
    formatted = []
    for hits in results:
        for hit in hits:
            formatted.append({
                "id": hit["id"],
                "material_id": hit["entity"].get("material_id"),
                "chunk_index": hit["entity"].get("chunk_index"),
                "content": hit["entity"].get("content"),
                "metadata": json.loads(hit["entity"].get("metadata", "{}")),
                "is_free": hit["entity"].get("is_free", 0),
                "score": float(hit["distance"]),
            })
    return formatted

def delete_by_material_id(material_id: int):
    """删除指定资料的所有向量"""
    client = get_client()
    client.delete(collection_name=COLLECTION_NAME, filter=f"material_id == {material_id}")
    logger.info(f"Deleted vectors for material {material_id}")

def get_chunk_count(material_id: int) -> int:
    """获取资料的切片数量"""
    client = get_client()
    try:
        result = client.query(
            collection_name=COLLECTION_NAME,
            filter=f"material_id == {material_id}",
            output_fields=["chunk_index"],
        )
        return len(result)
    except Exception as e:
        logger.error(f"get_chunk_count error: {e}")
        return 0
