"""FastAPI 应用入口"""
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from contextlib import asynccontextmanager
import logging

from app.config import settings
from app.api.routes import router
from app.api.arrival_routes import arrival_router
from app.database.connection import init_db, close_db

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """应用生命周期管理"""
    logger.info(f"Starting {settings.APP_NAME} v{settings.APP_VERSION}")

    # 初始化数据库连接
    try:
        await init_db()
        logger.info("Database connection initialized")
    except Exception as e:
        logger.warning(f"Database connection failed (will retry on demand): {e}")

    # 启动时加载模型
    from app.services.prediction_service import PredictionService
    prediction_service = PredictionService()
    await prediction_service.load_models()
    app.state.prediction_service = prediction_service
    logger.info("Models loaded successfully")

    # 初始化到货分析服务
    from app.services.arrival_analysis_service import ArrivalAnalysisService
    app.state.arrival_service = ArrivalAnalysisService()
    logger.info("Arrival analysis service initialized")

    yield

    # 关闭数据库连接
    await close_db()
    logger.info("Shutting down...")


app = FastAPI(
    title=settings.APP_NAME,
    version=settings.APP_VERSION,
    description="橙子便利物流 AI 预测与路径优化服务",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(router, prefix="/api/v1")
app.include_router(arrival_router, prefix="/api/v1/arrival")


@app.get("/health")
async def health_check():
    return {"status": "healthy", "service": settings.APP_NAME}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("app.main:app", host=settings.HOST, port=settings.PORT, reload=settings.DEBUG)
