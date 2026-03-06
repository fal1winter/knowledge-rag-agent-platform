"""生成模块 LLM 适配器。

模型路由策略：
- 简单任务（意图分类、查询改写）→ 本地部署 Qwen2.5 QLoRA 微调模型
- 复杂任务（回答生成、摘要、质量评估）→ SiliconFlow 托管 DeepSeek
"""

from typing import Dict, List, Protocol

from rag_agent_platform.integrations.http_json import OpenAICompatibleClient


class ChatModel(Protocol):
    def complete(self, messages: List[Dict[str, str]], temperature: float = 0.2) -> str:
        """返回模型响应文本。"""


class DeepSeekChatAdapter:
    """SiliconFlow 托管 DeepSeek 适配器，用于复杂推理和生成任务。

    通过硅基流动 API (https://api.siliconflow.cn/v1) 调用 DeepSeek 系列模型，
    兼容 OpenAI chat/completions 接口。
    适用于：回答生成、RAPTOR 摘要、迭代检索质量评估、LLM-as-Judge 评测。
    API 不可达时自动降级为抽取式兜底回答。
    """

    def __init__(
        self,
        endpoint: str = "https://api.siliconflow.cn/v1",
        api_key: str = "",
        model: str = "deepseek-ai/DeepSeek-V2.5",
        fallback_enabled: bool = True,
    ):
        self.endpoint = endpoint
        self.api_key = api_key
        self.model = model
        self.fallback_enabled = fallback_enabled
        self.client = OpenAICompatibleClient(endpoint, api_key=api_key or None, timeout=120.0) if endpoint and api_key else None

    def complete(self, messages: List[Dict[str, str]], temperature: float = 0.2) -> str:
        if self.client is not None:
            try:
                return self.client.chat(self.model, messages, temperature=temperature)
            except Exception:
                if not self.fallback_enabled:
                    raise
        return self._extractive_answer(messages)

    def _extractive_answer(self, messages: List[Dict[str, str]]) -> str:
        """SiliconFlow 不可达时的抽取式兜底。"""
        user = next((m.get("content", "") for m in reversed(messages) if m.get("role") == "user"), "")
        marker = "证据："
        if marker in user:
            evidence = user.split(marker, 1)[1].strip()
            first = evidence.split("\n\n", 1)[0].strip()
            if first:
                return f"根据已检索证据：{first}"
        return "DeepSeek 模型服务暂不可达，无法生成超出检索证据的回答。"


class QwenChatAdapter:
    """本地部署 Qwen/vLLM 适配器，用于简单低延迟任务。

    通过 vLLM 加载 QLoRA 微调权重，单机 GPU 推理。
    适用于：意图分类（1.5B QLoRA）、查询改写（7B QLoRA）等轻量推理。
    延迟要求：P99 < 200ms（1.5B）/ < 500ms（7B）。
    """

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
