"""TFT 模型训练脚本

使用 PyTorch Lightning 训练 Temporal Fusion Transformer 模型。
"""
import os
import sys
import torch
import torch.nn as nn
import numpy as np
import pandas as pd
from torch.utils.data import Dataset, DataLoader
import pytorch_lightning as pl
from pytorch_lightning.callbacks import ModelCheckpoint, EarlyStopping
import logging
from typing import Dict, Optional
from datetime import datetime

# 添加项目根目录到路径
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app.models.temporal_fusion import TemporalFusionTransformer, QuantileLoss
from app.features.time_features import TimeFeatures
from app.features.store_profile import StoreProfileFeatures

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


class DeliveryDataset(Dataset):
    """配送数据集"""

    def __init__(
        self,
        data: pd.DataFrame,
        past_steps: int = 168,
        future_steps: int = 24,
        num_static: int = 8,
        num_known: int = 6,
        num_observed: int = 10,
    ):
        self.data = data
        self.past_steps = past_steps
        self.future_steps = future_steps
        self.num_static = num_static
        self.num_known = num_known
        self.num_observed = num_observed

        # 预处理数据
        self.samples = self._prepare_samples()

    def _prepare_samples(self):
        """准备训练样本"""
        samples = []
        total_steps = self.past_steps + self.future_steps

        for i in range(len(self.data) - total_steps):
            window = self.data.iloc[i: i + total_steps]

            # 静态特征（取第一行的门店特征）
            static = np.array([
                window.iloc[0].get("latitude", 39.9),
                window.iloc[0].get("longitude", 116.4),
                window.iloc[0].get("store_type", 0),
                window.iloc[0].get("avg_daily_orders", 20),
                window.iloc[0].get("distance_km", 10),
                window.iloc[0].get("floor_level", 1),
                window.iloc[0].get("has_elevator", 1),
                window.iloc[0].get("parking_difficulty", 0.5),
            ], dtype=np.float32)

            # 已知时变特征
            known = np.zeros((total_steps, self.num_known), dtype=np.float32)
            for j in range(total_steps):
                row = window.iloc[j]
                ts = pd.Timestamp(row.get("timestamp", datetime.now()))
                known[j] = [
                    ts.hour / 24.0,
                    ts.dayofweek / 7.0,
                    ts.month / 12.0,
                    float(row.get("is_holiday", 0)),
                    row.get("temperature", 20) / 40.0,
                    row.get("precipitation", 0) / 100.0,
                ]

            # 观测时变特征（仅过去）
            observed = np.zeros((self.past_steps, self.num_observed), dtype=np.float32)
            for j in range(self.past_steps):
                row = window.iloc[j]
                observed[j] = [
                    row.get("delivery_time_hours", 3) / 24.0,
                    row.get("num_packages", 5) / 100.0,
                    row.get("total_weight_kg", 10) / 500.0,
                    row.get("traffic_index", 0.5),
                    row.get("driver_utilization", 0.7),
                    row.get("warehouse_load", 0.5),
                    row.get("order_density", 0.3),
                    row.get("avg_stop_time_min", 8) / 60.0,
                    row.get("route_complexity", 0.5),
                    row.get("return_rate", 0.05),
                ]

            # 目标：未来配送时效
            target = np.array([
                window.iloc[self.past_steps + j].get("delivery_time_hours", 3) / 24.0
                for j in range(self.future_steps)
            ], dtype=np.float32)

            samples.append({
                "static": static,
                "known_past": known[:self.past_steps],
                "known_future": known[self.past_steps:],
                "observed_past": observed,
                "target": target,
            })

        return samples

    def __len__(self):
        return len(self.samples)

    def __getitem__(self, idx):
        sample = self.samples[idx]
        return {
            "static_features": torch.FloatTensor(sample["static"]),
            "time_varying_known_past": torch.FloatTensor(sample["known_past"]),
            "time_varying_known_future": torch.FloatTensor(sample["known_future"]),
            "time_varying_observed_past": torch.FloatTensor(sample["observed_past"]),
            "target": torch.FloatTensor(sample["target"]),
        }


class TFTLightningModule(pl.LightningModule):
    """PyTorch Lightning 训练模块"""

    def __init__(
        self,
        learning_rate: float = 0.001,
        hidden_dim: int = 64,
        num_heads: int = 4,
        dropout: float = 0.1,
        quantiles: list = None,
    ):
        super().__init__()
        self.save_hyperparameters()
        self.learning_rate = learning_rate
        self.quantiles = quantiles or [0.1, 0.5, 0.9]

        self.model = TemporalFusionTransformer(
            hidden_dim=hidden_dim,
            num_heads=num_heads,
            dropout=dropout,
            quantiles=self.quantiles,
        )
        self.loss_fn = QuantileLoss(self.quantiles)

    def forward(self, batch):
        return self.model(
            static_features=batch["static_features"],
            time_varying_known_past=batch["time_varying_known_past"],
            time_varying_observed_past=batch["time_varying_observed_past"],
            time_varying_known_future=batch["time_varying_known_future"],
        )

    def training_step(self, batch, batch_idx):
        output = self(batch)
        loss = self.loss_fn(output["quantile_predictions"], batch["target"])
        self.log("train_loss", loss, prog_bar=True)
        return loss

    def validation_step(self, batch, batch_idx):
        output = self(batch)
        loss = self.loss_fn(output["quantile_predictions"], batch["target"])
        # MAE on median prediction
        median_pred = output["quantile_predictions"][:, :, 1]
        mae = torch.mean(torch.abs(median_pred - batch["target"]))
        self.log("val_loss", loss, prog_bar=True)
        self.log("val_mae", mae, prog_bar=True)
        return loss

    def configure_optimizers(self):
        optimizer = torch.optim.Adam(self.parameters(), lr=self.learning_rate)
        scheduler = torch.optim.lr_scheduler.ReduceLROnPlateau(
            optimizer, mode="min", factor=0.5, patience=5
        )
        return {
            "optimizer": optimizer,
            "lr_scheduler": {"scheduler": scheduler, "monitor": "val_loss"},
        }


