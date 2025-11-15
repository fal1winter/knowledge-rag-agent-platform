# 训练数据集

本目录存放意图路由和查询改写的训练样本。

## 数据来源

完整训练集共约 2000 条标注样本，来源于本地项目实际运营中积累的用户对话日志，经脱敏和人工审核后生成。

由于完整数据含业务敏感信息，仓库仅保留各 20 条代表性样本用于展示数据格式和模型输入结构。完整数据集存放于内部对象存储，训练时通过 `DATA_DIR` 环境变量指定路径加载。

## 文件说明

| 文件 | 用途 | 完整规模 |
|------|------|----------|
| `intent_train.jsonl` | 意图分类训练（QLoRA 微调 Qwen2.5-1.5B） | ~1200 条 |
| `rewrite_train.jsonl` | 查询改写训练（口语化消歧 + 指代消解） | ~800 条 |

## 数据格式

### intent_train.jsonl

每行一条 JSON，字段：
- `query`: 用户原始问题
- `label_json`: 标注结果（intent、confidence、reasoning）

意图类别：
- `material_search` — 知识库素材检索
- `complex_reasoning` — 跨文档复杂推理
- `entity_relation` — 实体关系/图谱查询
- `direct_qa` — 简单事实问答
- `chitchat` — 闲聊/无关请求
- `command` — 控制指令

### rewrite_train.jsonl

每行一条 JSON，字段：
- `context`: 上文摘要列表（可为空）
- `query`: 用户当前输入（可能含指代、省略）
- `target_json`: 改写目标（完整自包含查询）

## 训练方式

```bash
# 意图分类 QLoRA 微调
python3 scripts/train_intent_classifier.py \
  --data-path $DATA_DIR/intent_train.jsonl \
  --base-model Qwen/Qwen2.5-1.5B-Instruct \
  --output-dir artifacts/intent_lora \
  --epochs 3 --lr 2e-4 --lora-rank 16

# 查询改写微调
python3 scripts/train_query_rewriter.py \
  --data-path $DATA_DIR/rewrite_train.jsonl \
  --base-model Qwen/Qwen2.5-1.5B-Instruct \
  --output-dir artifacts/rewrite_lora \
  --epochs 3 --lr 2e-4 --lora-rank 8
```
