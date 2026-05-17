"""Pydantic 数据模型"""
from pydantic import BaseModel, Field
from typing import Dict, List, Optional, Any


# ===== 配送时效预测 =====

class DeliveryTimePredictionRequest(BaseModel):
    """配送时效预测请求"""
    store_id: str = Field(..., description="门店ID")
    store_features: Dict[str, Any] = Field(..., description="门店特征")
    origin: Optional[Dict[str, float]] = Field(None, description="出发点坐标 {lat, lon}")
    weather: Optional[Dict[str, Any]] = Field(None, description="天气数据")

    model_config = {"json_schema_extra": {
        "example": {
            "store_id": "STORE_001",
            "store_features": {
                "latitude": 39.9042,
                "longitude": 116.4074,
                "store_type": 0,
                "avg_daily_orders": 50.0,
                "distance_from_warehouse": 15.0,
                "floor_level": 1,
                "has_elevator": 1,
                "parking_difficulty": 0.3,
            },
            "origin": {"lat": 39.85, "lon": 116.35},
            "weather": {"condition": "clear", "temperature": 25.0},
        }
    }}


class ConfidenceInterval(BaseModel):
    lower: float
    upper: float


class HourlyForecast(BaseModel):
    hour_offset: int
    predicted_hours: float
    lower: float
    upper: float


class PredictionFactors(BaseModel):
    weather_impact: float = 0.0
    traffic_impact: float = 0.0
    store_accessibility: float = 0.5


class DeliveryTimePredictionResponse(BaseModel):
    """配送时效预测响应"""
    store_id: Optional[str] = None
    predicted_hours: float = Field(..., description="预测配送时效（小时）")
    confidence_interval: ConfidenceInterval
    hourly_forecast: List[HourlyForecast] = []
    factors: Optional[PredictionFactors] = None
    prediction_time: Optional[str] = None
    model_status: Optional[str] = None
    traffic_info: Optional[Dict[str, Any]] = None


# ===== 门店最优到货时间 =====

class StoreOptimalTimeRequest(BaseModel):
    """门店最优到货时间请求"""
    stores: List[Dict[str, Any]] = Field(..., description="门店列表")
    inventory_data: Optional[Dict[str, float]] = Field(None, description="库存数据 {store_id: ratio}")

    model_config = {"json_schema_extra": {
        "example": {
            "stores": [
                {
                    "store_id": "STORE_001",
                    "store_name": "朝阳便利店",
                    "store_type": "convenience",
                    "open_hour": 7,
                    "close_hour": 23,
                    "peak_hours": [8, 12, 18],
                    "avg_hourly_sales": 3000.0,
                    "inventory_decay_rate": 0.12,
                    "restock_urgency": 0.7,
                }
            ],
            "inventory_data": {"STORE_001": 0.3},
        }
    }}


class StoreOptimalTimeResponse(BaseModel):
    """门店最优到货时间响应"""
    predictions: List[Dict[str, Any]]
    prediction_time: Optional[str] = None
    total_stores: int = 0
    model_status: Optional[str] = None


# ===== 路径优化 =====

class RouteOptimizationRequest(BaseModel):
    """路径优化请求"""
    warehouse: Dict[str, Any] = Field(..., description="仓库信息")
    stores: List[Dict[str, Any]] = Field(..., description="门店列表")
    vehicles: List[Dict[str, Any]] = Field(..., description="车辆列表")
    constraints: Optional[Dict[str, Any]] = Field(None, description="约束条件")

    model_config = {"json_schema_extra": {
        "example": {
            "warehouse": {"lat": 39.85, "lon": 116.35, "name": "北京仓"},
            "stores": [
                {
                    "store_id": "S001",
                    "store_name": "门店A",
                    "latitude": 39.92,
                    "longitude": 116.46,
                    "demand": 20,
                    "time_window_start_min": 60,
                    "time_window_end_min": 300,
                    "service_time_min": 15,
                }
            ],
            "vehicles": [
                {
                    "vehicle_id": "V001",
                    "capacity": 100,
                    "max_route_time_min": 600,
                    "cost_per_km": 2.0,
                    "fixed_cost": 100.0,
                }
            ],
        }
    }}


class RouteSummary(BaseModel):
    total_distance_km: float
    total_cost: float
    total_time_min: int
    vehicles_used: int
    stores_served: int
    stores_unserved: int


class RouteOptimizationResponse(BaseModel):
    """路径优化响应"""
    routes: List[Dict[str, Any]]
    summary: RouteSummary
    unserved_stores: List[str] = []
    optimization_time: Optional[str] = None


# ===== 配送计划优化 =====

class ScheduleOptimizationRequest(BaseModel):
    """配送计划优化请求"""
    tasks: List[Dict[str, Any]] = Field(..., description="配送任务列表")
    vehicles: List[Dict[str, Any]] = Field(..., description="车辆列表")
    config: Optional[Dict[str, Any]] = Field(None, description="GA 配置")


class ScheduleOptimizationResponse(BaseModel):
    """配送计划优化响应"""
    routes: Dict[str, List[Dict[str, Any]]]
    total_cost: float
    total_revenue: float
    time_penalty: float
    fitness: float
    generations_run: int
    convergence_history: List[Dict[str, Any]] = []
    optimization_time: Optional[str] = None
    config: Optional[Dict[str, Any]] = None


# ===== 训练 =====

class TrainingTriggerRequest(BaseModel):
    """训练触发请求"""
    model_type: str = Field(..., description="模型类型: tft/store_revenue")
    params: Optional[Dict[str, Any]] = Field(None, description="训练参数")


class TrainingTriggerResponse(BaseModel):
    """训练触发响应"""
    status: str
    task_id: Optional[str] = None
    message: str
    current_job: Optional[str] = None


# ===== 模型状态 =====

class ModelStatusResponse(BaseModel):
    """模型状态响应"""
    prediction: Dict[str, Any]
    training: Dict[str, Any]
