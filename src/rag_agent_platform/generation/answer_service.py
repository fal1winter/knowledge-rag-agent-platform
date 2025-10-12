"""基于检索证据的可引用回答生成。"""

from typing import List

from rag_agent_platform.generation.llm_client import ChatModel, PromptBuilder
from rag_agent_platform.models import RetrievalHit


class AnswerService:
    def __init__(self, chat_model: ChatModel, prompt_builder: PromptBuilder | None = None):
        self.chat_model = chat_model
        self.prompt_builder = prompt_builder or PromptBuilder()

    def answer(self, question: str, hits: List[RetrievalHit]) -> tuple[str, List[dict]]:
        evidence = [
            {
                "text": hit.text,
                "citation": hit.citation,
                "score": hit.score,
                "source": hit.source,
            }
            for hit in hits
        ]
        messages = self.prompt_builder.build_answer_messages(question, evidence)
        text = self.chat_model.complete(messages)
        citations = [hit.citation for hit in hits if hit.citation]
        return text, citations