def generate_synthetic_data(num_stores: int = 10, days: int = 30) -> pd.DataFrame:
    """生成合成训练数据（用于演示）"""
    records = []
    for store_idx in range(num_stores):
        base_delivery_time = np.random.uniform(1.5, 5.0)
        for day in range(days):
            for hour in range(24):
                # 模拟真实配送模式
                traffic_factor = 1.0 + 0.5 * np.sin(np.pi * hour / 12)
                weather_noise = np.random.normal(0, 0.2)
                delivery_time = base_delivery_time * traffic_factor + weather_noise
                delivery_time = max(0.5, delivery_time)

                records.append({
                    "store_id": f"STORE_{store_idx:03d}",
                    "timestamp": datetime(2024, 1, 1 + day, hour),
                    "delivery_time_hours": delivery_time,
                    "num_packages": np.random.randint(1, 30),
                    "total_weight_kg": np.random.uniform(2, 100),
                    "traffic_index": 0.3 + 0.4 * np.sin(np.pi * hour / 12),
                    "driver_utilization": np.random.uniform(0.4, 0.95),
                    "warehouse_load": np.random.uniform(0.3, 0.9),
                    "order_density": np.random.uniform(0.1, 0.8),
                    "avg_stop_time_min": np.random.uniform(3, 20),
                    "route_complexity": np.random.uniform(0.2, 0.9),
                    "return_rate": np.random.uniform(0.01, 0.1),
                    "temperature": 15 + 10 * np.sin(np.pi * (day + 1) / 30),
                    "precipitation": max(0, np.random.normal(5, 10)),
                    "is_holiday": day % 7 >= 5,
                    "latitude": 39.9 + np.random.uniform(-0.1, 0.1),
                    "longitude": 116.4 + np.random.uniform(-0.1, 0.1),
                    "store_type": store_idx % 3,
                    "avg_daily_orders": 20 + store_idx * 5,
                    "distance_km": 5 + store_idx * 2,
                    "floor_level": np.random.randint(1, 5),
                    "has_elevator": int(np.random.random() > 0.3),
                    "parking_difficulty": np.random.uniform(0.1, 0.9),
                })

    return pd.DataFrame(records)


def train(
    data_path: Optional[str] = None,
    epochs: int = 50,
    batch_size: int = 64,
    learning_rate: float = 0.001,
    output_dir: str = "./models_store",
):
    """主训练函数"""
    logger.info("Starting TFT model training...")

    # 加载或生成数据
    if data_path and os.path.exists(data_path):
        data = pd.read_parquet(data_path)
        logger.info(f"Loaded data from {data_path}: {len(data)} records")
    else:
        logger.info("Generating synthetic training data...")
        data = generate_synthetic_data(num_stores=10, days=30)
        logger.info(f"Generated {len(data)} synthetic records")

    # 划分训练/验证集
    split_idx = int(len(data) * 0.8)
    train_data = data.iloc[:split_idx]
    val_data = data.iloc[split_idx:]

    # 创建数据集
    train_dataset = DeliveryDataset(train_data)
    val_dataset = DeliveryDataset(val_data)

    logger.info(f"Train samples: {len(train_dataset)}, Val samples: {len(val_dataset)}")

    if len(train_dataset) == 0:
        logger.warning("Not enough data for training. Need at least 192 records.")
        return

    train_loader = DataLoader(train_dataset, batch_size=batch_size, shuffle=True, num_workers=0)
    val_loader = DataLoader(val_dataset, batch_size=batch_size, num_workers=0)

    # 创建模型
    model = TFTLightningModule(learning_rate=learning_rate)

    # 回调
    os.makedirs(output_dir, exist_ok=True)
    checkpoint_callback = ModelCheckpoint(
        dirpath=output_dir,
        filename="tft_model-{epoch:02d}-{val_loss:.4f}",
        monitor="val_loss",
        mode="min",
        save_top_k=3,
    )
    early_stop = EarlyStopping(monitor="val_loss", patience=10, mode="min")

    # 训练
    trainer = pl.Trainer(
        max_epochs=epochs,
        callbacks=[checkpoint_callback, early_stop],
        accelerator="auto",
        devices=1,
        log_every_n_steps=10,
    )

    trainer.fit(model, train_loader, val_loader)

    # 保存最终模型
    final_path = os.path.join(output_dir, "tft_model.pt")
    torch.save({
        "model_state_dict": model.model.state_dict(),
        "feature_stats": {},
        "training_config": {
            "epochs": epochs,
            "batch_size": batch_size,
            "learning_rate": learning_rate,
        },
    }, final_path)

    logger.info(f"Model saved to {final_path}")
    logger.info("Training complete!")


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description="Train TFT model")
    parser.add_argument("--data", type=str, default=None, help="Path to training data")
    parser.add_argument("--epochs", type=int, default=50)
    parser.add_argument("--batch-size", type=int, default=64)
    parser.add_argument("--lr", type=float, default=0.001)
    parser.add_argument("--output", type=str, default="./models_store")

    args = parser.parse_args()
    train(
        data_path=args.data,
        epochs=args.epochs,
        batch_size=args.batch_size,
        learning_rate=args.lr,
        output_dir=args.output,
    )
