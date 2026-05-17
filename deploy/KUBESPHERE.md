# KubeSphere + 本地 GitLab 安装指南

## 前提条件
- Docker Desktop 运行中，K8s 集群正常
- Helm 已安装（已完成）
- 网络能访问 GitHub / Docker Hub

## 一、安装 KubeSphere

```powershell
# 方式1：直接安装（需要网络通畅）
helm upgrade --install -n kubesphere-system --create-namespace ks-core https://charts.kubesphere.io/main/ks-core-1.1.3.tgz --wait --timeout 15m

# 方式2：如果上面的 URL 不通，先下载 chart 再安装
# 浏览器下载: https://github.com/kubesphere/kubesphere/releases/download/v4.1.2/ks-core-1.1.2.tgz
# 然后:
helm upgrade --install -n kubesphere-system --create-namespace ks-core .\ks-core-1.1.2.tgz --wait --timeout 15m
```

安装完成后：
- **控制台：** http://localhost:30880
- **用户名：** admin
- **密码：** P@88w0rd

## 二、安装 DevOps 扩展

1. 登录 KubeSphere 控制台
2. 左侧菜单 → 扩展市场
3. 找到 "DevOps" → 安装
4. 等待安装完成（会自动部署 Jenkins）

## 三、在 KubeSphere 应用商店安装 GitLab

1. 扩展市场 → 应用商店 → 安装
2. 应用商店中搜索 "GitLab" → 部署
3. 或者用 Helm 手动安装：

```powershell
helm repo add gitlab https://charts.gitlab.io
helm install gitlab gitlab/gitlab \
  --namespace gitlab --create-namespace \
  --set global.hosts.domain=local \
  --set global.hosts.externalIP=127.0.0.1 \
  --set certmanager.install=false \
  --set global.ingress.configureCertmanager=false \
  --set gitlab-runner.install=true \
  --timeout 15m
```

## 四、配置 DevOps 流水线

### 在 KubeSphere 中创建 DevOps 项目

1. 工作台 → 创建 DevOps 项目
2. 项目名称：`orange-logistics`
3. 进入项目 → 流水线 → 创建流水线
4. 选择 "代码仓库" → 填入 GitLab 地址
5. 选择 Jenkinsfile 路径：`Jenkinsfile`

### 配置凭证

在 DevOps 项目 → 凭证 中添加：
- GitLab 账号密码
- Docker Registry 凭证
- kubeconfig（用于部署）

## 五、推送代码到本地 GitLab

```powershell
cd C:\Users\21779\Desktop\orange-logistics-platform
git remote add gitlab http://<gitlab-address>/root/orange-logistics-platform.git
git push gitlab --all
```

推送后 KubeSphere DevOps 会自动触发流水线。

## 六、KubeSphere 功能一览

安装完成后你将拥有：

| 功能 | 说明 |
|------|------|
| 应用管理 | 可视化管理所有微服务 |
| DevOps | Jenkins 流水线，代码推送自动构建部署 |
| 监控告警 | 内置 Prometheus + Grafana |
| 日志系统 | 集中日志查询 |
| 服务网格 | Istio 集成（可选） |
| 多租户 | 工作空间隔离 |
| 应用商店 | Helm 应用一键部署 |

## 七、资源需求

KubeSphere 全组件约需要额外：
- CPU: 2-4 核
- 内存: 4-8 GB
- 磁盘: 20 GB

最小安装（仅 Core）：
- CPU: 1 核
- 内存: 2 GB

## 八、卸载

```powershell
helm uninstall ks-core -n kubesphere-system
kubectl delete ns kubesphere-system
```
