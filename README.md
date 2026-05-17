# Orange物流智能实时监控平台

基于 Spring Cloud Alibaba 微服务架→+ Spark Structured Streaming + LangChain/LangGraph AI Agent 的企业级物流实时监控与智能决策系统→
## 系统架构

```
┌─────────────────────────────────────────────────────────────────────────────→→                           客户→/ 前端                                      →→        Web Dashboard →Mobile APP →小程→→Open API                       →└──────────────────────────────┬──────────────────────────────────────────────→                               →┌──────────────────────────────▼──────────────────────────────────────────────→→                    API Gateway (Spring Cloud Gateway)                       →→             动态路径→限流 →鉴权 →灰度发布 →请求聚合                      →└──────────────────────────────┬──────────────────────────────────────────────→                               →┌──────────────────────────────▼──────────────────────────────────────────────→→                        微服务集→(20 Services)                              →→                                                                             →→ ┌─────────→┌─────────→┌──────────→┌──────────→┌──────────→          →→ → Auth   →→ Order  →→Waybill  →│Warehouse →│Transport →          →→ →认证授权 →→订单服务 →→运单服务  →→仓储服务  →→运输服务  →          →→ └─────────→└─────────→└──────────→└──────────→└──────────→          →→                                                                             →→ ┌─────────→┌─────────→┌──────────→┌──────────→┌──────────→          →→ │Dispatch →→Billing →→Customer →→ Notify  →→ Report  →          →→ →配送服→→→计费服务 →→客户服务  →→通知服务  →→报表服务  →          →→ └─────────→└─────────→└──────────→└──────────→└──────────→          →→                                                                             →→ ┌─────────→┌─────────→┌──────────→┌──────────→┌──────────→          →→ → Risk   →│Scheduler→→ Search  →→Service  →│Streaming →          →→ →风控服务 →→调度中心 →→搜索服务  →→核心服务  →→流处→  →          →→ └─────────→└─────────→└──────────→└──────────→└──────────→          →└─────────────────────────────────────────────────────────────────────────────→                               →┌──────────────────────────────▼──────────────────────────────────────────────→→                     Python AI 服务→                                        →→                                                                             →→ ┌────────────────────────────→ ┌────────────────────────────────→        →→ →  AI Prediction Service    → →     AI Agent Service           →        →→ →→Temporal Fusion Trans.   → →→LangGraph 智能调度 Agent       →        →→ →→门店收益最大化模型         → →→LangChain RAG 客服 Agent      →        →→ →→OR-Tools 路径优化         → →→运营分析 Agent                →        →→ →→遗传算法多约束调→        → →→异常处理 Agent                →        →→ └────────────────────────────→ └────────────────────────────────→        →└─────────────────────────────────────────────────────────────────────────────→                               →┌──────────────────────────────▼──────────────────────────────────────────────→→                        基础设施→                                           →→                                                                             →→ ┌───────→┌───────→┌─────→┌───────→┌──────────→┌──────────→        →→ →Kafka →→MySQL →│Redis→→ ES   →│ClickHouse→→RocketMQ →        →→ └───────→└───────→└─────→└───────→└──────────→└──────────→        →→                                                                             →→ ┌───────→┌───────→┌─────────→┌──────────→┌──────────→              →→ →Nacos →│Sentinel→│SkyWalking→│Prometheus→→Grafana  →              →→ └───────→└───────→└─────────→└──────────→└──────────→              →→                                                                             →→ ┌───────────────→┌───────────────→┌───────────────→                    →→ →Spark Cluster →→   Milvus     →→   MLflow     →                    →→ →Master+Worker →→ 向量数据→   →→ 模型管理      →                    →→ └───────────────→└───────────────→└───────────────→                    →└─────────────────────────────────────────────────────────────────────────────→```

## 技术栈

