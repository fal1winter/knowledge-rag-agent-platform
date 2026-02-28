# 模型产出物

本目录存放本地训练和部署的模型配置。权重文件（`.safetensors`、`.bin`）因体积过大不纳入 Git 版本管理。

## 目录结构

```
models/
├── qwen2.5-1.5b-intent-qlora/    意图分类 QLoRA 适配器
│   ├── adapter_config.json        PEFT LoRA 配置
│   ├── tokenizer_config.json      分词器配置
│   └── training_args.json         训练超参与结果记录
├── qwen2.5-7b-rewrite-qlora/     查询改写 QLoRA 适配器
│   ├── adapter_config.json
│   ├── tokenizer_config.json
│   └── training_args.json
├── bge-large-zh-v1.5/            向量化模型部署配置
│   └── deploy_config.json
└── bge-reranker-v2-m3/           精排模型部署配置
    └── deploy_config.json
```

## 模型分层

| 模型 | 用途 | 部署位置 | 说明 |
|------|------|----------|------|
| Qwen2.5-1.5B-Instruct + QLoRA | 意图分类 | 本地 vLLM (port 8001) | 1200 条标注数据微调 |
| Qwen2.5-7B-Instruct + QLoRA | 查询改写 | 本地 vLLM (port 8000) | 800 条改写样本微调 |
| BAAI/bge-large-zh-v1.5 | 文本向量化 | 本地 (port 8003) | 1024 维稠密向量 |
| BAAI/bge-reranker-v2-m3 | 精排重排序 | 本地 (port 8004) | cross-encoder |
| DeepSeek-V2.5 | 回答生成/摘要/评测 | 硅基流动 SiliconFlow API | 复杂推理任务 |

## 复现训练

```bash
# 意图分类
python3 scripts/train_qwen_qlora.py \
  --base-model Qwen/Qwen2.5-1.5B-Instruct \
  --dataset datasets/intent_train.jsonl \
  --output-dir artifacts/models/qwen2.5-1.5b-intent-qlora \
  --task intent --epochs 3 --lr 2e-4

# 查询改写
python3 scripts/train_qwen_qlora.py \
  --base-model Qwen/Qwen2.5-7B-Instruct \
  --dataset datasets/rewrite_train.jsonl \
  --output-dir artifacts/models/qwen2.5-7b-rewrite-qlora \
  --task rewrite --epochs 3 --lr 2e-4
```

## 启动推理服务

```bash
bash scripts/serve_vllm.sh
```
