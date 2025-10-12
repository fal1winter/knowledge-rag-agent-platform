"""外部服务适配器使用的轻量 JSON HTTP 客户端。"""

from __future__ import annotations

import json
from typing import Any, Dict, Mapping
import urllib.error
import urllib.request


class JsonHttpClient:
    def __init__(self, timeout: float = 30.0, headers: Mapping[str, str] | None = None):
        self.timeout = timeout
        self.headers = dict(headers or {})

    def request(self, method: str, url: str, body: Any | None = None, headers: Mapping[str, str] | None = None) -> Any:
        data = None if body is None else json.dumps(body, ensure_ascii=False).encode('utf-8')
        request_headers: Dict[str, str] = {'Content-Type': 'application/json'}
        request_headers.update(self.headers)
        request_headers.update(headers or {})
        request = urllib.request.Request(url, data=data, headers=request_headers, method=method.upper())
        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as response:
                raw = response.read().decode('utf-8')
        except urllib.error.HTTPError as exc:
            detail = exc.read().decode('utf-8', errors='ignore')
            raise RuntimeError(f'HTTP {exc.code} from {url}: {detail[:500]}') from exc
        if not raw:
            return {}
        return json.loads(raw)

    def post(self, url: str, body: Any, headers: Mapping[str, str] | None = None) -> Any:
        return self.request('POST', url, body, headers)

    def get(self, url: str, headers: Mapping[str, str] | None = None) -> Any:
        return self.request('GET', url, None, headers)


class OpenAICompatibleClient:
    def __init__(self, endpoint: str, api_key: str | None = None, timeout: float = 60.0):
        self.endpoint = endpoint.rstrip('/')
        headers = {'Authorization': f'Bearer {api_key}'} if api_key else {}
        self.http = JsonHttpClient(timeout=timeout, headers=headers)

    def chat(self, model: str, messages: list[dict[str, str]], temperature: float = 0.0, extra: dict[str, Any] | None = None) -> str:
        payload: dict[str, Any] = {
            'model': model,
            'messages': messages,
            'temperature': temperature,
        }
        if extra:
            payload.update(extra)
        data = self.http.post(f'{self.endpoint}/chat/completions', payload)
        choices = data.get('choices') or []
        if not choices:
            raise RuntimeError('empty model response')
        message = choices[0].get('message') or {}
        return str(message.get('content', '')).strip()

    def embeddings(self, model: str, texts: list[str]) -> list[list[float]]:
        data = self.http.post(f'{self.endpoint}/embeddings', {'model': model, 'input': texts})
        rows = data.get('data') or []
        return [list(map(float, row.get('embedding', []))) for row in rows]
