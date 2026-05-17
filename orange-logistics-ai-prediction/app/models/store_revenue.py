"""门店到货收益最大化模型

建模门店销售曲线，计算最优到货时间窗口。
考虑因素：开门时间、高峰时段、库存消耗速率、补货紧急度。
"""
import numpy as np
from typing import Dict, List, Optional, Tuple
from datetime import datetime, time
from dataclasses import dataclass
import logging

logger = logging.getLogger(__name__)


@dataclass
class StoreProfile:
    """门店画像"""
    store_id: str
    store_name: str
    open_time: time  # 开门时间
    close_time: time  # 关门时间
    peak_hours: List[int]  # 高峰时段（小时）
    avg_hourly_sales: float  # 平均每小时销售额
    inventory_decay_rate: float  # 库存消耗速率（每小时）
    restock_urgency: float  # 补货紧急度 [0, 1]
    store_type: str  # 门店类型：convenience/supermarket/restaurant
    daily_revenue: float  # 日均营收


@dataclass
class RevenueWindow:
    """收益时间窗口"""
    optimal_arrival_start: int  # 最优到达开始时间（小时）
    optimal_arrival_end: int  # 最优到达结束时间（小时）
    expected_revenue_gain: float  # 预期收益增量
    urgency_score: float  # 紧急度评分
    penalty_per_hour_late: float  # 每迟到一小时的损失


class SalesCurveModel:
    """门店销售曲线模型

    基于历史数据建模门店每小时的销售强度。
    """

    # 不同门店类型的典型销售曲线模板
    CURVE_TEMPLATES = {
        "convenience": {
            # 便利店：早晚高峰
            "hourly_weights": [
                0.1, 0.05, 0.02, 0.02, 0.05, 0.15,  # 0-5
                0.4, 0.8, 1.0, 0.7, 0.5, 0.6,       # 6-11
                0.9, 0.7, 0.5, 0.4, 0.5, 0.7,       # 12-17
                0.9, 0.8, 0.6, 0.4, 0.3, 0.2,       # 18-23
            ]
        },
        "supermarket": {
            # 超市：午后和傍晚高峰
            "hourly_weights": [
                0.0, 0.0, 0.0, 0.0, 0.0, 0.0,       # 0-5
                0.0, 0.1, 0.3, 0.5, 0.7, 0.8,       # 6-11
                0.6, 0.5, 0.6, 0.7, 0.9, 1.0,       # 12-17
                0.9, 0.7, 0.5, 0.3, 0.0, 0.0,       # 18-23
            ]
        },
        "restaurant": {
            # 餐厅：午餐和晚餐高峰
            "hourly_weights": [
                0.0, 0.0, 0.0, 0.0, 0.0, 0.1,       # 0-5
                0.2, 0.3, 0.2, 0.1, 0.3, 0.8,       # 6-11
                1.0, 0.7, 0.3, 0.2, 0.3, 0.8,       # 12-17
                1.0, 0.8, 0.5, 0.2, 0.0, 0.0,       # 18-23
            ]
        },
    }

    def __init__(self):
        self.fitted_curves: Dict[str, np.ndarray] = {}

    def get_sales_curve(
        self, store_profile: StoreProfile, historical_sales: Optional[np.ndarray] = None
    ) -> np.ndarray:
        """获取门店销售曲线

        Args:
            store_profile: 门店画像
            historical_sales: 历史每小时销售数据 [24]

        Returns:
            24小时销售强度曲线 [24]
        """
        if historical_sales is not None and len(historical_sales) == 24:
            # 使用历史数据
            curve = historical_sales / (historical_sales.max() + 1e-8)
        else:
            # 使用模板
            template = self.CURVE_TEMPLATES.get(
                store_profile.store_type, self.CURVE_TEMPLATES["convenience"]
            )
            curve = np.array(template["hourly_weights"])

        # 根据开关门时间调整
        open_hour = store_profile.open_time.hour
        close_hour = store_profile.close_time.hour
        for h in range(24):
            if h < open_hour or h >= close_hour:
                curve[h] = 0.0

        self.fitted_curves[store_profile.store_id] = curve
        return curve


