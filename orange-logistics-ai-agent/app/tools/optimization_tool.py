"""路径优化服务调用工具

HTTP 调用路径优化服务。
"""
from typing import Optional, Type
from langchain_core.tools import BaseTool
from pydantic import BaseModel, Field
import httpx
import json
import logging

from app.config import settings

logger = logging.getLogger(__name__)


class OptimizationInput(BaseModel):
    """优化工具输入"""
    action: str = Field(
        description="优化动作: optimize_route(路径优化) / optimize_schedule(调度优化)"
    )
    params: str = Field(description="JSON 格式的请求参数")


class OptimizationTool(BaseTool):
    """路径优化服务调用工具

    调用 orange-logistics-ai-prediction 服务进行路径优化和调度优化。
    """

    name: str = "optimization_tool"
    description: str = (
        "调用路径优化服务。支持：\n"
        "1. optimize_route: 车辆路径优化（VRP），需要 warehouse、stores、vehicles\n"
        "2. optimize_schedule: 配送计划优化（遗传算法），需要 tasks、vehicles\n"
        "输入 action 和 JSON 格式的 params。"
    )
    args_schema: Type[BaseModel] = OptimizationInput

    async def _arun(self, action: str, params: str) -> str:
        """异步调用优化服务"""
        try:
            params_dict = json.loads(params) if isinstance(params, str) else params
        except json.JSONDecodeError:
            return "参数格式错误，请提供有效的 JSON"

        endpoint_map = {
            "optimize_route": "/api/v1/optimize/route",
            "optimize_schedule": "/api/v1/optimize/schedule",
        }

        endpoint = endpoint_map.get(action)
        if not endpoint:
            return f"不支持的优化动作: {action}"

        try:
            async with httpx.AsyncClient(timeout=60.0) as client:
                response = await client.post(
                    f"{settings.PREDICTION_SERVICE_URL}{endpoint}",
                    json=params_dict,
                )

                if response.status_code == 200:
                    result = response.json()
                    return self._format_result(action, result)
                else:
                    return f"优化服务返回错误: {response.status_code}"

        except httpx.ConnectError:
            return self._mock_result(action)
        except Exception as e:
            logger.error(f"Optimization service error: {e}")
            return f"优化服务调用失败: {str(e)}"

    def _run(self, action: str, params: str) -> str:
        """同步调用"""
        import asyncio
        return asyncio.run(self._arun(action, params))

    def _format_result(self, action: str, result: dict) -> str:
        """格式化优化结果"""
        if action == "optimize_route":
            summary = result.get("summary", {})
            routes = result.get("routes", [])
            lines = [
                "路径优化结果:",
                f"  使用车辆: {summary.get('vehicles_used', 0)} 辆",
                f"  总距离: {summary.get('total_distance_km', 0):.1f} km",
                f"  总成本: ¥{summary.get('total_cost', 0):.0f}",
                f"  服务门店: {summary.get('stores_served', 0)} 个",
                f"  未服务: {summary.get('stores_unserved', 0)} 个",
            ]
            for i, route in enumerate(routes[:3]):
                lines.append(
                    f"  路线{i+1} ({route.get('vehicle_id', '')}): "
                    f"{len(route.get('stops', []))} 站, "
                    f"{route.get('distance_km', 0):.1f}km"
                )
            return "\n".join(lines)

        elif action == "optimize_schedule":
            return (
                f"调度优化结果:\n"
                f"  总成本: ¥{result.get('total_cost', 0):.0f}\n"
                f"  总收益: ¥{result.get('total_revenue', 0):.0f}\n"
                f"  适应度: {result.get('fitness', 0):.4f}\n"
                f"  迭代次数: {result.get('generations_run', 0)}"
            )

        return json.dumps(result, ensure_ascii=False, indent=2)

    def _mock_result(self, action: str) -> str:
        """模拟优化结果（服务不可用时）"""
        if action == "optimize_route":
            return (
                "路径优化结果（模拟）:\n"
                "  使用车辆: 3 辆\n"
                "  总距离: 85.6 km\n"
                "  总成本: ¥342\n"
                "  服务门店: 15 个\n"
                "  注: 优化服务暂不可用，使用默认方案"
            )
        return "优化服务暂不可用"
