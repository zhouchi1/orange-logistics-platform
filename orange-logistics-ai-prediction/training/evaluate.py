"""模型评估脚本"""
import os
import sys
import numpy as np
import pandas as pd
import torch
from typing import Dict
import logging
import json

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app.models.temporal_fusion import TemporalFusionTransformer
from app.models.delivery_predictor import DeliveryPredictor
from training.train_tft import DeliveryDataset, generate_synthetic_data

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


def evaluate_tft_model(
    model_path: str,
    test_data: pd.DataFrame,
) -> Dict:
    """评估 TFT 模型"""
    logger.info(f"Evaluating TFT model from {model_path}")

    predictor = DeliveryPredictor(model_path=model_path)

    # 创建测试数据集
    dataset = DeliveryDataset(test_data)
    if len(dataset) == 0:
        logger.warning("No test samples available")
        return {"error": "insufficient_data"}

    # 收集预测结果
    all_predictions = []
    all_targets = []

    for i in range(min(len(dataset), 100)):  # 最多评估100个样本
        sample = dataset[i]
        with torch.no_grad():
            output = predictor.model(
                static_features=sample["static_features"].unsqueeze(0),
                time_varying_known_past=sample["time_varying_known_past"].unsqueeze(0),
                time_varying_observed_past=sample["time_varying_observed_past"].unsqueeze(0),
                time_varying_known_future=sample["time_varying_known_future"].unsqueeze(0),
            )

        # 取中位数预测
        median_pred = output["quantile_predictions"][0, :, 1].numpy()
        target = sample["target"].numpy()

        all_predictions.append(median_pred)
        all_targets.append(target)

    predictions = np.array(all_predictions)
    targets = np.array(all_targets)

    # 计算指标
    mae = np.mean(np.abs(predictions - targets))
    rmse = np.sqrt(np.mean((predictions - targets) ** 2))
    mape = np.mean(np.abs((predictions - targets) / (targets + 1e-8))) * 100

    # 分位数覆盖率（如果有上下界）
    metrics = {
        "mae": float(mae),
        "rmse": float(rmse),
        "mape": float(mape),
        "num_samples": len(all_predictions),
        "prediction_horizon": predictions.shape[1] if len(predictions) > 0 else 0,
    }

    logger.info(f"Evaluation results: MAE={mae:.4f}, RMSE={rmse:.4f}, MAPE={mape:.2f}%")
    return metrics


def evaluate_store_model(model_path: str) -> Dict:
    """评估门店收益模型"""
    logger.info(f"Evaluating store model from {model_path}")

    if not os.path.exists(model_path):
        return {"error": "model_not_found"}

    with open(model_path) as f:
        store_models = json.load(f)

    # 统计分析
    daily_revenues = [m["daily_revenue"] for m in store_models.values()]
    optimal_windows = [m["optimal_window"] for m in store_models.values()]

    metrics = {
        "num_stores": len(store_models),
        "avg_daily_revenue": float(np.mean(daily_revenues)),
        "std_daily_revenue": float(np.std(daily_revenues)),
        "avg_window_size_hours": float(np.mean([
            w["end"] - w["start"] for w in optimal_windows
        ])),
        "most_common_start_hour": int(pd.Series([
            w["start"] for w in optimal_windows
        ]).mode().iloc[0]),
    }

    logger.info(f"Store model evaluation: {metrics}")
    return metrics


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description="Evaluate models")
    parser.add_argument("--model-type", choices=["tft", "store", "all"], default="all")
    parser.add_argument("--tft-model", type=str, default="./models_store/tft_model.pt")
    parser.add_argument("--store-model", type=str, default="./models_store/store_models.json")
    parser.add_argument("--test-data", type=str, default=None)

    args = parser.parse_args()

    results = {}

    if args.model_type in ("tft", "all"):
        if args.test_data and os.path.exists(args.test_data):
            test_data = pd.read_parquet(args.test_data)
        else:
            test_data = generate_synthetic_data(num_stores=5, days=10)

        results["tft"] = evaluate_tft_model(args.tft_model, test_data)

    if args.model_type in ("store", "all"):
        results["store"] = evaluate_store_model(args.store_model)

    print("\n" + "=" * 50)
    print("EVALUATION RESULTS")
    print("=" * 50)
    print(json.dumps(results, indent=2))