### Java 微服务层
| 技→| 版本 | 用→|
|------|------|------|
| Spring Boot | 3.2.5 | 微服务基础框架 |
| Spring Cloud | 2023.0.1 | 微服务治→|
| Spring Cloud Alibaba | 2023.0.1.0 | Nacos/Sentinel/Seata |
| Spring WebFlux | 3.2.5 | 响应→Web 框架 |
| Spring Cloud Gateway | →| API 网关 |
| Nacos | 2.3.x | 注册中心 + 配置中心 |
| Sentinel | 1.8.7 | 流量控制、熔断降→|
| Seata | 2.0.0 | 分布式事→|
| SkyWalking | 9.x | 分布式链路追→|
| Apache Spark | 3.5.1 | 流处→+ MLlib |
| Apache Kafka | 3.7.0 | 消息队列 |
| RocketMQ | 5.x | 业务消息 |
| MySQL | 8.0 | 关系数据→|
| Redis | 7.x | 缓存 + 分布式锁 |
| Elasticsearch | 8.13 | 全文检→|
| ClickHouse | 24.3 | OLAP 分析 |
| R2DBC | →| 响应式数据库驱动 |
| MapStruct | 1.5.5 | 对象映射 |
| JWT (jjwt) | 0.12.5 | 认证令牌 |
| XXL-Job | 2.4.0 | 分布式任务调→|

### Python AI →| 技→| 用→|
|------|------|
| PyTorch + Lightning | 深度学习框架 |
| Temporal Fusion Transformer | 时序预测模型 |
| OR-Tools | 运筹优化（VRP→|
| Prophet | 时间序列基线预测 |
| LangChain | LLM 应用框架 |
| LangGraph | Agent 工作流编→|
| LangSmith | Agent 可观测→|
| Milvus | 向量数据→|
| FastAPI | Python Web 框架 |
| Celery | 异步任务队列 |
| MLflow | 模型版本管理 |
| sentence-transformers | 文本嵌入（BGE→|

### 监控运维
| 技→| 用→|
|------|------|
| Prometheus | 指标采集 |
| Grafana | 可视化监控大→|
| SkyWalking | 链路追踪 |
| Docker Compose | 容器编排 |

## 项目模块

### 基础模块
| 模块 | 说明 |
|------|------|
| orange-logistics-common | 公共工具类、异常处理、常量定→|
| orange-logistics-model | 数据模型（Entity、DTO、VO、枚举） |
| orange-logistics-config | Nacos 配置中心客户→|
| orange-logistics-gateway | API 网关（动态路由、限流、鉴权） |

### 业务微服→| 模块 | 端口 | 说明 |
|------|------|------|
| orange-logistics-auth | 8081 | OAuth2 + JWT 认证授权、RBAC 权限、审计日→|
| orange-logistics-order | 8082 | 订单创建/查询/取消、状态机、拆单合单、COD |
| orange-logistics-waybill | →| 电子面单生成、运单轨迹、签收确→|
| orange-logistics-warehouse | →| 入库/出库、库位管理、拣货路径优化、波次管→|
| orange-logistics-transport | 8083 | 车队管理、线路规划、GPS定位、冷链温控|
| orange-logistics-dispatch | 8084 | 智能派单、配送路径优化、末端配送、签→|
| orange-logistics-billing | 8085 | 运费计算引擎、阶梯定价、优惠券、对账结算|
| orange-logistics-customer | 8086 | 客户画像、地址簿、等级积分、投诉工→|
| orange-logistics-notification | 8087 | 多渠道通知（短→推→微信/邮件）、模板、频→|
| orange-logistics-report | 8088 | KPI 指标、运营报表、趋势分析、区域热力图 |
| orange-logistics-risk | 8089 | 地址风险评分、异常行为检测、黑名单 |
| orange-logistics-scheduler | 8090 | 定时任务（超时关单、数据清理、报表生成） |
| orange-logistics-search | 8091 | ES 全文检索、地址智能解析、搜索建议|
| orange-logistics-service | 8080 | 核心业务服务（WebSocket 实时告警→|
| orange-logistics-streaming | →| Spark Structured Streaming 实时流处→|
| orange-logistics-monitor | →| Prometheus + Grafana 监控配置 |

