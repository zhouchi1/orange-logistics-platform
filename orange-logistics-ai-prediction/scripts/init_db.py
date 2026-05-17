"""初始化数据库表和模拟数据"""
import asyncio
import random
from datetime import date, datetime, timedelta
import numpy as np

import sys
sys.path.insert(0, ".")

from app.config import settings
from app.database.connection import init_db, close_db, get_session, engine
from app.database.models import Base, StoreSalesHourly, StoreInfo, DeliveryRecord


# 门店模拟数据
MOCK_STORES = [
    {"store_id": "STORE_001", "store_name": "朝阳路便利店", "store_type": "convenience", "city": "北京", "district": "朝阳区", "open_hour": 6, "close_hour": 23, "daily_avg_revenue": 45000, "lat": 39.921, "lon": 116.461},
    {"store_id": "STORE_002", "store_name": "望京超市", "store_type": "supermarket", "city": "北京", "district": "朝阳区", "open_hour": 8, "close_hour": 22, "daily_avg_revenue": 120000, "lat": 39.998, "lon": 116.480},
    {"store_id": "STORE_003", "store_name": "中关村便利店", "store_type": "convenience", "city": "北京", "district": "海淀区", "open_hour": 7, "close_hour": 23, "daily_avg_revenue": 55000, "lat": 39.984, "lon": 116.316},
    {"store_id": "STORE_004", "store_name": "国贸餐厅", "store_type": "restaurant", "city": "北京", "district": "朝阳区", "open_hour": 9, "close_hour": 22, "daily_avg_revenue": 80000, "lat": 39.909, "lon": 116.460},
    {"store_id": "STORE_005", "store_name": "西单便利店", "store_type": "convenience", "city": "北京", "district": "西城区", "open_hour": 6, "close_hour": 24, "daily_avg_revenue": 62000, "lat": 39.912, "lon": 116.375},
    {"store_id": "STORE_006", "store_name": "通州超市", "store_type": "supermarket", "city": "北京", "district": "通州区", "open_hour": 8, "close_hour": 21, "daily_avg_revenue": 95000, "lat": 39.902, "lon": 116.662},
    {"store_id": "STORE_007", "store_name": "三里屯餐厅", "store_type": "restaurant", "city": "北京", "district": "朝阳区", "open_hour": 10, "close_hour": 23, "daily_avg_revenue": 110000, "lat": 39.933, "lon": 116.454},
    {"store_id": "STORE_008", "store_name": "回龙观便利店", "store_type": "convenience", "city": "北京", "district": "昌平区", "open_hour": 7, "close_hour": 22, "daily_avg_revenue": 38000, "lat": 40.070, "lon": 116.336},
    {"store_id": "STORE_009", "store_name": "大兴超市", "store_type": "supermarket", "city": "北京", "district": "大兴区", "open_hour": 8, "close_hour": 21, "daily_avg_revenue": 85000, "lat": 39.726, "lon": 116.338},
    {"store_id": "STORE_010", "store_name": "五道口便利店", "store_type": "convenience", "city": "北京", "district": "海淀区", "open_hour": 6, "close_hour": 24, "daily_avg_revenue": 52000, "lat": 39.992, "lon": 116.338},
]

# 不同门店类型的销售曲线模板
SALES_TEMPLATES = {
    "convenience": [0.1, 0.05, 0.02, 0.02, 0.05, 0.15, 0.4, 0.8, 1.0, 0.7, 0.5, 0.6, 0.9, 0.7, 0.5, 0.4, 0.5, 0.7, 0.9, 0.8, 0.6, 0.4, 0.3, 0.2],
    "supermarket": [0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.1, 0.3, 0.5, 0.7, 0.8, 0.6, 0.5, 0.6, 0.7, 0.9, 1.0, 0.9, 0.7, 0.5, 0.3, 0.0, 0.0],
    "restaurant": [0.0, 0.0, 0.0, 0.0, 0.0, 0.1, 0.2, 0.3, 0.2, 0.1, 0.3, 0.8, 1.0, 0.7, 0.3, 0.2, 0.3, 0.8, 1.0, 0.8, 0.5, 0.2, 0.0, 0.0],
}


async def create_tables():
    """创建所有表"""
    from app.database.connection import engine as db_engine
    async with db_engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    print("Tables created successfully")


