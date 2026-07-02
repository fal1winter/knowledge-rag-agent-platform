"""多模态视觉理解客户端。

支持调用多种视觉大模型进行场景描述、物体检测、OCR 等任务。
"""

import base64
import json
from dataclasses import dataclass
from typing import Any, Dict, List, Protocol

import numpy as np
import requests
from PIL import Image


class VisionClient(Protocol):
    """视觉理解客户端协议。"""

    def describe_scene(self, image: np.ndarray, prompt: str | None = None) -> str:
        """
        描述图像中的场景。

        Args:
            image: OpenCV 格式图像（BGR）
            prompt: 自定义提示词

        Returns:
            场景描述文本
        """

    def detect_objects(self, image: np.ndarray) -> List[Dict[str, Any]]:
        """
        检测图像中的物体。

        Returns:
            物体列表，每个包含 {"label": str, "confidence": float, "bbox": [x, y, w, h]}
        """

    def extract_screen_text(self, image: np.ndarray) -> str:
        """
        提取图像中的文字（适用于截图、PPT 等）。

        Returns:
            识别出的文本
        """


@dataclass
class QwenVLClient:
    """通义千问 VL 多模态模型客户端。"""

    endpoint: str
    api_key: str
    model: str = "qwen-vl-max"
    timeout: int = 60

    def describe_scene(self, image: np.ndarray, prompt: str | None = None) -> str:
        """使用 Qwen-VL 描述场景。"""
        default_prompt = "请详细描述这个画面中的场景、人物、物体和正在发生的活动。"
        prompt = prompt or default_prompt

        # 转换图像为 base64
        image_base64 = self._image_to_base64(image)

        payload = {
            "model": self.model,
            "messages": [
                {
                    "role": "user",
                    "content": [
                        {"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{image_base64}"}},
                        {"type": "text", "text": prompt}
                    ]
                }
            ],
            "max_tokens": 500,
            "temperature": 0.7
        }

        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json"
        }

        try:
            response = requests.post(
                self.endpoint,
                json=payload,
                headers=headers,
                timeout=self.timeout
            )
            response.raise_for_status()
            data = response.json()
            return data["choices"][0]["message"]["content"]
        except Exception as e:
            return f"[视觉理解失败: {str(e)}]"

    def detect_objects(self, image: np.ndarray) -> List[Dict[str, Any]]:
        """使用 Qwen-VL 检测物体（通过 prompt 引导）。"""
        prompt = "列出图片中的所有物体、人物和重要元素，用逗号分隔。"
        response = self.describe_scene(image, prompt)

        # 简单解析（生产环境应该用专门的物体检测模型）
        objects = []
        for item in response.split(","):
            item = item.strip()
            if item:
                objects.append({
                    "label": item,
                    "confidence": 1.0,  # Qwen-VL 不返回置信度
                    "bbox": None
                })
        return objects

    def extract_screen_text(self, image: np.ndarray) -> str:
        """使用 Qwen-VL 提取屏幕文字。"""
        prompt = "识别并提取图片中的所有文字内容，保持原有格式。"
        return self.describe_scene(image, prompt)

    def _image_to_base64(self, image: np.ndarray) -> str:
        """将 OpenCV 图像转为 base64。"""
        import cv2
        from io import BytesIO

        # BGR -> RGB
        image_rgb = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
        pil_img = Image.fromarray(image_rgb)

        # 压缩到合理大小
        max_size = 1024
        if max(pil_img.size) > max_size:
            pil_img.thumbnail((max_size, max_size), Image.Resampling.LANCZOS)

        buffer = BytesIO()
        pil_img.save(buffer, format="JPEG", quality=85)
        return base64.b64encode(buffer.getvalue()).decode("utf-8")


