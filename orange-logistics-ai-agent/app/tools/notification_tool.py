"""通知服务工具

触发通知服务发送消息。
"""
from typing import Optional, Type
from langchain_core.tools import BaseTool
from pydantic import BaseModel, Field
import httpx
import logging

from app.config import settings

logger = logging.getLogger(__name__)


class NotificationInput(BaseModel):
    """通知工具输入"""
    action: str = Field(default="send", description="动作: send")
    target: str = Field(description="通知目标: customer/driver/dispatch_team/ops_team")
    message: str = Field(description="通知内容")


class NotificationTool(BaseTool):
    """通知服务工具

    触发通知服务向不同角色发送消息。
    支持：客户通知、司机通知、调度团队通知、运营团队通知。
    """

    name: str = "notification_tool"
    description: str = (
        "发送通知消息。指定目标（customer/driver/dispatch_team/ops_team）和消息内容。"
        "适用于：通知客户配送状态、通知司机新任务、上报异常给运营团队等。"
    )
    args_schema: Type[BaseModel] = NotificationInput

    async def _arun(
        self, action: str = "send", target: str = "", message: str = ""
    ) -> str:
        """异步发送通知"""
        if not target or not message:
            return "错误：需要指定通知目标和消息内容"

        try:
            async with httpx.AsyncClient(timeout=10.0) as client:
                response = await client.post(
                    f"{settings.NOTIFICATION_SERVICE_URL}/api/v1/notify",
                    json={
                        "target": target,
                        "message": message,
                        "channel": self._get_channel(target),
                        "priority": "normal",
                    },
                )

                if response.status_code == 200:
                    return f"通知已发送给 {target}: {message[:50]}..."
                else:
                    return f"通知发送失败: HTTP {response.status_code}"

        except httpx.ConnectError:
            # 服务不可用时记录日志
            logger.info(f"[模拟通知] 目标: {target}, 内容: {message}")
            return f"通知已记录（模拟）: 目标={target}, 内容={message[:100]}"
        except Exception as e:
            logger.error(f"Notification error: {e}")
            return f"通知发送异常: {str(e)}"

    def _run(self, action: str = "send", target: str = "", message: str = "") -> str:
        """同步发送"""
        import asyncio
        return asyncio.run(self._arun(action, target, message))

    def _get_channel(self, target: str) -> str:
        """获取通知渠道"""
        channel_map = {
            "customer": "sms",       # 客户用短信
            "driver": "app_push",    # 司机用 APP 推送
            "dispatch_team": "im",   # 调度团队用即时通讯
            "ops_team": "im",        # 运营团队用即时通讯
        }
        return channel_map.get(target, "im")
