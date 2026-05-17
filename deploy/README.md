# CI/CD & Kubernetes Deployment Guide

## Architecture Overview

```
Git Push → Jenkins/GitLab CI → Build → Test → Docker Build → Push to Harbor → Helm Deploy to K8s
```

## Directory Structure

```
deploy/
├── cicd/                          # CI/CD related configs
├── helm/
│   └── orange-logistics/          # Helm Chart
│       ├── Chart.yaml
│       ├── values.yaml            # Default values
│       └── templates/
│           ├── deployment.yaml    # Deployment template (all 18 services)
│           ├── service.yaml       # Service template
│           ├── ingress.yaml       # Ingress template
│           └── hpa.yaml           # HorizontalPodAutoscaler
├── k8s/
│   ├── base/                      # Base K8s resources
│   │   ├── namespace.yaml
│   │   ├── configmap.yaml
│   │   └── secrets.yaml
│   ├── infrastructure/            # Stateful infrastructure
│   │   ├── mysql.yaml
│   │   ├── redis.yaml
│   │   ├── nacos.yaml
│   │   ├── kafka.yaml
│   │   ├── elasticsearch.yaml
│   │   ├── rocketmq.yaml
│   │   └── seata-spark.yaml
│   └── overlays/
│       ├── dev/values-dev.yaml    # Dev overrides (1 replica)
│       └── prod/values-prod.yaml  # Prod overrides (3 replicas, more resources)
└── deploy.sh                      # One-click deploy script
```

## Quick Start

### Prerequisites
- Kubernetes cluster (1.26+)
- Helm 3.x
- kubectl configured
- Harbor registry accessible
- Jenkins or GitLab CI runner

### Manual Deployment

```bash
# Deploy to dev
./deploy/deploy.sh orange-logistics-dev dev latest

# Deploy to production
./deploy/deploy.sh orange-logistics prod 42-abc1234
```

### Helm Commands

```bash
# Install
helm install orange-logistics ./deploy/helm/orange-logistics \
  --namespace orange-logistics --create-namespace

# Upgrade
helm upgrade orange-logistics ./deploy/helm/orange-logistics \
  --namespace orange-logistics \
  --set global.imageTag=1.0.1

# Rollback
helm rollback orange-logistics 1 --namespace orange-logistics

# Uninstall
helm uninstall orange-logistics --namespace orange-logistics
```

## CI/CD Pipeline

### Jenkins (Jenkinsfile)
- Triggered on push to `develop`, `release/*`, `main`
- Stages: Checkout → Build → Test → Docker Build → Deploy
- Production deploy requires manual approval

### GitLab CI (.gitlab-ci.yml)
- Same flow as Jenkins
- Uses GitLab environments for tracking
- Production deploy is manual trigger

### Pipeline Flow by Branch

| Branch | Build | Test | Docker | Deploy |
|--------|-------|------|--------|--------|
| feature/* | ✅ | ✅ | ❌ | ❌ |
| develop | ✅ | ✅ | ✅ | Dev (auto) |
| release/* | ✅ | ✅ | ✅ | Staging (auto) |
| main | ✅ | ✅ | ✅ | Prod (manual) |

## Kubernetes Features

### Auto-scaling (HPA)
- CPU threshold: 70%
- Memory threshold: 80%
- Min replicas: defined per service
- Max replicas: 3x min

### Health Checks
- **Startup Probe**: `/actuator/health` (30s delay, 20 retries)
- **Liveness Probe**: `/actuator/health/liveness` (120s delay)
- **Readiness Probe**: `/actuator/health/readiness` (60s delay)

### Rolling Update Strategy
- maxSurge: 1
- maxUnavailable: 0
- Zero-downtime deployment

### Monitoring
- Prometheus scraping via pod annotations
- Grafana dashboards auto-provisioned
- Actuator metrics exposed at `/actuator/prometheus`

## Harbor Setup

```bash
# Create project in Harbor
# Login
docker login harbor.orange-logistics.com

# Create K8s secret for image pull
kubectl create secret docker-registry harbor-credentials \
  --docker-server=harbor.orange-logistics.com \
  --docker-username=admin \
  --docker-password=<password> \
  --namespace=orange-logistics
```

## Useful Commands

```bash
# Check all pods
kubectl get pods -n orange-logistics

# Check service logs
kubectl logs -f deployment/logistics-gateway -n orange-logistics

# Scale a service
kubectl scale deployment logistics-order --replicas=5 -n orange-logistics

# Port forward for local access
kubectl port-forward svc/logistics-gateway 8888:8888 -n orange-logistics

# Check HPA status
kubectl get hpa -n orange-logistics
```
