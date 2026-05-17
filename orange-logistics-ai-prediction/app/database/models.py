"""数据库模型定义"""
from sqlalchemy import Column, Integer, String, Float, DateTime, Date, Text, Index
from sqlalchemy.orm import DeclarativeBase
from datetime import datetime


class Base(DeclarativeBase):
    pass


class StoreSalesHourly(Base):
    """门店每小时销售数据"""
    __tablename__ = "store_sales_hourly"

    id = Column(Integer, primary_key=True, autoincrement=True)
    store_id = Column(String(64), nullable=False, index=True)
    sale_date = Column(Date, nullable=False, index=True)
    hour = Column(Integer, nullable=False)  # 0-23
    sales_amount = Column(Float, default=0.0)  # 销售额
    order_count = Column(Integer, default=0)  # 订单数
    sku_sold_count = Column(Integer, default=0)  # 售出SKU数
    stockout_sku_count = Column(Integer, default=0)  # 缺货SKU数
    created_at = Column(DateTime, default=datetime.now)

    __table_args__ = (
        Index("idx_store_date_hour", "store_id", "sale_date", "hour", unique=True),
    )


class StoreInfo(Base):
    """门店基础信息"""
    __tablename__ = "store_info"

    id = Column(Integer, primary_key=True, autoincrement=True)
    store_id = Column(String(64), nullable=False, unique=True)
    store_name = Column(String(128), default="")
    store_type = Column(String(32), default="convenience")  # convenience/supermarket/restaurant
    province = Column(String(32), default="")
    city = Column(String(32), default="")
    district = Column(String(64), default="")
    address = Column(String(256), default="")
    latitude = Column(Float, default=0.0)
    longitude = Column(Float, default=0.0)
    open_hour = Column(Integer, default=8)
    close_hour = Column(Integer, default=22)
    daily_avg_revenue = Column(Float, default=0.0)
    contact_phone = Column(String(32), default="")
    created_at = Column(DateTime, default=datetime.now)
    updated_at = Column(DateTime, default=datetime.now, onupdate=datetime.now)


class DeliveryRecord(Base):
    """配送到货记录"""
    __tablename__ = "delivery_record"

    id = Column(Integer, primary_key=True, autoincrement=True)
    waybill_no = Column(String(64), nullable=False, index=True)
    store_id = Column(String(64), nullable=False, index=True)
    driver_id = Column(String(64), nullable=False, index=True)
    planned_arrival_time = Column(DateTime, nullable=True)  # 计划到达时间
    actual_arrival_time = Column(DateTime, nullable=True)  # 实际到达时间
    departure_time = Column(DateTime, nullable=True)  # 出发时间
    delivery_date = Column(Date, nullable=False, index=True)
    arrival_hour = Column(Integer, nullable=True)  # 实际到达小时 0-23
    delivery_duration_min = Column(Integer, default=0)  # 配送耗时(分钟)
    cargo_weight_kg = Column(Float, default=0.0)
    cargo_volume_m3 = Column(Float, default=0.0)
    weather_condition = Column(String(32), default="clear")
    traffic_level = Column(Integer, default=1)  # 1-5 拥堵等级
    created_at = Column(DateTime, default=datetime.now)

    __table_args__ = (
        Index("idx_store_delivery_date", "store_id", "delivery_date"),
    )


class StoreArrivalAnalysis(Base):
    """门店到货时间分析结果"""
    __tablename__ = "store_arrival_analysis"

    id = Column(Integer, primary_key=True, autoincrement=True)
    store_id = Column(String(64), nullable=False, index=True)
    analysis_date = Column(Date, nullable=False)
    day_of_week = Column(Integer, nullable=False)  # 0=Monday, 6=Sunday
    optimal_arrival_hour = Column(Integer, nullable=False)  # 最优到达小时
    optimal_window_start = Column(Integer, nullable=False)  # 最优窗口开始
    optimal_window_end = Column(Integer, nullable=False)  # 最优窗口结束
    expected_revenue_gain = Column(Float, default=0.0)  # 预期收益增量
    penalty_per_hour_late = Column(Float, default=0.0)  # 每迟到1小时损失
    confidence_score = Column(Float, default=0.0)  # 置信度
    model_version = Column(String(32), default="v1.0")
    factors = Column(Text, nullable=True)  # JSON: 影响因素详情
    created_at = Column(DateTime, default=datetime.now)

    __table_args__ = (
        Index("idx_store_analysis_date", "store_id", "analysis_date"),
    )


class ArrivalRevenueCorrelation(Base):
    """到货时间与收益关联表 - 用于模型训练"""
    __tablename__ = "arrival_revenue_correlation"

    id = Column(Integer, primary_key=True, autoincrement=True)
    store_id = Column(String(64), nullable=False, index=True)
    delivery_date = Column(Date, nullable=False)
    arrival_hour = Column(Integer, nullable=False)  # 实际到达小时
    pre_arrival_inventory_ratio = Column(Float, default=0.5)  # 到货前库存比
    post_arrival_sales_amount = Column(Float, default=0.0)  # 到货后当天剩余销售额
    total_day_sales = Column(Float, default=0.0)  # 当天总销售额
    revenue_vs_avg = Column(Float, default=0.0)  # 相比平均值的收益差异(%)
    is_peak_before_arrival = Column(Integer, default=0)  # 到达前是否已过高峰
    weather_condition = Column(String(32), default="clear")
    day_of_week = Column(Integer, default=0)
    created_at = Column(DateTime, default=datetime.now)

    __table_args__ = (
        Index("idx_store_corr_date", "store_id", "delivery_date"),
    )
