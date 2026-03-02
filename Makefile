.PHONY: help install test lint fmt serve serve-java serve-front docker-up docker-down train-intent train-rewrite benchmark clean

PYTHON ?= python3
VENV   ?= .venv
PIP    := $(VENV)/bin/pip
PYTEST := $(VENV)/bin/pytest

help: ## 显示可用命令
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-18s\033[0m %s\n", $$1, $$2}'

install: ## 安装 Python 依赖并以 editable 模式安装项目
	$(PYTHON) -m venv $(VENV)
	$(PIP) install --upgrade pip
	$(PIP) install -e .
	$(PIP) install pytest pytest-cov ruff

test: ## 运行单元测试
	ENABLE_LOCAL_FALLBACK=1 $(PYTEST) tests/ -q --tb=short

test-cov: ## 运行测试并输出覆盖率
	ENABLE_LOCAL_FALLBACK=1 $(PYTEST) tests/ --cov=rag_agent_platform --cov-report=term-missing

lint: ## 代码风格检查
	$(VENV)/bin/ruff check src/ tests/

fmt: ## 自动格式化代码
	$(VENV)/bin/ruff format src/ tests/
	$(VENV)/bin/ruff check --fix src/ tests/

serve: ## 启动 Python RAG 引擎
	$(VENV)/bin/uvicorn rag_agent_platform.api.run:app --host 0.0.0.0 --port 8080 --reload

serve-java: ## 启动 Java 网关
	cd java-gateway && mvn spring-boot:run

serve-front: ## 启动前端开发服务
	cd frontend && npm run serve

docker-up: ## 启动基础设施容器 (Milvus/ES/Neo4j/Redis)
	docker compose up -d

docker-down: ## 停止基础设施容器
	docker compose down

train-intent: ## 训练意图分类 QLoRA 模型
	$(PYTHON) scripts/train_qwen_qlora.py \
		--base-model Qwen/Qwen2.5-1.5B-Instruct \
		--dataset datasets/intent_train.jsonl \
		--output-dir artifacts/models/qwen2.5-1.5b-intent-qlora \
		--task intent --epochs 3

train-rewrite: ## 训练查询改写 QLoRA 模型
	$(PYTHON) scripts/train_qwen_qlora.py \
		--base-model Qwen/Qwen2.5-7B-Instruct \
		--dataset datasets/rewrite_train.jsonl \
		--output-dir artifacts/models/qwen2.5-7b-rewrite-qlora \
		--task rewrite --epochs 3

benchmark: ## 运行检索评测
	PYTHONPATH=src $(PYTHON) scripts/run_rag_benchmark.py \
		--dataset benchmarks/sample_eval.jsonl \
		--output artifacts/benchmarks/candidate.json

clean: ## 清理构建产物
	rm -rf $(VENV) dist/ build/ *.egg-info src/*.egg-info
	find . -type d -name __pycache__ -exec rm -rf {} + 2>/dev/null || true
