# 生产环境部署指南

## 架构概览

```
                    ┌─────────────────────────────────────────────┐
                    │              Nginx Ingress                    │
                    │   api.orange-logistics.com                    │
                    └──────────────────┬──────────────────────────┘
                                       │
                    ┌──────────────────┼──────────────────────────┐
                    │                  │        K8s Cluster         │
                    │    ┌─────────────▼──────────────┐            │
                    │    │     Gateway (x3)            │            │
                    │    └─────────────┬──────────────┘            │
                    │                  │                            │
                    │    ┌─────────────▼──────────────────────┐    │
                    │    │  18 Java Microservices              │    │
                    │    │  (auth, order, dispatch, ...)       │    │
                    │    └─────────────┬──────────────────────┘    │
                    │                  │                            │
                    │    ┌─────────────▼──────────────────────┐    │
                    │    │  AI Services                        │    │
                    │    │  (prediction:8001, agent:8002)      │    │
                    │    └─────────────┬──────────────────────┘    │
                    │                  │                            │
                    │    ┌─────────────▼──────────────────────┐    │
                    │    │  Infrastructure                     │    │
                    │    │  MySQL | Redis | Nacos | Kafka      │    │
                    │    │  ES | RocketMQ | Seata | Spark      │    │
                    │    └────────────────────────────────────┘    │
                    └──────────────────────────────────────────────┘

CI/CD: GitLab → Build → Harbor → Helm Deploy → K8s
```

## 服务器要求

| 角色 | 最低配置 | 推荐配置 | 数量 |
|------|---------|---------|------|
| K8s Master | 4C 8G 100G SSD | 8C 16G 200G SSD | 3 |
| K8s Worker | 8C 16G 200G SSD | 16C 32G 500G SSD | 3+ |
| Harbor | 4C 8G 500G | 8C 16G 1T | 1 |
| GitLab | 4C 8G 200G | 8C 16G 500G | 1 |

单机开发/测试环境：16C 32G 即可跑全部服务。

---

## 一、安装 K8s 集群

### 方案 A：k3s（轻量，适合单机或小集群）

```bash
# Master 节点
curl -sfL https://get.k3s.io | sh -s - --write-kubeconfig-mode 644

# Worker 节点（可选）
curl -sfL https://get.k3s.io | K3S_URL=https://<master-ip>:6443 \
  K3S_TOKEN=$(cat /var/lib/rancher/k3s/server/node-token) sh -
```

### 方案 B：kubeadm（标准生产集群）

```bash
# 所有节点
apt-get update && apt-get install -y kubelet kubeadm kubectl docker.io
systemctl enable docker kubelet

# Master 初始化
kubeadm init --pod-network-cidr=10.244.0.0/16 --apiserver-advertise-address=<master-ip>

# 安装网络插件
kubectl apply -f https://raw.githubusercontent.com/flannel-io/flannel/master/Documentation/kube-flannel.yml

# Worker 加入
kubeadm join <master-ip>:6443 --token <token> --discovery-token-ca-cert-hash <hash>
```

---

## 二、安装 Harbor 镜像仓库

```bash
# 下载 Harbor
wget https://github.com/goharbor/harbor/releases/download/v2.10.0/harbor-offline-installer-v2.10.0.tgz
tar xzf harbor-offline-installer-v2.10.0.tgz
cd harbor

# 配置
cp harbor.yml.tmpl harbor.yml
```

编辑 `harbor.yml`：
```yaml
hostname: harbor.orange-logistics.com
http:
  port: 80
https:
  port: 443
  certificate: /etc/ssl/harbor/server.crt
  private_key: /etc/ssl/harbor/server.key
harbor_admin_password: Harbor12345
database:
  password: Harbor-db-2024
data_volume: /data/harbor
```

```bash
# 安装
./install.sh --with-trivy  # 带漏洞扫描

# 开机自启
systemctl enable harbor
```

### 在 K8s 中配置 Harbor 访问

```bash
# 创建 imagePullSecret
kubectl create secret docker-registry harbor-credentials \
  --docker-server=harbor.orange-logistics.com \
  --docker-username=admin \
  --docker-password=Harbor12345 \
  -n orange-logistics
```

---

## 三、安装 GitLab + GitLab Runner

```bash
# Docker 方式安装 GitLab
docker run -d \
  --hostname gitlab.orange-logistics.com \
  --name gitlab \
  -p 443:443 -p 80:80 -p 22:22 \
  -v /data/gitlab/config:/etc/gitlab \
  -v /data/gitlab/logs:/var/log/gitlab \
  -v /data/gitlab/data:/var/opt/gitlab \
  gitlab/gitlab-ce:latest

# 安装 GitLab Runner（K8s executor）
helm repo add gitlab https://charts.gitlab.io
helm install gitlab-runner gitlab/gitlab-runner \
  --namespace gitlab-runner --create-namespace \
  --set gitlabUrl=https://gitlab.orange-logistics.com \
  --set runnerRegistrationToken="<token>" \
  --set runners.privileged=true
```

