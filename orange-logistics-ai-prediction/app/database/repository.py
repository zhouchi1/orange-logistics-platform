"""数据访问层 - 门店销售与配送数据查询"""
import logging
from typing import Dict, List, Optional, Tuple
from datetime import date, datetime, timedelta
import numpy as np
from sqlalchemy import text, select, func
from sqlalchemy.ext.asyncio import AsyncSession

from app.database.connection import get_session
from app.database.models import (
    StoreSalesHourly, StoreInfo, DeliveryRecord,
    StoreArrivalAnalysis, ArrivalRevenueCorrelation,
)

logger = logging.getLogger(__name__)


class StoreRepository:
    """门店数据仓库"""

    async def get_store_info(self, store_id: str) -> Optional[Dict]:
        """获取门店基础信息"""
        async with get_session() as session:
            result = await session.execute(
                select(StoreInfo).where(StoreInfo.store_id == store_id)
            )
            row = result.scalar_one_or_none()
            if not row:
                return None
            return {
                "store_id": row.store_id,
                "store_name": row.store_name,
                "store_type": row.store_type,
                "open_hour": row.open_hour,
                "close_hour": row.close_hour,
                "daily_avg_revenue": row.daily_avg_revenue,
                "latitude": row.latitude,
                "longitude": row.longitude,
                "city": row.city,
            }

    async def get_all_stores(self) -> List[Dict]:
        """获取所有门店"""
        async with get_session() as session:
            result = await session.execute(select(StoreInfo))
            rows = result.scalars().all()
            return [
                {
                    "store_id": r.store_id,
                    "store_name": r.store_name,
                    "store_type": r.store_type,
                    "open_hour": r.open_hour,
                    "close_hour": r.close_hour,
                    "daily_avg_revenue": r.daily_avg_revenue,
                    "latitude": r.latitude,
                    "longitude": r.longitude,
                }
                for r in rows
            ]

    async def get_hourly_sales_curve(
        self, store_id: str, days: int = 30, day_of_week: Optional[int] = None
    ) -> np.ndarray:
        """获取门店平均每小时销售曲线

        Args:
            store_id: 门店ID
            days: 回溯天数
            day_of_week: 指定星期几 (0=Mon, 6=Sun), None=全部

        Returns:
            24维数组，每小时平均销售额
        """
        start_date = date.today() - timedelta(days=days)
        async with get_session() as session:
            query = (
                select(
                    StoreSalesHourly.hour,
                    func.avg(StoreSalesHourly.sales_amount).label("avg_sales"),
                )
                .where(
                    StoreSalesHourly.store_id == store_id,
                    StoreSalesHourly.sale_date >= start_date,
                )
                .group_by(StoreSalesHourly.hour)
                .order_by(StoreSalesHourly.hour)
            )

            if day_of_week is not None:
                query = query.where(
                    func.weekday(StoreSalesHourly.sale_date) == day_of_week
                )

            result = await session.execute(query)
            rows = result.all()

        curve = np.zeros(24)
        for row in rows:
            curve[row.hour] = float(row.avg_sales or 0)
        return curve

    async def get_peak_hours(self, store_id: str, days: int = 30) -> List[int]:
        """识别门店高峰时段（销售额超过平均值1.5倍的时段）"""
        curve = await self.get_hourly_sales_curve(store_id, days)
        if curve.sum() == 0:
            return [11, 12, 17, 18]  # 默认高峰

        avg = curve[curve > 0].mean() if curve[curve > 0].size > 0 else 0
        threshold = avg * 1.3
        peak_hours = [h for h in range(24) if curve[h] >= threshold]
        return peak_hours if peak_hours else [11, 12, 17, 18]

    async def get_inventory_decay_rate(self, store_id: str, days: int = 30) -> float:
        """计算库存消耗速率（基于每小时缺货SKU增长率）"""
        start_date = date.today() - timedelta(days=days)
        async with get_session() as session:
            result = await session.execute(
                select(
                    StoreSalesHourly.hour,
                    func.avg(StoreSalesHourly.stockout_sku_count).label("avg_stockout"),
                )
                .where(
                    StoreSalesHourly.store_id == store_id,
                    StoreSalesHourly.sale_date >= start_date,
                )
                .group_by(StoreSalesHourly.hour)
                .order_by(StoreSalesHourly.hour)
            )
            rows = result.all()

        if not rows:
            return 0.1  # 默认

        stockouts = [float(r.avg_stockout or 0) for r in rows]
        if len(stockouts) < 2:
            return 0.1

        # 计算缺货增长斜率作为消耗速率
        diffs = [stockouts[i+1] - stockouts[i] for i in range(len(stockouts)-1) if stockouts[i+1] > stockouts[i]]
        return np.mean(diffs) / 100.0 if diffs else 0.1


