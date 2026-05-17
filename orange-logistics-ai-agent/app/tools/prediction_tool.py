"""预测服务调用工具

HTTP 调用 AI 预测服务。
"""
from typing import Optional, Type
from langchain_core.tools import BaseTool
from pydantic import BaseModel, Field
import httpx
import json
import logging

from app.config import settings

logger = logging.getLogger(__name__)


class PredictionInput(BaseModel):
    """预测工具输入"""
    action: str = Field(
        description="预测动作: predict_delivery_time(预测配送时效) / predict_store_optimal_time(预测最优到货时间)"
    )
    params: str = Field(description="JSON 格式的请求参数")


class PredictionTool(BaseTool):
    """AI 预测服务调用工具

    调用 orange-logistics-ai-prediction 服务进行时效预测和门店最优时间预测。
    """

    name: str = "prediction_tool"
    description: str = (
        "调用 AI 预测服务。支持两种预测：\n"
        "1. predict_delivery_time: 预测配送时效，需要 store_id 和 store_features\n"
        "2. predict_store_optimal_time: 预测门店最优到货时间，需要 stores 列表\n"
        "输入 action 和 JSON 格式的 params。"
    )
    args_schema: Type[BaseModel] = PredictionInput

    async def _arun(self, action: str, params: str) -> str:
        """异步调用预测服务"""
        try:
            params_dict = json.loads(params) if isinstance(params, str) else params
        except json.JSONDecodeError:
            return "参数格式错误，请提供有效的 JSON"

        endpoint_map = {
            "predict_delivery_time": "/api/v1/predict/delivery-time",
            "predict_store_optimal_time": "/api/v1/predict/store-optimal-time",
        }

        endpoint = endpoint_map.get(action)
        if not endpoint:
            return f"不支持的预测动作: {action}。支持: {list(endpoint_map.keys())}"

        try:
            async with httpx.AsyncClient(timeout=30.0) as client:
                response = await client.post(
                    f"{settings.PREDICTION_SERVICE_URL}{endpoint}",
                    json=params_dict,
                )

                if response.status_code == 200:
                    result = response.json()
                    return self._format_prediction(action, result)
                else:
                    return f"预测服务返回错误: {response.status_code} - {response.text}"

        except httpx.ConnectError:
            return self._mock_prediction(action, params_dict)
        except Exception as e:
            logger.error(f"Prediction service error: {e}")
            return f"预测服务调用失败: {str(e)}"

    def _run(self, action: str, params: str) -> str:
        """同步调用"""
        import asyncio
        return asyncio.run(self._arun(action, params))

    def _format_prediction(self, action: str, result: dict) -> str:
        """格式化预测结果"""
        if action == "predict_delivery_time":
            hours = result.get("predicted_hours", 0)
            ci = result.get("confidence_interval", {})
            return (
                f"配送时效预测:\n"
                f"  预计: {hours:.1f} 小时\n"
                f"  置信区间: {ci.get('lower', 0):.1f} - {ci.get('upper', 0):.1f} 小时\n"
                f"  模型状态: {result.get('model_status', 'unknown')}"
            )
        elif action == "predict_store_optimal_time":
            predictions = result.get("predictions", [])
            lines = ["门店最优到货时间预测:"]
            for p in predictions[:5]:
                window = p.get("optimal_window", {})
                lines.append(
                    f"  {p.get('store_name', p.get('store_id'))}: "
                    f"{window.get('start_hour', '?')}:00-{window.get('end_hour', '?')}:00 "
                    f"(收益增量: ¥{p.get('expected_revenue_gain', 0):.0f})"
                )
            return "\n".join(lines)
        return json.dumps(result, ensure_ascii=False, indent=2)

    def _mock_prediction(self, action: str, params: dict) -> str:
        """模拟预测结果（服务不可用时）"""
        if action == "predict_delivery_time":
            return (
                "配送时效预测（模拟）:\n"
                "  预计: 3.2 小时\n"
                "  置信区间: 2.5 - 4.1 小时\n"
                "  注: 预测服务暂不可用，使用默认估算"
            )
        return "预测服务暂不可用，请稍后重试"