@dataclass
class GPT4VisionClient:
    """OpenAI GPT-4 Vision 客户端。"""

    api_key: str
    endpoint: str = "https://api.openai.com/v1/chat/completions"
    model: str = "gpt-4o"
    timeout: int = 60

    def describe_scene(self, image: np.ndarray, prompt: str | None = None) -> str:
        """使用 GPT-4V 描述场景。"""
        default_prompt = "Describe this scene in detail, including people, objects, activities, and context."
        prompt = prompt or default_prompt

        image_base64 = self._image_to_base64(image)

        payload = {
            "model": self.model,
            "messages": [
                {
                    "role": "user",
                    "content": [
                        {"type": "text", "text": prompt},
                        {
                            "type": "image_url",
                            "image_url": {"url": f"data:image/jpeg;base64,{image_base64}"}
                        }
                    ]
                }
            ],
            "max_tokens": 500
        }

        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json"
        }

        try:
            response = requests.post(
                self.endpoint,
                json=payload,
                headers=headers,
                timeout=self.timeout
            )
            response.raise_for_status()
            data = response.json()
            return data["choices"][0]["message"]["content"]
        except Exception as e:
            return f"[Vision API failed: {str(e)}]"

    def detect_objects(self, image: np.ndarray) -> List[Dict[str, Any]]:
        """通过 GPT-4V 检测物体。"""
        prompt = "List all objects, people, and important elements in this image. Format: item1, item2, item3"
        response = self.describe_scene(image, prompt)

        objects = []
        for item in response.split(","):
            item = item.strip()
            if item:
                objects.append({
                    "label": item,
                    "confidence": 1.0,
                    "bbox": None
                })
        return objects

    def extract_screen_text(self, image: np.ndarray) -> str:
        """提取屏幕文字。"""
        prompt = "Extract all text visible in this image. Preserve formatting when possible."
        return self.describe_scene(image, prompt)

    def _image_to_base64(self, image: np.ndarray) -> str:
        """将 OpenCV 图像转为 base64。"""
        import cv2
        from io import BytesIO

        image_rgb = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
        pil_img = Image.fromarray(image_rgb)

        max_size = 1024
        if max(pil_img.size) > max_size:
            pil_img.thumbnail((max_size, max_size), Image.Resampling.LANCZOS)

        buffer = BytesIO()
        pil_img.save(buffer, format="JPEG", quality=85)
        return base64.b64encode(buffer.getvalue()).decode("utf-8")


@dataclass
class LocalYOLODetector:
    """本地 YOLO 物体检测器（可选，用于降低成本）。"""

    model_path: str = "yolov8n.pt"  # YOLOv8 nano 模型

    def __post_init__(self):
        try:
            from ultralytics import YOLO
            self.model = YOLO(self.model_path)
        except ImportError:
            raise RuntimeError("Please install ultralytics: pip install ultralytics")

    def detect_objects(self, image: np.ndarray) -> List[Dict[str, Any]]:
        """使用 YOLO 检测物体。"""
        results = self.model(image, verbose=False)

        objects = []
        for result in results:
            boxes = result.boxes
            for box in boxes:
                objects.append({
                    "label": result.names[int(box.cls)],
                    "confidence": float(box.conf),
                    "bbox": box.xywh[0].tolist()  # [x_center, y_center, width, height]
                })

        return objects


class HybridVisionClient:
    """混合视觉客户端：VLM + 专用模型。"""

    def __init__(
        self,
        vlm_client: VisionClient,
        object_detector: LocalYOLODetector | None = None,
        ocr_client: Any | None = None
    ):
        self.vlm_client = vlm_client
        self.object_detector = object_detector
        self.ocr_client = ocr_client

    def describe_scene(self, image: np.ndarray, prompt: str | None = None) -> str:
        """场景描述（使用 VLM）。"""
        return self.vlm_client.describe_scene(image, prompt)

    def detect_objects(self, image: np.ndarray) -> List[Dict[str, Any]]:
        """物体检测（优先用本地 YOLO）。"""
        if self.object_detector:
            return self.object_detector.detect_objects(image)
        return self.vlm_client.detect_objects(image)

    def extract_screen_text(self, image: np.ndarray) -> str:
        """文字提取（优先用 OCR 服务）。"""
        if self.ocr_client:
            # 假设 ocr_client 有 extract_text 方法
            try:
                # 将 numpy 转为临时文件
                import tempfile
                import cv2
                with tempfile.NamedTemporaryFile(suffix=".jpg", delete=False) as f:
                    cv2.imwrite(f.name, image)
                    return self.ocr_client.extract_text(f.name)
            except Exception:
                pass
        return self.vlm_client.extract_screen_text(image)
