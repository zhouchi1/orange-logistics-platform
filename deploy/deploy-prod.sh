#!/bin/bash
# ============================================================
# Orange Logistics Platform - 生产环境部署脚本
# ============================================================
# 用法:
#   ./deploy-prod.sh init        # 首次初始化（安装基础设施）
#   ./deploy-prod.sh deploy      # 全量部署
#   ./deploy-prod.sh update <svc> # 更新单个服务
#   ./deploy-prod.sh rollback    # 回滚到上一版本
#   ./deploy-prod.sh status      # 查看状态
#   ./deploy-prod.sh scale <svc> <n>  # 扩缩容
# ============================================================

set -euo pipefail

# ===== 配置 =====
NAMESPACE="orange-logistics"
HARBOR_REGISTRY="harbor.orange-logistics.com"
HELM_RELEASE="orange-logistics"
HELM_CHART="./deploy/helm/orange-logistics"
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
DEPLOY_LOG="/var/log/orange-logistics/deploy-$(date +%Y%m%d-%H%M%S).log"

# 颜色
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log() { echo -e "${GREEN}[$(date +%H:%M:%S)]${NC} $*" | tee -a "$DEPLOY_LOG"; }
warn() { echo -e "${YELLOW}[$(date +%H:%M:%S)] WARN:${NC} $*" | tee -a "$DEPLOY_LOG"; }
error() { echo -e "${RED}[$(date +%H:%M:%S)] ERROR:${NC} $*" | tee -a "$DEPLOY_LOG"; exit 1; }

# ===== 前置检查 =====
check_prerequisites() {
    log "检查前置条件..."
    command -v kubectl >/dev/null 2>&1 || error "kubectl 未安装"
    command -v helm >/dev/null 2>&1 || error "helm 未安装"
    command -v docker >/dev/null 2>&1 || error "docker 未安装"
    kubectl cluster-info >/dev/null 2>&1 || error "无法连接 K8s 集群"
    log "前置检查通过 ✓"
}

# ===== 首次初始化 =====
init() {
    check_prerequisites
    log "=== 初始化生产环境 ==="

    # 创建命名空间
    kubectl create namespace $NAMESPACE --dry-run=client -o yaml | kubectl apply -f -

    # 部署基础配置
    log "部署 ConfigMap 和 Secrets..."
    kubectl apply -f deploy/k8s/base/configmap.yaml
    kubectl apply -f deploy/k8s/base/secrets.yaml

    # 部署基础设施
    log "部署基础设施（MySQL, Redis, Nacos, Kafka, ES, RocketMQ, Seata, Spark）..."
    kubectl apply -f deploy/k8s/infrastructure/mysql.yaml
    kubectl apply -f deploy/k8s/infrastructure/redis.yaml
    kubectl apply -f deploy/k8s/infrastructure/nacos.yaml
    kubectl apply -f deploy/k8s/infrastructure/kafka.yaml
    kubectl apply -f deploy/k8s/infrastructure/elasticsearch.yaml
    kubectl apply -f deploy/k8s/infrastructure/rocketmq.yaml
    kubectl apply -f deploy/k8s/infrastructure/seata-spark.yaml
    kubectl apply -f deploy/k8s/infrastructure/spark.yaml
    kubectl apply -f deploy/k8s/infrastructure/xxl-job-admin.yaml

    # 等待基础设施就绪
    log "等待基础设施就绪..."
    kubectl wait --for=condition=ready pod -l app=mysql -n $NAMESPACE --timeout=300s
    kubectl wait --for=condition=ready pod -l app=redis -n $NAMESPACE --timeout=120s
    kubectl wait --for=condition=ready pod -l app=nacos -n $NAMESPACE --timeout=180s

    # 初始化数据库
    log "初始化 XXL-JOB 数据库..."
    kubectl exec -n $NAMESPACE $(kubectl get pod -l app=mysql -n $NAMESPACE -o jsonpath='{.items[0].metadata.name}') \
        -- mysql -uroot -p'orange_logistics_2024' < scripts/init-xxl-job.sql

    log "=== 初始化完成 ==="
}

