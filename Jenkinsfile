pipeline {
    agent {
        kubernetes {
            yaml '''
                apiVersion: v1
                kind: Pod
                spec:
                  containers:
                  - name: maven
                    image: maven:3.9-eclipse-temurin-17
                    command: ['sleep', '99d']
                    volumeMounts:
                    - name: maven-cache
                      mountPath: /root/.m2
                  - name: python
                    image: python:3.12-slim
                    command: ['sleep', '99d']
                  - name: docker
                    image: docker:24-dind
                    securityContext:
                      privileged: true
                    env:
                    - name: DOCKER_TLS_CERTDIR
                      value: ""
                  - name: helm
                    image: alpine/helm:3.14
                    command: ['sleep', '99d']
                  volumes:
                  - name: maven-cache
                    persistentVolumeClaim:
                      claimName: maven-cache-pvc
            '''
        }
    }

    environment {
        HARBOR_REGISTRY = 'harbor.orange-logistics.com'
        HARBOR_CREDENTIALS = credentials('harbor-credentials')
        IMAGE_TAG = "${env.BUILD_NUMBER}-${env.GIT_COMMIT?.take(7) ?: 'latest'}"
        NAMESPACE = 'orange-logistics'
        HELM_RELEASE = 'orange-logistics'
        WECHAT_WEBHOOK = credentials('wechat-webhook-url')
        SONAR_URL = credentials('sonar-url')
        SONAR_TOKEN = credentials('sonar-token')
    }

    options {
        timeout(time: 45, unit: 'MINUTES')
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '30'))
        timestamps()
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                script {
                    env.GIT_BRANCH_NAME = env.GIT_BRANCH?.replaceAll('origin/', '') ?: 'unknown'
                    env.DEPLOY_ENV = env.GIT_BRANCH_NAME == 'main' ? 'prod' :
                                     env.GIT_BRANCH_NAME == 'develop' ? 'dev' :
                                     env.GIT_BRANCH_NAME.startsWith('release/') ? 'staging' : 'none'
                }
            }
        }

        stage('Build Java') {
            steps {
                container('maven') {
                    sh 'mvn clean package -DskipTests -B -q'
                }
            }
        }

        stage('Test') {
            parallel {
                stage('Java Unit Tests') {
                    steps {
                        container('maven') {
                            sh 'mvn test -B -pl "!orange-logistics-streaming"'
                        }
                    }
                    post {
                        always {
                            junit '**/target/surefire-reports/*.xml'
                        }
                    }
                }
                stage('Python Validation') {
                    steps {
                        container('python') {
                            sh '''
                                cd orange-logistics-ai-prediction
                                pip install -r requirements-docker.txt -q
                                python -c "from app.main import app; print('ai-prediction OK')"
                                cd ../orange-logistics-ai-agent
                                pip install -r requirements-docker.txt -q
                                python -c "from app.main import app; print('ai-agent OK')"
                            '''
                        }
                    }
                }
            }
        }

        stage('Code Quality') {
            when {
                anyOf {
                    branch 'main'
                    branch 'develop'
                }
            }
            steps {
                container('maven') {
                    sh """
                        mvn sonar:sonar \
                            -Dsonar.host.url=${SONAR_URL} \
                            -Dsonar.token=${SONAR_TOKEN} \
                            -Dsonar.projectKey=orange-logistics-platform \
                            -B
                    """
                }
            }
        }

        stage('Build & Push Images') {
            when {
                anyOf {
                    branch 'main'
                    branch 'develop'
                    branch pattern: 'release/.*'
                }
            }
            steps {
                container('docker') {
                    sh """
                        # Wait for Docker daemon
                        while ! docker info > /dev/null 2>&1; do sleep 1; done

                        echo '${HARBOR_CREDENTIALS_PSW}' | docker login ${HARBOR_REGISTRY} -u '${HARBOR_CREDENTIALS_USR}' --password-stdin

                        # Java microservices
                        MODULES="gateway service monitor transport dispatch billing customer notification report risk scheduler search config im auth order waybill warehouse"
                        for module in \$MODULES; do
                            echo "=== Building orange-logistics-\${module} ==="
                            docker build \
                                --build-arg BUILD_DATE=\$(date -u +%Y-%m-%dT%H:%M:%SZ) \
                                --build-arg GIT_COMMIT=${GIT_COMMIT} \
                                -t ${HARBOR_REGISTRY}/orange-logistics-\${module}:${IMAGE_TAG} \
                                -t ${HARBOR_REGISTRY}/orange-logistics-\${module}:latest \
                                ./orange-logistics-\${module}
                            docker push ${HARBOR_REGISTRY}/orange-logistics-\${module}:${IMAGE_TAG}
                            docker push ${HARBOR_REGISTRY}/orange-logistics-\${module}:latest
                        done

                        # AI services
                        echo "=== Building AI Prediction ==="
                        docker build \
                            -t ${HARBOR_REGISTRY}/orange-logistics-ai-prediction:${IMAGE_TAG} \
                            -t ${HARBOR_REGISTRY}/orange-logistics-ai-prediction:latest \
                            ./orange-logistics-ai-prediction
                        docker push ${HARBOR_REGISTRY}/orange-logistics-ai-prediction:${IMAGE_TAG}
                        docker push ${HARBOR_REGISTRY}/orange-logistics-ai-prediction:latest

                        echo "=== Building AI Agent ==="
                        docker build \
                            -t ${HARBOR_REGISTRY}/orange-logistics-ai-agent:${IMAGE_TAG} \
                            -t ${HARBOR_REGISTRY}/orange-logistics-ai-agent:latest \
                            ./orange-logistics-ai-agent
                        docker push ${HARBOR_REGISTRY}/orange-logistics-ai-agent:${IMAGE_TAG}
                        docker push ${HARBOR_REGISTRY}/orange-logistics-ai-agent:latest

                        docker logout ${HARBOR_REGISTRY}
                    """
                }
            }
        }

        stage('Deploy to Dev') {
            when { branch 'develop' }
            steps {
                container('helm') {
                    sh """
                        helm upgrade --install ${HELM_RELEASE} ./deploy/helm/orange-logistics \
                            --namespace ${NAMESPACE}-dev \
                            --create-namespace \
                            --set global.imageTag=${IMAGE_TAG} \
                            --set global.imageRegistry=${HARBOR_REGISTRY} \
                            -f ./deploy/helm/orange-logistics/values.yaml \
                            -f ./deploy/k8s/overlays/dev/values-dev.yaml \
                            --wait --timeout 10m

                        # Deploy AI services
                        kubectl apply -f deploy/k8s/infrastructure/ai-prediction.yaml -n ${NAMESPACE}-dev
                        kubectl apply -f deploy/k8s/infrastructure/ai-agent.yaml -n ${NAMESPACE}-dev
                        kubectl set image deployment/ai-prediction ai-prediction=${HARBOR_REGISTRY}/orange-logistics-ai-prediction:${IMAGE_TAG} -n ${NAMESPACE}-dev
                        kubectl set image deployment/ai-agent ai-agent=${HARBOR_REGISTRY}/orange-logistics-ai-agent:${IMAGE_TAG} -n ${NAMESPACE}-dev
                    """
                }
            }
            post {
                success {
                    sh """
                        curl -s -X POST '${WECHAT_WEBHOOK}' \
                            -H 'Content-Type: application/json' \
                            -d '{"msgtype":"markdown","markdown":{"content":"## ✅ Dev 部署成功\\n> 版本: ${IMAGE_TAG}\\n> 分支: ${GIT_BRANCH_NAME}"}}'
                    """
                }
            }
        }

        stage('Integration Tests') {
            when { branch 'develop' }
            steps {
                container('maven') {
                    sh """
                        mvn verify -Pintegration-test -B \
                            -Dtest.gateway.url=http://logistics-gateway.${NAMESPACE}-dev.svc.cluster.local:8888
                    """
                }
            }
        }

        stage('Deploy to Staging') {
            when { branch pattern: 'release/.*' }
            steps {
                container('helm') {
                    sh """
                        helm upgrade --install ${HELM_RELEASE} ./deploy/helm/orange-logistics \
                            --namespace ${NAMESPACE}-staging \
                            --create-namespace \
                            --set global.imageTag=${IMAGE_TAG} \
                            --set global.imageRegistry=${HARBOR_REGISTRY} \
                            -f ./deploy/helm/orange-logistics/values.yaml \
                            --wait --timeout 10m

                        kubectl apply -f deploy/k8s/infrastructure/ai-prediction.yaml -n ${NAMESPACE}-staging
                        kubectl apply -f deploy/k8s/infrastructure/ai-agent.yaml -n ${NAMESPACE}-staging
                        kubectl set image deployment/ai-prediction ai-prediction=${HARBOR_REGISTRY}/orange-logistics-ai-prediction:${IMAGE_TAG} -n ${NAMESPACE}-staging
                        kubectl set image deployment/ai-agent ai-agent=${HARBOR_REGISTRY}/orange-logistics-ai-agent:${IMAGE_TAG} -n ${NAMESPACE}-staging
                    """
                }
            }
        }

        stage('Deploy to Production') {
            when { branch 'main' }
            input {
                message "确认部署到生产环境?"
                ok "确认部署"
                submitter "admin,devops,zhangsan"
                parameters {
                    string(name: 'CONFIRM', defaultValue: '', description: '输入 DEPLOY 确认')
                }
            }
            steps {
                script {
                    if (env.CONFIRM != 'DEPLOY') {
                        error('未确认部署，流水线终止')
                    }
                }
                container('helm') {
                    sh """
                        # 记录当前版本用于回滚
                        helm history ${HELM_RELEASE} -n ${NAMESPACE} --max 1 || true

                        helm upgrade --install ${HELM_RELEASE} ./deploy/helm/orange-logistics \
                            --namespace ${NAMESPACE} \
                            --set global.imageTag=${IMAGE_TAG} \
                            --set global.imageRegistry=${HARBOR_REGISTRY} \
                            -f ./deploy/helm/orange-logistics/values.yaml \
                            -f ./deploy/k8s/overlays/prod/values-prod.yaml \
                            --wait --timeout 15m

                        kubectl apply -f deploy/k8s/infrastructure/ai-prediction.yaml -n ${NAMESPACE}
                        kubectl apply -f deploy/k8s/infrastructure/ai-agent.yaml -n ${NAMESPACE}
                        kubectl set image deployment/ai-prediction ai-prediction=${HARBOR_REGISTRY}/orange-logistics-ai-prediction:${IMAGE_TAG} -n ${NAMESPACE}
                        kubectl set image deployment/ai-agent ai-agent=${HARBOR_REGISTRY}/orange-logistics-ai-agent:${IMAGE_TAG} -n ${NAMESPACE}
                        kubectl rollout status deployment --all -n ${NAMESPACE} --timeout=300s
                    """
                }
            }
            post {
                success {
                    sh """
                        curl -s -X POST '${WECHAT_WEBHOOK}' \
                            -H 'Content-Type: application/json' \
                            -d '{"msgtype":"markdown","markdown":{"content":"## 🚀 生产部署成功\\n> 版本: ${IMAGE_TAG}\\n> 操作人: ${BUILD_USER}\\n> [查看](${BUILD_URL})"}}'
                    """
                }
                failure {
                    sh """
                        curl -s -X POST '${WECHAT_WEBHOOK}' \
                            -H 'Content-Type: application/json' \
                            -d '{"msgtype":"markdown","markdown":{"content":"## ❌ 生产部署失败!\\n> 版本: ${IMAGE_TAG}\\n> 请立即检查!\\n> [日志](${BUILD_URL}console)"}}'
                    """
                }
            }
        }
    }

    post {
        always {
            cleanWs()
        }
    }
}