async def insert_stores():
    """插入门店数据"""
    async with get_session() as session:
        for s in MOCK_STORES:
            store = StoreInfo(
                store_id=s["store_id"],
                store_name=s["store_name"],
                store_type=s["store_type"],
                city=s["city"],
                district=s["district"],
                latitude=s["lat"],
                longitude=s["lon"],
                open_hour=s["open_hour"],
                close_hour=s["close_hour"],
                daily_avg_revenue=s["daily_avg_revenue"],
            )
            session.add(store)
    print(f"Inserted {len(MOCK_STORES)} stores")


async def generate_sales_data(days: int = 60):
    """生成模拟销售数据"""
    total = 0
    async with get_session() as session:
        for store in MOCK_STORES:
            template = np.array(SALES_TEMPLATES[store["store_type"]])
            daily_rev = store["daily_avg_revenue"]

            for day_offset in range(days):
                sale_date = date.today() - timedelta(days=day_offset)
                dow = sale_date.weekday()

                # 周末销售额波动
                day_multiplier = 1.2 if dow >= 5 else 1.0
                # 随机波动 ±20%
                day_multiplier *= random.uniform(0.8, 1.2)

                for hour in range(24):
                    if template[hour] == 0:
                        continue

                    sales = daily_rev * template[hour] / template.sum() * day_multiplier
                    sales *= random.uniform(0.7, 1.3)  # 小时级随机波动

                    order_count = int(sales / random.uniform(30, 80))
                    sku_sold = int(order_count * random.uniform(1.5, 3.0))
                    # 越晚缺货越多
                    stockout = max(0, int((hour - store["open_hour"]) * random.uniform(0, 2)))

                    record = StoreSalesHourly(
                        store_id=store["store_id"],
                        sale_date=sale_date,
                        hour=hour,
                        sales_amount=round(sales, 2),
                        order_count=order_count,
                        sku_sold_count=sku_sold,
                        stockout_sku_count=stockout,
                    )
                    session.add(record)
                    total += 1

                # 每10天提交一次避免内存过大
                if day_offset % 10 == 0:
                    await session.flush()

    print(f"Generated {total} hourly sales records")


async def generate_delivery_records(days: int = 90):
    """生成模拟配送记录"""
    drivers = [f"D{i:03d}" for i in range(1, 11)]
    total = 0

    async with get_session() as session:
        for store in MOCK_STORES:
            for day_offset in range(days):
                delivery_date = date.today() - timedelta(days=day_offset)

                # 每个门店每天1-2次配送
                num_deliveries = random.choice([1, 1, 1, 2])
                for _ in range(num_deliveries):
                    # 到达时间：通常在开门前后
                    base_hour = store["open_hour"] + random.randint(-1, 4)
                    arrival_hour = max(5, min(22, base_hour))
                    arrival_minute = random.randint(0, 59)

                    arrival_time = datetime.combine(
                        delivery_date,
                        datetime.min.time().replace(hour=arrival_hour, minute=arrival_minute)
                    )
                    departure_time = arrival_time - timedelta(minutes=random.randint(30, 120))

                    record = DeliveryRecord(
                        waybill_no=f"WB{delivery_date.strftime('%Y%m%d')}{store['store_id'][-3:]}{random.randint(100,999)}",
                        store_id=store["store_id"],
                        driver_id=random.choice(drivers),
                        planned_arrival_time=arrival_time - timedelta(minutes=random.randint(-30, 30)),
                        actual_arrival_time=arrival_time,
                        departure_time=departure_time,
                        delivery_date=delivery_date,
                        arrival_hour=arrival_hour,
                        delivery_duration_min=random.randint(20, 90),
                        cargo_weight_kg=random.uniform(50, 500),
                        cargo_volume_m3=random.uniform(0.5, 5.0),
                        weather_condition=random.choice(["clear", "clear", "clear", "cloudy", "rain", "rain"]),
                        traffic_level=random.randint(1, 5),
                    )
                    session.add(record)
                    total += 1

            await session.flush()

    print(f"Generated {total} delivery records")


async def main():
    """主函数"""
    print("Initializing database...")
    await init_db()

    print("Creating tables...")
    await create_tables()

    print("Inserting store data...")
    await insert_stores()

    print("Generating sales data (60 days)...")
    await generate_sales_data(60)

    print("Generating delivery records (90 days)...")
    await generate_delivery_records(90)

    await close_db()
    print("\nDone! Database initialized with mock data.")


if __name__ == "__main__":
    asyncio.run(main())