### Python AI 服务
| 模块 | 说明 |
|------|------|
| orange-logistics-ai-prediction | Transformer 时序预测、门店收益最大化、OR-Tools 路径优化 |
| orange-logistics-ai-agent | LangChain/LangGraph 智能 Agent（调→客服/分析/异常→|

## 核心业务功能

### 📦 物流全链路管→- 订单全生命周期（创建议揽收→运输→中转→派送→签收→- 电子面单生成与运单轨迹实时追→- 仓储 WMS（入→出库/拣货/波次→- 干线运输管理 + 车辆 GPS 实时定位
- 末端配送（驿站/自提→上门→
### 🚚 智能调度与优化- **智能派单**：基于距离、负载、时效的多因子评分算→- **路径优化**：OR-Tools 求解 VRPTW（带时间窗的车辆路径问题→- **门店到货收益最大化**：Transformer 预测门店销售曲线，遗传算法求解最优到货时→- **运力调度**：实时路径+ 天气 + 订单紧急度动态调→
### 🤖 AI 智能决策（LangChain + LangGraph→- **智能调度 Agent**：LangGraph 多步决策工作流（感知→推理→行动），自动调用路径优化和时效预测工→- **物流客服 Agent**：RAG 知识库问答，多轮对话处理查件/催件/改地址/投诉
- **运营分析 Agent**：自然语言查询运营数据，自动生成分析报告和优化建议
- **异常处理 Agent**：ReAct 模式自动分析异常原因，推荐处置方→
### 📊 实时监控与异常检→- Spark Structured Streaming 实时处理物流事件
- 滞留检测（包裹超时未更新）
- 路由回环检测（相同节点间反复流转）
- MLlib 物流时效预测模型
- WebSocket 实时告警推→
### 💰 计费与风→- 运费计算引擎（重→× 单价 + 续重 + 距离附加 + 时效加价→- 地址风险评分（模糊地址、高风险区域、历史投诉）
- 异常行为检测（刷单、恶意拒收）
- 黑名单管→
### 📈 报表与分析- KPI 指标：妥投率、时效达成率、破损率
- 区域热力图、趋势分析（环比/同比→- ClickHouse OLAP 聚合分析
- 运营日报/周报/月报自动生成

## 快速启→
### 前置条件
- Docker & Docker Compose v2
- JDK 17+
- Maven 3.8+
- Python 3.11+（AI 服务→
### 1. 启动基础设施

```bash
# 启动全部基础设施（Kafka、MySQL、Redis、ES、ClickHouse、Nacos、Spark等）
docker-compose up -d zookeeper kafka mysql redis elasticsearch clickhouse \
  nacos sentinel-dashboard skywalking-oap skywalking-ui \
  spark-master spark-worker prometheus grafana
```

### 2. 初始化数→
```bash
# MySQL 建表
docker exec -i orange-mysql mysql -uroot -porange_logistics_2024 < scripts/init-mysql.sql

# ClickHouse 建表
docker exec -i orange-clickhouse clickhouse-client < scripts/init-clickhouse.sql

# Kafka Topics
docker exec orange-kafka bash /scripts/init-kafka-topics.sh
```

### 3. 编译 Java 项目

```bash
mvn clean package -DskipTests
```

### 4. 启动 Java 微服→
```bash
# 按依赖顺序启→docker-compose up -d logistics-auth
docker-compose up -d logistics-order logistics-waybill logistics-warehouse
docker-compose up -d logistics-transport logistics-dispatch logistics-billing
docker-compose up -d logistics-customer logistics-notification logistics-report
docker-compose up -d logistics-risk logistics-scheduler logistics-search
docker-compose up -d logistics-service logistics-gateway
```

### 5. 启动 Python AI 服务

