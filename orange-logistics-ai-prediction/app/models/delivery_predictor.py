"""配送时效预测模型

基于 TFT 模型的配送时效预测，考虑多种影响因素。
"""
import torch
import numpy as np
import pandas as pd
from typing import Dict, List, Optional
from datetime import datetime
import logging

from app.models.temporal_fusion import TemporalFusionTransformer

logger = logging.getLogger(__name__)


class DeliveryPredictor:
    """配送时效预测器

    综合考虑以下因素预测配送时效：
    - 历史配送数据
    - 天气状况
    - 节假日
    - 门店特征（位置、类型、历史接收能力）
    - 路况信息
    - 时间特征（小时、星期、月份）
    """

    def __init__(self, model_path: Optional[str] = None, device: str = "cpu"):
        self.device = torch.device(device)
        self.model = TemporalFusionTransformer(
            num_static_features=8,
            num_time_varying_known=6,
            num_time_varying_observed=10,
            hidden_dim=64,
            num_heads=4,
            forecast_horizon=24,
        ).to(self.device)

        if model_path:
            self.load_model(model_path)

        # 特征归一化参数
        self.feature_stats: Dict[str, Dict[str, float]] = {}
        self.is_fitted = False

    def load_model(self, path: str):
        """加载训练好的模型"""
        try:
            checkpoint = torch.load(path, map_location=self.device)
            self.model.load_state_dict(checkpoint["model_state_dict"])
            self.feature_stats = checkpoint.get("feature_stats", {})
            self.is_fitted = True
            logger.info(f"Model loaded from {path}")
        except FileNotFoundError:
            logger.warning(f"Model file not found: {path}, using random weights")
            self.is_fitted = False

    def preprocess_features(
        self,
        store_features: Dict,
        historical_data: pd.DataFrame,
        weather_data: Optional[Dict] = None,
        traffic_data: Optional[Dict] = None,
    ) -> Dict[str, torch.Tensor]:
        """特征预处理

        Args:
            store_features: 门店静态特征
            historical_data: 历史配送数据 DataFrame
            weather_data: 天气数据
            traffic_data: 路况数据

        Returns:
            模型输入张量字典
        """
        # 静态特征：门店属性
        static = np.array([
            store_features.get("latitude", 0.0),
            store_features.get("longitude", 0.0),
            store_features.get("store_type", 0),  # 门店类型编码
            store_features.get("avg_daily_orders", 0.0),
            store_features.get("distance_from_warehouse", 0.0),
            store_features.get("floor_level", 1),
            store_features.get("has_elevator", 0),
            store_features.get("parking_difficulty", 0.5),
        ], dtype=np.float32)

        # 已知时变特征（过去）：时间特征 + 天气预报
        past_steps = min(len(historical_data), 168)  # 最多7天历史
        known_past = np.zeros((past_steps, 6), dtype=np.float32)

        for i in range(past_steps):
            idx = len(historical_data) - past_steps + i
            if idx >= 0 and idx < len(historical_data):
                row = historical_data.iloc[idx]
                ts = pd.Timestamp(row.get("timestamp", datetime.now()))
                known_past[i] = [
                    ts.hour / 24.0,
                    ts.dayofweek / 7.0,
                    ts.month / 12.0,
                    int(row.get("is_holiday", False)),
                    row.get("temperature", 20.0) / 40.0,
                    row.get("precipitation", 0.0) / 100.0,
                ]

        # 观测时变特征（过去）：实际配送数据
        observed_past = np.zeros((past_steps, 10), dtype=np.float32)
        for i in range(past_steps):
            idx = len(historical_data) - past_steps + i
            if idx >= 0 and idx < len(historical_data):
                row = historical_data.iloc[idx]
                observed_past[i] = [
                    row.get("delivery_time_hours", 2.0) / 24.0,
                    row.get("num_packages", 1) / 100.0,
                    row.get("total_weight_kg", 5.0) / 500.0,
                    row.get("traffic_index", 0.5),
                    row.get("driver_utilization", 0.7),
                    row.get("warehouse_load", 0.5),
                    row.get("order_density", 0.3),
                    row.get("avg_stop_time_min", 5.0) / 60.0,
                    row.get("route_complexity", 0.5),
                    row.get("return_rate", 0.05),
                ]

        # 已知时变特征（未来）：预报天气 + 时间特征
        future_steps = 24
        known_future = np.zeros((future_steps, 6), dtype=np.float32)
        now = datetime.now()
        for i in range(future_steps):
            hour = (now.hour + i) % 24
            day = (now.weekday() + (now.hour + i) // 24) % 7
            known_future[i] = [
                hour / 24.0,
                day / 7.0,
                now.month / 12.0,
                0,  # 节假日标记
                weather_data.get("forecast_temp", 20.0) / 40.0 if weather_data else 0.5,
                weather_data.get("forecast_rain", 0.0) / 100.0 if weather_data else 0.0,
            ]

        return {
            "static_features": torch.FloatTensor(static).unsqueeze(0).to(self.device),
            "time_varying_known_past": torch.FloatTensor(known_past).unsqueeze(0).to(self.device),
            "time_varying_observed_past": torch.FloatTensor(observed_past).unsqueeze(0).to(self.device),
            "time_varying_known_future": torch.FloatTensor(known_future).unsqueeze(0).to(self.device),
        }

    def predict_delivery_time(
        self,
        store_features: Dict,
        historical_data: Optional[pd.DataFrame] = None,
        weather_data: Optional[Dict] = None,
        traffic_data: Optional[Dict] = None,
    ) -> Dict:
        """预测配送时效

        Args:
            store_features: 门店特征
            historical_data: 历史数据
            weather_data: 天气数据
            traffic_data: 路况数据

        Returns:
            预测结果，包含点预测和置信区间
        """
        if historical_data is None or historical_data.empty:
            # 无历史数据时使用默认值
            historical_data = self._generate_default_history(store_features)

        inputs = self.preprocess_features(
            store_features, historical_data, weather_data, traffic_data
        )

        predictions = self.model.predict(**inputs)

        # 反归一化（预测值是归一化后的小时数）
        median_hours = predictions["median"][0] * 24.0
        lower_hours = predictions["lower"][0] * 24.0
        upper_hours = predictions["upper"][0] * 24.0

        # 取第一个时间步作为即时预测
        return {
            "predicted_hours": float(np.clip(median_hours[0], 0.5, 72.0)),
            "confidence_interval": {
                "lower": float(np.clip(lower_hours[0], 0.5, 72.0)),
                "upper": float(np.clip(upper_hours[0], 0.5, 72.0)),
            },
            "hourly_forecast": [
                {
                    "hour_offset": i,
                    "predicted_hours": float(np.clip(median_hours[i], 0.5, 72.0)),
                    "lower": float(np.clip(lower_hours[i], 0.5, 72.0)),
                    "upper": float(np.clip(upper_hours[i], 0.5, 72.0)),
                }
                for i in range(min(24, len(median_hours)))
            ],
            "factors": {
                "weather_impact": weather_data.get("impact_score", 0.0) if weather_data else 0.0,
                "traffic_impact": traffic_data.get("congestion_index", 0.0) if traffic_data else 0.0,
                "store_accessibility": store_features.get("parking_difficulty", 0.5),
            },
        }

    def _generate_default_history(self, store_features: Dict) -> pd.DataFrame:
        """生成默认历史数据（用于冷启动）"""
        now = datetime.now()
        records = []
        for i in range(168):  # 7天
            records.append({
                "timestamp": now - pd.Timedelta(hours=168 - i),
                "delivery_time_hours": np.random.normal(3.0, 0.5),
                "num_packages": np.random.randint(1, 20),
                "total_weight_kg": np.random.uniform(2, 50),
                "traffic_index": np.random.uniform(0.3, 0.9),
                "driver_utilization": np.random.uniform(0.5, 0.95),
                "warehouse_load": np.random.uniform(0.3, 0.8),
                "order_density": np.random.uniform(0.2, 0.7),
                "avg_stop_time_min": np.random.uniform(3, 15),
                "route_complexity": np.random.uniform(0.3, 0.8),
                "return_rate": np.random.uniform(0.01, 0.1),
                "temperature": np.random.uniform(5, 35),
                "precipitation": np.random.uniform(0, 30),
                "is_holiday": False,
            })
        return pd.DataFrame(records)

    def batch_predict(
        self, stores: List[Dict], historical_data_map: Dict[str, pd.DataFrame]
    ) -> List[Dict]:
        """批量预测多个门店的配送时效"""
        results = []
        for store in stores:
            store_id = store.get("store_id", "unknown")
            history = historical_data_map.get(store_id)
            result = self.predict_delivery_time(store, history)
            result["store_id"] = store_id
            results.append(result)
        return results