# ===== 全量部署 =====
deploy() {
    check_prerequisites
    local IMAGE_TAG="${1:-latest}"
    log "=== 开始全量部署 (版本: $IMAGE_TAG) ==="

    # 构建 Java 服务
    log "Maven 构建..."
    cd "$PROJECT_DIR"
    mvn clean package -DskipTests -B -q

    # 构建 Docker 镜像
    log "构建 Docker 镜像..."
    MODULES="gateway service monitor transport dispatch billing customer notification report risk scheduler search config im auth order waybill warehouse"
    for module in $MODULES; do
        log "  构建 orange-logistics-${module}..."
        docker build -t ${HARBOR_REGISTRY}/orange-logistics-${module}:${IMAGE_TAG} \
                     -t ${HARBOR_REGISTRY}/orange-logistics-${module}:latest \
                     ./orange-logistics-${module}
        docker push ${HARBOR_REGISTRY}/orange-logistics-${module}:${IMAGE_TAG}
        docker push ${HARBOR_REGISTRY}/orange-logistics-${module}:latest
    done

    # 构建 AI 服务
    log "构建 AI 服务镜像..."
    docker build -t ${HARBOR_REGISTRY}/orange-logistics-ai-prediction:${IMAGE_TAG} \
                 -t ${HARBOR_REGISTRY}/orange-logistics-ai-prediction:latest \
                 ./orange-logistics-ai-prediction
    docker push ${HARBOR_REGISTRY}/orange-logistics-ai-prediction:${IMAGE_TAG}
    docker push ${HARBOR_REGISTRY}/orange-logistics-ai-prediction:latest

    docker build -t ${HARBOR_REGISTRY}/orange-logistics-ai-agent:${IMAGE_TAG} \
                 -t ${HARBOR_REGISTRY}/orange-logistics-ai-agent:latest \
                 ./orange-logistics-ai-agent
    docker push ${HARBOR_REGISTRY}/orange-logistics-ai-agent:${IMAGE_TAG}
    docker push ${HARBOR_REGISTRY}/orange-logistics-ai-agent:latest

    # Helm 部署
    log "Helm 部署微服务..."
    helm upgrade --install ${HELM_RELEASE} ${HELM_CHART} \
        --namespace ${NAMESPACE} \
        --set global.imageTag=${IMAGE_TAG} \
        --set global.imageRegistry=${HARBOR_REGISTRY} \
        -f ${HELM_CHART}/values.yaml \
        -f deploy/k8s/overlays/prod/values-prod.yaml \
        --wait --timeout 15m

    # 部署 AI 服务
    log "部署 AI 服务..."
    kubectl apply -f deploy/k8s/infrastructure/ai-prediction.yaml
    kubectl apply -f deploy/k8s/infrastructure/ai-agent.yaml
    kubectl set image deployment/ai-prediction ai-prediction=${HARBOR_REGISTRY}/orange-logistics-ai-prediction:${IMAGE_TAG} -n $NAMESPACE
    kubectl set image deployment/ai-agent ai-agent=${HARBOR_REGISTRY}/orange-logistics-ai-agent:${IMAGE_TAG} -n $NAMESPACE

    # 等待所有 Pod 就绪
    log "等待所有服务就绪..."
    kubectl rollout status deployment --all -n $NAMESPACE --timeout=300s

    log "=== 部署完成 ✓ ==="
    status
}