```bash
# 预测服务
cd orange-logistics-ai-prediction
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8100

# Agent 服务
cd orange-logistics-ai-agent
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8200
```

或使→Docker→```bash
docker-compose up -d ai-prediction ai-agent
```

### 6. 提交 Spark Streaming 任务

```bash
docker exec orange-spark-master spark-submit \
  --master spark://spark-master:7077 \
  --class com.orange.logistics.streaming.job.LogisticsStreamingJob \
  /app/orange-logistics-streaming.jar
```

### 7. 启动模拟数据

```bash
java -cp orange-logistics-service/target/orange-logistics-service.jar \
  com.orange.logistics.service.service.DataGenerator localhost:9092 logistics-tracking-events 10 1000 10
```

## API 接口

### 网关入口
所→API 通过网关统一访问：`http://localhost:8888`

### 核心接口

```
# 认证
POST   /api/auth/login                    # 登录
POST   /api/auth/register                 # 注册

# 订单
POST   /api/v1/orders                     # 创建订单
GET    /api/v1/orders/{orderId}           # 查询订单
PUT    /api/v1/orders/{orderId}/cancel    # 取消订单

# 运单
GET    /api/v1/tracking/{waybillNo}       # 查询物流轨迹
POST   /api/v1/waybills                   # 生成运单

# 配置POST   /api/v1/dispatch/assign            # 智能派单
GET    /api/v1/dispatch/tasks/{courierId} # 快递员任务列表

# 计费
POST   /api/v1/billing/calculate          # 运费计算
GET    /api/v1/billing/bills/{orderId}    # 查询账单

# 搜索
GET    /api/v1/search?q={keyword}         # 全文检→POST   /api/v1/search/address/parse       # 地址解析

# AI 预测
POST   /api/v1/predict/delivery-time      # 配送时效预→POST   /api/v1/predict/store-optimal-time # 门店最优到货时→POST   /api/v1/optimize/route             # 路径优化
POST   /api/v1/optimize/schedule          # 配送计划优化（收益最大化→
# AI Agent
POST   /api/v1/agent/dispatch             # 智能调度决策
POST   /api/v1/agent/customer/chat        # 客服对话
POST   /api/v1/agent/analytics/query      # 自然语言数据查询
POST   /api/v1/agent/anomaly/handle       # 异常自动处理

# 监控
GET    /api/v1/statistics/realtime         # 实时统计
WS     ws://localhost:8080/ws/alerts       # WebSocket 告警订阅
```

## 服务端口

| 服务 | 端口 | 说明 |
|------|------|------|
| API Gateway | 8888 | 统一入口 |
| Auth Service | 8081 | 认证授权 |
| Order Service | 8082 | 订单 |
| Transport Service | 8083 | 运输 |
| Dispatch Service | 8084 | 配置|
| Billing Service | 8085 | 计费 |
| Customer Service | 8086 | 客户 |
| Notification Service | 8087 | 通知 |
| Report Service | 8088 | 报表 |
| Risk Service | 8089 | 风控 |
| Scheduler Service | 8090 | 调度 |
| Search Service | 8091 | 搜索 |
| Config Service | 8092 | 配置 |
| Core Service | 8080 | 核心业务 |
| AI Prediction | 8100 | AI 预测 |
| AI Agent | 8200 | AI Agent |
| Kafka | 9092 | 消息队列 |
| Spark Master UI | 8082 | Spark 管理 |
| Nacos | 8848 | 注册/配置中心 |
| Sentinel Dashboard | 8858 | 流控管理 |
| SkyWalking UI | 8080 | 链路追踪 |
| MySQL | 3306 | 数据→|
| Redis | 6379 | 缓存 |
| Elasticsearch | 9200 | 搜索引擎 |
| ClickHouse | 8123 | OLAP |
| Prometheus | 9090 | 监控采集 |
| Grafana | 3000 | 监控大盘 |

## 监控面板

