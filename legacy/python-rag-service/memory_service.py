"""
RAG服务 - 智能记忆服务
实现分层记忆架构：
1. 工作记忆（Working Memory）：最近 6 轮对话
2. 短期记忆（Short-term Memory）：当前会话的核心信息摘要
3. 长期记忆（Long-term Memory）：跨会话的用户知识图谱
"""
import logging
import json
import os
from typing import List, Dict, Optional
from datetime import datetime
from pymongo import MongoClient
import requests

logger = logging.getLogger(__name__)

# OpenRouter 配置（用于摘要和信息提取）
API_KEY = os.getenv("OPENAI_API_KEY", "")
API_BASE = os.getenv("OPENAI_API_BASE", "https://openrouter.fans/v1")
CHAT_MODEL = os.getenv("CHAT_MODEL", "deepseek-chat")

# MongoDB
MONGO_URI = os.getenv("MONGODB_URI", "mongodb://127.0.0.1:27017/ns_bbs")

_mongo_client = None
_db = None

def _get_db():
    global _mongo_client, _db
    if _db is None:
        _mongo_client = MongoClient(MONGO_URI)
        _db = _mongo_client.get_default_database()
    return _db

def _call_llm(messages: list) -> str:
    """调用 LLM 进行摘要和信息提取"""
    url = API_BASE.rstrip("/")
    if not url.endswith("/chat/completions"):
        url = url.rstrip("/") + "/chat/completions"

    headers = {
        "Authorization": f"Bearer {API_KEY}",
        "Content-Type": "application/json",
    }
    payload = {
        "model": CHAT_MODEL,
        "messages": messages,
        "temperature": 0.3,  # 低温度，更确定性
        "stream": False,
    }

    try:
        resp = requests.post(url, headers=headers, json=payload, timeout=30)
        resp.raise_for_status()
        data = resp.json()
        return data["choices"][0]["message"]["content"]
    except Exception as e:
        logger.error(f"LLM call failed: {e}")
        return ""

# ==================== 1. 核心信息提取 ====================

def extract_key_information(messages: List[Dict]) -> Dict:
    """
    从对话中提取核心信息

    提取内容：
    - 用户背景（技术栈、项目信息、环境配置）
    - 关键决策（选择了什么方案、为什么）
    - 重要结论（问题的答案、最佳实践）
    - 待办事项（未完成的任务）
    """
    if not messages or len(messages) < 2:
        return {}

    # 构建对话文本
    conversation = "\n".join([
        f"{msg['role']}: {msg['content']}"
        for msg in messages
    ])

    prompt = f"""分析以下对话，提取核心信息。以 JSON 格式返回，包含以下字段：

1. user_context: 用户的背景信息（技术栈、项目、环境等）
2. key_decisions: 关键决策和选择（用户做了什么决定，为什么）
3. important_facts: 重要事实和结论（问题的答案、最佳实践）
4. pending_tasks: 待办事项（未完成的任务）
5. topics: 讨论的主题标签（如 ["FastAPI", "部署", "性能优化"]）

对话内容：
{conversation}

请返回 JSON 格式（不要包含 markdown 代码块标记）：
"""

    llm_messages = [
        {"role": "system", "content": "你是一个信息提取专家，擅长从对话中提取结构化的核心信息。"},
        {"role": "user", "content": prompt}
    ]

    try:
        response = _call_llm(llm_messages)
        # 清理可能的 markdown 代码块标记
        response = response.strip()
        if response.startswith("```json"):
            response = response[7:]
        if response.startswith("```"):
            response = response[3:]
        if response.endswith("```"):
            response = response[:-3]
        response = response.strip()

        key_info = json.loads(response)
        return key_info
    except Exception as e:
        logger.error(f"Failed to extract key information: {e}")
        return {}

# ==================== 2. 短期记忆（会话摘要）====================

def summarize_conversation(messages: List[Dict], max_length: int = 500) -> str:
    """
    对历史对话进行摘要压缩

    当对话超过 10 轮时，将前面的对话压缩成摘要
    """
    if not messages or len(messages) < 4:
        return ""

    conversation = "\n".join([
        f"{msg['role']}: {msg['content'][:200]}"
        for msg in messages
    ])

    prompt = f"""请将以下对话压缩成简洁的摘要（不超过 {max_length} 字），保留关键信息：

对话内容：
{conversation}

摘要要求：
1. 保留用户的背景信息和需求
2. 保留重要的决策和结论
3. 使用第三人称描述
4. 简洁明了，去除冗余

摘要："""

    llm_messages = [
        {"role": "system", "content": "你是一个对话摘要专家。"},
        {"role": "user", "content": prompt}
    ]

    try:
        summary = _call_llm(llm_messages)
        return summary.strip()
    except Exception as e:
        logger.error(f"Failed to summarize conversation: {e}")
        return ""

def save_session_memory(user_id: int, material_id: int, session_id: str, messages: List[Dict]):
    """
    保存会话的短期记忆（核心信息 + 摘要）
    """
    try:
        db = _get_db()
        col = db.session_memory

        # 提取核心信息
        key_info = extract_key_information(messages)

        # 生成摘要
        summary = summarize_conversation(messages)

        # 保存到数据库
        col.update_one(
            {"user_id": user_id, "material_id": material_id, "session_id": session_id},
            {
                "$set": {
                    "key_information": key_info,
                    "summary": summary,
                    "message_count": len(messages),
                    "updated_time": datetime.utcnow()
                }
            },
            upsert=True
        )

        logger.info(f"Session memory saved for session {session_id}")

    except Exception as e:
        logger.error(f"Failed to save session memory: {e}")

# ==================== 3. 长期记忆（用户知识图谱）====================