---

## 四、首次部署

```bash
cd /opt/orange-logistics-platform

# 1. 初始化基础设施
chmod +x deploy/deploy-prod.sh
./deploy/deploy-prod.sh init

# 2. 登录 Harbor
docker login harbor.orange-logistics.com

# 3. 全量构建并部署
./deploy/deploy-prod.sh deploy 1.0.0
```

---

## 五、CI/CD 流程

### 分支策略

| 分支 | 触发动作 | 部署环境 |
|------|---------|---------|
| `develop` | 自动构建 + 部署 | Dev |
| `release/*` | 自动构建 + 部署 | Staging |
| `main` | 自动构建，手动确认部署 | Production |

### 流水线阶段

```
代码推送 → 编译 → 单元测试 → 代码质量 → 构建镜像 → 推送Harbor → Helm部署 → 通知
```

### GitLab CI/CD Variables 配置

在 GitLab → Settings → CI/CD → Variables 中添加：

| Variable | Value | Protected | Masked |
|----------|-------|-----------|--------|
| `HARBOR_USERNAME` | admin | ✓ | ✗ |
| `HARBOR_PASSWORD` | Harbor12345 | ✓ | ✓ |
| `SONAR_URL` | http://sonar.orange-logistics.com | ✗ | ✗ |
| `SONAR_TOKEN` | squ_xxx | ✓ | ✓ |
| `WECHAT_WEBHOOK` | https://qyapi.weixin.qq.com/... | ✓ | ✓ |
| `KUBECONFIG` | (file type, kubeconfig content) | ✓ | ✗ |

---

## 六、日常运维

### 更新单个服务
```bash
./deploy/deploy-prod.sh update dispatch 1.0.1
./deploy/deploy-prod.sh update ai-prediction latest
```

### 扩缩容
```bash
./deploy/deploy-prod.sh scale logistics-gateway 5
./deploy/deploy-prod.sh scale logistics-order 3
```

### 回滚
```bash
./deploy/deploy-prod.sh rollback
```

### 查看状态
```bash
./deploy/deploy-prod.sh status
kubectl top pods -n orange-logistics  # 资源使用
kubectl logs -f deployment/logistics-gateway -n orange-logistics  # 日志
```

### 停止/启动
```bash
# 停止所有业务（保留基础设施）
kubectl scale deployment -l tier!=infrastructure --replicas=0 -n orange-logistics

# 启动
kubectl scale deployment --all --replicas=1 -n orange-logistics

# 完全停止
kubectl scale deployment --all --replicas=0 -n orange-logistics
```

---

## 七、监控告警（推荐）

```bash
# 安装 Prometheus + Grafana
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm install monitoring prometheus-community/kube-prometheus-stack \
  --namespace monitoring --create-namespace \
  --set grafana.adminPassword=admin123

# 访问 Grafana
kubectl port-forward svc/monitoring-grafana 3000:80 -n monitoring
```

所有 Java 微服务已暴露 `/actuator/prometheus` 端点，Prometheus 自动采集。

---

## 八、备份策略

```bash
# MySQL 每日备份（添加到 crontab）
0 2 * * * kubectl exec -n orange-logistics mysql-0 -- \
  mysqldump -uroot -p'orange_logistics_2024' --all-databases | \
  gzip > /backup/mysql/$(date +\%Y\%m\%d).sql.gz

# 保留 30 天
find /backup/mysql -mtime +30 -delete
```

---

## 目录结构

```
deploy/
├── k8s/
│   ├── base/                    # 基础配置
│   │   ├── namespace.yaml
│   │   ├── configmap.yaml
│   │   ├── secrets.yaml
│   │   └── microservices.yaml
│   ├── infrastructure/          # 中间件
│   │   ├── mysql.yaml
│   │   ├── redis.yaml
│   │   ├── nacos.yaml
│   │   ├── kafka.yaml
│   │   ├── elasticsearch.yaml
│   │   ├── rocketmq.yaml
│   │   ├── seata-spark.yaml
│   │   ├── spark.yaml
│   │   ├── xxl-job-admin.yaml
│   │   ├── ai-prediction.yaml
│   │   └── ai-agent.yaml
│   └── overlays/
│       ├── dev/values-dev.yaml
│       └── prod/values-prod.yaml
├── helm/orange-logistics/       # Helm Chart
│   ├── Chart.yaml
│   ├── values.yaml
│   └── templates/
│       ├── deployment.yaml
│       ├── service.yaml
│       ├── ingress.yaml
│       ├── hpa.yaml
│       └── ai-services.yaml
├── cicd/
│   └── (runner configs)
├── deploy-prod.sh               # 生产部署脚本
└── PRODUCTION.md                # 本文档
```
