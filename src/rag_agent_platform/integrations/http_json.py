"""外部服务适配器使用的轻量 JSON HTTP 客户端。

功能点：
- 基于 urllib 零依赖 HTTP 客户端
- 自动重试（指数退避）
- 请求/响应日志
- 流式 SSE 读取（用于模型 streaming 输出）
- OpenAI 兼容 API 封装（chat + embeddings + token 统计）
"""

from __future__ import annotations

import json
import logging
import time
from dataclasses import dataclass, field
from typing import Any, Dict, Generator, List, Mapping, Optional
import urllib.error
import urllib.request

logger = logging.getLogger(__name__)


@dataclass
class HttpResponse:
    """HTTP 响应包装。"""
    status: int
    body: Any
    headers: Dict[str, str] = field(default_factory=dict)
    elapsed_ms: float = 0.0


class RetryConfig:
    """重试配置。"""

    def __init__(
        self,
        max_attempts: int = 3,
        initial_delay: float = 0.5,
        max_delay: float = 5.0,
        backoff_multiplier: float = 2.0,
        retryable_status_codes: Optional[List[int]] = None,
    ):
        self.max_attempts = max_attempts
        self.initial_delay = initial_delay
        self.max_delay = max_delay
        self.backoff_multiplier = backoff_multiplier
        self.retryable_status_codes = retryable_status_codes or [429, 502, 503, 504]


class HttpError(RuntimeError):
    """HTTP 请求错误，附带状态码和响应体摘要。"""

    def __init__(self, status: int, url: str, detail: str):
        self.status = status
        self.url = url
        self.detail = detail
        super().__init__(f"HTTP {status} from {url}: {detail[:500]}")


class JsonHttpClient:
    """零外部依赖的 JSON HTTP 客户端。

    特性：
    - 自动 JSON 序列化/反序列化
    - 可配置超时和默认头
    - 指数退避重试（可选）
    - 请求耗时跟踪
    """

    def __init__(
        self,
        timeout: float = 30.0,
        headers: Mapping[str, str] | None = None,
        retry: RetryConfig | None = None,
    ):
        self.timeout = timeout
        self.headers = dict(headers or {})
        self.retry = retry or RetryConfig(max_attempts=1)  # 默认不重试

    def request(
        self,
        method: str,
        url: str,
        body: Any | None = None,
        headers: Mapping[str, str] | None = None,
    ) -> HttpResponse:
        """发送 HTTP 请求，支持自动重试。"""
        data = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
        request_headers: Dict[str, str] = {"Content-Type": "application/json"}
        request_headers.update(self.headers)
        request_headers.update(headers or {})

        last_error: Optional[Exception] = None
        delay = self.retry.initial_delay

        for attempt in range(1, self.retry.max_attempts + 1):
            try:
                return self._do_request(method, url, data, request_headers)
            except HttpError as e:
                last_error = e
                if e.status not in self.retry.retryable_status_codes:
                    raise
                if attempt == self.retry.max_attempts:
                    raise
                logger.warning(
                    "请求失败 (attempt %d/%d): %s %s → %d, %.1fs 后重试",
                    attempt, self.retry.max_attempts, method, url, e.status, delay,
                )
            except (urllib.error.URLError, OSError) as e:
                last_error = e
                if attempt == self.retry.max_attempts:
                    raise RuntimeError(f"请求失败 (已重试 {attempt} 次): {url}") from e
                logger.warning(
                    "网络错误 (attempt %d/%d): %s %s → %s, %.1fs 后重试",
                    attempt, self.retry.max_attempts, method, url, e, delay,
                )

            time.sleep(delay)
            delay = min(delay * self.retry.backoff_multiplier, self.retry.max_delay)

        raise last_error or RuntimeError(f"请求失败: {url}")

    def _do_request(
        self,
        method: str,
        url: str,
        data: Optional[bytes],
        headers: Dict[str, str],
    ) -> HttpResponse:
        """执行单次 HTTP 请求。"""
        req = urllib.request.Request(url, data=data, headers=headers, method=method.upper())
        start = time.time()
        try:
            with urllib.request.urlopen(req, timeout=self.timeout) as response:
                raw = response.read().decode("utf-8")
                elapsed = (time.time() - start) * 1000
                resp_headers = dict(response.headers.items())
        except urllib.error.HTTPError as exc:
            elapsed = (time.time() - start) * 1000
            detail = exc.read().decode("utf-8", errors="ignore")
            logger.debug("HTTP %s %s → %d (%.0fms)", method, url, exc.code, elapsed)
            raise HttpError(exc.code, url, detail) from exc

        logger.debug("HTTP %s %s → 200 (%.0fms)", method, url, elapsed)
        parsed = json.loads(raw) if raw.strip() else {}
        return HttpResponse(status=200, body=parsed, headers=resp_headers, elapsed_ms=elapsed)

    def post(self, url: str, body: Any, headers: Mapping[str, str] | None = None) -> Any:
        """POST 请求，返回解析后的 JSON body。"""
        return self.request("POST", url, body, headers).body

    def get(self, url: str, headers: Mapping[str, str] | None = None) -> Any:
        """GET 请求，返回解析后的 JSON body。"""
        return self.request("GET", url, None, headers).body


@dataclass
class TokenUsage:
    """API 调用的 token 使用量。"""
    prompt_tokens: int = 0
    completion_tokens: int = 0
    total_tokens: int = 0


