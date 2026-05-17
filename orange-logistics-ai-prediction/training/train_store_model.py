"""门店收益模型训练脚本"""
import os
import sys
import numpy as np
import pandas as pd
from datetime import datetime, time
from typing import Dict, List
import logging
import json

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app.models.store_revenue import StoreRevenueModel, StoreProfile, SalesCurveModel

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


def generate_store_sales_data(num_stores: int = 50, days: int = 90) -> pd.DataFrame:
    """生成门店销售数据"""
    records = []
    store_types = ["convenience", "supermarket", "restaurant"]

    for store_idx in range(num_stores):
        store_type = store_types[store_idx % 3]
        base_revenue = np.random.uniform(3000, 15000)

        # 获取该类型的销售曲线模板
        template = SalesCurveModel.CURVE_TEMPLATES[store_type]["hourly_weights"]

        for day in range(days):
            day_factor = 1.0 + 0.1 * np.sin(2 * np.pi * day / 7)  # 周期性
            for hour in range(24):
                hourly_sales = base_revenue * template[hour] * day_factor
                hourly_sales += np.random.normal(0, hourly_sales * 0.1)
                hourly_sales = max(0, hourly_sales)

                records.append({
                    "store_id": f"STORE_{store_idx:03d}",
                    "store_type": store_type,
                    "date": datetime(2024, 1, 1) + pd.Timedelta(days=day),
                    "hour": hour,
                    "hourly_sales": hourly_sales,
                    "inventory_level": max(0, 1.0 - hour * 0.04 + np.random.normal(0, 0.05)),
                    "restock_time": np.random.choice([7, 8, 9, 10, 14, 15]),
                })

    return pd.DataFrame(records)


def train_store_models(
    data: pd.DataFrame,
    output_dir: str = "./models_store",
) -> Dict:
    """训练门店收益模型"""
    logger.info("Training store revenue models...")

    model = StoreRevenueModel()
    results = {}

    # 按门店分组训练
    store_ids = data["store_id"].unique()
    for store_id in store_ids:
        store_data = data[data["store_id"] == store_id]

        # 计算每小时平均销售
        hourly_avg = store_data.groupby("hour")["hourly_sales"].mean().values

        # 拟合销售曲线
        store_type = store_data["store_type"].iloc[0]
        profile = StoreProfile(
            store_id=store_id,
            store_name=f"Store {store_id}",
            open_time=time(7, 0),
            close_time=time(23, 0),
            peak_hours=[int(h) for h in np.argsort(hourly_avg)[-3:]],
            avg_hourly_sales=float(hourly_avg.mean()),
            inventory_decay_rate=0.08,
            restock_urgency=0.5,
            store_type=store_type,
            daily_revenue=float(hourly_avg.sum()),
        )

        # 使用实际数据拟合曲线
        curve = model.sales_model.get_sales_curve(profile, hourly_avg)

        # 计算最优时间窗口
        window = model.find_optimal_window(profile, current_inventory_ratio=0.5)

        results[store_id] = {
            "sales_curve": curve.tolist(),
            "optimal_window": {
                "start": window.optimal_arrival_start,
                "end": window.optimal_arrival_end,
            },
            "daily_revenue": float(hourly_avg.sum()),
            "peak_hours": profile.peak_hours,
        }

    # 保存结果
    os.makedirs(output_dir, exist_ok=True)
    output_path = os.path.join(output_dir, "store_models.json")
    with open(output_path, "w") as f:
        json.dump(results, f, indent=2)

    logger.info(f"Trained models for {len(results)} stores, saved to {output_path}")

    # 计算整体指标
    metrics = {
        "stores_trained": len(results),
        "avg_daily_revenue": np.mean([r["daily_revenue"] for r in results.values()]),
        "model_path": output_path,
    }

    return metrics


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description="Train store revenue models")
    parser.add_argument("--data", type=str, default=None)
    parser.add_argument("--output", type=str, default="./models_store")
    parser.add_argument("--num-stores", type=int, default=50)
    parser.add_argument("--days", type=int, default=90)

    args = parser.parse_args()

    if args.data and os.path.exists(args.data):
        data = pd.read_parquet(args.data)
    else:
        logger.info("Generating synthetic store sales data...")
        data = generate_store_sales_data(args.num_stores, args.days)

    metrics = train_store_models(data, args.output)
    logger.info(f"Training metrics: {metrics}")
