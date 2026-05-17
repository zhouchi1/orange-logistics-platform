"""服务配置"""
from pydantic_settings import BaseSettings
from typing import Optional


class Settings(BaseSettings):
    """应用配置"""
    # 服务配置
    APP_NAME: str = "orange-logistics-ai-prediction"
    APP_VERSION: str = "1.0.0"
    DEBUG: bool = False
    HOST: str = "0.0.0.0"
    PORT: int = 8001

    # 数据库配置
    DB_HOST: str = "localhost"
    DB_PORT: int = 13306
    DB_USER: str = "root"
    DB_PASSWORD: str = "orange_logistics_2024"
    DB_NAME: str = "orange_logistics"

    # Redis 配置
    REDIS_URL: str = "redis://localhost:6379/0"

    # MLflow 配置
    MLFLOW_TRACKING_URI: str = "http://localhost:5000"
    MLFLOW_EXPERIMENT_NAME: str = "orange-logistics-prediction"

    # 模型配置
    MODEL_DIR: str = "/app/models_store"
    TFT_MODEL_PATH: str = "/app/models_store/tft_model.pt"
    STORE_MODEL_PATH: str = "/app/models_store/store_model.pt"

    # Celery 配置
    CELERY_BROKER_URL: str = "redis://localhost:6379/1"
    CELERY_RESULT_BACKEND: str = "redis://localhost:6379/2"

    # 预测服务配置
    MAX_PREDICTION_HORIZON: int = 72  # 最大预测时长（小时）
    DEFAULT_NUM_VEHICLES: int = 20
    MAX_ROUTE_TIME_HOURS: int = 10

    class Config:
        env_file = ".env"
        env_prefix = "PREDICTION_"


settings = Settings()