@dataclass
class ChatResult:
    """Chat completion 的结构化结果。"""
    content: str
    usage: TokenUsage
    model: str = ""
    finish_reason: str = ""


class OpenAICompatibleClient:
    """OpenAI 兼容 API 客户端。

    支持 SiliconFlow、vLLM、Ollama 等实现 OpenAI 协议的后端。

    功能：
    - chat completions（同步 + 流式）
    - embeddings（批量，自动分批）
    - token 用量统计
    - 自动重试网络瞬时故障
    """

    # 单次 embedding 请求的最大文本数（避免超时）
    EMBEDDING_BATCH_SIZE = 32

    def __init__(
        self,
        endpoint: str,
        api_key: str | None = None,
        timeout: float = 60.0,
        max_retries: int = 3,
    ):
        self.endpoint = endpoint.rstrip("/")
        self.model_name: str = ""
        headers: Dict[str, str] = {}
        if api_key:
            headers["Authorization"] = f"Bearer {api_key}"
        retry = RetryConfig(max_attempts=max_retries)
        self.http = JsonHttpClient(timeout=timeout, headers=headers, retry=retry)
        # 累计 token 用量（生命周期内）
        self._total_usage = TokenUsage()

    def chat(
        self,
        model: str,
        messages: list[dict[str, str]],
        temperature: float = 0.0,
        max_tokens: Optional[int] = None,
        extra: dict[str, Any] | None = None,
    ) -> ChatResult:
        """发送 chat completion 请求，返回结构化结果。"""
        payload: dict[str, Any] = {
            "model": model,
            "messages": messages,
            "temperature": temperature,
        }
        if max_tokens is not None:
            payload["max_tokens"] = max_tokens
        if extra:
            payload.update(extra)

        data = self.http.post(f"{self.endpoint}/chat/completions", payload)
        choices = data.get("choices") or []
        if not choices:
            raise RuntimeError("模型返回空响应")

        message = choices[0].get("message") or {}
        content = str(message.get("content", "")).strip()
        finish_reason = choices[0].get("finish_reason", "")

        # 解析 token 用量
        usage_data = data.get("usage") or {}
        usage = TokenUsage(
            prompt_tokens=usage_data.get("prompt_tokens", 0),
            completion_tokens=usage_data.get("completion_tokens", 0),
            total_tokens=usage_data.get("total_tokens", 0),
        )
        self._accumulate_usage(usage)

        return ChatResult(
            content=content,
            usage=usage,
            model=data.get("model", model),
            finish_reason=finish_reason,
        )

    def chat_stream(
        self,
        model: str,
        messages: list[dict[str, str]],
        temperature: float = 0.0,
        extra: dict[str, Any] | None = None,
    ) -> Generator[str, None, None]:
        """流式 chat completion，逐 token 生成内容。

        Yields:
            每个增量 token 文本片段
        """
        payload: dict[str, Any] = {
            "model": model,
            "messages": messages,
            "temperature": temperature,
            "stream": True,
        }
        if extra:
            payload.update(extra)

        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        headers = {"Content-Type": "application/json"}
        headers.update(self.http.headers)
        req = urllib.request.Request(
            f"{self.endpoint}/chat/completions",
            data=data,
            headers=headers,
            method="POST",
        )

        with urllib.request.urlopen(req, timeout=self.http.timeout) as response:
            for line in response:
                text = line.decode("utf-8").strip()
                if not text or not text.startswith("data: "):
                    continue
                json_str = text[6:]
                if json_str == "[DONE]":
                    break
                try:
                    chunk = json.loads(json_str)
                    delta = chunk.get("choices", [{}])[0].get("delta", {})
                    content = delta.get("content", "")
                    if content:
                        yield content
                except (json.JSONDecodeError, IndexError, KeyError):
                    continue

    def embeddings(self, model: str, texts: list[str]) -> list[list[float]]:
        """批量生成 embeddings，自动分批避免超时。"""
        all_embeddings: list[list[float]] = []

        for i in range(0, len(texts), self.EMBEDDING_BATCH_SIZE):
            batch = texts[i : i + self.EMBEDDING_BATCH_SIZE]
            data = self.http.post(
                f"{self.endpoint}/embeddings",
                {"model": model, "input": batch},
            )
            rows = data.get("data") or []
            # 按 index 排序确保顺序正确
            rows.sort(key=lambda r: r.get("index", 0))
            batch_embeddings = [list(map(float, row.get("embedding", []))) for row in rows]
            all_embeddings.extend(batch_embeddings)

            # 统计 token 用量
            usage_data = data.get("usage") or {}
            if usage_data:
                usage = TokenUsage(
                    prompt_tokens=usage_data.get("prompt_tokens", 0),
                    total_tokens=usage_data.get("total_tokens", 0),
                )
                self._accumulate_usage(usage)

        return all_embeddings

    def _accumulate_usage(self, usage: TokenUsage) -> None:
        """累加 token 使用量。"""
        self._total_usage.prompt_tokens += usage.prompt_tokens
        self._total_usage.completion_tokens += usage.completion_tokens
        self._total_usage.total_tokens += usage.total_tokens

    @property
    def total_usage(self) -> TokenUsage:
        """获取客户端生命周期内的累计 token 使用量。"""
        return self._total_usage
