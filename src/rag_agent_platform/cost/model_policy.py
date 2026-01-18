"""轻量模型路由策略、QLoRA 训练配方与向量化调用优化。

模型分层策略：
- 简单任务（意图分类、查询改写）：本地部署 Qwen QLoRA 微调模型，低延迟低成本
- 复杂任务（回答生成、RAPTOR 摘要、质量评估）：DeepSeek via 硅基流动 API，推理能力强
"""

from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, Iterable, List

from rag_agent_platform.models import IntentType


@dataclass(frozen=True)
class ModelChoice:
    task: str
    model: str
    reason: str


@dataclass(frozen=True)
class QLoRARecipe:
    task: str
    base_model: str
    adapter_name: str
    dataset_path: str
    output_dir: str
    target_modules: List[str] = field(
        default_factory=lambda: [
            "q_proj",
            "k_proj",
            "v_proj",
            "o_proj",
            "gate_proj",
            "up_proj",
            "down_proj",
        ]
    )
    lora_r: int = 16
    lora_alpha: int = 32
    lora_dropout: float = 0.05
    quantization: str = "nf4-4bit-double-quant"
    epochs: float = 2.0
    learning_rate: float = 2e-4
    batch_size: int = 4
    gradient_accumulation_steps: int = 8

    def command(self, project_root: str | Path = ".") -> List[str]:
        script = Path(project_root) / "scripts" / "train_qwen_qlora.py"
        return [
            "python3",
            str(script),
            "--base-model",
            self.base_model,
            "--dataset",
            self.dataset_path,
            "--output-dir",
            self.output_dir,
            "--task",
            self.task,
            "--epochs",
            str(self.epochs),
            "--learning-rate",
            str(self.learning_rate),
            "--batch-size",
            str(self.batch_size),
            "--gradient-accumulation-steps",
            str(self.gradient_accumulation_steps),
        ]


@dataclass(frozen=True)
class EmbeddingBudgetDecision:
    embed: bool
    reason: str
    estimated_saved_calls: int = 0


class CostAwareModelPolicy:
    """将低复杂度任务路由到本地 QLoRA 适配器，复杂推理/生成任务路由到 DeepSeek (硅基流动)。"""

    def __init__(self):
        # 简单任务 — 本地部署 Qwen QLoRA 微调
        self.intent_model = "Qwen2.5-1.5B-Instruct-QLoRA"
        self.rewrite_model = "Qwen2.5-7B-Instruct-QLoRA"
        # 复杂任务 — DeepSeek via 硅基流动 SiliconFlow API
        self.answer_model = "deepseek-ai/DeepSeek-V3"
        self.summarizer_model = "deepseek-ai/DeepSeek-V3"
        self._recipes = {
            "intent": QLoRARecipe(
                task="intent",
                base_model="Qwen/Qwen2.5-1.5B-Instruct",
                adapter_name=self.intent_model,
                dataset_path="datasets/intent_train.jsonl",
                output_dir="artifacts/models/qwen2.5-1.5b-intent-qlora",
            ),
            "rewrite": QLoRARecipe(
                task="rewrite",
                base_model="Qwen/Qwen2.5-7B-Instruct",
                adapter_name=self.rewrite_model,
                dataset_path="datasets/rewrite_train.jsonl",
                output_dir="artifacts/models/qwen2.5-7b-rewrite-qlora",
                batch_size=2,
                gradient_accumulation_steps=16,
            ),
        }

    def choose_for_intent(self, intent: IntentType) -> ModelChoice:
        if intent in {IntentType.CONTROL, IntentType.DIRECT_QA, IntentType.MATERIAL_SEARCH}:
            return ModelChoice("intent_or_simple_answer", self.intent_model, "低复杂度，本地 Qwen QLoRA 处理")
        if intent == IntentType.COMPLEX_REASONING:
            return ModelChoice("deepseek_reasoning", self.answer_model, "复杂推理，路由到 DeepSeek via SiliconFlow")
        return ModelChoice("deepseek_grounded_answer", self.answer_model, "生成回答，路由到 DeepSeek via SiliconFlow")

    def training_recipe(self, task: str) -> QLoRARecipe:
        if task not in self._recipes:
            raise ValueError(f"unsupported QLoRA task: {task}")
        return self._recipes[task]

    def training_commands(self, tasks: Iterable[str] = ("intent", "rewrite"), project_root: str | Path = ".") -> Dict[str, List[str]]:
        return {task: self.training_recipe(task).command(project_root) for task in tasks}

    def embedding_decision(self, query: str, cache_hit: bool, route_confidence: float) -> EmbeddingBudgetDecision:
        if cache_hit:
            return EmbeddingBudgetDecision(False, "semantic cache hit", estimated_saved_calls=1)
        if len(query.strip()) < 8 and route_confidence > 0.9:
            return EmbeddingBudgetDecision(False, "high-confidence short command", estimated_saved_calls=1)
        return EmbeddingBudgetDecision(True, "retrieval requires fresh embedding")

    def should_embed(self, query: str, cache_hit: bool, route_confidence: float) -> bool:
        return self.embedding_decision(query, cache_hit, route_confidence).embed

    def estimate_call_savings(self, total_requests: int, cache_hits: int, short_routed_requests: int) -> Dict[str, float]:
        if total_requests <= 0:
            return {"saved_embedding_calls": 0.0, "saved_ratio": 0.0}
        saved = min(total_requests, max(0, cache_hits) + max(0, short_routed_requests))
        return {
            "saved_embedding_calls": float(saved),
            "saved_ratio": saved / total_requests,
        }

    def metrics_snapshot(self) -> Dict[str, str]:
        return {
            "intent_model": self.intent_model,
            "rewrite_model": self.rewrite_model,
            "answer_model": self.answer_model,
            "intent_training_script": "scripts/train_qwen_qlora.py --task intent",
            "rewrite_training_script": "scripts/train_qwen_qlora.py --task rewrite",
            "saving_strategy": "semantic cache + confident route skip embedding + QLoRA adapters for intent/rewrite",
        }
