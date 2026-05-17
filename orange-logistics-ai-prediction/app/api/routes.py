"""API 路由"""
from fastapi import APIRouter, Request, HTTPException
from app.api.schemas import (
    DeliveryTimePredictionRequest,
    DeliveryTimePredictionResponse,
    StoreOptimalTimeRequest,
    StoreOptimalTimeResponse,
    RouteOptimizationRequest,
    RouteOptimizationResponse,
    ScheduleOptimizationRequest,
    ScheduleOptimizationResponse,
    TrainingTriggerRequest,
    TrainingTriggerResponse,
    ModelStatusResponse,
)
from app.services.prediction_service import PredictionService
from app.services.optimization_service import OptimizationService
from app.services.training_service import TrainingService

router = APIRouter()

# 服务实例（在应用启动时通过 lifespan 初始化）
optimization_service = OptimizationService()
training_service = TrainingService()


def get_prediction_service(request: Request) -> PredictionService:
    return request.app.state.prediction_service


@router.post("/predict/delivery-time", response_model=DeliveryTimePredictionResponse)
async def predict_delivery_time(
    request: Request, body: DeliveryTimePredictionRequest
):
    """预测配送时效"""
    service = get_prediction_service(request)
    result = await service.predict_delivery_time(
        store_id=body.store_id,
        store_features=body.store_features,
        origin=body.origin,
        weather=body.weather,
    )
    return result


@router.post("/predict/store-optimal-time", response_model=StoreOptimalTimeResponse)
async def predict_store_optimal_time(
    request: Request, body: StoreOptimalTimeRequest
):
    """预测门店最优到货时间"""
    service = get_prediction_service(request)
    result = await service.predict_store_optimal_time(
        stores=body.stores,
        inventory_data=body.inventory_data,
    )
    return result


@router.post("/optimize/route", response_model=RouteOptimizationResponse)
async def optimize_route(body: RouteOptimizationRequest):
    """路径优化"""
    result = await optimization_service.optimize_route(
        warehouse=body.warehouse,
        stores=body.stores,
        vehicles=body.vehicles,
        constraints=body.constraints,
    )
    return result


@router.post("/optimize/schedule", response_model=ScheduleOptimizationResponse)
async def optimize_schedule(body: ScheduleOptimizationRequest):
    """配送计划优化（收益最大化）"""
    result = await optimization_service.optimize_schedule(
        tasks=body.tasks,
        vehicles=body.vehicles,
        config=body.config,
    )
    return result


@router.post("/train/trigger", response_model=TrainingTriggerResponse)
async def trigger_training(body: TrainingTriggerRequest):
    """触发模型训练"""
    result = await training_service.trigger_training(
        model_type=body.model_type,
        params=body.params,
    )
    return result


@router.get("/model/status", response_model=ModelStatusResponse)
async def get_model_status(request: Request):
    """获取模型状态"""
    service = get_prediction_service(request)
    prediction_status = service.get_model_status()
    training_status = training_service.get_training_status()
    return {
        "prediction": prediction_status,
        "training": training_status,
    }
