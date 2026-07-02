"""多模态视频 RAG 依赖装配。

集成关键帧提取、视觉理解、时序对齐到入库管线。
"""

from rag_agent_platform.config import load_config
from rag_agent_platform.documents.loaders import ASRClient
from rag_agent_platform.video.multimodal_loader import MultimodalVideoLoader, SmartVideoLoader
from rag_agent_platform.video.vision_client import QwenVLClient, GPT4VisionClient, HybridVisionClient


def build_vision_client():
    """构建视觉理解客户端。"""
    config = load_config()

    # 根据配置选择客户端
    if "gpt-4" in config.models.vision_model.lower():
        return GPT4VisionClient(
            api_key=config.models.vision_api_key,
            endpoint=config.models.vision_endpoint,
            model=config.models.vision_model,
        )
    else:
        # 默认使用 Qwen-VL
        return QwenVLClient(
            endpoint=config.models.vision_endpoint,
            api_key=config.models.vision_api_key,
            model=config.models.vision_model,
        )


def build_multimodal_video_loader(asr_client: ASRClient):
    """构建完整多模态视频加载器。"""
    vision_client = build_vision_client()

    return MultimodalVideoLoader(
        asr_client=asr_client,
        vision_client=vision_client,
        max_keyframes=50,
        keyframe_interval=2.0,
        enable_object_detection=True,
        enable_screen_text=True,
    )


def build_smart_video_loader(asr_client: ASRClient):
    """构建智能视频加载器（根据内容决定是否启用视觉理解）。"""
    vision_client = build_vision_client()

    return SmartVideoLoader(
        asr_client=asr_client,
        vision_client=vision_client,
        max_keyframes=30,
    )
