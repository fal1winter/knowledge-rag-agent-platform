"""生成模块和轻量模型降本使用的 LLM 适配器。"""

from typing import Dict, List, Protocol

from rag_agent_platform.integrations.http_json import OpenAICompatibleClient


class ChatModel(Protocol):
    def complete(self, messages: List[Dict[str, str]], temperature: float = 0.2) -> str:
        """返回模型响应文本。"""


class QwenChatAdapter:
    """兼容 OpenAI 格式的 Qwen/vLLM 适配器。"""

    def __init__(self, endpoint: str, api_key: str = "", model: str = "Qwen2.5-7B-Instruct", fallback_enabled: bool = True):
        self.endpoint = endpoint
        self.api_key = api_key
        self.model = model
        self.fallback_enabled = fallback_enabled
        self.client = OpenAICompatibleClient(endpoint, api_key=api_key or None, timeout=90.0) if endpoint else None

    def complete(self, messages: List[Dict[str, str]], temperature: float = 0.2) -> str:
        if self.client is not None:
            try:
                return self.client.chat(self.model, messages, temperature=temperature)
            except Exception:
                if not self.fallback_enabled:
                    raise
        return self._extractive_answer(messages)

    def _extractive_answer(self, messages: List[Dict[str, str]]) -> str:
        user = next((m.get("content", "") for m in reversed(messages) if m.get("role") == "user"), "")
        marker = "证据："
        if marker in user:
            evidence = user.split(marker, 1)[1].strip()
            first = evidence.split("\n\n", 1)[0].strip()
            if first:
                return f"根据已检索证据：{first}"
        return "未配置生成模型，无法生成超出检索证据的回答。"


class PromptBuilder:
    def build_answer_messages(self, question: str, evidence: List[Dict]) -> List[Dict[str, str]]:
        evidence_text = "\n\n".join(
            f"[{idx}] {item.get('text', '')}\n来源: {item.get('citation', {})}"
            for idx, item in enumerate(evidence, start=1)
        )
        return [
            {"role": "system", "content": "你是企业知识库 AI 对话 Agent。只基于给定证据回答，无法从证据推出时说明不确定，并保留引用编号。"},
            {"role": "user", "content": f"问题：{question}\n\n证据：\n{evidence_text}"},
        ]
