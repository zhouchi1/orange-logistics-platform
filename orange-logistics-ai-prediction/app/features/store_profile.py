"""门店画像特征工程"""
from typing import Dict, List, Optional
import numpy as np
from datetime import datetime


class StoreProfileFeatures:
    """门店画像特征提取

    从门店基础数据中提取用于预测模型的特征。
    """

    # 门店类型编码
    STORE_TYPE_MAP = {
        "convenience": 0,
        "supermarket": 1,
        "restaurant": 2,
        "pharmacy": 3,
        "electronics": 4,
        "clothing": 5,
        "other": 6,
    }

    # 区域等级编码
    AREA_LEVEL_MAP = {
        "core_business": 0,  # 核心商圈
        "sub_business": 1,   # 次级商圈
        "residential": 2,    # 居民区
        "industrial": 3,     # 工业区
        "suburban": 4,       # 郊区
    }

    def extract_features(self, store_data: Dict) -> Dict[str, float]:
        """提取门店特征向量

        Args:
            store_data: 门店原始数据

        Returns:
            特征字典
        """
        features = {}

        # 基础位置特征
        features["latitude"] = store_data.get("latitude", 0.0)
        features["longitude"] = store_data.get("longitude", 0.0)
        features["distance_from_warehouse"] = store_data.get("distance_km", 0.0)

        # 门店属性特征
        store_type = store_data.get("store_type", "other")
        features["store_type_encoded"] = self.STORE_TYPE_MAP.get(store_type, 6)

        area_level = store_data.get("area_level", "residential")
        features["area_level_encoded"] = self.AREA_LEVEL_MAP.get(area_level, 2)

        # 运营特征
        features["avg_daily_orders"] = store_data.get("avg_daily_orders", 10.0)
        features["avg_order_weight"] = store_data.get("avg_order_weight_kg", 5.0)
        features["monthly_revenue"] = store_data.get("monthly_revenue", 100000.0)

        # 配送难度特征
        features["floor_level"] = store_data.get("floor_level", 1)
        features["has_elevator"] = float(store_data.get("has_elevator", True))
        features["parking_difficulty"] = store_data.get("parking_difficulty", 0.5)
        features["road_access_score"] = store_data.get("road_access_score", 0.7)

        # 历史配送表现
        features["avg_delivery_time_hours"] = store_data.get("avg_delivery_time", 3.0)
        features["delivery_success_rate"] = store_data.get("delivery_success_rate", 0.95)
        features["avg_wait_time_min"] = store_data.get("avg_wait_time_min", 10.0)
        features["rejection_rate"] = store_data.get("rejection_rate", 0.02)

        # 时间偏好特征
        features["preferred_morning"] = float(store_data.get("prefer_morning", False))
        features["preferred_afternoon"] = float(store_data.get("prefer_afternoon", False))
        features["open_hour"] = store_data.get("open_hour", 8)
        features["close_hour"] = store_data.get("close_hour", 22)
        features["operating_hours"] = features["close_hour"] - features["open_hour"]

        return features

    def compute_store_score(self, features: Dict[str, float]) -> float:
        """计算门店综合评分（用于优先级排序）"""
        score = 0.0

        # 收益贡献
        score += min(features.get("monthly_revenue", 0) / 500000, 1.0) * 30

        # 配送效率
        score += features.get("delivery_success_rate", 0.9) * 20

        # 可达性
        score += (1.0 - features.get("parking_difficulty", 0.5)) * 15
        score += features.get("road_access_score", 0.7) * 15

        # 订单密度
        score += min(features.get("avg_daily_orders", 0) / 50, 1.0) * 20

        return round(score, 2)

    def batch_extract(self, stores: List[Dict]) -> List[Dict[str, float]]:
        """批量提取特征"""
        return [self.extract_features(store) for store in stores]
