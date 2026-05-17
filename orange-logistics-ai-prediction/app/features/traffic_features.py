"""路况特征工程"""
from typing import Dict, List, Optional
from datetime import datetime, time
import numpy as np


class TrafficFeatures:
    """路况特征提取

    基于时间、区域、历史数据提取路况相关特征。
    """

    # 城市拥堵时段模板（拥堵指数 0-1）
    CONGESTION_TEMPLATE = {
        "weekday": [
            0.1, 0.05, 0.05, 0.05, 0.1, 0.2,   # 0-5
            0.4, 0.7, 0.9, 0.7, 0.5, 0.5,       # 6-11
            0.6, 0.5, 0.4, 0.5, 0.6, 0.8,       # 12-17
            0.9, 0.7, 0.5, 0.3, 0.2, 0.15,      # 18-23
        ],
        "weekend": [
            0.1, 0.05, 0.05, 0.05, 0.05, 0.1,   # 0-5
            0.15, 0.2, 0.3, 0.4, 0.5, 0.6,      # 6-11
            0.6, 0.5, 0.5, 0.5, 0.5, 0.5,       # 12-17
            0.4, 0.3, 0.25, 0.2, 0.15, 0.1,     # 18-23
        ],
    }

    # 天气对路况的影响系数
    WEATHER_IMPACT = {
        "clear": 1.0,
        "cloudy": 1.0,
        "light_rain": 1.2,
        "heavy_rain": 1.5,
        "snow": 1.8,
        "fog": 1.4,
        "extreme": 2.0,
    }

    def extract_features(
        self,
        origin: Dict[str, float],
        destination: Dict[str, float],
        departure_time: datetime,
        weather: str = "clear",
        historical_speed: Optional[float] = None,
    ) -> Dict[str, float]:
        """提取路况特征

        Args:
            origin: 起点坐标 {"lat": ..., "lon": ...}
            destination: 终点坐标
            departure_time: 出发时间
            weather: 天气状况
            historical_speed: 历史平均速度（km/h）

        Returns:
            路况特征字典
        """
        features = {}

        # 时间相关拥堵
        hour = departure_time.hour
        is_weekend = departure_time.weekday() >= 5
        day_type = "weekend" if is_weekend else "weekday"
        congestion = self.CONGESTION_TEMPLATE[day_type][hour]

        features["congestion_index"] = congestion
        features["is_peak_hour"] = float(congestion > 0.7)
        features["is_weekend"] = float(is_weekend)

        # 天气影响
        weather_factor = self.WEATHER_IMPACT.get(weather, 1.0)
        features["weather_impact_factor"] = weather_factor
        features["adjusted_congestion"] = min(congestion * weather_factor, 1.0)

        # 距离和预估时间
        distance = self._calculate_distance(
            origin["lat"], origin["lon"],
            destination["lat"], destination["lon"],
        )
        features["straight_line_distance_km"] = distance

        # 实际路径距离估算（直线距离 * 绕行系数）
        detour_factor = 1.3 + congestion * 0.2  # 拥堵时绕行更多
        features["estimated_road_distance_km"] = distance * detour_factor

        # 速度估算
        base_speed = historical_speed or 35.0  # km/h
        adjusted_speed = base_speed * (1.0 - congestion * 0.6) / weather_factor
        adjusted_speed = max(adjusted_speed, 5.0)  # 最低5km/h
        features["estimated_speed_kmh"] = adjusted_speed

        # 预估行驶时间（分钟）
        road_distance = features["estimated_road_distance_km"]
        features["estimated_travel_time_min"] = (road_distance / adjusted_speed) * 60

        # 不确定性（拥堵时不确定性更高）
        features["time_uncertainty_min"] = features["estimated_travel_time_min"] * (
            0.1 + congestion * 0.3
        )

        return features

    def get_hourly_congestion_forecast(
        self,
        start_hour: int,
        hours_ahead: int = 12,
        is_weekend: bool = False,
        weather_forecast: Optional[List[str]] = None,
    ) -> List[Dict[str, float]]:
        """获取未来几小时的拥堵预测

        Args:
            start_hour: 起始小时
            hours_ahead: 预测时长
            is_weekend: 是否周末
            weather_forecast: 天气预报列表

        Returns:
            每小时拥堵预测
        """
        day_type = "weekend" if is_weekend else "weekday"
        template = self.CONGESTION_TEMPLATE[day_type]

        forecast = []
        for i in range(hours_ahead):
            hour = (start_hour + i) % 24
            base_congestion = template[hour]

            weather = "clear"
            if weather_forecast and i < len(weather_forecast):
                weather = weather_forecast[i]

            weather_factor = self.WEATHER_IMPACT.get(weather, 1.0)
            adjusted = min(base_congestion * weather_factor, 1.0)

            forecast.append({
                "hour": hour,
                "congestion_index": round(adjusted, 3),
                "weather": weather,
                "recommended_departure": adjusted < 0.5,
            })

        return forecast

    @staticmethod
    def _calculate_distance(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
        """Haversine 距离"""
        R = 6371.0
        lat1_r, lat2_r = np.radians(lat1), np.radians(lat2)
        dlat = np.radians(lat2 - lat1)
        dlon = np.radians(lon2 - lon1)
        a = np.sin(dlat / 2) ** 2 + np.cos(lat1_r) * np.cos(lat2_r) * np.sin(dlon / 2) ** 2
        return R * 2 * np.arctan2(np.sqrt(a), np.sqrt(1 - a))