class DeliveryRepository:
    """配送记录数据仓库"""

    async def get_delivery_history(
        self, store_id: str, days: int = 90
    ) -> List[Dict]:
        """获取门店历史配送记录"""
        start_date = date.today() - timedelta(days=days)
        async with get_session() as session:
            result = await session.execute(
                select(DeliveryRecord)
                .where(
                    DeliveryRecord.store_id == store_id,
                    DeliveryRecord.delivery_date >= start_date,
                    DeliveryRecord.actual_arrival_time.isnot(None),
                )
                .order_by(DeliveryRecord.delivery_date.desc())
            )
            rows = result.scalars().all()

        return [
            {
                "waybill_no": r.waybill_no,
                "driver_id": r.driver_id,
                "delivery_date": r.delivery_date.isoformat(),
                "arrival_hour": r.arrival_hour,
                "actual_arrival_time": r.actual_arrival_time.isoformat() if r.actual_arrival_time else None,
                "weather_condition": r.weather_condition,
                "traffic_level": r.traffic_level,
            }
            for r in rows
        ]

    async def get_arrival_hour_distribution(
        self, store_id: str, days: int = 90
    ) -> Dict[int, int]:
        """获取门店到货时间分布"""
        start_date = date.today() - timedelta(days=days)
        async with get_session() as session:
            result = await session.execute(
                select(
                    DeliveryRecord.arrival_hour,
                    func.count().label("cnt"),
                )
                .where(
                    DeliveryRecord.store_id == store_id,
                    DeliveryRecord.delivery_date >= start_date,
                    DeliveryRecord.arrival_hour.isnot(None),
                )
                .group_by(DeliveryRecord.arrival_hour)
            )
            rows = result.all()

        return {row.arrival_hour: row.cnt for row in rows}

    async def save_delivery_record(self, record: Dict) -> None:
        """保存配送记录"""
        async with get_session() as session:
            obj = DeliveryRecord(**record)
            session.add(obj)


class CorrelationRepository:
    """到货-收益关联数据仓库"""

    async def get_correlation_data(
        self, store_id: str, days: int = 90
    ) -> List[Dict]:
        """获取到货时间与收益的关联数据"""
        start_date = date.today() - timedelta(days=days)
        async with get_session() as session:
            result = await session.execute(
                select(ArrivalRevenueCorrelation)
                .where(
                    ArrivalRevenueCorrelation.store_id == store_id,
                    ArrivalRevenueCorrelation.delivery_date >= start_date,
                )
                .order_by(ArrivalRevenueCorrelation.delivery_date)
            )
            rows = result.scalars().all()

        return [
            {
                "delivery_date": r.delivery_date.isoformat(),
                "arrival_hour": r.arrival_hour,
                "pre_arrival_inventory_ratio": r.pre_arrival_inventory_ratio,
                "post_arrival_sales_amount": r.post_arrival_sales_amount,
                "total_day_sales": r.total_day_sales,
                "revenue_vs_avg": r.revenue_vs_avg,
                "is_peak_before_arrival": r.is_peak_before_arrival,
                "weather_condition": r.weather_condition,
                "day_of_week": r.day_of_week,
            }
            for r in rows
        ]

    async def compute_revenue_by_arrival_hour(
        self, store_id: str, days: int = 90
    ) -> Dict[int, float]:
        """按到达小时统计平均收益差异"""
        start_date = date.today() - timedelta(days=days)
        async with get_session() as session:
            result = await session.execute(
                select(
                    ArrivalRevenueCorrelation.arrival_hour,
                    func.avg(ArrivalRevenueCorrelation.revenue_vs_avg).label("avg_revenue_diff"),
                    func.count().label("sample_count"),
                )
                .where(
                    ArrivalRevenueCorrelation.store_id == store_id,
                    ArrivalRevenueCorrelation.delivery_date >= start_date,
                )
                .group_by(ArrivalRevenueCorrelation.arrival_hour)
                .order_by(ArrivalRevenueCorrelation.arrival_hour)
            )
            rows = result.all()

        return {
            row.arrival_hour: {
                "avg_revenue_diff_pct": float(row.avg_revenue_diff or 0),
                "sample_count": row.sample_count,
            }
            for row in rows
        }

    async def save_correlation(self, data: Dict) -> None:
        """保存关联数据"""
        async with get_session() as session:
            obj = ArrivalRevenueCorrelation(**data)
            session.add(obj)

    async def batch_save_correlations(self, records: List[Dict]) -> int:
        """批量保存关联数据"""
        async with get_session() as session:
            objects = [ArrivalRevenueCorrelation(**r) for r in records]
            session.add_all(objects)
        return len(records)


class AnalysisRepository:
    """分析结果数据仓库"""

    async def save_analysis(self, data: Dict) -> None:
        """保存分析结果"""
        async with get_session() as session:
            obj = StoreArrivalAnalysis(**data)
            session.add(obj)

    async def batch_save_analyses(self, records: List[Dict]) -> int:
        """批量保存分析结果"""
        async with get_session() as session:
            objects = [StoreArrivalAnalysis(**r) for r in records]
            session.add_all(objects)
        return len(records)

    async def get_latest_analysis(self, store_id: str) -> Optional[Dict]:
        """获取门店最新分析结果"""
        async with get_session() as session:
            result = await session.execute(
                select(StoreArrivalAnalysis)
                .where(StoreArrivalAnalysis.store_id == store_id)
                .order_by(StoreArrivalAnalysis.analysis_date.desc())
                .limit(1)
            )
            row = result.scalar_one_or_none()

        if not row:
            return None
        return {
            "store_id": row.store_id,
            "analysis_date": row.analysis_date.isoformat(),
            "day_of_week": row.day_of_week,
            "optimal_arrival_hour": row.optimal_arrival_hour,
            "optimal_window_start": row.optimal_window_start,
            "optimal_window_end": row.optimal_window_end,
            "expected_revenue_gain": row.expected_revenue_gain,
            "penalty_per_hour_late": row.penalty_per_hour_late,
            "confidence_score": row.confidence_score,
            "model_version": row.model_version,
        }

    async def get_batch_latest_analyses(self, store_ids: List[str]) -> List[Dict]:
        """批量获取门店最新分析结果"""
        results = []
        for store_id in store_ids:
            analysis = await self.get_latest_analysis(store_id)
            if analysis:
                results.append(analysis)
        return results
