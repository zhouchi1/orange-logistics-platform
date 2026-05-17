"""预测服务

封装模型调用逻辑，提供统一的预测接口。
"""
import logging
from typing import Dict, List, Optional
import pandas as pd
from datetime import datetime

from app.config import settings
try:
    from app.models.delivery_predictor import DeliveryPredictor
    HAS_TORCH = True
except ImportError:
    HAS_TORCH = False
    DeliveryPredictor = None
from app.models.store_revenue import StoreRevenueModel
from app.features.traffic_features import TrafficFeatures
from app.features.time_features import TimeFeatures
from app.features.store_profile import StoreProfileFeatures

logger = logging.getLogger(__name__)


class PredictionService:
    """预测服务

    提供配送时效预测和门店最优到货时间预测。
    """

    def __init__(self):
        self.delivery_predictor: Optional[DeliveryPredictor] = None
        self.store_revenue_model: Optional[StoreRevenueModel] = None
        self.traffic_features = TrafficFeatures()
        self.time_features = TimeFeatures()
        self.store_features = StoreProfileFeatures()
        self.model_status = "not_loaded"

    async def load_models(self):
        """加载所有模型"""
        try:
            if HAS_TORCH:
                self.delivery_predictor = DeliveryPredictor(
                    model_path=settings.TFT_MODEL_PATH
                )
            else:
                logger.warning("torch not available, delivery_predictor disabled")
                self.delivery_predictor = None
            self.store_revenue_model = StoreRevenueModel()
            self.model_status = "loaded"
            logger.info("Prediction models loaded (torch=%s)", HAS_TORCH)
        except Exception as e:
            logger.error(f"Failed to load models: {e}")
            # 使用未训练的模型作为 fallback
            if HAS_TORCH:
                self.delivery_predictor = DeliveryPredictor()
            else:
                self.delivery_predictor = None
            self.store_revenue_model = StoreRevenueModel()
            self.model_status = "fallback"

    async def predict_delivery_time(
        self,
        store_id: str,
        store_features: Dict,
        origin: Optional[Dict] = None,
        weather: Optional[Dict] = None,
    ) -> Dict:
        """预测配送时效

        Args:
            store_id: 门店ID
            store_features: 门店特征
            origin: 出发点坐标
            weather: 天气数据

        Returns:
            预测结果
        """
        if self.delivery_predictor is None:
            await self.load_models()

        # 提取路况特征
        traffic_data = None
        if origin and "latitude" in store_features:
            traffic_data = self.traffic_features.extract_features(
                origin=origin,
                destination={
                    "lat": store_features.get("latitude", 39.9),
                    "lon": store_features.get("longitude", 116.4),
                },
                departure_time=datetime.now(),
                weather=weather.get("condition", "clear") if weather else "clear",
            )

        # 提取时间特征
        time_feats = self.time_features.extract_features(datetime.now())

        # 增强门店特征
        enhanced_features = {**store_features}
        enhanced_features["is_peak_hour"] = time_feats.get("is_morning_peak", 0) or time_feats.get("is_evening_peak", 0)
        enhanced_features["is_holiday"] = time_feats.get("is_holiday", 0)

        # 调用预测模型
        result = self.delivery_predictor.predict_delivery_time(
            store_features=enhanced_features,
            weather_data=weather,
            traffic_data=traffic_data,
        )

        result["store_id"] = store_id
        result["prediction_time"] = datetime.now().isoformat()
        result["model_status"] = self.model_status

        # 添加路况信息
        if traffic_data:
            result["traffic_info"] = {
                "congestion_index": traffic_data.get("congestion_index", 0),
                "estimated_travel_time_min": traffic_data.get("estimated_travel_time_min", 0),
                "weather_impact": traffic_data.get("weather_impact_factor", 1.0),
            }

        return result

    async def predict_store_optimal_time(
        self,
        stores: List[Dict],
        inventory_data: Optional[Dict[str, float]] = None,
    ) -> List[Dict]:
        """预测门店最优到货时间

        Args:
            stores: 门店列表
            inventory_data: 库存数据

        Returns:
            每个门店的最优时间窗口
        """
        if self.store_revenue_model is None:
            await self.load_models()

        results = self.store_revenue_model.batch_optimize(stores, inventory_data)

        return {
            "predictions": results,
            "prediction_time": datetime.now().isoformat(),
            "total_stores": len(stores),
            "model_status": self.model_status,
        }

    def get_model_status(self) -> Dict:
        """获取模型状态"""
        return {
            "status": self.model_status,
            "models": {
                "delivery_predictor": {
                    "loaded": self.delivery_predictor is not None,
                    "is_fitted": self.delivery_predictor.is_fitted if self.delivery_predictor else False,
                },
                "store_revenue_model": {
                    "loaded": self.store_revenue_model is not None,
                },
            },
            "last_check": datetime.now().isoformat(),
        }
