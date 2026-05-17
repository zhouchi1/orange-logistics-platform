"""服务配置"""
from pydantic_settings import BaseSettings
from typing import Optional


class Settings(BaseSettings):
    """应用配置"""
    # 服务配置
    APP_NAME: str = "orange-logistics-ai-agent"
    APP_VERSION: str = "1.0.0"
    DEBUG: bool = False
    HOST: str = "0.0.0.0"
    PORT: int = 8002

    # LLM 配置
    LLM_PROVIDER: str = "openai"  # openai / azure / local
    OPENAI_API_KEY: str = ""
    OPENAI_API_BASE: str = "https://api.openai.com/v1"
    LLM_MODEL: str = "gpt-4-turbo-preview"
    LLM_TEMPERATURE: float = 0.1
    LLM_MAX_TOKENS: int = 4096

    # 向量数据库配置
    VECTOR_DB_TYPE: str = "milvus"  # milvus / pgvector
    MILVUS_HOST: str = "localhost"
    MILVUS_PORT: int = 19530
    MILVUS_COLLECTION: str = "logistics_knowledge"

    # Redis 配置
    REDIS_URL: str = "redis://localhost:6379/3"

    # 嵌入模型配置
    EMBEDDING_MODEL: str = "BAAI/bge-large-zh-v1.5"
    EMBEDDING_DIMENSION: int = 1024

    # 外部服务
    PREDICTION_SERVICE_URL: str = "http://localhost:8001"
    LOGISTICS_API_URL: str = "http://localhost:8080"
    NOTIFICATION_SERVICE_URL: str = "http://localhost:8090"

    # 数据库
    MYSQL_URL: str = "mysql+pymysql://root:password@localhost:3306/logistics"
    CLICKHOUSE_URL: str = "clickhouse://default:@localhost:8123/logistics"

    # LangSmith 追踪
    LANGCHAIN_TRACING_V2: bool = False
    LANGCHAIN_API_KEY: str = ""
    LANGCHAIN_PROJECT: str = "orange-logistics-agent"

    class Config:
        env_file = ".env"
        env_prefix = "AGENT_"


settings = Settings()
