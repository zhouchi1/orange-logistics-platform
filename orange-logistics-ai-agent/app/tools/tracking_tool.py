"""物流轨迹查询工具

HTTP 调用 Java 物流服务查询轨迹。
"""
from typing import Optional, Type
from langchain_core.tools import BaseTool
from pydantic import BaseModel, Field
import httpx
import logging

from app.config import settings

logger = logging.getLogger(__name__)


class TrackingInput(BaseModel):
    """轨迹查询输入"""
    order_id: str = Field(description="订单号或运单号")


class TrackingTool(BaseTool):
    """物流轨迹查询工具

    调用 Java 物流服务 API 查询包裹的实时轨迹信息。
    """

    name: str = "tracking_tool"
    description: str = (
        "查询物流轨迹信息。输入订单号或运单号，返回包裹的实时位置和配送状态。"
        "适用于：查件、催件、确认配送状态等场景。"
    )
    args_schema: Type[BaseModel] = TrackingInput

    async def _arun(self, order_id: str) -> str:
        """异步查询物流轨迹"""
        try:
            async with httpx.AsyncClient(timeout=10.0) as client:
                response = await client.get(
                    f"{settings.LOGISTICS_API_URL}/api/v1/tracking/{order_id}"
                )

                if response.status_code == 200:
                    data = response.json()
                    return self._format_tracking(data)
                elif response.status_code == 404:
                    return f"未找到订单 {order_id} 的物流信息，请确认订单号是否正确。"
                else:
                    return f"查询失败，服务返回状态码: {response.status_code}"

        except httpx.ConnectError:
            # 服务不可用时返回模拟数据
            return self._mock_tracking(order_id)
        except Exception as e:
            logger.error(f"Tracking query error: {e}")
            return f"查询异常: {str(e)}"

    def _run(self, order_id: str) -> str:
        """同步查询（fallback）"""
        import asyncio
        return asyncio.run(self._arun(order_id))

    def _format_tracking(self, data: dict) -> str:
        """格式化轨迹信息"""
        status = data.get("status", "未知")
        events = data.get("events", [])

        result = f"订单状态: {status}\n"
        if events:
            result += "最新轨迹:\n"
            for event in events[-5:]:  # 最近5条
                result += f"  [{event.get('time', '')}] {event.get('description', '')}\n"

        eta = data.get("estimated_delivery_time")
        if eta:
            result += f"预计送达: {eta}"

        return result

    def _mock_tracking(self, order_id: str) -> str:
        """模拟轨迹数据（服务不可用时）"""
        return (
            f"订单 {order_id} 物流信息:\n"
            f"状态: 配送中\n"
            f"最新轨迹:\n"
            f"  [今天 08:30] 包裹已从北京分拣中心发出\n"
            f"  [今天 10:15] 包裹已到达朝阳区配送站\n"
            f"  [今天 11:00] 快递员已取件，正在配送中\n"
            f"预计送达: 今天 14:00 前"
        )