def update_user_knowledge_graph(user_id: int, key_info: Dict):
    """
    更新用户的长期知识图谱

    跨会话累积用户的：
    - 技术栈和偏好
    - 常见问题和盲区
    - 项目信息
    """
    try:
        db = _get_db()
        col = db.user_knowledge_graph

        # 获取现有知识图谱
        existing = col.find_one({"user_id": user_id})

        if not existing:
            # 创建新的知识图谱
            col.insert_one({
                "user_id": user_id,
                "tech_stack": key_info.get("topics", []),
                "user_context": key_info.get("user_context", ""),
                "frequent_topics": {},
                "created_time": datetime.utcnow(),
                "updated_time": datetime.utcnow()
            })
        else:
            # 更新现有知识图谱
            updates = {}

            # 累积主题频率
            topics = key_info.get("topics", [])
            frequent_topics = existing.get("frequent_topics", {})
            for topic in topics:
                frequent_topics[topic] = frequent_topics.get(topic, 0) + 1
            updates["frequent_topics"] = frequent_topics

            # 更新技术栈（去重）
            tech_stack = list(set(existing.get("tech_stack", []) + topics))
            updates["tech_stack"] = tech_stack

            # 追加用户上下文
            if key_info.get("user_context"):
                updates["user_context"] = key_info.get("user_context")

            updates["updated_time"] = datetime.utcnow()

            col.update_one(
                {"user_id": user_id},
                {"$set": updates}
            )

        logger.info(f"User knowledge graph updated for user {user_id}")

    except Exception as e:
        logger.error(f"Failed to update user knowledge graph: {e}")

# ==================== 4. 智能记忆检索 ====================

def retrieve_relevant_memory(user_id: int, material_id: int, current_query: str, session_id: str = None) -> Dict:
    """
    检索相关的记忆

    返回：
    - 当前会话的摘要和核心信息
    - 用户的长期知识图谱
    - 其他相关会话的核心信息
    """
    try:
        db = _get_db()
        memory = {
            "current_session": {},
            "user_profile": {},
            "related_sessions": []
        }

        # 1. 当前会话的记忆
        if session_id:
            session_mem = db.session_memory.find_one({
                "user_id": user_id,
                "material_id": material_id,
                "session_id": session_id
            })
            if session_mem:
                memory["current_session"] = {
                    "summary": session_mem.get("summary", ""),
                    "key_information": session_mem.get("key_information", {})
                }

        # 2. 用户的长期知识图谱
        user_kg = db.user_knowledge_graph.find_one({"user_id": user_id})
        if user_kg:
            memory["user_profile"] = {
                "tech_stack": user_kg.get("tech_stack", []),
                "user_context": user_kg.get("user_context", ""),
                "frequent_topics": user_kg.get("frequent_topics", {})
            }

        # 3. 相关的其他会话（简单实现：最近的 3 个会话）
        # TODO: 可以改进为基于语义相似度的检索
        related = db.session_memory.find({
            "user_id": user_id,
            "material_id": material_id,
            "session_id": {"$ne": session_id}
        }).sort("updated_time", -1).limit(3)

        for sess in related:
            memory["related_sessions"].append({
                "session_id": sess.get("session_id"),
                "summary": sess.get("summary", ""),
                "key_information": sess.get("key_information", {})
            })

        return memory

    except Exception as e:
        logger.error(f"Failed to retrieve memory: {e}")
        return {"current_session": {}, "user_profile": {}, "related_sessions": []}

# ==================== 5. 构建增强的 Prompt ====================

def build_memory_enhanced_prompt(memory: Dict, context: str) -> str:
    """
    构建包含记忆的系统提示
    """
    base_prompt = f"""你是一个专业的资料助手，基于提供的资料内容回答用户问题。

规则：
1. 只根据提供的资料内容回答，不要编造信息
2. 如果资料中没有相关信息，明确告知用户
3. 回答要准确、简洁、有条理
4. 引用资料时可以标注片段编号
5. 使用友好、专业的语气

资料内容：
{context}"""

    # 添加记忆信息
    memory_parts = []

    # 用户画像
    if memory.get("user_profile", {}).get("user_context"):
        memory_parts.append(f"\n用户背景：{memory['user_profile']['user_context']}")

    if memory.get("user_profile", {}).get("tech_stack"):
        tech_stack = ", ".join(memory['user_profile']['tech_stack'][:5])
        memory_parts.append(f"用户技术栈：{tech_stack}")

    # 当前会话摘要
    if memory.get("current_session", {}).get("summary"):
        memory_parts.append(f"\n之前的对话摘要：{memory['current_session']['summary']}")

    # 当前会话的核心信息
    key_info = memory.get("current_session", {}).get("key_information", {})
    if key_info.get("key_decisions"):
        memory_parts.append(f"之前的关键决策：{key_info['key_decisions']}")

    if memory_parts:
        enhanced_prompt = base_prompt + "\n\n--- 记忆信息 ---" + "".join(memory_parts)
        return enhanced_prompt

    return base_prompt

# ==================== 6. 定期记忆整理 ====================

def consolidate_memory_periodic(user_id: int, material_id: int, session_id: str, messages: List[Dict]):
    """
    定期整理记忆（每 5 轮对话触发一次）

    1. 提取核心信息
    2. 更新会话摘要
    3. 更新用户知识图谱
    """
    try:
        # 每 5 轮对话整理一次
        if len(messages) % 10 != 0:
            return

        logger.info(f"Consolidating memory for session {session_id}, {len(messages)} messages")

        # 提取核心信息
        key_info = extract_key_information(messages)

        # 保存会话记忆
        save_session_memory(user_id, material_id, session_id, messages)

        # 更新用户知识图谱
        update_user_knowledge_graph(user_id, key_info)

    except Exception as e:
        logger.error(f"Failed to consolidate memory: {e}")
