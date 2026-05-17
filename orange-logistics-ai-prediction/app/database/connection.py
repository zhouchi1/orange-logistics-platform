"""数据库连接管理"""
from sqlalchemy.ext.asyncio import create_async_engine, AsyncSession, async_sessionmaker
from contextlib import asynccontextmanager
import logging

from app.config import settings

logger = logging.getLogger(__name__)

engine = None
async_session_factory = None


def get_database_url() -> str:
    """构建数据库连接URL"""
    return (
        f"mysql+aiomysql://{settings.DB_USER}:{settings.DB_PASSWORD}"
        f"@{settings.DB_HOST}:{settings.DB_PORT}/{settings.DB_NAME}"
        f"?charset=utf8mb4"
    )


async def init_db():
    """初始化数据库连接池"""
    global engine, async_session_factory
    url = get_database_url()
    engine = create_async_engine(
        url,
        pool_size=10,
        max_overflow=20,
        pool_recycle=3600,
        echo=False,
    )
    async_session_factory = async_sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)
    logger.info(f"Database connected: {settings.DB_HOST}:{settings.DB_PORT}/{settings.DB_NAME}")


async def close_db():
    """关闭数据库连接"""
    global engine
    if engine:
        await engine.dispose()
        logger.info("Database connection closed")


@asynccontextmanager
async def get_session():
    """获取数据库会话"""
    async with async_session_factory() as session:
        try:
            yield session
            await session.commit()
        except Exception:
            await session.rollback()
            raise
