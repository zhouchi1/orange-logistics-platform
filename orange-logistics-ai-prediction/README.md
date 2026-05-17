# Orange Logistics AI Prediction Service

Orange物流 AI 时序预测与路径优化微服务→
## 功能

### 时序预测
- **Temporal Fusion Transformer (TFT)**: 基于 PyTorch 实现的多步时序预测模块- **配送时效预→*: 综合考虑天气、路况、门店特征等因素
- **门店到货收益最大化**: 建模门店销售曲线，计算最优到货时间窗→
### 路径优化
- **VRPTW 求解**: 基于 Google OR-Tools 的带时间窗车辆路径问题求解- **时间窗口优化**: 贪心 + 局部搜索策略分配最优配送时→- **遗传算法调度**: 多目标优化（配送成本vs 门店收益→
## API 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/predict/delivery-time` | 预测配送时→|
| POST | `/api/v1/predict/store-optimal-time` | 预测门店最优到货时→|
| POST | `/api/v1/optimize/route` | 路径优化 |
| POST | `/api/v1/optimize/schedule` | 配送计划优化（收益最大化→|
| POST | `/api/v1/train/trigger` | 触发模型训练 |
| GET  | `/api/v1/model/status` | 模型状→|

## 快速开→
### 本地运行

```bash
# 安装依赖
pip install -r requirements.txt

# 启动服务
uvicorn app.main:app --host 0.0.0.0 --port 8001 --reload
```

### Docker 运行

```bash
docker-compose -f docker-compose.override.yml up -d
```

### 模型训练

```bash
# 训练 TFT 模型
python training/train_tft.py --epochs 50 --batch-size 64

# 训练门店收益模型
python training/train_store_model.py --num-stores 50 --days 90

# 评估模型
python training/evaluate.py --model-type all
```

## 项目结构

```
├── app/
→  ├── main.py                    # FastAPI 入口
→  ├── config.py                  # 配置
→  ├── models/                    # 预测模型
→  →  ├── temporal_fusion.py     # TFT 模型
→  →  ├── delivery_predictor.py  # 配送时效预→→  →  └── store_revenue.py       # 门店收益模型
→  ├── optimization/              # 优化算法
→  →  ├── vrp_solver.py          # OR-Tools VRP
→  →  ├── time_window.py         # 时间窗口优化
→  →  └── genetic_algorithm.py   # 遗传算法
→  ├── features/                  # 特征工程
→  ├── services/                  # 业务服务→→  └── api/                       # API 路由和模块├── training/                      # 训练脚本
├── Dockerfile
└── requirements.txt
```

## 技术栈

- **框架**: FastAPI + Uvicorn
- **深度学习**: PyTorch + PyTorch Lightning
- **优化**: Google OR-Tools + 自研遗传算法
- **特征存储**: Redis
- **模型管理**: MLflow
- **异步任务**: Celery

## 配置

通过环境变量配置，前缀 `PREDICTION_`→
| 变量 | 默认→| 说明 |
|------|--------|------|
| `PREDICTION_HOST` | 0.0.0.0 | 服务地址 |
| `PREDICTION_PORT` | 8001 | 服务端口 |
| `PREDICTION_REDIS_URL` | redis://localhost:6379/0 | Redis 地址 |
| `PREDICTION_MLFLOW_TRACKING_URI` | http://localhost:5000 | MLflow 地址 |
| `PREDICTION_MODEL_DIR` | /app/models_store | 模型存储目录 |