- **Grafana**: http://localhost:3000 (admin/admin123)
  - 物流实时监控大盘（事件速率、异常数、Kafka Lag、包裹状态分布）
  - API 响应时间、JVM 内存、Spark 批处理时→- **Prometheus**: http://localhost:9090
- **Nacos Console**: http://localhost:8848/nacos (nacos/nacos)
- **Sentinel Dashboard**: http://localhost:8858
- **SkyWalking UI**: http://localhost:8080
- **Spark Master UI**: http://localhost:8082

## 告警规则

| 规则 | 条件 | 级别 |
|------|------|------|
| Kafka 消费延迟 | Lag > 10000 持续 5min | Warning |
| 异常数量突增 | 10min →> 50 →| Critical |
| 服务宕机 | 服务不可→> 1min | Critical |
| API 延迟过高 | P99 > 2s 持续 5min | Warning |
| JVM 内存过高 | Heap > 85% 持续 5min | Warning |
| Spark 批处理延→| > 30s 持续 3min | Warning |
| Redis 连接数过→| > 500 持续 5min | Warning |

## 项目结构

```
orange-logistics-platform/
├── pom.xml                          # →POM（统一依赖版本管理→├── docker-compose.yml               # Docker 编排
├── README.md
→├── orange-logistics-common/             # 公共模块
├── orange-logistics-model/              # 数据模型
├── orange-logistics-config/             # 配置中心
├── orange-logistics-gateway/            # API 网关
├── orange-logistics-auth/               # 认证授权
├── orange-logistics-order/              # 订单服务
├── orange-logistics-waybill/            # 运单服务
├── orange-logistics-warehouse/          # 仓储服务
├── orange-logistics-transport/          # 运输服务
├── orange-logistics-dispatch/           # 配送服→├── orange-logistics-billing/            # 计费服务
├── orange-logistics-customer/           # 客户服务
├── orange-logistics-notification/       # 通知服务
├── orange-logistics-report/             # 报表服务
├── orange-logistics-risk/               # 风控服务
├── orange-logistics-scheduler/          # 调度中心
├── orange-logistics-search/             # 搜索服务
├── orange-logistics-service/            # 核心业务服务
├── orange-logistics-streaming/          # Spark 流处→├── orange-logistics-monitor/            # 监控配置
├── orange-logistics-ai-prediction/      # Python AI 预测服务
├── orange-logistics-ai-agent/           # Python AI Agent 服务
→├── config/                          # 监控配置
→  ├── prometheus/
→  →  ├── prometheus.yml
→  →  └── alert-rules.yml
→  └── grafana/
→      ├── grafana.ini
→      ├── dashboards/
→      └── provisioning/
→├── docker/                          # Dockerfile
→  ├── Dockerfile-gateway
→  ├── Dockerfile-service
→  └── Dockerfile-streaming
→└── scripts/                         # 初始化脚→    ├── init-mysql.sql
    ├── init-clickhouse.sql
    └── init-kafka-topics.sh
```

## 开发指→
### 本地开发环→
1. 启动基础设施：`docker-compose up -d mysql redis kafka nacos`
2. IDEA 导入项目：File →Open →选择 `pom.xml`
3. 配置 JDK 17
4. 按需启动各微服务（先启动 auth，再启动业务服务→
### 新增微服→
1. 在根目录创建模块目录
2. 创建 `pom.xml`，parent 指向 `orange-logistics-platform`
3. 在父 `pom.xml` →`<modules>` 中添加模块4. 添加 `application.yml`，配置Nacos 注册
5. 创建 Dockerfile

### 代码规范

- Controller 层：只做参数校验和响应封→- Service 层：业务逻辑
- Repository 层：数据访问
- DTO/VO 分离：DTO 用于服务间传输，VO 用于前端响应
- 统一异常处理：GlobalExceptionHandler
- 统一响应格式：Result<T>

## License

Internal Use Only - Orange Logistics
