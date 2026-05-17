"""数据库查询工具

直接查询 MySQL/ClickHouse 获取业务数据。
"""
from typing import Optional, Type
from langchain_core.tools import BaseTool
from pydantic import BaseModel, Field
import logging
import json

from app.config import settings

logger = logging.getLogger(__name__)


class DatabaseInput(BaseModel):
    """数据库查询输入"""
    query: str = Field(description="自然语言查询描述，例如：'查询今天待配送的订单数量'")


class DatabaseTool(BaseTool):
    """数据库查询工具

    将自然语言转换为 SQL 查询，支持 MySQL 和 ClickHouse。
    """

    name: str = "database_tool"
    description: str = (
        "查询物流业务数据库。输入自然语言描述你想查询的数据，"
        "例如：'查询今天待配送的订单数量'、'统计本周各区域配送时效'、"
        "'查询门店 STORE_001 的历史订单'。"
        "支持查询：订单、配送记录、门店信息、车辆状态、运营指标等。"
    )
    args_schema: Type[BaseModel] = DatabaseInput

    # 可查询的表结构（供 LLM 参考）
    SCHEMA_INFO = {
        "orders": "订单表(order_id, store_id, status, created_at, delivery_time, weight_kg, address)",
        "delivery_records": "配送记录(record_id, order_id, driver_id, vehicle_id, start_time, end_time, distance_km)",
        "stores": "门店表(store_id, name, type, latitude, longitude, area, daily_orders)",
        "vehicles": "车辆表(vehicle_id, type, capacity, status, current_lat, current_lon, driver_id)",
        "drivers": "司机表(driver_id, name, phone, status, total_deliveries, rating)",
    }

    async def _arun(self, query: str) -> str:
        """异步查询数据库"""
        # 在实际环境中，这里会：
        # 1. 使用 LLM 将自然语言转为 SQL
        # 2. 执行 SQL 查询
        # 3. 格式化返回结果

        # 模拟查询结果（实际应连接数据库）
        return self._simulate_query(query)

    def _run(self, query: str) -> str:
        """同步查询"""
        return self._simulate_query(query)

    def _simulate_query(self, query: str) -> str:
        """模拟数据库查询结果"""
        query_lower = query.lower()

        if "待配送" in query or "pending" in query_lower:
            return json.dumps({
                "total_pending_orders": 156,
                "by_priority": {"urgent": 12, "high": 34, "normal": 110},
                "by_area": {"朝阳区": 45, "海淀区": 38, "丰台区": 32, "其他": 41},
            }, ensure_ascii=False)

        elif "在途" in query or "车辆" in query:
            return json.dumps({
                "total_vehicles": 25,
                "in_transit": 18,
                "idle": 5,
                "maintenance": 2,
                "avg_utilization": 0.72,
            }, ensure_ascii=False)

        elif "时效" in query or "配送时间" in query:
            return json.dumps({
                "avg_delivery_hours": 3.2,
                "median_delivery_hours": 2.8,
                "on_time_rate": 0.94,
                "by_area": {
                    "朝阳区": {"avg": 2.5, "on_time": 0.96},
                    "海淀区": {"avg": 3.1, "on_time": 0.93},
                    "丰台区": {"avg": 3.8, "on_time": 0.91},
                },
            }, ensure_ascii=False)

        elif "门店" in query or "store" in query_lower:
            return json.dumps({
                "total_stores": 320,
                "active_today": 298,
                "by_type": {"convenience": 180, "supermarket": 85, "restaurant": 55},
                "avg_daily_orders": 15.6,
            }, ensure_ascii=False)

        elif "异常" in query or "anomaly" in query_lower:
            return json.dumps({
                "today_anomalies": 8,
                "by_type": {"delay": 4, "address_issue": 2, "rejection": 1, "damage": 1},
                "resolved": 5,
                "pending": 3,
            }, ensure_ascii=False)

        elif "历史" in query or "类似" in query:
            return json.dumps({
                "similar_cases": [
                    {"case_id": "ANM_001", "type": "delay", "resolution": "自动通知客户", "time": "2小时"},
                    {"case_id": "ANM_002", "type": "delay", "resolution": "重新调度", "time": "1小时"},
                ],
                "avg_resolution_time": "1.5小时",
                "auto_resolve_rate": 0.65,
            }, ensure_ascii=False)

        else:
            return json.dumps({
                "message": f"查询: {query}",
                "result": "暂无匹配数据，请尝试更具体的查询条件",
                "available_tables": list(self.SCHEMA_INFO.keys()),
            }, ensure_ascii=False)
