"""
RAG服务 - 资料推荐服务
基于当前对话内容和资料信息推荐相关资料
"""
import logging
import requests
from typing import List, Dict, Optional

logger = logging.getLogger(__name__)

# Java后端API地址
JAVA_API_BASE = "http://127.0.0.1:7010/api/bbs/material"


def get_material_info(material_id: int) -> Optional[Dict]:
    """获取资料基本信息"""
    try:
        response = requests.get(f"{JAVA_API_BASE}/{material_id}", timeout=5)
        if response.status_code == 200:
            data = response.json()
            if data.get("success"):
                return data.get("data")
    except Exception as e:
        logger.error(f"get_material_info error: {e}")
    return None


def get_recommendations(
    material_id: int,
    query: str,
    limit: int = 3
) -> List[Dict]:
    """
    获取推荐资料列表

    推荐策略：
    1. 同类别资料（优先）
    2. 同卖家的其他资料
    3. 相关标签的资料

    Args:
        material_id: 当前资料ID
        query: 用户问题（用于分析意图）
        limit: 推荐数量

    Returns:
        推荐资料列表
    """
    try:
        # 获取当前资料信息
        current_material = get_material_info(material_id)
        if not current_material:
            return []

        category_id = current_material.get("categoryId")
        seller_id = current_material.get("sellerId")
        tags = current_material.get("tags", "")

        recommendations = []

        # 1. 同类别资料（排除当前资料）
        if category_id:
            try:
                response = requests.get(
                    f"{JAVA_API_BASE}/list",
                    params={
                        "categoryId": category_id,
                        "pageNum": 1,
                        "pageSize": limit + 1,
                        "status": 1
                    },
                    timeout=5
                )
                if response.status_code == 200:
                    data = response.json()
                    if data.get("success"):
                        materials = data.get("data", {}).get("list", [])
                        for m in materials:
                            if m["id"] != material_id and len(recommendations) < limit:
                                recommendations.append({
                                    "id": m["id"],
                                    "title": m["title"],
                                    "description": m.get("description", "")[:100],
                                    "price": m.get("price", 0),
                                    "coverUrl": m.get("coverUrl"),
                                    "reason": "同类别资料"
                                })
            except Exception as e:
                logger.error(f"get category recommendations error: {e}")

        # 2. 如果推荐不足，添加同卖家的其他资料
        if len(recommendations) < limit and seller_id:
            try:
                response = requests.get(
                    f"{JAVA_API_BASE}/seller/{seller_id}",
                    params={"pageNum": 1, "pageSize": limit},
                    timeout=5
                )
                if response.status_code == 200:
                    data = response.json()
                    if data.get("success"):
                        materials = data.get("data", {}).get("list", [])
                        for m in materials:
                            if m["id"] != material_id and len(recommendations) < limit:
                                # 检查是否已推荐
                                if not any(r["id"] == m["id"] for r in recommendations):
                                    recommendations.append({
                                        "id": m["id"],
                                        "title": m["title"],
                                        "description": m.get("description", "")[:100],
                                        "price": m.get("price", 0),
                                        "coverUrl": m.get("coverUrl"),
                                        "reason": "同作者资料"
                                    })
            except Exception as e:
                logger.error(f"get seller recommendations error: {e}")

        return recommendations[:limit]

    except Exception as e:
        logger.error(f"get_recommendations error: {e}", exc_info=True)
        return []
