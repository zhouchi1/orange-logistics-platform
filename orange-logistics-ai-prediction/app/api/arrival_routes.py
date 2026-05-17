"""到货时间收益分析 API 路由"""
from fastapi import APIRouter, Request, HTTPException, Query
from pydantic import BaseModel, Field
from typing import Dict, List, Optional
from datetime import date

arrival_router = APIRouter(tags=["arrival-analysis"])


# ===== Request/Response Models =====

class SingleStoreAnalysisRequest(BaseModel):
    """单门店分析请求"""
    store_id: str = Field(..., description="门店ID")
    target_date: Optional[str] = Field(None, description="目标日期 YYYY-MM-DD")
    inventory_ratio: float = Field(0.5, ge=0, le=1, description="当前库存比率")

    model_config = {"json_schema_extra": {
        "example": {
            "store_id": "STORE_001",
            "target_date": "2026-05-18",
            "inventory_ratio": 0.3,
        }
    }}


class BatchAnalysisRequest(BaseModel):
    """批量分析请求"""
    store_ids: Optional[List[str]] = Field(None, description="门店ID列表，空则分析全部")
    target_date: Optional[str] = Field(None, description="目标日期")
    inventory_data: Optional[Dict[str, float]] = Field(None, description="库存数据 {store_id: ratio}")

    model_config = {"json_schema_extra": {
        "example": {
            "store_ids": ["STORE_001", "STORE_002", "STORE_003"],
            "target_date": "2026-05-18",
            "inventory_data": {"STORE_001": 0.3, "STORE_002": 0.7, "STORE_003": 0.1},
        }
    }}


class DriverScheduleRequest(BaseModel):
    """司机排班建议请求"""
    driver_id: str = Field(..., description="司机ID")
    store_ids: List[str] = Field(..., description="需要配送的门店列表")
    target_date: Optional[str] = Field(None, description="配送日期")
    departure_time: Optional[str] = Field(None, description="最早出发时间 HH:MM")
    inventory_data: Optional[Dict[str, float]] = Field(None, description="各门店库存")

    model_config = {"json_schema_extra": {
        "example": {
            "driver_id": "D101",
            "store_ids": ["STORE_001", "STORE_002", "STORE_003"],
            "target_date": "2026-05-18",
            "departure_time": "06:00",
            "inventory_data": {"STORE_001": 0.2, "STORE_002": 0.6, "STORE_003": 0.1},
        }
    }}


class DailyAnalysisRequest(BaseModel):
    """每日全量分析请求（通常由XXL-JOB触发）"""
    target_date: Optional[str] = Field(None, description="分析日期")


class CorrelationBuildRequest(BaseModel):
    """构建关联数据请求"""
    days: int = Field(7, ge=1, le=90, description="回溯天数")


# ===== API Endpoints =====

@arrival_router.post("/analyze/single")
async def analyze_single_store(request: Request, body: SingleStoreAnalysisRequest):
    """分析单个门店的最优到货时间

    返回该门店的最优到货时间窗口、预期收益增量、迟到惩罚等。
    """
    service = request.app.state.arrival_service
    target = date.fromisoformat(body.target_date) if body.target_date else None

    result = await service.analyze_single_store(
        store_id=body.store_id,
        target_date=target,
        inventory_ratio=body.inventory_ratio,
    )

    return {
        "store_id": result.store_id,
        "store_name": result.store_name,
        "optimal_arrival": {
            "hour": int(result.optimal_hour),
            "window_start": f"{result.window_start:02d}:00",
            "window_end": f"{result.window_end:02d}:00",
        },
        "revenue": {
            "expected_gain": round(float(result.revenue_gain), 2),
            "penalty_per_hour_late": round(float(result.penalty_per_hour), 2),
        },
        "confidence": round(float(result.confidence), 3),
        "factors": result.factors,
        "recommendation": result.recommendation,
    }


@arrival_router.post("/analyze/batch")
async def analyze_batch(request: Request, body: BatchAnalysisRequest):
    """批量分析多个门店的最优到货时间"""
    service = request.app.state.arrival_service
    target = date.fromisoformat(body.target_date) if body.target_date else None

    results = await service.analyze_batch(
        store_ids=body.store_ids,
        target_date=target,
        inventory_data=body.inventory_data,
    )

    return {
        "total": len(results),
        "analysis_date": (target or date.today()).isoformat(),
        "results": [
            {
                "store_id": r.store_id,
                "store_name": r.store_name,
                "optimal_window": f"{r.window_start:02d}:00-{r.window_end:02d}:00",
                "revenue_gain": round(float(r.revenue_gain), 2),
                "penalty_per_hour": round(float(r.penalty_per_hour), 2),
                "confidence": round(float(r.confidence), 3),
                "recommendation": r.recommendation,
            }
            for r in results
        ],
    }


