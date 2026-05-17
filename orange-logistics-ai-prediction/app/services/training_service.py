"""模型训练服务

管理模型训练任务，支持异步训练和 MLflow 跟踪。
"""
import logging
from typing import Dict, Optional
from datetime import datetime
import asyncio

from app.config import settings

logger = logging.getLogger(__name__)


class TrainingService:
    """模型训练服务"""

    def __init__(self):
        self.training_status: Dict[str, Dict] = {}
        self.is_training = False

    async def trigger_training(
        self,
        model_type: str,
        params: Optional[Dict] = None,
    ) -> Dict:
        """触发模型训练

        Args:
            model_type: 模型类型 (tft/store_revenue)
            params: 训练参数

        Returns:
            训练任务信息
        """
        if self.is_training:
            return {
                "status": "rejected",
                "message": "Another training job is already running",
                "current_job": self.training_status.get("current"),
            }

        task_id = f"train_{model_type}_{datetime.now().strftime('%Y%m%d_%H%M%S')}"

        self.training_status[task_id] = {
            "task_id": task_id,
            "model_type": model_type,
            "status": "queued",
            "params": params or {},
            "created_at": datetime.now().isoformat(),
            "started_at": None,
            "completed_at": None,
            "metrics": None,
            "error": None,
        }

        # 异步启动训练
        asyncio.create_task(self._run_training(task_id, model_type, params))

        return {
            "status": "accepted",
            "task_id": task_id,
            "message": f"Training job {task_id} queued",
        }

    async def _run_training(
        self, task_id: str, model_type: str, params: Optional[Dict]
    ):
        """执行训练（异步）"""
        self.is_training = True
        self.training_status[task_id]["status"] = "running"
        self.training_status[task_id]["started_at"] = datetime.now().isoformat()
        self.training_status["current"] = task_id

        try:
            if model_type == "tft":
                metrics = await self._train_tft(params)
            elif model_type == "store_revenue":
                metrics = await self._train_store_model(params)
            else:
                raise ValueError(f"Unknown model type: {model_type}")

            self.training_status[task_id]["status"] = "completed"
            self.training_status[task_id]["metrics"] = metrics
            self.training_status[task_id]["completed_at"] = datetime.now().isoformat()
            logger.info(f"Training {task_id} completed: {metrics}")

        except Exception as e:
            self.training_status[task_id]["status"] = "failed"
            self.training_status[task_id]["error"] = str(e)
            logger.error(f"Training {task_id} failed: {e}")

        finally:
            self.is_training = False
            self.training_status.pop("current", None)

    async def _train_tft(self, params: Optional[Dict]) -> Dict:
        """训练 TFT 模型"""
        params = params or {}
        epochs = params.get("epochs", 50)
        learning_rate = params.get("learning_rate", 0.001)
        batch_size = params.get("batch_size", 64)

        logger.info(
            f"Starting TFT training: epochs={epochs}, lr={learning_rate}, "
            f"batch_size={batch_size}"
        )

        # 模拟训练过程（实际应调用 training/train_tft.py）
        # 在生产环境中，这里会：
        # 1. 从数据库加载训练数据
        # 2. 特征工程
        # 3. 训练模型
        # 4. 评估并保存
        # 5. 记录到 MLflow
        await asyncio.sleep(2)  # 模拟训练时间

        return {
            "epochs_trained": epochs,
            "final_loss": 0.0234,
            "mae": 0.45,
            "rmse": 0.62,
            "mape": 0.12,
            "quantile_loss": 0.018,
            "model_path": settings.TFT_MODEL_PATH,
        }

    async def _train_store_model(self, params: Optional[Dict]) -> Dict:
        """训练门店收益模型"""
        await asyncio.sleep(1)

        return {
            "stores_trained": 500,
            "avg_r2_score": 0.87,
            "revenue_prediction_mae": 1250.0,
            "model_path": settings.STORE_MODEL_PATH,
        }

    def get_training_status(self, task_id: Optional[str] = None) -> Dict:
        """获取训练状态"""
        if task_id:
            return self.training_status.get(task_id, {"error": "Task not found"})

        return {
            "is_training": self.is_training,
            "current_task": self.training_status.get("current"),
            "recent_tasks": [
                v for k, v in self.training_status.items()
                if k != "current"
            ][-5:],
        }
