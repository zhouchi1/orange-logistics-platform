"""时间特征工程"""
from typing import Dict, List
from datetime import datetime, date
import numpy as np


class TimeFeatures:
    """时间特征提取

    提取周期性时间特征，用于时序预测模型。
    """

    # 中国法定节假日（示例，实际应从配置加载）
    HOLIDAYS_2024 = {
        date(2024, 1, 1): "元旦",
        date(2024, 2, 10): "春节",
        date(2024, 2, 11): "春节",
        date(2024, 2, 12): "春节",
        date(2024, 2, 13): "春节",
        date(2024, 2, 14): "春节",
        date(2024, 2, 15): "春节",
        date(2024, 2, 16): "春节",
        date(2024, 2, 17): "春节",
        date(2024, 4, 4): "清明节",
        date(2024, 4, 5): "清明节",
        date(2024, 4, 6): "清明节",
        date(2024, 5, 1): "劳动节",
        date(2024, 5, 2): "劳动节",
        date(2024, 5, 3): "劳动节",
        date(2024, 5, 4): "劳动节",
        date(2024, 5, 5): "劳动节",
        date(2024, 6, 8): "端午节",
        date(2024, 6, 9): "端午节",
        date(2024, 6, 10): "端午节",
        date(2024, 9, 15): "中秋节",
        date(2024, 9, 16): "中秋节",
        date(2024, 9, 17): "中秋节",
        date(2024, 10, 1): "国庆节",
        date(2024, 10, 2): "国庆节",
        date(2024, 10, 3): "国庆节",
        date(2024, 10, 4): "国庆节",
        date(2024, 10, 5): "国庆节",
        date(2024, 10, 6): "国庆节",
        date(2024, 10, 7): "国庆节",
        date(2024, 11, 11): "双十一",
        date(2024, 6, 18): "618",
        date(2024, 12, 12): "双十二",
    }

    # 电商大促日期
    PROMOTION_DATES = {
        (6, 18): "618大促",
        (11, 11): "双十一",
        (12, 12): "双十二",
    }

    def extract_features(self, dt: datetime) -> Dict[str, float]:
        """提取时间特征

        Args:
            dt: 时间戳

        Returns:
            时间特征字典
        """
        features = {}

        # 基础时间特征
        features["hour"] = dt.hour
        features["minute"] = dt.minute
        features["day_of_week"] = dt.weekday()
        features["day_of_month"] = dt.day
        features["month"] = dt.month
        features["quarter"] = (dt.month - 1) // 3 + 1
        features["year"] = dt.year

        # 周期性编码（正弦/余弦变换）
        features["hour_sin"] = np.sin(2 * np.pi * dt.hour / 24)
        features["hour_cos"] = np.cos(2 * np.pi * dt.hour / 24)
        features["day_sin"] = np.sin(2 * np.pi * dt.weekday() / 7)
        features["day_cos"] = np.cos(2 * np.pi * dt.weekday() / 7)
        features["month_sin"] = np.sin(2 * np.pi * dt.month / 12)
        features["month_cos"] = np.cos(2 * np.pi * dt.month / 12)

        # 业务时间特征
        features["is_weekend"] = float(dt.weekday() >= 5)
        features["is_holiday"] = float(dt.date() in self.HOLIDAYS_2024)
        features["is_month_start"] = float(dt.day <= 3)
        features["is_month_end"] = float(dt.day >= 28)

        # 配送时段分类
        features["is_morning_peak"] = float(7 <= dt.hour <= 9)
        features["is_noon"] = float(11 <= dt.hour <= 13)
        features["is_evening_peak"] = float(17 <= dt.hour <= 19)
        features["is_night"] = float(dt.hour >= 22 or dt.hour <= 5)

        # 电商大促
        features["is_promotion"] = float(
            (dt.month, dt.day) in self.PROMOTION_DATES
        )
        # 大促前后（前3天和后2天物流压力大）
        features["near_promotion"] = float(self._is_near_promotion(dt))

        # 节假日前后效应
        features["pre_holiday"] = float(self._is_pre_holiday(dt))
        features["post_holiday"] = float(self._is_post_holiday(dt))

        # 发薪日效应（每月10号、15号、25号前后订单增加）
        features["near_payday"] = float(dt.day in [9, 10, 11, 14, 15, 16, 24, 25, 26])

        return features

    def extract_sequence_features(
        self, timestamps: List[datetime]
    ) -> List[Dict[str, float]]:
        """批量提取时间序列特征"""
        return [self.extract_features(ts) for ts in timestamps]

    def _is_near_promotion(self, dt: datetime) -> bool:
        """是否在大促附近（前3天到后2天）"""
        for (month, day), _ in self.PROMOTION_DATES.items():
            promo_date = date(dt.year, month, day)
            diff = (dt.date() - promo_date).days
            if -3 <= diff <= 2:
                return True
        return False

    def _is_pre_holiday(self, dt: datetime) -> bool:
        """是否在节假日前1-2天"""
        from datetime import timedelta
        for i in range(1, 3):
            check_date = dt.date() + timedelta(days=i)
            if check_date in self.HOLIDAYS_2024:
                return True
        return False

    def _is_post_holiday(self, dt: datetime) -> bool:
        """是否在节假日后1-2天"""
        from datetime import timedelta
        for i in range(1, 3):
            check_date = dt.date() - timedelta(days=i)
            if check_date in self.HOLIDAYS_2024:
                return True
        return False

    def get_time_slot_label(self, hour: int) -> str:
        """获取时间段标签"""
        if 6 <= hour < 9:
            return "早高峰"
        elif 9 <= hour < 11:
            return "上午"
        elif 11 <= hour < 14:
            return "午间"
        elif 14 <= hour < 17:
            return "下午"
        elif 17 <= hour < 20:
            return "晚高峰"
        elif 20 <= hour < 22:
            return "晚间"
        else:
            return "夜间"
