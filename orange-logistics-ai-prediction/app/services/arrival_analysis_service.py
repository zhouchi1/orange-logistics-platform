"""到货时间收益分析服务

核心业务逻辑：
1. 从数据库获取门店销售曲线和历史配送数据
2. 建立到货时间与收益的因果关系模型
3. 计算每个门店的最优到货时间窗口
4. 生成调度建议并写回数据库
"""
import logging
import json
from typing import Dict, List, Optional, Tuple
from datetime import date, datetime, time, timedelta
import numpy as np
from dataclasses import dataclass

from app.database.repository import (
    StoreRepository, DeliveryRepository,
    CorrelationRepository, AnalysisRepository,
)
from app.models.store_revenue import StoreRevenueModel, StoreProfile, RevenueWindow

logger = logging.getLogger(__name__)


@dataclass
class ArrivalAnalysisResult:
    """单个门店的到货分析结果"""
    store_id: str
    store_name: str
    optimal_hour: int
    window_start: int
    window_end: int
    revenue_gain: float
    penalty_per_hour: float
    confidence: float
    factors: Dict
    recommendation: str


class ArrivalAnalysisService:
    """到货时间收益分析服务"""

    def __init__(self):
        self.store_repo = StoreRepository()
        self.delivery_repo = DeliveryRepository()
        self.correlation_repo = CorrelationRepository()
        self.analysis_repo = AnalysisRepository()
        self.revenue_model = StoreRevenueModel()

    async def analyze_single_store(
        self,
        store_id: str,
        target_date: Optional[date] = None,
        inventory_ratio: float = 0.5,
    ) -> ArrivalAnalysisResult:
        """分析单个门店的最优到货时间

        综合考虑：
        - 历史销售曲线（从DB）
        - 历史到货时间与收益关联（从DB）
        - 当前库存水位
        - 星期几效应
        - 门店类型特征
        """
        target_date = target_date or date.today()
        day_of_week = target_date.weekday()

        # 1. 获取门店信息
        store_info = await self.store_repo.get_store_info(store_id)
        if not store_info:
            logger.warning(f"Store {store_id} not found, using defaults")
            store_info = {
                "store_id": store_id,
                "store_name": "Unknown",
                "store_type": "convenience",
                "open_hour": 8,
                "close_hour": 22,
                "daily_avg_revenue": 50000.0,
            }

        # 2. 获取该门店按星期几的销售曲线
        sales_curve = await self.store_repo.get_hourly_sales_curve(
            store_id, days=60, day_of_week=day_of_week
        )
        has_real_data = sales_curve.sum() > 0

        # 3. 获取高峰时段
        peak_hours = await self.store_repo.get_peak_hours(store_id, days=60)

        # 4. 获取库存消耗速率
        decay_rate = await self.store_repo.get_inventory_decay_rate(store_id, days=30)

        # 5. 获取历史到货-收益关联数据
        revenue_by_hour = await self.correlation_repo.compute_revenue_by_arrival_hour(
            store_id, days=90
        )

        # 6. 构建门店画像
        avg_hourly_sales = float(sales_curve.mean()) if has_real_data else store_info.get("daily_avg_revenue", 50000) / 14
        close_hour = store_info.get("close_hour", 22)
        close_time_obj = time(23, 59) if close_hour >= 24 else time(close_hour, 0)
        profile = StoreProfile(
            store_id=store_id,
            store_name=store_info.get("store_name", ""),
            open_time=time(store_info.get("open_hour", 8), 0),
            close_time=close_time_obj,
            peak_hours=peak_hours,
            avg_hourly_sales=avg_hourly_sales,
            inventory_decay_rate=decay_rate,
            restock_urgency=min(1.0, (1.0 - inventory_ratio) * 1.5),
            store_type=store_info.get("store_type", "convenience"),
            daily_revenue=store_info.get("daily_avg_revenue", 50000.0),
        )

        # 7. 用模型计算最优窗口
        window = self.revenue_model.find_optimal_window(
            profile,
            current_inventory_ratio=inventory_ratio,
            earliest_possible_hour=max(5, store_info.get("open_hour", 8) - 1),
            latest_possible_hour=min(22, store_info.get("close_hour", 22) - 1),
        )

        # 8. 如果有历史关联数据，用数据修正模型结果
        if revenue_by_hour:
            window = self._adjust_with_historical_data(window, revenue_by_hour, profile)

        # 9. 计算置信度
        confidence = self._compute_confidence(has_real_data, revenue_by_hour, sales_curve)

        # 10. 生成影响因素
        factors = {
            "data_driven": bool(has_real_data),
            "sales_curve_source": "historical_db" if has_real_data else "template",
            "peak_hours": [int(h) for h in peak_hours],
            "inventory_ratio": float(inventory_ratio),
            "decay_rate": float(decay_rate),
            "day_of_week": int(day_of_week),
            "historical_samples": int(sum(
                v.get("sample_count", 0) for v in revenue_by_hour.values()
            )) if revenue_by_hour else 0,
        }

        # 11. 生成建议
        recommendation = self._generate_recommendation(window, profile, confidence)

        return ArrivalAnalysisResult(
            store_id=store_id,
            store_name=store_info.get("store_name", ""),
            optimal_hour=window.optimal_arrival_start,
            window_start=window.optimal_arrival_start,
            window_end=window.optimal_arrival_end,
            revenue_gain=window.expected_revenue_gain,
            penalty_per_hour=window.penalty_per_hour_late,
            confidence=confidence,
            factors=factors,
            recommendation=recommendation,
        )

    def _adjust_with_historical_data(
        self,
        window: RevenueWindow,
        revenue_by_hour: Dict[int, Dict],
        profile: StoreProfile,
    ) -> RevenueWindow:
        """用历史数据修正模型预测

        如果历史数据显示某个时间段收益明显更高，调整最优窗口。
        """
        # 找到历史数据中收益最高的时段
        best_hour = None
        best_diff = -float("inf")
        total_samples = 0

        for hour, data in revenue_by_hour.items():
            total_samples += data.get("sample_count", 0)
            if data.get("sample_count", 0) >= 5:  # 至少5个样本才可信
                if data["avg_revenue_diff_pct"] > best_diff:
                    best_diff = data["avg_revenue_diff_pct"]
                    best_hour = hour

        if best_hour is None or total_samples < 20:
            return window  # 数据不足，不修正

        # 加权融合：模型预测 70% + 历史数据 30%（数据越多，历史权重越高）
        data_weight = min(0.6, total_samples / 200.0)
        model_weight = 1.0 - data_weight

        adjusted_start = int(
            model_weight * window.optimal_arrival_start + data_weight * best_hour
        )
        adjusted_end = max(adjusted_start + 1, int(
            model_weight * window.optimal_arrival_end + data_weight * (best_hour + 2)
        ))

        return RevenueWindow(
            optimal_arrival_start=adjusted_start,
            optimal_arrival_end=adjusted_end,
            expected_revenue_gain=window.expected_revenue_gain * (1 + best_diff / 100),
            urgency_score=window.urgency_score,
            penalty_per_hour_late=window.penalty_per_hour_late,
        )

    def _compute_confidence(
        self,
        has_real_data: bool,
        revenue_by_hour: Dict,
        sales_curve: np.ndarray,
    ) -> float:
        """计算分析结果的置信度"""
        score = 0.3  # 基础分（模型模板）

        if has_real_data:
            score += 0.3  # 有真实销售数据

        if revenue_by_hour:
            total_samples = sum(v.get("sample_count", 0) for v in revenue_by_hour.values())
            if total_samples >= 50:
                score += 0.3
            elif total_samples >= 20:
                score += 0.2
            elif total_samples >= 5:
                score += 0.1

        # 销售曲线方差越大，说明时间效应越明显，置信度越高
        if sales_curve.sum() > 0:
            cv = sales_curve.std() / (sales_curve.mean() + 1e-8)
            if cv > 0.5:
                score += 0.1

        return min(1.0, score)

    def _generate_recommendation(
        self, window: RevenueWindow, profile: StoreProfile, confidence: float
    ) -> str:
        """生成自然语言建议"""
        if confidence >= 0.7:
            level = "高置信度"
        elif confidence >= 0.4:
            level = "中置信度"
        else:
            level = "低置信度(建议积累更多数据)"

        urgency = ""
        if window.urgency_score > 0.8:
            urgency = "[紧急补货] "
        elif window.urgency_score > 0.5:
            urgency = "[优先配送] "

        return (
            f"{urgency}建议 {window.optimal_arrival_start:02d}:00-"
            f"{window.optimal_arrival_end:02d}:00 到货 | "
            f"预期收益+¥{window.expected_revenue_gain:.0f} | "
            f"迟到损失¥{window.penalty_per_hour_late:.0f}/h | "
            f"{level}"
        )

    async def analyze_batch(
        self,
        store_ids: Optional[List[str]] = None,
        target_date: Optional[date] = None,
        inventory_data: Optional[Dict[str, float]] = None,
    ) -> List[ArrivalAnalysisResult]:
        """批量分析多个门店"""
        if store_ids is None:
            stores = await self.store_repo.get_all_stores()
            store_ids = [s["store_id"] for s in stores]

        inventory_data = inventory_data or {}
        results = []

        for store_id in store_ids:
            try:
                inv_ratio = inventory_data.get(store_id, 0.5)
                result = await self.analyze_single_store(store_id, target_date, inv_ratio)
                results.append(result)
            except Exception as e:
                logger.error(f"Failed to analyze store {store_id}: {e}")

        return results

    async def run_daily_analysis(self, target_date: Optional[date] = None) -> Dict:
        """每日全量分析（由XXL-JOB调度）

        1. 获取所有门店
        2. 逐个分析最优到货时间
        3. 结果写入 store_arrival_analysis 表
        4. 返回统计摘要
        """
        target_date = target_date or date.today()
        day_of_week = target_date.weekday()

        stores = await self.store_repo.get_all_stores()
        logger.info(f"Starting daily analysis for {len(stores)} stores, date={target_date}")

        results = await self.analyze_batch(
            store_ids=[s["store_id"] for s in stores],
            target_date=target_date,
        )

        # 写入数据库
        analysis_records = []
        for r in results:
            analysis_records.append({
                "store_id": r.store_id,
                "analysis_date": target_date,
                "day_of_week": day_of_week,
                "optimal_arrival_hour": r.optimal_hour,
                "optimal_window_start": r.window_start,
                "optimal_window_end": r.window_end,
                "expected_revenue_gain": r.revenue_gain,
                "penalty_per_hour_late": r.penalty_per_hour,
                "confidence_score": r.confidence,
                "model_version": "v1.0",
                "factors": json.dumps(r.factors, ensure_ascii=False),
            })

        if analysis_records:
            saved = await self.analysis_repo.batch_save_analyses(analysis_records)
            logger.info(f"Saved {saved} analysis records")

        return {
            "date": target_date.isoformat(),
            "total_stores": len(stores),
            "analyzed": len(results),
            "avg_confidence": np.mean([r.confidence for r in results]) if results else 0,
            "high_urgency_count": sum(1 for r in results if r.factors.get("inventory_ratio", 1) < 0.3),
        }

    async def build_correlation_data(self, days: int = 7) -> Dict:
        """构建到货-收益关联数据（由XXL-JOB调度）

        将配送记录和销售数据关联，计算每次到货对当天收益的影响。
        """
        stores = await self.store_repo.get_all_stores()
        total_records = 0

        for store in stores:
            store_id = store["store_id"]
            try:
                records = await self._build_store_correlation(store_id, days)
                if records:
                    await self.correlation_repo.batch_save_correlations(records)
                    total_records += len(records)
            except Exception as e:
                logger.error(f"Failed to build correlation for {store_id}: {e}")

        logger.info(f"Built {total_records} correlation records for {len(stores)} stores")
        return {"total_records": total_records, "stores_processed": len(stores)}

    async def _build_store_correlation(self, store_id: str, days: int) -> List[Dict]:
        """为单个门店构建关联数据"""
        # 获取历史配送记录
        deliveries = await self.delivery_repo.get_delivery_history(store_id, days)
        if not deliveries:
            return []

        # 获取该门店的平均日销售额
        sales_curve = await self.store_repo.get_hourly_sales_curve(store_id, days=60)
        avg_daily_sales = sales_curve.sum() if sales_curve.sum() > 0 else 50000.0

        records = []
        for delivery in deliveries:
            arrival_hour = delivery.get("arrival_hour")
            if arrival_hour is None:
                continue

            delivery_date = date.fromisoformat(delivery["delivery_date"])

            # 获取到货后的销售额
            post_arrival_sales = await self._get_post_arrival_sales(
                store_id, delivery_date, arrival_hour
            )
            total_day_sales = await self._get_total_day_sales(store_id, delivery_date)

            if total_day_sales <= 0:
                continue

            # 计算相对平均值的收益差异
            revenue_vs_avg = ((total_day_sales - avg_daily_sales) / avg_daily_sales) * 100

            # 判断到达前是否已过高峰
            peak_hours = await self.store_repo.get_peak_hours(store_id)
            is_peak_before = 1 if any(h < arrival_hour for h in peak_hours) else 0

            records.append({
                "store_id": store_id,
                "delivery_date": delivery_date,
                "arrival_hour": arrival_hour,
                "pre_arrival_inventory_ratio": 0.5,  # 需要库存系统接入
                "post_arrival_sales_amount": post_arrival_sales,
                "total_day_sales": total_day_sales,
                "revenue_vs_avg": revenue_vs_avg,
                "is_peak_before_arrival": is_peak_before,
                "weather_condition": delivery.get("weather_condition", "clear"),
                "day_of_week": delivery_date.weekday(),
            })

        return records

    async def _get_post_arrival_sales(
        self, store_id: str, sale_date: date, arrival_hour: int
    ) -> float:
        """获取到货后当天剩余时段的销售额"""
        from app.database.connection import get_session
        from sqlalchemy import select, func
        from app.database.models import StoreSalesHourly

        async with get_session() as session:
            result = await session.execute(
                select(func.sum(StoreSalesHourly.sales_amount))
                .where(
                    StoreSalesHourly.store_id == store_id,
                    StoreSalesHourly.sale_date == sale_date,
                    StoreSalesHourly.hour >= arrival_hour,
                )
            )
            total = result.scalar()
        return float(total or 0)

    async def _get_total_day_sales(self, store_id: str, sale_date: date) -> float:
        """获取当天总销售额"""
        from app.database.connection import get_session
        from sqlalchemy import select, func
        from app.database.models import StoreSalesHourly

        async with get_session() as session:
            result = await session.execute(
                select(func.sum(StoreSalesHourly.sales_amount))
                .where(
                    StoreSalesHourly.store_id == store_id,
                    StoreSalesHourly.sale_date == sale_date,
                )
            )
            total = result.scalar()
        return float(total or 0)
