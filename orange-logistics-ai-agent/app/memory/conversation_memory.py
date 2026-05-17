"""对话记忆管理器"""
from typing import Dict, List, Optional, Any
from collections import defaultdict
import logging
import time

logger = logging.getLogger(__name__)


class ConversationMemoryManager:
    """对话记忆管理

    基于 Redis 的对话历史存储，支持：
    - 短期记忆（当前对话上下文）
    - 长期记忆（用户偏好、历史决策）
    - 摘要记忆（长对话自动摘要）
    """

    def __init__(self):
        self._memory: Dict[str, List[Dict[str, Any]]] = defaultdict(list)
        self._max_history = 20

    async def get_history(
        self,
        session_id: str,
        limit: int = 10,
    ) -> List[Dict[str, Any]]:
        """获取对话历史

        Args:
            session_id: 会话ID
            limit: 返回条数

        Returns:
            对话历史列表
        """
        history = self._memory.get(session_id, [])
        return history[-limit:]

    async def add_message(
        self,
        session_id: str,
        role: str,
        content: str,
        metadata: Optional[Dict] = None,
    ):
        """添加消息到对话历史

        Args:
            session_id: 会话ID
            role: 角色 (user/assistant/system)
            content: 消息内容
            metadata: 附加元数据
        """
        message = {
            "role": role,
            "content": content,
            "timestamp": time.time(),
            "metadata": metadata or {},
        }
        self._memory[session_id].append(message)

        # 超过最大长度时截断
        if len(self._memory[session_id]) > self._max_history:
            self._memory[session_id] = self._memory[session_id][-self._max_history:]

    async def clear_session(self, session_id: str):
        """清除会话历史"""
        self._memory.pop(session_id, None)
        logger.info(f"Cleared memory for session: {session_id}")

    async def get_summary(self, session_id: str) -> Optional[str]:
        """获取对话摘要"""
        history = self._memory.get(session_id, [])
        if not history:
            return None
        return f"对话包含 {len(history)} 条消息"