@arrival_router.post("/schedule/driver")
async def generate_driver_schedule(request: Request, body: DriverScheduleRequest):
    """生成司机配送排班建议

    综合各门店最优到货时间，生成按时间排序的配送顺序建议。
    """
    service = request.app.state.arrival_service
    target = date.fromisoformat(body.target_date) if body.target_date else None
    inventory_data = body.inventory_data or {}

    # 分析所有门店
    results = await service.analyze_batch(
        store_ids=body.store_ids,
        target_date=target,
        inventory_data=inventory_data,
    )

    # 按最优到达时间排序，生成配送顺序
    sorted_results = sorted(results, key=lambda r: (
        -r.factors.get("inventory_ratio", 1) < 0.3,  # 紧急的优先
        r.optimal_hour,  # 按最优时间排序
    ))

    schedule = []
    for idx, r in enumerate(sorted_results):
        urgency = "urgent" if inventory_data.get(r.store_id, 0.5) < 0.3 else "normal"
        schedule.append({
            "sequence": idx + 1,
            "store_id": r.store_id,
            "store_name": r.store_name,
            "target_arrival": f"{r.window_start:02d}:00-{r.window_end:02d}:00",
            "urgency": urgency,
            "revenue_gain": round(r.revenue_gain, 2),
            "penalty_if_late": round(r.penalty_per_hour, 2),
        })

    total_potential_gain = sum(r.revenue_gain for r in results)

    return {
        "driver_id": body.driver_id,
        "delivery_date": (target or date.today()).isoformat(),
        "total_stores": len(schedule),
        "total_potential_revenue_gain": round(total_potential_gain, 2),
        "schedule": schedule,
        "tips": _generate_driver_tips(sorted_results),
    }


@arrival_router.post("/job/daily-analysis")
async def trigger_daily_analysis(request: Request, body: DailyAnalysisRequest):
    """触发每日全量分析（XXL-JOB调用）"""
    service = request.app.state.arrival_service
    target = date.fromisoformat(body.target_date) if body.target_date else None

    summary = await service.run_daily_analysis(target_date=target)
    return {"status": "completed", "summary": summary}


@arrival_router.post("/job/build-correlation")
async def trigger_build_correlation(request: Request, body: CorrelationBuildRequest):
    """触发构建关联数据（XXL-JOB调用）"""
    service = request.app.state.arrival_service
    result = await service.build_correlation_data(days=body.days)
    return {"status": "completed", "result": result}


@arrival_router.get("/history/{store_id}")
async def get_store_arrival_history(
    request: Request,
    store_id: str,
    days: int = Query(90, ge=1, le=365),
):
    """获取门店历史到货时间分布"""
    from app.database.repository import DeliveryRepository, AnalysisRepository

    delivery_repo = DeliveryRepository()
    analysis_repo = AnalysisRepository()

    distribution = await delivery_repo.get_arrival_hour_distribution(store_id, days)
    latest_analysis = await analysis_repo.get_latest_analysis(store_id)

    return {
        "store_id": store_id,
        "arrival_distribution": distribution,
        "latest_analysis": latest_analysis,
        "data_period_days": days,
    }


@arrival_router.get("/revenue-curve/{store_id}")
async def get_store_revenue_curve(
    request: Request,
    store_id: str,
    day_of_week: Optional[int] = Query(None, ge=0, le=6),
):
    """获取门店销售曲线（按小时）"""
    from app.database.repository import StoreRepository, CorrelationRepository

    store_repo = StoreRepository()
    corr_repo = CorrelationRepository()

    curve = await store_repo.get_hourly_sales_curve(store_id, days=60, day_of_week=day_of_week)
    peak_hours = await store_repo.get_peak_hours(store_id)
    revenue_by_hour = await corr_repo.compute_revenue_by_arrival_hour(store_id)

    return {
        "store_id": store_id,
        "day_of_week": day_of_week,
        "hourly_sales": [
            {"hour": h, "avg_sales": round(float(curve[h]), 2)}
            for h in range(24)
        ],
        "peak_hours": peak_hours,
        "arrival_revenue_impact": revenue_by_hour,
    }


def _generate_driver_tips(results) -> List[str]:
    """生成司机配送提示"""
    tips = []
    urgent_stores = [r for r in results if r.factors.get("inventory_ratio", 1) < 0.3]
    if urgent_stores:
        names = ", ".join(r.store_name or r.store_id for r in urgent_stores[:3])
        tips.append(f"紧急补货门店: {names}，请优先配送")

    early_stores = [r for r in results if r.optimal_hour <= 8]
    if early_stores:
        tips.append(f"有{len(early_stores)}家门店建议8点前到货，建议提前出发")

    total_penalty = sum(r.penalty_per_hour for r in results)
    if total_penalty > 1000:
        tips.append(f"整体迟到成本较高(¥{total_penalty:.0f}/h)，请尽量按时到达")

    if not tips:
        tips.append("今日配送压力适中，按建议顺序配送即可")

    return tips
