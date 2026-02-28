#!/bin/bash
# vLLM 推理服务启动脚本
# 用于本地部署 Qwen 意图分类和查询改写模型

set -e

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MODELS_DIR="${PROJECT_ROOT}/artifacts/models"

echo "=== 启动意图分类模型 (Qwen2.5-1.5B + QLoRA) ==="
python3 -m vllm.entrypoints.openai.api_server \
  --model Qwen/Qwen2.5-1.5B-Instruct \
  --enable-lora \
  --lora-modules intent-qlora="${MODELS_DIR}/qwen2.5-1.5b-intent-qlora" \
  --port 8001 \
  --max-model-len 2048 \
  --gpu-memory-utilization 0.3 \
  --dtype bfloat16 \
  --trust-remote-code &

echo "=== 启动查询改写模型 (Qwen2.5-7B + QLoRA) ==="
python3 -m vllm.entrypoints.openai.api_server \
  --model Qwen/Qwen2.5-7B-Instruct \
  --enable-lora \
  --lora-modules rewrite-qlora="${MODELS_DIR}/qwen2.5-7b-rewrite-qlora" \
  --port 8000 \
  --max-model-len 2048 \
  --gpu-memory-utilization 0.5 \
  --dtype bfloat16 \
  --trust-remote-code &

echo "=== 启动 BGE Embedding 服务 ==="
python3 -m vllm.entrypoints.openai.api_server \
  --model BAAI/bge-large-zh-v1.5 \
  --port 8003 \
  --max-model-len 512 \
  --gpu-memory-utilization 0.15 \
  --dtype float16 \
  --trust-remote-code &

echo "=== 启动 BGE Reranker 服务 ==="
python3 -c "
from FlagEmbedding import FlagReranker
from flask import Flask, request, jsonify

app = Flask(__name__)
reranker = FlagReranker('BAAI/bge-reranker-v2-m3', use_fp16=True)

@app.route('/rerank', methods=['POST'])
def rerank():
    data = request.json
    query = data['query']
    passages = data['passages']
    pairs = [[query, p] for p in passages]
    scores = reranker.compute_score(pairs)
    if isinstance(scores, float):
        scores = [scores]
    results = [{'index': i, 'relevance_score': s} for i, s in enumerate(scores)]
    results.sort(key=lambda x: x['relevance_score'], reverse=True)
    return jsonify({'results': results})

app.run(host='0.0.0.0', port=8004)
" &

wait
