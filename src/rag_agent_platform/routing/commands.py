"""用户会话管控指令解析与注册表。

支持的指令族：
- /clear          清除当前会话上下文
- /context        切换持久上下文模式
- /agentic <q>    强制走迭代检索路径
- /materials <ids> 指定检索范围的资料 ID 列表
- /help           列出可用指令
- /debug          切换调试模式（返回检索链路信息）
"""

from dataclasses import dataclass, field
from typing import Dict, List, Optional


@dataclass
class ControlCommand:
    """解析后的控制指令。"""
    name: str
    argument: Optional[str] = None
    metadata: Dict[str, str] = field(default_factory=dict)


@dataclass
class CommandDefinition:
    """指令注册表条目：名称、描述、是否接受参数。"""
    slash: str
    internal_name: str
    description: str
    accepts_argument: bool = False


class ControlCommandParser:
    """解析用户输入中的 /xxx 控制指令。

    设计要点：
    - 指令通过注册表驱动，新增指令只需追加 REGISTRY 条目
    - 支持参数提取（如 /agentic 后的查询文本）
    - 大小写不敏感
    - 非指令消息返回 None，不影响正常对话流
    """

    REGISTRY: List[CommandDefinition] = [
        CommandDefinition("/clear", "clear_conversation", "清除当前会话历史和短期记忆"),
        CommandDefinition("/context", "toggle_persistent_context", "切换持久上下文模式"),
        CommandDefinition("/agentic", "force_iterative_retrieval", "强制使用迭代检索策略", accepts_argument=True),
        CommandDefinition("/materials", "set_material_scope", "限定检索范围到指定资料 ID", accepts_argument=True),
        CommandDefinition("/help", "show_help", "显示可用控制指令列表"),
        CommandDefinition("/debug", "toggle_debug", "切换调试模式，返回完整检索链路信息"),
    ]

    def __init__(self):
        self._lookup: Dict[str, CommandDefinition] = {
            cmd.slash: cmd for cmd in self.REGISTRY
        }

    def parse(self, message: str) -> ControlCommand | None:
        """解析消息为控制指令，非指令消息返回 None。"""
        text = message.strip()
        if not text.startswith("/"):
            return None
        parts = text.split(maxsplit=1)
        slash = parts[0].lower()
        argument = parts[1].strip() if len(parts) > 1 else None

        definition = self._lookup.get(slash)
        if definition is None:
            return None

        return ControlCommand(
            name=definition.internal_name,
            argument=argument if definition.accepts_argument else None,
        )

    def available_commands(self) -> List[Dict[str, str]]:
        """返回所有可用指令的描述列表，供 /help 使用。"""
        return [
            {"command": cmd.slash, "description": cmd.description}
            for cmd in self.REGISTRY
        ]

    def help_text(self) -> str:
        """生成用户可读的帮助文本。"""
        lines = ["可用控制指令："]
        for cmd in self.REGISTRY:
            arg_hint = " <参数>" if cmd.accepts_argument else ""
            lines.append(f"  {cmd.slash}{arg_hint} — {cmd.description}")
        return "\n".join(lines)

