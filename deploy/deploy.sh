#!/bin/bash
# deploy.sh - One-click deployment script for Orange Logistics Platform
set -e

NAMESPACE=${1:-orange-logistics}
ENVIRONMENT=${2:-dev}
IMAGE_TAG=${3:-latest}
REGISTRY=${HARBOR_REGISTRY:-harbor.orange-logistics.com}

echo "=========================================="
echo " Orange Logistics Platform Deployment"
echo " Namespace: ${NAMESPACE}"
echo " Environment: ${ENVIRONMENT}"
echo " Image Tag: ${IMAGE_TAG}"
echo "=========================================="

# 1. Create namespace
echo "[1/5] Creating namespace..."
kubectl create namespace ${NAMESPACE} --dry-run=client -o yaml | kubectl apply -f -

# 2. Deploy secrets
echo "[2/5] Deploying secrets..."
kubectl apply -f deploy/k8s/base/secrets.yaml -n ${NAMESPACE}

# 3. Deploy infrastructure
echo "[3/5] Deploying infrastructure..."
kubectl apply -f deploy/k8s/infrastructure/ -n ${NAMESPACE}

echo "Waiting for infrastructure to be ready..."
kubectl wait --for=condition=ready pod -l app=mysql -n ${NAMESPACE} --timeout=120s
kubectl wait --for=condition=ready pod -l app=redis -n ${NAMESPACE} --timeout=60s
kubectl wait --for=condition=ready pod -l app=nacos -n ${NAMESPACE} --timeout=120s
kubectl wait --for=condition=ready pod -l app=kafka -n ${NAMESPACE} --timeout=120s

# 4. Deploy microservices via Helm
echo "[4/5] Deploying microservices..."
VALUES_FILE="deploy/k8s/overlays/${ENVIRONMENT}/values-${ENVIRONMENT}.yaml"

HELM_ARGS="--set global.imageTag=${IMAGE_TAG} --set global.imageRegistry=${REGISTRY}"
if [ -f "${VALUES_FILE}" ]; then
    HELM_ARGS="${HELM_ARGS} -f ${VALUES_FILE}"
fi

helm upgrade --install orange-logistics ./deploy/helm/orange-logistics \
    --namespace ${NAMESPACE} \
    -f ./deploy/helm/orange-logistics/values.yaml \
    ${HELM_ARGS} \
    --wait --timeout 15m

# 5. Verify
echo "[5/5] Verifying deployment..."
kubectl get pods -n ${NAMESPACE}
echo ""
echo "Deployment complete!"
echo "Gateway: kubectl port-forward svc/logistics-gateway 8888:8888 -n ${NAMESPACE}"