class StoreRevenueModel:
    """门店到货收益最大化模型

    核心思想：
    - 门店在不同时间到货，对销售的影响不同
    - 在高峰前到货 → 货架充足 → 收益最大化
    - 在高峰后到货 → 错过销售窗口 → 收益损失
    - 库存越低、补货越紧急 → 早到的收益越高
    """

    def __init__(self):
        self.sales_model = SalesCurveModel()

    def compute_arrival_revenue(
        self,
        store_profile: StoreProfile,
        arrival_hour: int,
        current_inventory_ratio: float = 0.5,
        order_quantity: float = 100.0,
    ) -> float:
        """计算特定到达时间的收益

        Args:
            store_profile: 门店画像
            arrival_hour: 到达时间（小时，0-23）
            current_inventory_ratio: 当前库存比率 [0, 1]
            order_quantity: 订单数量

        Returns:
            预期收益值
        """
        sales_curve = self.sales_model.get_sales_curve(store_profile)

        # 计算到达后的累计销售潜力
        remaining_sales = 0.0
        close_hour = store_profile.close_time.hour or 24

        for h in range(arrival_hour, close_hour):
            # 到达后每小时的销售强度
            sales_intensity = sales_curve[h % 24]
            # 库存充足时的销售额
            remaining_sales += sales_intensity * store_profile.avg_hourly_sales

        # 库存缺货损失：如果不补货，从当前库存消耗到0的损失
        stockout_hour = arrival_hour + int(
            current_inventory_ratio / (store_profile.inventory_decay_rate + 1e-8)
        )
        stockout_loss = 0.0
        if stockout_hour < close_hour:
            for h in range(stockout_hour, close_hour):
                stockout_loss += sales_curve[h % 24] * store_profile.avg_hourly_sales

        # 补货紧急度加权
        urgency_multiplier = 1.0 + store_profile.restock_urgency * 2.0

        # 总收益 = 补货后可实现的销售 - 如果不补货的损失
        revenue = (remaining_sales * urgency_multiplier) - stockout_loss

        # 时间惩罚：越晚到达，收益越低
        peak_hours = store_profile.peak_hours
        if peak_hours:
            first_peak = min(peak_hours)
            if arrival_hour > first_peak:
                # 错过高峰的惩罚
                hours_late = arrival_hour - first_peak
                penalty = hours_late * store_profile.avg_hourly_sales * 0.3
                revenue -= penalty

        return max(revenue, 0.0)

    def find_optimal_window(
        self,
        store_profile: StoreProfile,
        current_inventory_ratio: float = 0.5,
        order_quantity: float = 100.0,
        earliest_possible_hour: int = 6,
        latest_possible_hour: int = 22,
    ) -> RevenueWindow:
        """找到门店最优到货时间窗口

        Args:
            store_profile: 门店画像
            current_inventory_ratio: 当前库存比率
            order_quantity: 订单数量
            earliest_possible_hour: 最早可能到达时间
            latest_possible_hour: 最晚可能到达时间

        Returns:
            最优到货时间窗口
        """
        revenues = []
        for hour in range(earliest_possible_hour, latest_possible_hour + 1):
            rev = self.compute_arrival_revenue(
                store_profile, hour, current_inventory_ratio, order_quantity
            )
            revenues.append((hour, rev))

        # 按收益排序
        revenues.sort(key=lambda x: x[1], reverse=True)

        if not revenues:
            return RevenueWindow(
                optimal_arrival_start=8,
                optimal_arrival_end=10,
                expected_revenue_gain=0.0,
                urgency_score=store_profile.restock_urgency,
                penalty_per_hour_late=0.0,
            )

        # 最优时间
        best_hour = revenues[0][0]
        best_revenue = revenues[0][1]

        # 找到收益在最优值 90% 以上的时间范围
        threshold = best_revenue * 0.9
        good_hours = [h for h, r in revenues if r >= threshold]
        window_start = min(good_hours)
        window_end = max(good_hours)

        # 计算迟到惩罚
        if len(revenues) >= 2:
            penalty = (revenues[0][1] - revenues[-1][1]) / max(
                revenues[-1][0] - revenues[0][0], 1
            )
        else:
            penalty = 0.0

        return RevenueWindow(
            optimal_arrival_start=window_start,
            optimal_arrival_end=window_end,
            expected_revenue_gain=best_revenue,
            urgency_score=store_profile.restock_urgency,
            penalty_per_hour_late=abs(penalty),
        )

    def batch_optimize(
        self,
        stores: List[Dict],
        inventory_data: Optional[Dict[str, float]] = None,
    ) -> List[Dict]:
        """批量计算多个门店的最优到货时间

        Args:
            stores: 门店列表
            inventory_data: 门店库存数据 {store_id: inventory_ratio}

        Returns:
            每个门店的最优时间窗口
        """
        results = []
        for store_data in stores:
            profile = StoreProfile(
                store_id=store_data["store_id"],
                store_name=store_data.get("store_name", ""),
                open_time=time(store_data.get("open_hour", 8), 0),
                close_time=time(store_data.get("close_hour", 22), 0),
                peak_hours=store_data.get("peak_hours", [11, 12, 17, 18]),
                avg_hourly_sales=store_data.get("avg_hourly_sales", 5000.0),
                inventory_decay_rate=store_data.get("inventory_decay_rate", 0.1),
                restock_urgency=store_data.get("restock_urgency", 0.5),
                store_type=store_data.get("store_type", "convenience"),
                daily_revenue=store_data.get("daily_revenue", 50000.0),
            )

            inventory_ratio = 0.5
            if inventory_data and store_data["store_id"] in inventory_data:
                inventory_ratio = inventory_data[store_data["store_id"]]

            window = self.find_optimal_window(profile, inventory_ratio)

            results.append({
                "store_id": store_data["store_id"],
                "store_name": store_data.get("store_name", ""),
                "optimal_window": {
                    "start_hour": window.optimal_arrival_start,
                    "end_hour": window.optimal_arrival_end,
                },
                "expected_revenue_gain": round(window.expected_revenue_gain, 2),
                "urgency_score": round(window.urgency_score, 3),
                "penalty_per_hour_late": round(window.penalty_per_hour_late, 2),
                "recommendation": self._generate_recommendation(window, profile),
            })

        return results

    def _generate_recommendation(
        self, window: RevenueWindow, profile: StoreProfile
    ) -> str:
        """生成到货建议"""
        if window.urgency_score > 0.8:
            priority = "紧急"
        elif window.urgency_score > 0.5:
            priority = "优先"
        else:
            priority = "正常"

        return (
            f"[{priority}] 建议在 {window.optimal_arrival_start}:00-"
            f"{window.optimal_arrival_end}:00 到货，"
            f"预期收益增量 ¥{window.expected_revenue_gain:.0f}，"
            f"每迟到1小时损失 ¥{window.penalty_per_hour_late:.0f}"
        )
