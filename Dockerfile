FROM python:3.10-slim AS base

WORKDIR /app

# 系统依赖（LibreOffice 用于旧格式文档转换）
RUN apt-get update && apt-get install -y --no-install-recommends \
    libreoffice-core libreoffice-writer libreoffice-calc libreoffice-impress \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt pyproject.toml ./
RUN pip install --no-cache-dir -r requirements.txt && pip install --no-cache-dir -e .

COPY src/ src/
COPY scripts/ scripts/

EXPOSE 8080

CMD ["uvicorn", "rag_agent_platform.api.run:app", "--host", "0.0.0.0", "--port", "8080"]
