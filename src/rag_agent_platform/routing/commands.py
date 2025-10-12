"""用户会话管控指令解析。"""

from dataclasses import dataclass
from typing import Optional


@dataclass
class ControlCommand:
    name: str
    argument: Optional[str] = None


class ControlCommandParser:
    """解析 /clear、/context、/agentic 等专用控制指令。"""

    COMMANDS = {
        "/clear": "clear_conversation",
        "/context": "toggle_persistent_context",
        "/agentic": "force_iterative_retrieval",
    }

    def parse(self, message: str) -> ControlCommand | None:
        text = message.strip()
        if not text.startswith("/"):
            return None
        command, _, argument = text.partition(" ")
        mapped = self.COMMANDS.get(command.lower())
        if mapped is None:
            return None
        return ControlCommand(name=mapped, argument=argument.strip() or None)