# ===== 更新单个服务 =====
update() {
    check_prerequisites
    local SERVICE="$1"
    local IMAGE_TAG="${2:-latest}"
    log "=== 更新服务: $SERVICE (版本: $IMAGE_TAG) ==="

    cd "$PROJECT_DIR"

    if [[ "$SERVICE" == "ai-prediction" || "$SERVICE" == "ai-agent" ]]; then
        # Python AI 服务
        docker build -t ${HARBOR_REGISTRY}/orange-logistics-${SERVICE}:${IMAGE_TAG} ./orange-logistics-${SERVICE}
        docker push ${HARBOR_REGISTRY}/orange-logistics-${SERVICE}:${IMAGE_TAG}
        kubectl set image deployment/${SERVICE} ${SERVICE}=${HARBOR_REGISTRY}/orange-logistics-${SERVICE}:${IMAGE_TAG} -n $NAMESPACE
    else
        # Java 微服务
        mvn package -pl orange-logistics-${SERVICE} -am -DskipTests -B -q
        docker build -t ${HARBOR_REGISTRY}/orange-logistics-${SERVICE}:${IMAGE_TAG} ./orange-logistics-${SERVICE}
        docker push ${HARBOR_REGISTRY}/orange-logistics-${SERVICE}:${IMAGE_TAG}
        kubectl set image deployment/logistics-${SERVICE} logistics-${SERVICE}=${HARBOR_REGISTRY}/orange-logistics-${SERVICE}:${IMAGE_TAG} -n $NAMESPACE
    fi

    kubectl rollout status deployment -l app=logistics-${SERVICE} -n $NAMESPACE --timeout=180s 2>/dev/null || \
    kubectl rollout status deployment/${SERVICE} -n $NAMESPACE --timeout=180s 2>/dev/null || true

    log "=== 服务 $SERVICE 更新完成 ✓ ==="
}

# ===== 回滚 =====
rollback() {
    check_prerequisites
    log "=== 回滚到上一版本 ==="
    helm rollback ${HELM_RELEASE} -n $NAMESPACE
    kubectl rollout status deployment --all -n $NAMESPACE --timeout=300s
    log "=== 回滚完成 ✓ ==="
    status
}

# ===== 状态查看 =====
status() {
    echo ""
    echo "========== Orange Logistics Platform Status =========="
    echo ""
    kubectl get pods -n $NAMESPACE -o wide
    echo ""
    echo "===== Helm Release ====="
    helm status ${HELM_RELEASE} -n $NAMESPACE --short 2>/dev/null || echo "Helm release not found"
    echo ""
    echo "===== Services ====="
    kubectl get svc -n $NAMESPACE
    echo ""
}

# ===== 扩缩容 =====
scale() {
    local SERVICE="$1"
    local REPLICAS="$2"
    log "扩缩容: $SERVICE -> $REPLICAS 副本"
    kubectl scale deployment/${SERVICE} --replicas=${REPLICAS} -n $NAMESPACE
    kubectl rollout status deployment/${SERVICE} -n $NAMESPACE --timeout=120s
    log "扩缩容完成 ✓"
}

# ===== 主入口 =====
mkdir -p /var/log/orange-logistics

case "${1:-help}" in
    init)
        init
        ;;
    deploy)
        deploy "${2:-latest}"
        ;;
    update)
        [[ -z "${2:-}" ]] && error "用法: $0 update <service-name> [image-tag]"
        update "$2" "${3:-latest}"
        ;;
    rollback)
        rollback
        ;;
    status)
        status
        ;;
    scale)
        [[ -z "${2:-}" || -z "${3:-}" ]] && error "用法: $0 scale <service-name> <replicas>"
        scale "$2" "$3"
        ;;
    *)
        echo "用法: $0 {init|deploy|update|rollback|status|scale}"
        echo ""
        echo "  init              首次初始化生产环境"
        echo "  deploy [tag]      全量构建并部署"
        echo "  update <svc> [tag] 更新单个服务"
        echo "  rollback          回滚到上一版本"
        echo "  status            查看集群状态"
        echo "  scale <svc> <n>   扩缩容"
        echo ""
        echo "示例:"
        echo "  $0 deploy 1.0.0"
        echo "  $0 update dispatch 1.0.1"
        echo "  $0 update ai-prediction latest"
        echo "  $0 scale logistics-gateway 5"
        echo "  $0 rollback"
        ;;
esac
